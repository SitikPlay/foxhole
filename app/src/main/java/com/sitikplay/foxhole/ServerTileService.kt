package com.sitikplay.foxhole

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ServerTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refreshStatus()
    }

    override fun onTileAdded() {
        refreshStatus()
    }

    override fun onTileRemoved() = Unit

    private fun refreshStatus() {
        Thread {
            val online = ServerClient.request(this, "ping").startsWith("OK pong")
            handler.post { applyState(online) }
        }.start()
    }

    private fun applyState(online: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (online) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (online) R.string.t389 else R.string.t390)
        }
        tile.updateTile()
    }

    private fun setStartingState() {
        val tile = qsTile ?: return

        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.t391)
        }
        tile.updateTile()
    }

    override fun onClick() {
        val online = ServerClient.request(this, "ping").startsWith("OK pong")
        if (online) {

            applyState(false)
            Thread {
                ServerClient.request(this, "stop")
            }.start()
            return
        }
        if (FoxholeAdb.hasKey(this)) {
            handler.post { setStartingState() }
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
