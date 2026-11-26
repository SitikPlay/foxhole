package com.sitikplay.foxhole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        android.util.Log.i("FoxholeBoot", "$action received, scheduling autostart & watchdog")

        WatchdogWorker.syncSchedule(context)

        val isUpdate = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val data = Data.Builder()
            .putBoolean(AutostartWorker.EXTRA_FORCE_AFTER_UPDATE, isUpdate)
            .build()

        val builder = OneTimeWorkRequest.Builder(AutostartWorker::class.java)
            .setInputData(data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            AutostartWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }
}
