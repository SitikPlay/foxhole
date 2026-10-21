/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SitikPlay
 */

package com.sitikplay.foxhole

sealed interface PairParse {
    data class Ok(val port: Int, val code: String) : PairParse
    data class Error(val message: String) : PairParse
}

/**
 * Разбирает ответ на уведомление. Порт и код можно вводить в любом порядке:
 * шестизначное число считается кодом, остальное — портом.
 */
fun parsePairInput(raw: String, reservedPort: Int): PairParse {
    val numbers = Regex("\\d+").findAll(raw).map { it.value }.toList()
    val code = numbers.firstOrNull { it.length == 6 }
    val port = numbers.firstOrNull { it != code }?.toIntOrNull()
    if (code == null || port == null || port !in 1..65535) {
        return PairParse.Error("Введи порт и код через пробел, например: 41221 483661")
    }
    if (port == reservedPort) {
        return PairParse.Error("Это порт сервера Foxhole, а не порт спаривания")
    }
    return PairParse.Ok(port, code)
}
