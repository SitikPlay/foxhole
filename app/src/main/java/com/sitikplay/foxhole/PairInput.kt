package com.sitikplay.foxhole

import android.content.Context

sealed interface PairParse {
    data class Ok(val port: Int, val code: String) : PairParse
    /** Введён только 6-значный код: порт приложение находит само через mDNS. */
    data class CodeOnly(val code: String) : PairParse
    data class Error(val message: String) : PairParse
}

sealed interface PairParseKind {
    data class Ok(val port: Int, val code: String) : PairParseKind
    data class CodeOnly(val code: String) : PairParseKind
    object ReservedPort : PairParseKind
    object Invalid : PairParseKind
}

fun parsePairInputKind(raw: String, reservedPort: Int): PairParseKind {
    val numbers = Regex("\\d+").findAll(raw).map { it.value }.toList()
    val code = numbers.firstOrNull { it.length == 6 }
    if (code == null) return PairParseKind.Invalid
    val port = numbers.firstOrNull { it != code }?.toIntOrNull()
    // Код без порта — допустимый ввод: порт найдём через mDNS (_adb-tls-pairing._tcp).
    if (port == null) return PairParseKind.CodeOnly(code)
    if (port !in 1..65535) return PairParseKind.Invalid
    if (port == reservedPort) return PairParseKind.ReservedPort
    return PairParseKind.Ok(port, code)
}

fun parsePairInput(ctx: Context, raw: String, reservedPort: Int): PairParse =
    when (val kind = parsePairInputKind(raw, reservedPort)) {
        is PairParseKind.Ok -> PairParse.Ok(kind.port, kind.code)
        is PairParseKind.CodeOnly -> PairParse.CodeOnly(kind.code)
        PairParseKind.ReservedPort -> PairParse.Error(ctx.getString(R.string.t343))
        PairParseKind.Invalid -> PairParse.Error(ctx.getString(R.string.t344))
    }

fun pairPortError(ctx: Context, port: Int, reservedPort: Int): String? =
    if (port == reservedPort) ctx.getString(R.string.t343) else null
