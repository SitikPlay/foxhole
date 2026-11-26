package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test

class OemCompatTest {

    @Test
    fun detectsHyperOsFromPropertyOrDisplay() {
        assertEquals(
            OemCompat.RomFamily.HYPEROS,
            OemCompat.classifyRom("Xiaomi", "Redmi", "UKQ1.231003.002", "V816", "OS1.0.5.0", null)
        )
        assertEquals(
            OemCompat.RomFamily.HYPEROS,
            OemCompat.classifyRom("Xiaomi", "POCO", "OS2.0.10.0.VNPMIXM", "V816", null, null)
        )
    }

    @Test
    fun detectsMiui() {
        assertEquals(
            OemCompat.RomFamily.MIUI,
            OemCompat.classifyRom("Xiaomi", "Xiaomi", "V14.0.4.0", "V140", null, null)
        )
    }

    @Test
    fun detectsSamsungOneUi() {
        assertEquals(
            OemCompat.RomFamily.ONEUI,
            OemCompat.classifyRom("samsung", "samsung", "UP1A.231005.007", null, null, "160100")
        )
    }

    @Test
    fun detectsColorOsAndMagicAndAosp() {
        assertEquals(
            OemCompat.RomFamily.COLOROS,
            OemCompat.classifyRom("realme", "realme", "RMX3890", null, null, null)
        )
        assertEquals(
            OemCompat.RomFamily.COLOROS,
            OemCompat.classifyRom("OnePlus", "OnePlus", "CPH2581", null, null, null)
        )
        assertEquals(
            OemCompat.RomFamily.MAGIC,
            OemCompat.classifyRom("HONOR", "HONOR", "9.0.0", null, null, null)
        )
        assertEquals(
            OemCompat.RomFamily.AOSP,
            OemCompat.classifyRom("Google", "google", "AP2A.240905.003", null, null, null)
        )
    }

    @Test
    fun customAospOnXiaomiHardwareDetectedAsAosp() {
        assertEquals(
            OemCompat.RomFamily.AOSP,
            OemCompat.classifyRom("Xiaomi", "Redmi", "CP2A.260605.016 custom_sweet", null, null, null)
        )
    }
}
