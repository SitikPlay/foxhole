package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemTweaksTest {

    @Test
    fun parseSettingValueHandlesNullAndErrors() {
        assertEquals("0.5", SystemTweaks.parseSettingValue("OK 0.5"))
        assertEquals("", SystemTweaks.parseSettingValue("OK null"))
        assertEquals("", SystemTweaks.parseSettingValue("OK"))
        assertEquals("", SystemTweaks.parseSettingValue("ERR denied"))
    }
}
