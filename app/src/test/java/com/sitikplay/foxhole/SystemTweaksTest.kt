package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTweaksTest {

    @Test
    fun parseSettingValueHandlesNullAndErrors() {
        assertEquals("0.5", SystemTweaks.parseSettingValue("OK 0.5"))
        assertEquals("", SystemTweaks.parseSettingValue("OK null"))
        assertEquals("", SystemTweaks.parseSettingValue("OK"))
        assertEquals("", SystemTweaks.parseSettingValue("ERR denied"))
    }

    @Test
    fun parseDnsPresetDetectsProvidersAndModes() {
        assertEquals(
            SystemTweaks.DnsPreset.ADGUARD,
            SystemTweaks.parseDnsPreset("hostname", "dns.adguard-dns.com")
        )
        assertEquals(
            SystemTweaks.DnsPreset.CLOUDFLARE,
            SystemTweaks.parseDnsPreset("hostname", "one.one.one.one")
        )
        assertEquals(
            SystemTweaks.DnsPreset.OFF,
            SystemTweaks.parseDnsPreset("off", "")
        )
        assertEquals(
            SystemTweaks.DnsPreset.AUTO,
            SystemTweaks.parseDnsPreset("opportunistic", "dns.adguard-dns.com")
        )
    }

    @Test
    fun parseDensityAndClampStaySafe() {
        val d1 = SystemTweaks.parseDensity("OK Physical density: 440")
        assertNotNull(d1)
        assertEquals(440, d1!!.physical)
        assertNull(d1.overrideDpi)
        assertEquals(440, d1.effective)

        val d2 = SystemTweaks.parseDensity("OK Physical density: 440\nOverride density: 392")
        assertNotNull(d2)
        assertEquals(392, d2!!.overrideDpi)
        assertEquals(392, d2.effective)

        assertNull(SystemTweaks.parseDensity("ERR cmd failed"))
        assertEquals(240, SystemTweaks.clampDpi(80))
        assertEquals(720, SystemTweaks.clampDpi(1200))
        assertEquals(400, SystemTweaks.clampDpi(400))
    }
}
