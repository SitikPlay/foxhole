package com.sitikplay.foxhole

import android.app.Application
import android.content.Context
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class DeletedCleanupWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val n = DeletedStore.cleanup(applicationContext)
        if (n > 0) {
            AccessStore.log(applicationContext, "корзина: автоочистка — удалено записей: $n")
        }
        return Result.success()
    }

    companion object {
        const val NAME = "foxhole_deleted_cleanup"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequest.Builder(
                DeletedCleanupWorker::class.java, 24, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

class FoxholeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DeletedCleanupWorker.schedule(this)
    }
}

