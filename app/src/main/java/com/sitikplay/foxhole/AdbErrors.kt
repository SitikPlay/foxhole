package com.sitikplay.foxhole

import android.content.Context
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

// Категории ошибок ADB. Вынесены в отдельную чистую функцию, чтобы логику
// распознавания можно было покрыть JVM-тестом (сам перевод требует Context).
enum class AdbErrorKind {
    StalePairing, // код/порт устарели либо обмен кодом не удался (неверный код)
    StaleTimeout, // операция не уложилась в таймаут
    PortClosed,   // порт спаривания закрыт
    KeyRejected,  // ADB отверг ключ/рукопожатие
    Raw,          // есть текст исключения — показываем первую строку
    Unknown       // текста нет
}

fun adbErrorKind(e: Throwable): AdbErrorKind {
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
    // А14 (подтверждено на устройстве): неверный код спаривания даёт
    // "Could not exchange peer info" — раньше показывалось сырым исключением.
    val peerInfo = raw.contains("peer info", true) ||
        raw.contains("spake", true)
    val auth = raw.contains("handshake", true) || raw.contains("auth", true)
    return when {
        peerInfo -> AdbErrorKind.StalePairing
        closed -> AdbErrorKind.PortClosed
        stale -> AdbErrorKind.StaleTimeout
        auth -> AdbErrorKind.KeyRejected
        raw.isNotEmpty() -> AdbErrorKind.Raw
        else -> AdbErrorKind.Unknown
    }
}

fun friendlyAdbError(ctx: Context, e: Throwable): String = when (adbErrorKind(e)) {
    AdbErrorKind.StalePairing, AdbErrorKind.StaleTimeout -> ctx.getString(R.string.t364)
    AdbErrorKind.PortClosed -> ctx.getString(R.string.t365)
    AdbErrorKind.KeyRejected -> ctx.getString(R.string.t366)
    AdbErrorKind.Raw -> e.message.orEmpty().substringBefore("\n")
    AdbErrorKind.Unknown -> ctx.getString(R.string.t367)
}
