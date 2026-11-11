package com.sitikplay.foxhole

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object AccessNotifications {

    const val CHANNEL_ACCESS = "foxhole_access"
    const val CHANNEL_AUTOSTART = "foxhole_autostart"

    const val ACTION_ALLOW = "com.sitikplay.foxhole.action.ALLOW_ACCESS"
    const val ACTION_DENY = "com.sitikplay.foxhole.action.DENY_ACCESS"

    const val ACTION_DANGER_RUN = "com.sitikplay.foxhole.action.RUN_DANGER"
    const val ACTION_DANGER_DENY = "com.sitikplay.foxhole.action.DENY_DANGER"

    const val EXTRA_UID = "uid"
    const val EXTRA_DANGER_ID = "danger_id"

    const val AUTOSTART_NOTIFY_ID = 2999_999

    const val START_RESULT_NOTIFY_ID = 6000_000

    fun notifyId(uid: Int): Int = uid

    fun dangerNotifyId(uid: Int): Int = uid + 4000_000

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACCESS, ctx.getString(R.string.t303),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = ctx.getString(R.string.t304)
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTOSTART, ctx.getString(R.string.t305),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = ctx.getString(R.string.t306)
                setShowBadge(false)
            }
        )
    }

    fun appLabel(ctx: Context, uid: Int): String {
        return try {
            val pkgs = ctx.packageManager.getPackagesForUid(uid)
            val name = pkgs?.firstOrNull { it.isNotBlank() } ?: "uid:$uid"
            val label = try {
                ctx.packageManager.getApplicationLabel(
                    ctx.packageManager.getApplicationInfo(name, 0)
                ).toString()
            } catch (e: Exception) {
                name
            }
            label
        } catch (e: Exception) {
            "uid:$uid"
        }
    }

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyPending(ctx: Context, app: AccessStore.App) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val uid = app.uid
        val allow = PendingIntent.getBroadcast(
            ctx, uid,
            Intent(ctx, AccessRequestReceiver::class.java)
                .setAction(ACTION_ALLOW)
                .putExtra(EXTRA_UID, uid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deny = PendingIntent.getBroadcast(
            ctx, uid + 1000_000,
            Intent(ctx, AccessRequestReceiver::class.java)
                .setAction(ACTION_DENY)
                .putExtra(EXTRA_UID, uid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            ctx, uid + 2000_000,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PENDING, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = ctx.getString(R.string.t307)
        val sharedMark = try {
            val n = ctx.packageManager.getPackagesForUid(uid)?.count { it.isNotBlank() } ?: 0
            if (n > 1) ctx.getString(R.string.t308, n) else ""
        } catch (e: Exception) {
            ""
        }
        val text = ctx.getString(R.string.t309, appLabel(ctx, uid), uid, sharedMark)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.t310), allow)
            .addAction(0, ctx.getString(R.string.t311), deny)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notifyId(uid), notification)
        } catch (e: SecurityException) {
        }
    }

    fun cancel(ctx: Context, uid: Int) {
        try {
            NotificationManagerCompat.from(ctx).cancel(notifyId(uid))
        } catch (e: Exception) {
        }
    }

    fun notifyDangerConfirm(ctx: Context, uid: Int, id: String, risk: String) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val run = PendingIntent.getBroadcast(
            ctx, uid,
            Intent(ctx, DangerConfirmReceiver::class.java)
                .setAction(ACTION_DANGER_RUN)
                .putExtra(EXTRA_DANGER_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deny = PendingIntent.getBroadcast(
            ctx, uid + 1,
            Intent(ctx, DangerConfirmReceiver::class.java)
                .setAction(ACTION_DANGER_DENY)
                .putExtra(EXTRA_DANGER_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            ctx, uid + 2,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = ctx.getString(R.string.t383, appLabel(ctx, uid), risk)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(ctx.getString(R.string.t382))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.t384), run)
            .addAction(0, ctx.getString(R.string.t385), deny)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(dangerNotifyId(uid), notification)
        } catch (e: SecurityException) {
        }
    }

    fun cancelDanger(ctx: Context, uid: Int) {
        try {
            NotificationManagerCompat.from(ctx).cancel(dangerNotifyId(uid))
        } catch (e: Exception) {
        }
    }

    fun notifyAutostart(ctx: Context, success: Boolean, text: String) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 3000_000,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_AUTOSTART)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(
                if (success) ctx.getString(R.string.t312) else ctx.getString(R.string.t313)
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(AUTOSTART_NOTIFY_ID, notification)
        } catch (e: SecurityException) {
        }
    }

    fun notifyStartResult(ctx: Context, success: Boolean, text: String) {
        ensureChannels(ctx)
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 3600_000,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_AUTOSTART)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(
                if (success) ctx.getString(R.string.t392) else ctx.getString(R.string.t393)
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(START_RESULT_NOTIFY_ID, notification)
        } catch (e: SecurityException) {
        }
    }
}

class AccessRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getIntExtra(AccessNotifications.EXTRA_UID, -1)
        if (uid < 0) return
        val app = AccessStore.pending(context).firstOrNull { it.uid == uid }
            ?: return
        when (intent.action) {
            AccessNotifications.ACTION_ALLOW -> {
                AccessStore.allow(context, app, AclLevel.READ)
                AccessStore.log(context, context.getString(R.string.t314, app.pkg))
            }
            AccessNotifications.ACTION_DENY -> {
                AccessStore.deny(context, app)
                AccessStore.log(context, context.getString(R.string.t315, app.pkg))
            }
            else -> return
        }
        AccessNotifications.cancel(context, uid)
    }
}

class DangerConfirmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AccessNotifications.EXTRA_DANGER_ID) ?: return
        when (intent.action) {
            AccessNotifications.ACTION_DANGER_RUN ->
                DangerApprovals.setDecision(context, id, approved = true)
            AccessNotifications.ACTION_DANGER_DENY ->
                DangerApprovals.setDecision(context, id, approved = false)
            else -> return
        }

        val uid = DangerApprovals.findUid(context, id)
        if (uid != null) {
            AccessNotifications.cancelDanger(context, uid)
            val label = AccessNotifications.appLabel(context, uid)
            if (intent.action == AccessNotifications.ACTION_DANGER_RUN) {
                AccessStore.log(context, context.getString(R.string.t386, label))
            } else {
                AccessStore.log(context, context.getString(R.string.t387, label))
            }
        }
    }
}
