package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object DeviceReport {

    fun report(ctx: Context): String {
        var version = "?"
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            version = pi.versionName ?: "?"
        } catch (e: Exception) {
        }

        val server = try {
            if (ServerClient.request(ctx, "ping").startsWith("OK pong"))
                ctx.getString(R.string.t434, Settings.port(ctx))
            else ctx.getString(R.string.t435)
        } catch (e: Exception) {
            ctx.getString(R.string.t435)
        }

        val pairing = ctx.getString(
            if (FoxholeAdb.hasKey(ctx)) R.string.t436 else R.string.t437
        )

        val wss = ctx.getString(
            if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            ) R.string.t438 else R.string.t439
        )

        val autostart = ctx.getString(
            if (Settings.bootStart(ctx)) R.string.t433 else R.string.t440
        )

        fun line(label: Int, value: String): String =
            ctx.getString(R.string.t427, ctx.getString(label), value)

        return listOf(
            line(R.string.t425, "${Build.MANUFACTURER} / ${Build.MODEL}"),
            line(R.string.t426, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            line(R.string.t428, ctx.getString(R.string.app_name) + " " + version),
            line(R.string.t429, server),
            line(R.string.t430, pairing),
            line(R.string.t431, wss),
            line(R.string.t432, autostart)
        ).joinToString("\n")
    }
}
