package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallCommandsTest {

    @Test
    fun quotesSimpleValue() {
        assertEquals("'com.example'", UninstallCommands.quoteShellArg("com.example"))
    }

    @Test
    fun quoteNeutralizesSubstitution() {

        val q = UninstallCommands.quoteShellArg("com.x$(rm -rf /)test")
        assertEquals("'com.x\$(rm -rf /)test'", q)
        assertFalse(q.contains("\\$("))
    }

    @Test
    fun quoteEscapesEmbeddedSingleQuote() {
        assertEquals("'it'\\''s'", UninstallCommands.quoteShellArg("it's"))
    }

    @Test
    fun quoteHandlesBacktickAndBackslash() {
        val q = UninstallCommands.quoteShellArg("a`rm -rf /`b\\c")
        assertEquals("'a`rm -rf /`b\\c'", q)
    }

    @Test
    fun parsedPmPathExtractsPaths() {
        val out = "package:/data/app/base.apk\npackage:/data/app/split.apk\n"
        assertEquals(
            listOf("/data/app/base.apk", "/data/app/split.apk"),
            UninstallCommands.parsePmPath(out)
        )
    }

    @Test
    fun parsedPmPathSkipsJunk() {
        val out = "NOT FOUND\npackage:/data/app/base.apk\n\n"
        assertEquals(
            listOf("/data/app/base.apk"),
            UninstallCommands.parsePmPath(out)
        )
    }

    @Test
    fun daysLeftRoundsUp() {
        val now = 0L
        assertEquals(25, UninstallCommands.daysLeft(now, now))
        assertEquals(
            1,
            UninstallCommands.daysLeft(now, now + UninstallCommands.RETENTION_MILLIS - 1)
        )

        assertEquals(
            0,
            UninstallCommands.daysLeft(now, now + UninstallCommands.RETENTION_MILLIS)
        )
        assertEquals(
            0,
            UninstallCommands.daysLeft(now, now + UninstallCommands.RETENTION_MILLIS + 1)
        )
    }

    @Test
    fun expiryNeedsFullRetention() {
        val now = 0L
        assertTrue(UninstallCommands.isExpired(now, now + UninstallCommands.RETENTION_MILLIS))
        assertFalse(UninstallCommands.isExpired(now, now + UninstallCommands.RETENTION_MILLIS - 1))
    }

    @Test
    fun stagingDirSanitizesPackage() {
        assertEquals(
            "/data/local/tmp/foxhole_restore/com.example_app",
            UninstallCommands.stagingDir("com.example/app")
        )
    }
}
