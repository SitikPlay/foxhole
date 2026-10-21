/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SitikPlay
 */

package com.sitikplay.foxhole

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.muntashirakon.adb.android.AndroidUtils
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Спаривание без ПК.
 *
 * Окно настроек держит порт спаривания открытым только пока оно в фокусе.
 * Шторка уведомлений не забирает фокус у активности, поэтому код можно ввести
 * в ответе на уведомление, не сворачивая настройки. Ответ приходит сюда,
 * и TLS-спаривание выполняется, пока системное окно ещё живо.
 */
class PairService : Service() {

    private val worker = Executors.newSingleThreadExecutor()
    private val pairing = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                stopForegroundNow()
                stopSelf()
            }
            ACTION_PAIR -> {
                val input = intent.getStringExtra(EXTRA_INPUT).orEmpty()
                startInForeground(progressNotification())
                worker.execute { pairFromInput(input) }
            }
            else -> startInForeground(readyNotification())
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker.shutdownNow()
        pairing.shutdownNow()
        super.onDestroy()
    }

    private fun pairFromInput(input: String) {
        val parsed = parsePairInput(input, Settings.port(this))
        if (parsed is PairParse.Error) {
            fail(parsed.message)
            return
        }
        parsed as PairParse.Ok
        val port = parsed.port
        val code = parsed.code
        val wifiEnabled = try {
            android.provider.Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
        if (!wifiEnabled) {
            fail("Беспроводная отладка выключена")
            return
        }

        val host = AndroidUtils.getHostIpAddress(this)
        val adb = FoxholeAdb.get(this)
        adb.setTimeout(30, TimeUnit.SECONDS)
        val future: Future<Boolean> = pairing.submit(Callable {
            adb.pair(host, port, code)
        })
        val paired = try {
            future.get(75, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            fail("Спаривание не удалось: ${friendlyAdbError(e)}")
            return
        }
        if (!paired) {
            fail("Устройство отклонило спаривание")
            return
        }
        succeed()
    }

    private fun succeed() {
        val notification = baseNotification()
            .setContentTitle("Спарено")
            .setContentText("Нажми, чтобы открыть Foxhole и запустить сервер")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(autoStart = true))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun fail(message: String) {
        val notification = baseNotification()
            .setContentTitle("Спаривание не удалось")
            .setContentText(message)
            .addAction(inputAction())
            .addAction(cancelAction())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun readyNotification(): Notification = baseNotification()
        .setContentTitle("Спаривание Foxhole")
        .setContentText("Открой «Подключение по коду», затем введи порт и код здесь")
        .setSubText("Настройки не закрывай")
        .setContentIntent(openAppPendingIntent(autoStart = false))
        .addAction(inputAction())
        .addAction(cancelAction())
        .build()

    private fun progressNotification(): Notification = baseNotification()
        .setContentTitle("Спариваю…")
        .setContentText("Не закрывай настройки до конца спаривания")
        .build()

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

    private fun inputAction(): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(EXTRA_INPUT)
            .setLabel("Порт и код: 41221 483661")
            .build()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            this,
            10,
            Intent(this, PairActionReceiver::class.java),
            flags
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_settings, "Ввести порт и код", intent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun cancelAction(): NotificationCompat.Action {
        val intent = PendingIntent.getService(
            this,
            11,
            Intent(this, PairService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_settings, "Отмена", intent).build()
    }

    private fun openAppPendingIntent(autoStart: Boolean): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START, autoStart)
        }
        return PendingIntent.getActivity(
            this,
            if (autoStart) 12 else 13,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Спаривание",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Ввод порта и кода беспроводной отладки"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_PREPARE = "com.sitikplay.foxhole.action.PREPARE_PAIRING"
        const val ACTION_PAIR = "com.sitikplay.foxhole.action.PAIR"
        const val ACTION_CANCEL = "com.sitikplay.foxhole.action.CANCEL_PAIRING"
        const val EXTRA_INPUT = "pair_input"
        const val EXTRA_AUTO_START = "auto_start"
        private const val CHANNEL_ID = "foxhole_pair"
        private const val NOTIFICATION_ID = 42

        fun prepare(context: Context) {
            val intent = Intent(context, PairService::class.java).setAction(ACTION_PREPARE)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

/** Принимает ответ из уведомления и передаёт его сервису. */
class PairActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence(PairService.EXTRA_INPUT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val service = Intent(context, PairService::class.java)
            .setAction(PairService.ACTION_PAIR)
            .putExtra(PairService.EXTRA_INPUT, text)
        ContextCompat.startForegroundService(context, service)
    }
}
