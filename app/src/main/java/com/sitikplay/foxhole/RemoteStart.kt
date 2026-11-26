package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings as SysSettings
import java.util.concurrent.TimeUnit

object RemoteStart {

    private const val WDS_BOOT_MS = 6_000L
    private const val MDNS_TIMEOUT_MS = 10_000L
    private const val PING_TIMEOUT_MS = 15_000L

    fun wifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        // А13: раньше требовали NET_CAPABILITY_VALIDATED — на Wi-Fi без проверки
        // (captive portal) автозапуск вечно ждал «подключения». Достаточно
        // самого транспорта Wi-Fi.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun permissionGranted(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun canManageWifiDebugging(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permissionGranted(ctx)

    private fun readWifiDebugging(ctx: Context): Int = try {
        SysSettings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0)
    } catch (e: Exception) {
        0
    }

    private fun writeWifiDebugging(ctx: Context, value: Int): Boolean = try {
        SysSettings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", value)
        true
    } catch (e: Exception) {
        false
    }

    fun run(
        ctx: Context,
        wifiTimeoutMs: Long = 120_000L,
        wifiStepMs: Long = 5_000L,
        disableWifiAfter: Boolean = true
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "ERR|" + ctx.getString(R.string.t240)
        }
        if (!FoxholeAdb.hasKey(ctx)) {
            return "ERR|" + ctx.getString(R.string.t328)
        }
        val wifiAt = System.currentTimeMillis()
        while (!wifiConnected(ctx) && System.currentTimeMillis() - wifiAt < wifiTimeoutMs) {
            try {
                Thread.sleep(wifiStepMs)
            } catch (e: InterruptedException) {
                // А4: прерывание фонового потока раньше убивало весь процесс.
                return "ERR|" + ctx.getString(R.string.t460)
            }
        }
        if (!wifiConnected(ctx)) {
            return "ERR|" + ctx.getString(R.string.t329)
        }
        if (!permissionGranted(ctx)) {
            return "ERR|" + ctx.getString(R.string.t330)
        }
        val wasOn = readWifiDebugging(ctx) == 1
        val turnedOn = !wasOn
        if (turnedOn && !writeWifiDebugging(ctx, 1)) {
            return "ERR|" + ctx.getString(R.string.t331)
        }
        if (turnedOn) {
            try {
                Thread.sleep(WDS_BOOT_MS)
            } catch (e: InterruptedException) {
                // А4: как и остальные sleep здесь — прерывание не должно
                // убивать поток с исключением.
                throw InterruptedException(ctx.getString(R.string.t460))
            }
        }
        val result = try {
            runBoot(ctx)
        } catch (e: InterruptedException) {
            // А4: прерывание фонового потока раньше убивало весь процесс
            // (неперехваченное исключение в потоке). Гасим поток штатно.
            if (turnedOn && disableWifiAfter) {
                try {
                    writeWifiDebugging(ctx, 0)
                } catch (ignored: Exception) {
                }
            }
            "ERR|" + ctx.getString(R.string.t460)
        } catch (e: Exception) {
            // А14: вместо имени класса и сырого текста — понятное сообщение
            "ERR|" + friendlyAdbError(ctx, e)
        }
        if (turnedOn && disableWifiAfter) {
            try {
                writeWifiDebugging(ctx, 0)
            } catch (e: Exception) {
            }
        }
        return result
    }

    private fun runBoot(ctx: Context): String {
        val adb = FoxholeAdb.get(ctx)
        adb.setTimeout(12, TimeUnit.SECONDS)

        var conn = Discover.connectWithFallback(
            ctx = ctx,
            adb = adb,
            mdnsTimeoutMs = MDNS_TIMEOUT_MS,
            scanBudgetMs = 20_000L,
            excludePorts = setOf(Settings.port(ctx))
        )
        if (conn.status == Discover.ConnectStatus.FAILED && wifiConnected(ctx)) {
            Thread.sleep(3_000)
            conn = Discover.connectWithFallback(
                ctx = ctx,
                adb = adb,
                mdnsTimeoutMs = 4_000L,
                scanBudgetMs = 20_000L,
                excludePorts = setOf(Settings.port(ctx))
            )
        }

        when (conn.status) {
            Discover.ConnectStatus.CONNECTED -> Unit
            Discover.ConnectStatus.PAIRING_REQUIRED -> return "ERR|" + ctx.getString(R.string.t090)
            Discover.ConnectStatus.AUTH_FAILED -> return "ERR|" + ctx.getString(R.string.t091)
            Discover.ConnectStatus.WIFI_DISABLED -> return "ERR|" + ctx.getString(R.string.t345)
            Discover.ConnectStatus.FAILED -> {
                val err = conn.lastError
                return if (err != null) {
                    "ERR|" + friendlyAdbError(ctx, err)
                } else {
                    "ERR|" + ctx.getString(R.string.t334)
                }
            }
        }

        val host = conn.host ?: "127.0.0.1"
        val port = conn.port
        // А8: после подключения adb-соединение должно закрываться на любом
        // пути выхода — раньше при неудаче записи скрипта или таймауте ping
        // соединение оставалось висеть.
        try {
            val path = ServerScript.write(ctx) ?: return "ERR|" + ctx.getString(R.string.t336)
            try {
                val stream = adb.openStream("shell:sh \"$path\"")
                Thread.sleep(4000)
                runCatching { stream.close() }
            } catch (e: Exception) {
                return "ERR|" + ctx.getString(R.string.t337, e.message ?: "")
            }
            val pingAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - pingAt < PING_TIMEOUT_MS) {
                val res = ServerClient.request(ctx, "ping")
                if (res.startsWith("OK pong")) {
                    return "OK|" + ctx.getString(R.string.t338, host, port)
                }
                Thread.sleep(1_000)
            }
            return "ERR|" + ctx.getString(R.string.t339)
        } finally {
            runCatching { adb.disconnect() }
        }
    }
}
