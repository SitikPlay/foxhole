package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInspectorTest {

    @Test
    fun validatesPackageNamesStrictly() {
        assertTrue(AppInspector.isValidPackage("com.sitikplay.foxhole"))
        assertTrue(AppInspector.isValidPackage("org.telegram.messenger_beta"))
        assertFalse(AppInspector.isValidPackage(""))
        assertFalse(AppInspector.isValidPackage("com.a; rm -rf /"))
        assertFalse(AppInspector.isValidPackage("com.a && id"))
        assertFalse(AppInspector.isValidPackage("singleword"))
    }

    @Test
    fun parsePidExtractsNumericPid() {
        assertEquals("22500", AppInspector.parsePid("OK 22500"))
        assertEquals("142", AppInspector.parsePid("OK 142 143"))
        assertNull(AppInspector.parsePid("OK"))
        assertNull(AppInspector.parsePid("ERR code=1"))
    }

    @Test
    fun parseAppOpModeReadsExplicitOrDefaultMode() {
        assertEquals(
            "allow",
            AppInspector.parseAppOpMode(
                "OK No operations.\nDefault mode: allow",
                "RUN_ANY_IN_BACKGROUND"
            )
        )
        assertEquals(
            "ignore",
            AppInspector.parseAppOpMode(
                "OK Uid mode: RUN_ANY_IN_BACKGROUND: ignore\nRUN_ANY_IN_BACKGROUND: ignore; time=+1m",
                "RUN_ANY_IN_BACKGROUND"
            )
        )
        assertEquals(
            "unknown",
            AppInspector.parseAppOpMode("ERR denied", "RUN_ANY_IN_BACKGROUND")
        )
    }

    @Test
    fun parseApkPathsCleansPackagePrefixes() {
        val raw = """
            OK package:/data/app/base.apk
            package:/data/app/split_config.arm64_v8a.apk
        """.trimIndent()
        assertEquals(
            listOf("/data/app/base.apk", "/data/app/split_config.arm64_v8a.apk"),
            AppInspector.parseApkPaths(raw)
        )
    }
}
