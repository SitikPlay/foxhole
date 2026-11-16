package com.sitikplay.foxhole

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.appcompat.app.AppCompatDelegate

class ServerTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refreshStatus()
    }

    override fun onTileAdded() {
        refreshStatus()
    }

    override fun onTileRemoved() = Unit

    companion object {
        // В1: публичная точка обновления плитки — приложение вызывает её после
        // смены состояния сервера, чтобы плитка не висела со старым статусом
        // до следующего открытия шторки (requestListeningState приводит к
        // onStartListening и тамошний refreshStatus).
        fun requestRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                android.content.ComponentName(context, ServerTileService::class.java)
            )
        }
    }

    private fun refreshStatus() {
        Thread {
            val online = ServerClient.request(this, "ping").startsWith("OK pong")
            handler.post { applyState(online) }
        }.start()
    }

    // Плитка живёт в SystemUI и не знает о пер-апп локалях приложения: её
    // манифестная метка и getString() идут по системной локали телефона.
    // Строим контекст с языком, выбранным в приложении, и сами проставляем
    // label/subtitle/contentDescription (API 29+, см. guard в applyState).
    private fun localizedString(resId: Int): String {
        val loc = AppCompatDelegate.getApplicationLocales()[0] ?: return getString(resId)
        val conf = Configuration(resources.configuration)
        conf.setLocale(loc)
        return createConfigurationContext(conf).getString(resId)
    }

    private fun applyState(online: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (online) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = localizedString(R.string.t388)
            tile.subtitle = localizedString(if (online) R.string.t389 else R.string.t390)
            tile.contentDescription = tile.label
        }
        tile.updateTile()
    }

    private fun setStartingState() {
        val tile = qsTile ?: return

        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = localizedString(R.string.t388)
            tile.subtitle = localizedString(R.string.t391)
            tile.contentDescription = tile.label
        }
        tile.updateTile()
    }

    override fun onClick() {
        // А1 (подтверждено на устройстве): ping выполнялся прямо на главном
        // потоке — Android рвал его с NetworkOnMainThreadException, исключение
        // глоталось в ServerClient, и плитка всегда считала сервер выключенным.
        // Проверяем статус в фоне и уже оттуда решаем, что делать.
        Thread {
            val online = ServerClient.request(this, "ping").startsWith("OK pong")
            handler.post { actOnServerState(online) }
        }.start()
    }

    private fun actOnServerState(online: Boolean) {
        if (online) {

            applyState(false)
            Thread {
                ServerClient.request(this, "stop")
            }.start()
            return
        }
        if (FoxholeAdb.hasKey(this)) {
            setStartingState()
            Thread {
                val res = RemoteStart.run(
                    this,
                    wifiTimeoutMs = 30_000L,
                    wifiStepMs = 3_000L,
                    disableWifiAfter = true
                )
                AccessNotifications.notifyStartResult(
                    this, res.startsWith("OK"), res.substringAfter("|").trim()
                )
                AccessStore.log(
                    this, if (res.startsWith("OK")) "плитка: сервер запущен" else "плитка: $res"
                )
                handler.post { applyState(res.startsWith("OK")) }
            }.start()
            return
        }

        openApp()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 3500_000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
