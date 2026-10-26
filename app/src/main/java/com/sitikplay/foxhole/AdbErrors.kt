package com.sitikplay.foxhole

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

fun friendlyAdbError(e: Throwable): String {
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
        stale && closed.not() -> "Код или порт устарели — возьми свежие в настройках"
        closed -> "Порт спаривания закрыт: окно с кодом свернулось"
        raw.contains("handshake", true) || raw.contains("auth", true) ->
            "ADB отклонил ключ: нажми «Забыть ADB-ключ» и спарь заново"
        raw.isNotEmpty() -> raw.substringBefore("\n")
        else -> "Неизвестная ошибка ADB"
    }
}

