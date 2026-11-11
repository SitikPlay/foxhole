package com.sitikplay.foxhole

import android.content.Context
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

fun friendlyAdbError(ctx: Context, e: Throwable): String {
    val raw = e.message?.takeIf { it.isNotBlank() }.orEmpty()
    val stale = raw.contains("timed out", true) ||
        raw.contains("timeout", true) ||
        raw.contains("wifi payload failed", true) ||
        e is SocketTimeoutException ||
        e is TimeoutException
    val closed = raw.contains("Failed to connect", true) ||
        raw.contains("refused", true) ||
        raw.contains("unreachable", true) ||
        e is ConnectException
    return when {
        stale && closed.not() -> ctx.getString(R.string.t364)
        closed -> ctx.getString(R.string.t365)
        raw.contains("handshake", true) || raw.contains("auth", true) ->
            ctx.getString(R.string.t366)
        raw.isNotEmpty() -> raw.substringBefore("\n")
        else -> ctx.getString(R.string.t367)
    }
}
