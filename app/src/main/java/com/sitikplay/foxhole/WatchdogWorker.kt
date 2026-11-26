package com.sitikplay.foxhole

import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val NAME = "foxhole_server_watchdog"

        fun syncSchedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (Settings.watchdog(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req
                )
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }

    private val ctx: Context = applicationContext

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Result.success()
        if (!Settings.watchdog(ctx)) return Result.success()
        if (!FoxholeAdb.hasKey(ctx)) return Result.success()
        if (!RemoteStart.canManageWifiDebugging(ctx)) return Result.success()

        val bootCount = try {
            android.provider.Settings.Global.getInt(
                ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1
            )
        } catch (_: Exception) {
            -1
        }
        if (bootCount in 0..Int.MAX_VALUE && Settings.stoppedBootCount(ctx) == bootCount) {
            return Result.success()
        }

        val alive = try {
            ServerClient.request(ctx, "ping").startsWith("OK pong")
        } catch (_: Exception) {
            false
        }
        if (alive) return Result.success()

        if (!RemoteStart.wifiConnected(ctx)) return Result.success()

        val result = RemoteStart.run(
            ctx,
            wifiTimeoutMs = 20_000L,
            wifiStepMs = 2_000L,
            disableWifiAfter = Settings.bootDisableWifi(ctx)
        )
        if (result.startsWith("OK")) {
            AccessStore.log(ctx, "watchdog: ${result.substringAfter("OK|")}")
            ServerTileService.requestRefresh(ctx)
        } else {
            AccessStore.log(ctx, "watchdog: ${result.substringAfter("ERR|").trim()}")
        }
        return Result.success()
    }
}
