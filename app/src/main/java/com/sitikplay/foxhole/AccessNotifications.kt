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

    const val EXTRA_UID = "uid"

    const val AUTOSTART_NOTIFY_ID = 2999_999

    fun notifyId(uid: Int): Int = uid

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACCESS, "Запросы доступа",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Кто-то просит доступ к Foxhole"
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTOSTART, "Автозапуск сервера",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Результат автозапуска сервера после перезагрузки"
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

    fun notifyPending(ctx: Context, app: AccessStore.App) {
        ensureChannels(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
        val title = "Foxhole: запрос доступа"
        val text = "${appLabel(ctx, uid)} (uid $uid) хочет выполнять shell-команды"
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Разрешить (READ)", allow)
            .addAction(0, "Отклонить", deny)
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

    fun notifyAutostart(ctx: Context, success: Boolean, text: String) {
        ensureChannels(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            ctx, 3000_000,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_AUTOSTART)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(if (success) "Foxhole запущен после перезагрузки" else "Автозапуск Foxhole не удался")
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
                AccessStore.log(context, "${app.pkg}: доступ READ выдан из уведомления")
            }
            AccessNotifications.ACTION_DENY -> {
                AccessStore.deny(context, app)
                AccessStore.log(context, "${app.pkg}: отклонено из уведомления")
            }
            else -> return
        }
        AccessNotifications.cancel(context, uid)
    }
}

