package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsWdsTest {

    @Test
    fun parsesOrdinaryAddress() {
        assertEquals(
            Pair("192.168.1.5", 38815),
            Settings.parseWdsAddress("192.168.1.5:38815")
        )
    }

    @Test
    fun trimsSpacesAndTabs() {
        assertEquals(
            Pair("192.168.0.3", 40123),
            Settings.parseWdsAddress("  192.168.0.3:40123\t")
        )
    }

    @Test
    fun acceptsHostname() {
        assertEquals(
            Pair("pixelos-home.lan", 5555),
            Settings.parseWdsAddress("pixelos-home.lan:5555")
        )
    }

    @Test
    fun rejectsMissingPort() {
        assertNull(Settings.parseWdsAddress("192.168.1.5"))
        assertNull(Settings.parseWdsAddress("192.168.1.5:"))
    }

    @Test
    fun rejectsMissingHost() {
        assertNull(Settings.parseWdsAddress(":38815"))
        assertNull(Settings.parseWdsAddress(":"))
    }

    @Test
    fun rejectsEmptyValue() {
        assertNull(Settings.parseWdsAddress(""))
        assertNull(Settings.parseWdsAddress("   "))
    }

    @Test
    fun rejectsNonNumericPort() {
        assertNull(Settings.parseWdsAddress("192.168.1.5:adb"))
        assertNull(Settings.parseWdsAddress("192.168.1.5:38.815"))
    }

    @Test
    fun rejectsOutOfRangePorts() {
        assertNull(Settings.parseWdsAddress("192.168.1.5:1023"))
        assertNull(Settings.parseWdsAddress("192.168.1.5:80"))
        assertNull(Settings.parseWdsAddress("192.168.1.5:65536"))
        assertNull(Settings.parseWdsAddress("192.168.1.5:-1"))
    }

    @Test
    fun rejectsHostWithSpaces() {
        assertNull(Settings.parseWdsAddress("192.168.1 ;reboot:1234"))
        assertNull(Settings.parseWdsAddress("мой хост:38815")
        )
    }
}
