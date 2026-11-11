package com.sitikplay.foxhole

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AutostartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val NAME = "foxhole_boot_autostart"
    }

    private val ctx: Context = applicationContext

    override fun doWork(): Result {
        if (android.os.Build.VERSION.SDK_INT < 30) return Result.success()
        if (!Settings.bootStart(ctx)) {
            return Result.success()
        }
        AccessStore.log(ctx, ctx.getString(R.string.t324))

        val bootCount = try {
            android.provider.Settings.Global.getInt(
                ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1
            )
        } catch (e: Exception) {
            -1
        }
        if (Settings.autostartBootCount(ctx) == bootCount) {
            AccessStore.log(ctx, ctx.getString(R.string.t325))
            return Result.success()
        }
        if (bootCount in 0..Int.MAX_VALUE && Settings.stoppedBootCount(ctx) == bootCount) {
            AccessStore.log(ctx, ctx.getString(R.string.t326))
            Settings.setAutostartBootCount(ctx, bootCount)
            return Result.success()
        }
        try {
            if (ServerClient.request(ctx, "ping").startsWith("OK pong")) {
                AccessStore.log(ctx, ctx.getString(R.string.t327))
                Settings.setAutostartBootCount(ctx, bootCount)
                return Result.success()
            }
        } catch (e: Exception) {
        }

        Settings.setAutostartBootCount(ctx, bootCount)

        val result = RemoteStart.run(
            ctx,
            wifiTimeoutMs = 120_000L,
            wifiStepMs = 5_000L,
            disableWifiAfter = Settings.bootDisableWifi(ctx)
        )

        return if (result.startsWith("OK")) {
            AccessStore.log(ctx, ctx.getString(R.string.t332, result))
            AccessNotifications.notifyAutostart(ctx, true, result.substringAfter("OK|"))
            Result.success()
        } else {
            AccessStore.log(ctx, ctx.getString(R.string.t332, result))
            AccessNotifications.notifyAutostart(ctx, false, result.substringAfter("ERR|").trim())
            Result.success()
        }
    }
}
