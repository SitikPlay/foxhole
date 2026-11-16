package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverTest {

    @Test
    fun parsesPortRangeFromProc() {
        assertEquals(37000..50000, Discover.parsePortRange("37000 50000"))
        assertEquals(37000..50000, Discover.parsePortRange("37000\t50000\n"))
    }

    @Test
    fun fallsBackOnBadRange() {
        assertEquals(32768..60999, Discover.parsePortRange(null))
        assertEquals(32768..60999, Discover.parsePortRange(""))
        assertEquals(32768..60999, Discover.parsePortRange("мусор"))
        // Границы вне правдоподобных значений — тоже запасной диапазон.
        assertEquals(32768..60999, Discover.parsePortRange("500 70000"))
        // «До» меньше «от» — бессмыслица.
        assertEquals(32768..60999, Discover.parsePortRange("50000 37000"))
    }

    @Test
    fun clientHelloIsWellFormed() {
        val hello = Discover.javaClass.getDeclaredMethod("clientHello").apply { isAccessible = true }
            .invoke(Discover) as ByteArray
        // TLS-запись: content type 0x16 (handshake), версия 3.1.
        assertEquals(0x16, hello[0].toInt() and 0xff)
        assertEquals(0x03, hello[1].toInt() and 0xff)
        assertEquals(0x01, hello[2].toInt() and 0xff)
        val recordLen = ((hello[3].toInt() and 0xff) shl 8) or (hello[4].toInt() and 0xff)
        assertEquals(hello.size - 5, recordLen)
        // Рукопожатие: тип 0x01 (ClientHello), длина в 3 байтах.
        assertEquals(0x01, hello[5].toInt() and 0xff)
        val hsLen = ((hello[6].toInt() and 0xff) shl 16) or
            ((hello[7].toInt() and 0xff) shl 8) or (hello[8].toInt() and 0xff)
        assertEquals(recordLen - 4, hsLen)
        // Внутри тела: версия 3.3 (байты 9-10), случай 32 байта (11-42),
        // далее без сессии один набор шифров.
        assertEquals(0x03, hello[9].toInt() and 0xff)
        assertEquals(0x03, hello[10].toInt() and 0xff)
        val sessionIdLen = hello[43].toInt() and 0xff
        assertEquals(0, sessionIdLen)
        assertTrue(hello.size > 43)
    }
}
