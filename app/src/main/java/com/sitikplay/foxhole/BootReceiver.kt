package com.sitikplay.foxhole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        android.util.Log.i("FoxholeBoot", "BOOT_COMPLETED received, scheduling autostart work")
        val request = androidx.work.OneTimeWorkRequest.Builder(AutostartWorker::class.java)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            AutostartWorker.NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

