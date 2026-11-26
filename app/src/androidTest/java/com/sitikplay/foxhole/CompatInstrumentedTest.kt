package com.sitikplay.foxhole

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatInstrumentedTest {

    @Test
    fun detectRomAndBatteryIntentWorkOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val rom = OemCompat.detectRom()
        assertNotNull(rom)
        assertTrue(rom.label.isNotBlank())

        val batteryIntent = OemCompat.requestIgnoreBatteryOptimizationsIntent(ctx)
        assertNotNull(batteryIntent.action)
    }

    @Test
    fun deviceReportIncludesSystemAndFirmwareInfo() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val report = DeviceReport.report(ctx)
        assertTrue(report.contains("Android"))
        assertTrue(report.contains("1.4.0"))
        assertFalse(report.isBlank())
    }

    @Test
    fun appInspectorReadsSelfPackageOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val details = AppInspector.inspect(ctx, ctx.packageName)
        assertNotNull(details)
        assertTrue(details!!.uid > 0)
        assertTrue(details.versionName.startsWith("1.4.0"))
        assertTrue(details.permissions.isNotEmpty())
    }
}
