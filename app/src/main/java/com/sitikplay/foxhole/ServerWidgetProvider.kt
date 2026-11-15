package com.sitikplay.foxhole

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.widget.RemoteViews

class ServerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.sitikplay.foxhole.widget.TOGGLE"

        // В1: публичная точка обновления всех виджетов — плитка, терминал и
        // другие части приложения вызывают её после смены состояния сервера,
        // чтобы виджет не висел со старым статусом до планового обновления.
        // Заодно просим плитку перечитать состояние: requestListeningState
        // приводит к onStartListening у TileService и тамошний refreshStatus.
        fun refreshAllWidgets(context: Context) {
            Thread {
                val online = ServerClient.request(context, "ping").startsWith("OK pong")
                val manager = context.getSystemService(AppWidgetManager::class.java)
                    ?: return@Thread
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, ServerWidgetProvider::class.java)
                )
                TileService.requestListeningState(
                    context,
                    android.content.ComponentName(context, ServerTileService::class.java)
                )
                Handler(Looper.getMainLooper()).post {
                    ids.forEach { id -> pushState(context, manager, id, online) }
                }
            }.start()
        }

        // Сборка RemoteViews для одного экземпляра виджета. Отдельно от класса
        // провайдера, чтобы companion мог обновлять виджеты без создания приёмника.
        private fun pushState(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            online: Boolean
        ) {
            val rv = RemoteViews(context.packageName, R.layout.widget_server)
            rv.setOnClickPendingIntent(R.id.widget_btn, toggleIntent(context))
            rv.setTextViewText(
                R.id.widget_status,
                context.getString(if (online) R.string.t394 else R.string.t395)
            )
            rv.setTextViewText(
                R.id.widget_btn,
                context.getString(if (online) R.string.t396 else R.string.t397)
            )
            try {
                manager.updateAppWidget(id, rv)
            } catch (e: Exception) {
                // Виджет мог быть снят с экрана между получением id и апдейтом.
            }
        }

        private fun toggleIntent(context: Context): PendingIntent {
            val intent = Intent(context, ServerWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            Thread {
                val online = ServerClient.request(context, "ping").startsWith("OK pong")
                if (online) {
                    ServerClient.request(context, "stop")
                    AccessStore.log(context, "виджет: сервер остановлен")
                } else {
                    startViaKey(context)
                }
                // В1: после переключения сразу обновляем и виджет, и плитку.
                refreshAllWidgets(context)
            }.start()
            return
        }

        refreshAllWidgets(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        refreshAllWidgets(context)
    }

    private fun startViaKey(context: Context) {
        if (!FoxholeAdb.hasKey(context)) {
            AccessNotifications.notifyStartResult(context, false, context.getString(R.string.t328))
            return
        }
        val res = RemoteStart.run(
            context,
            wifiTimeoutMs = 30_000L,
            wifiStepMs = 3_000L,
            disableWifiAfter = true
        )
        AccessStore.log(
            context, if (res.startsWith("OK")) "виджет: сервер запущен" else "виджет: $res"
        )
        AccessNotifications.notifyStartResult(
            context, res.startsWith("OK"), res.substringAfter("|").trim()
        )
    }
}
