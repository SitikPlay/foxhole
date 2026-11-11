package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings as SysSettings
import io.github.muntashirakon.adb.android.AdbMdns
import java.util.concurrent.TimeUnit

object RemoteStart {

    private const val WDS_BOOT_MS = 6_000L
    private const val MDNS_TIMEOUT_MS = 10_000L
    private const val PING_TIMEOUT_MS = 15_000L

    fun wifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
            Thread.sleep(wifiStepMs)
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
            Thread.sleep(WDS_BOOT_MS)
        }
        val result = try {
            runBoot(ctx)
        } catch (e: Exception) {
            "ERR|${e.javaClass.simpleName}: ${e.message ?: ""}"
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
        var service: Pair<String, Int>? = null
        for (attempt in 0 until 2) {
            service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Discover.service(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, MDNS_TIMEOUT_MS)
            } else {
                null
            }
            if (service != null) break
            if (attempt == 0) {
                Thread.sleep(5_000)
                if (!wifiConnected(ctx)) return "ERR|" + ctx.getString(R.string.t333)
            }
        }

        if (service == null) {
            service = Settings.parseWdsAddress(Settings.wdsAddress(ctx))
        }
        if (service == null) {
            return "ERR|" + ctx.getString(R.string.t334)
        }
        val host = service.first
        val port = service.second
        val adb = FoxholeAdb.get(ctx)

        adb.setTimeout(12, TimeUnit.SECONDS)
        val connected = try {
            adb.connect(host, port)
        } catch (e: Exception) {
            false
        }
        if (!connected) {
            return "ERR|" + ctx.getString(R.string.t335)
        }
        val path = ServerScript.write(ctx) ?: return "ERR|" + ctx.getString(R.string.t336)
        try {
            val stream = adb.openStream("shell:sh \"$path\"")
            Thread.sleep(4000)
            runCatching { stream.close() }
            adb.disconnect()
        } catch (e: Exception) {
            return "ERR|" + ctx.getString(R.string.t337, e.message ?: "")
        }
        val pingAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - pingAt < PING_TIMEOUT_MS) {
            val res = ServerClient.request(ctx, "ping")
            if (res.startsWith("OK pong")) {
                return "OK|" + ctx.getString(R.string.t338, service.first, service.second)
            }
            Thread.sleep(1_000)
        }
        return "ERR|" + ctx.getString(R.string.t339)
    }
}
