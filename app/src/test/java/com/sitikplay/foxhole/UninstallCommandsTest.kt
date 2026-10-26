package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallCommandsTest {

    @Test
    fun uninstallUserIsPlainPmUninstall() {
        assertEquals("pm uninstall \"com.example.app\"", UninstallCommands.uninstallUserCmd("com.example.app"))
    }

    @Test
    fun uninstallSystemKeepsDataAndTargetsUser0() {
        assertEquals(
            "pm uninstall -k --user 0 \"com.android.systemui\"",
            UninstallCommands.uninstallSystemCmd("com.android.systemui")
        )
    }

    @Test
    fun restoreSystemUsesInstallExisting() {
        assertEquals(
            "pm install-existing --user 0 \"com.android.systemui\"",
            UninstallCommands.restoreSystemCmd("com.android.systemui")
        )
    }

    @Test
    fun restoreApksUseSinglePmInstall() {
        assertEquals(
            "pm install -r --user 0 \"/data/app/x/base.apk\"",
            UninstallCommands.restoreUserCmd(listOf("/data/app/x/base.apk"))
        )
        assertEquals(
            "pm install -r --user 0 \"/a/base.apk\" \"/a/config.en.apk\"",
            UninstallCommands.restoreUserCmd(listOf("/a/base.apk", "/a/config.en.apk"))
        )
    }

    @Test
    fun restoreWithoutApkFails() {
        assertTrue(UninstallCommands.restoreUserCmd(emptyList()).startsWith("ERR"))
    }

    @Test
    fun stagingDirKeepsSafePackageNames() {
        assertEquals(
            "/data/local/tmp/foxhole_restore/com.example.app",
            UninstallCommands.stagingDir("com.example.app")
        )
    }

    @Test
    fun stagingDirReplacesUnsafeChars() {
        assertEquals(
            "/data/local/tmp/foxhole_restore/bad_pkg_",
            UninstallCommands.stagingDir("bad/pkg!")
        )
    }

    @Test
    fun stagingPathJoinsDirAndName() {
        assertEquals(
            "/tmp/stage/base.apk",
            UninstallCommands.stagingPath("/tmp/stage", "base.apk")
        )
    }

    @Test
    fun restoreCopyBuildsMkdirAndCopy() {
        assertEquals(
            "mkdir -p \"/tmp/stage\" && cp -f \"/a/base.apk\" \"/tmp/stage/base.apk\"",
            UninstallCommands.restoreCopyCmd(listOf("/a/base.apk"), "/tmp/stage")
        )
        assertEquals(
            "mkdir -p \"/tmp/stage\" && cp -f \"/a/base.apk\" \"/tmp/stage/base.apk\" && cp -f \"/a/split.apk\" \"/tmp/stage/split.apk\"",
            UninstallCommands.restoreCopyCmd(listOf("/a/base.apk", "/a/split.apk"), "/tmp/stage")
        )
    }

    @Test
    fun restoreCopyWithoutFilesFails() {
        assertTrue(UninstallCommands.restoreCopyCmd(emptyList(), "/tmp/stage").startsWith("ERR"))
    }

    @Test
    fun parsesPmPathOutput() {
        val output = "package:/data/app/com.example-abc/base.apk\n" +
            "package:/data/app/com.example-abc/split_config.arm64_v8a.apk"
        assertEquals(
            listOf(
                "/data/app/com.example-abc/base.apk",
                "/data/app/com.example-abc/split_config.arm64_v8a.apk"
            ),
            UninstallCommands.parsePmPath(output)
        )
    }

    @Test
    fun ignoresNoiseInPmPathOutput() {
        val output = "Warning: something\npackage:\nnot-a-path\nOK "
        assertTrue(UninstallCommands.parsePmPath(output).isEmpty())
    }

    @Test
    fun expiryBoundaries() {
        val now = 1_000_000_000_000L
        val removedAt = now - UninstallCommands.RETENTION_MILLIS
        assertTrue(UninstallCommands.isExpired(removedAt, now))
        assertFalse(UninstallCommands.isExpired(removedAt + 1, now))
        assertFalse(UninstallCommands.isExpired(now, now))
    }

    @Test
    fun daysLeftCountsWholeDays() {
        val now = 1_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        assertEquals(25, UninstallCommands.daysLeft(now, now))
        assertEquals(1, UninstallCommands.daysLeft(now - 24 * day, now))
        assertEquals(1, UninstallCommands.daysLeft(now - 24 * day - 1, now))
        assertEquals(0, UninstallCommands.daysLeft(now - UninstallCommands.RETENTION_MILLIS, now))
        assertEquals(0, UninstallCommands.daysLeft(now - UninstallCommands.RETENTION_MILLIS - day, now))
    }

    @Test
    fun dirSizeIsZeroForMissingDir() {
        assertEquals(0L, UninstallCommands.dirSize(null))
        assertEquals(0L, UninstallCommands.dirSize(java.io.File("definitely/not/here")))
    }

    @Test
    fun shellArgsAreQuoted() {
        assertEquals("\"/a b/x.apk\"", UninstallCommands.quoteShellArg("/a b/x.apk"))
        assertEquals("\"safe\"", UninstallCommands.quoteShellArg("sa\"fe"))
    }

    @Test
    fun removeDirIsForceRecursive() {
        assertEquals(
            "rm -rf \"/storage/emulated/0/apk/com.example\"",
            UninstallCommands.removeDirCmd("/storage/emulated/0/apk/com.example")
        )
    }
}

