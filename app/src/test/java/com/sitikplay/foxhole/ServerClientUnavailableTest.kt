package com.sitikplay.foxhole

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// А9: клиентская ошибка «до сервера не достучались» помечается стабильным
// машинным префиксом "ERR|" и больше не привязана к русскому тексту.
// UI должен отличать её от ответов сервера по маркеру, а не по подстроке.
class ServerClientUnavailableTest {

    @Test
    fun clientNetworkErrorIsMarkedUnavailable() {
        // Формат, который выдаёт ServerClient.request при невозможности
        // соединиться: "ERR|" + локализованное сообщение.
        assertTrue(ServerClient.isUnavailable("ERR|Сервер недоступен (Connection refused)"))
        assertTrue(ServerClient.isUnavailable("ERR|Foxhole server unreachable (timeout)"))
    }

    @Test
    fun serverRepliesAreNotUnavailable() {
        // Ответы сервера начинаются с "OK " или "ERR " (пробел) — они дошли,
        // это не клиентский сбой соединения.
        assertFalse(ServerClient.isUnavailable("OK pong uid=2000"))
        assertFalse(ServerClient.isUnavailable("ERR code=127 sh: not found"))
        assertFalse(ServerClient.isUnavailable("ERR bad token"))
        assertFalse(ServerClient.isUnavailable(""))
    }
}
