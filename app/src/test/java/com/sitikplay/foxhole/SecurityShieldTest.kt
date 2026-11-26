package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityShieldTest {

    @Test
    fun parseComponentPackageListExtractsUniquePackages() {
        val raw = "OK com.arlosoft.macrodroid/com.example.Svc:com.foo.bar/a.B:com.arlosoft.macrodroid/other"
        assertEquals(
            listOf("com.arlosoft.macrodroid", "com.foo.bar"),
            SecurityShield.parseComponentPackageList(raw)
        )
        assertEquals(emptyList<String>(), SecurityShield.parseComponentPackageList("OK null"))
        assertEquals(emptyList<String>(), SecurityShield.parseComponentPackageList("null"))
    }

    @Test
    fun parseQueryOpPackagesFiltersInvalidAndDeduplicates() {
        val raw = """
            OK com.android.chrome
            org.telegram.messenger
            invalid_word
            com.android.chrome
        """.trimIndent()
        assertEquals(
            listOf("com.android.chrome", "org.telegram.messenger"),
            SecurityShield.parseQueryOpPackages(raw)
        )
        assertEquals(emptyList<String>(), SecurityShield.parseQueryOpPackages("ERR failed"))
    }

    @Test
    fun computeScoreReflectsHardeningAndRisks() {
        val ideal = SecurityShield.computeScore(
            confirmDeviceOn = true,
            flagSecureOn = true,
            allowedFullCount = 0,
            usbDebugOn = false,
            wifiDebugOn = false,
            adbVerifierOn = true,
            privateDnsProtected = true,
            a11yCount = 0,
            notifListenerCount = 0,
            clipboardCount = 0,
            installerCount = 0
        )
        assertEquals(100, ideal)

        val risky = SecurityShield.computeScore(
            confirmDeviceOn = false,
            flagSecureOn = false,
            allowedFullCount = 2,
            usbDebugOn = true,
            wifiDebugOn = true,
            adbVerifierOn = false,
            privateDnsProtected = false,
            a11yCount = 1,
            notifListenerCount = 1,
            clipboardCount = 5,
            installerCount = 4
        )
        assertTrue("Expected degraded score under 50, got $risky", risky in 0..49)
    }
}
