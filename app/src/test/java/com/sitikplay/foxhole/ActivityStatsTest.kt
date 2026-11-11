package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ActivityStatsTest {

    private val loc = Locale.US

    private val NOW = System.currentTimeMillis() / 1000L * 1000L

    private fun line(msDelta: Long, body: String): String =
        "${ActivityStats.formatTime(NOW + msDelta, loc)} $body"

    @Test
    fun parsesCommandDeniedAndEvent() {
        val lines = listOf(
            line(-60_000, "com.example.app ping -> OK pong uid=2000"),
            line(-30_000, "org.test.app ОТКАЗ (нет разрешения): rm -rf /sdcard"),
            line(-10_000, "com.example.app ДОСТУП ВЫДАН READ")
        )
        val entries = ActivityStats.parse(lines, NOW, loc)
        assertEquals(3, entries.size)
        assertEquals(ActivityStats.Kind.OK, entries[0].kind)
        assertEquals(ActivityStats.Kind.DENIED, entries[1].kind)
        assertEquals(ActivityStats.Kind.EVENT, entries[2].kind)
        assertEquals("com.example.app", entries[0].pkg)
        assertEquals("ping -> OK pong uid=2000", entries[0].text)
    }

    @Test
    fun skipsGarbageLines() {
        val entries = ActivityStats.parse(listOf("", "мусор без времени", "x"), NOW, loc)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun futureDateBelongsToLastYear() {

        val entries = ActivityStats.parse(listOf("05.12 10:00:00 com.a ping -> OK pong"), NOW, loc)
        assertEquals(1, entries.size)
        assertTrue(entries[0].timeMs < NOW)
    }

    @Test
    fun summaryCountsAndLastCommands() {
        val now = NOW
        val lines = listOf(

            "${ActivityStats.formatTime(now - 86_400_000L * 8, loc)} org.old.app ping -> OK pong",
            "${ActivityStats.formatTime(now - 3_600_000L, loc)} com.a pm path com.x -> OK /data/app",
            "${ActivityStats.formatTime(now - 2_600_000L, loc)} com.a ОТКАЗ (нужен уровень FULL): dumpsys",
            "${ActivityStats.formatTime(now - 1_000_000L, loc)} com.a dumpsys battery -> OK"
        )
        val entries = ActivityStats.parse(lines, now, loc)
        val stats = ActivityStats.summary(entries, now)
        assertEquals(1, stats.size)
        val a = stats.first()
        assertEquals("com.a", a.pkg)
        assertEquals(2, a.ok)
        assertEquals(1, a.denied)
        assertTrue(a.lastCommands.contains("dumpsys battery"))
        assertTrue(a.lastCommands.contains("pm path com.x"))
        assertEquals(now - 1_000_000L, a.lastMs)
    }

    @Test
    fun filterByPackageKindAndQuery() {
        val lines = listOf(
            "${ActivityStats.formatTime(NOW - 1000, loc)} com.a ping -> OK pong",
            "${ActivityStats.formatTime(NOW - 500, loc)} com.b ОТКАЗ (нет разрешения): ls",
            "${ActivityStats.formatTime(NOW - 100, loc)} com.a logcat -v time -> OK"
        )
        val entries = ActivityStats.parse(lines, NOW, loc)
        assertEquals(2, ActivityStats.filter(entries, "", "com.a", null).size)
        assertEquals(1, ActivityStats.filter(entries, "", "com.b", null).size)

        assertEquals(
            2,
            ActivityStats.filter(entries, "", "com.a", ActivityStats.Kind.OK).size
        )
        assertEquals(
            1,
            ActivityStats.filter(entries, "отказ", null, ActivityStats.Kind.DENIED).size
        )
        assertEquals(
            1,
            ActivityStats.filter(entries, "logcat", null, null).size
        )
        assertEquals(3, ActivityStats.filter(entries, "", null, null).size)
    }

    @Test
    fun commandOfTruncates() {
        assertEquals("x", ActivityStats.commandOf("x -> OK"))
        val long = "abcdefghij".repeat(8)
        assertEquals(60, ActivityStats.commandOf("$long -> OK").length)
    }

    @Test
    fun looksLikeEntryChecksTimePrefix() {
        assertTrue(
            ActivityStats.looksLikeEntry("01.11 12:00:00 com.a ping -> OK pong", loc)
        )
        assertFalse(ActivityStats.looksLikeEntry("не журнал", loc))
    }
}
