package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings as SysSettings
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.muntashirakon.adb.android.AdbMdns

class AutostartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val NAME = "foxhole_boot_autostart"
        private const val WIFI_TIMEOUT_MS = 120_000L
        private const val WIFI_STEP_MS = 5_000L
        private const val WDS_BOOT_MS = 6_000L
        private const val MDNS_TIMEOUT_MS = 10_000L
        private const val PING_TIMEOUT_MS = 15_000L
    }

    private val ctx: Context = applicationContext

    private fun permissionGranted(): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun wifiConnected(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun settingName(): String = "adb_wifi_enabled"

    private fun readWifiDebugging(): Int = try {
        SysSettings.Global.getInt(ctx.contentResolver, settingName(), 0)
    } catch (e: Exception) {
        0
    }

    private fun writeWifiDebugging(value: Int): Boolean = try {
        SysSettings.Global.putInt(ctx.contentResolver, settingName(), value)
        true
    } catch (e: Exception) {
        false
    }

    override fun doWork(): Result {
        if (android.os.Build.VERSION.SDK_INT < 30) return Result.success()
        if (!Settings.bootStart(ctx)) {
            return Result.success()
        }
        AccessStore.log(ctx, "автозапуск: перезагрузка")

        val bootCount = try {
            SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
        if (Settings.autostartBootCount(ctx) == bootCount) {
            AccessStore.log(ctx, "автозапуск: пропущен (уже отработал в этой загрузке)")
            return Result.success()
        }
        if (bootCount in 0..Int.MAX_VALUE && Settings.stoppedBootCount(ctx) == bootCount) {
            AccessStore.log(ctx, "автозапуск: пропущен (сервер остановлен вручную)")
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }
        try {
            if (ServerClient.request(ctx, "ping").startsWith("OK pong")) {
                AccessStore.log(ctx, "автозапуск: сервер уже работает")
                Settings.setAutostartBootCount(ctx, bootCount)
                return Result.success()
            }
        } catch (e: Exception) {
        }

        if (!FoxholeAdb.hasKey(ctx)) {
            AccessNotifications.notifyAutostart(
                ctx, false,
                "Нет спаренного ADB-ключа. Спарь телефон (Запуск без ПК), затем включи этот переключатель заново."
            )
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }

        val wifiAt = System.currentTimeMillis()
        while (!wifiConnected() && System.currentTimeMillis() - wifiAt < WIFI_TIMEOUT_MS) {
            Thread.sleep(WIFI_STEP_MS)
        }
        if (!wifiConnected()) {
            AccessNotifications.notifyAutostart(ctx, false, "Нет Wi-Fi — сервер не запущен")
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }

        if (!permissionGranted()) {
            AccessNotifications.notifyAutostart(
                ctx, false,
                "Нет WRITE_SECURE_SETTINGS: включи переключатель автозапуска заново (сервер должен работать)."
            )
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }
        val wasOn = readWifiDebugging() == 1
        val turnedOn = !wasOn
        if (turnedOn && !writeWifiDebugging(1)) {
            AccessNotifications.notifyAutostart(ctx, false, "Не удалось включить беспроводную отладку")
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }
        if (turnedOn) {
            Thread.sleep(WDS_BOOT_MS)
        }

        val result = try {
            runBoot()
        } catch (e: Exception) {
            "ERR|${e.javaClass.simpleName}: ${e.message ?: ""}"
        }

        if (turnedOn && Settings.bootDisableWifi(ctx)) {
            try {
                writeWifiDebugging(0)
            } catch (e: Exception) {
            }
        }

        Settings.setAutostartBootCount(ctx, bootCount)
        return if (result.startsWith("OK")) {
            AccessStore.log(ctx, "автозапуск: $result")
            AccessNotifications.notifyAutostart(ctx, true, result.substringAfter("OK|"))
            Result.success()
        } else {
            AccessStore.log(ctx, "автозапуск: $result")
            AccessNotifications.notifyAutostart(ctx, false, result.substringAfter("ERR|").trim())
            Result.success()
        }
    }

    private fun runBoot(): String {
        var service: Pair<String, Int>? = null
        for (attempt in 0 until 2) {
            service = Discover.service(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, MDNS_TIMEOUT_MS)
            if (service != null) break
            if (attempt == 0) {
                Thread.sleep(5_000)
                if (!wifiConnected()) return "ERR|Wi-Fi отключился во время автозапуска"
            }
        }
        if (service == null) {
            return "ERR|Служба подключения не найдена: беспроводная отладка включена?"
        }
        val adb = FoxholeAdb.get(ctx)
        val connected = try {
            adb.connectTls(ctx, 10_000)
        } catch (e: Exception) {
            false
        }
        if (!connected) {
            return "ERR|ADB не принял подключение ключом: спарь телефон заново"
        }
        val path = ServerScript.write(ctx) ?: return "ERR|Не удалось записать start.sh"
        try {
            val stream = adb.openStream("shell:sh \"$path\"")
            Thread.sleep(4000)
            runCatching { stream.close() }
            adb.disconnect()
        } catch (e: Exception) {
            return "ERR|Не удалось выполнить start.sh: ${e.message}"
        }
        val pingAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - pingAt < PING_TIMEOUT_MS) {
            val res = ServerClient.request(ctx, "ping")
            if (res.startsWith("OK pong")) {
                return "OK|Сервер запущен и отвечает (Wi-Fi, adb ${service.first}:${service.second})"
            }
            Thread.sleep(1_000)
        }
        return "ERR|Сервер запущен, но не отвечает"
    }
}

