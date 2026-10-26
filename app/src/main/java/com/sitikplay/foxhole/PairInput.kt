package com.sitikplay.foxhole

sealed interface PairParse {
    data class Ok(val port: Int, val code: String) : PairParse
    data class Error(val message: String) : PairParse
}

fun pairPortError(port: Int, reservedPort: Int): String? =
    if (port == reservedPort) "Это порт сервера Foxhole, а не порт спаривания" else null

fun parsePairInput(raw: String, reservedPort: Int): PairParse {
    val numbers = Regex("\\d+").findAll(raw).map { it.value }.toList()
    val code = numbers.firstOrNull { it.length == 6 }
    val port = numbers.firstOrNull { it != code }?.toIntOrNull()
    if (code == null || port == null || port !in 1..65535) {
        return PairParse.Error("Введи порт и код через пробел, например: 41221 483661")
    }
    pairPortError(port, reservedPort)?.let { return PairParse.Error(it) }
    return PairParse.Ok(port, code)
}
