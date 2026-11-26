package com.sitikplay.foxhole

import android.net.Uri
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    @Test
    fun securityShieldScanWorksOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val report = SecurityShield.scan(ctx)
        assertTrue(report.score in 0..100)
        assertTrue(report.allowedTotal >= 0)
    }

    @Test
    fun bridgeProviderEnforcesAclSelfProtectionAndPanicRevokeOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://${ctx.packageName}.bridge")
        val uid = Process.myUid()
        val sig = PkgSignature.hashOf(ctx, listOf(ctx.packageName))

        try {
            AccessStore.revokeAll(ctx)
            DangerApprovals.clearAll(ctx)

            val unauth = ctx.contentResolver.call(uri, "exec", "ping", null)
            assertNotNull(unauth)
            assertFalse(unauth!!.getBoolean("ok", true))
            assertTrue(AccessStore.isPending(ctx, uid))

            AccessStore.allow(
                ctx,
                AccessStore.App(uid, ctx.packageName, AclLevel.PERMISSIONS, sigHash = sig),
                level = AclLevel.PERMISSIONS
            )

            val stopBlocked = ctx.contentResolver.call(uri, "exec", "stop", null)
            assertNotNull(stopBlocked)
            assertFalse(stopBlocked!!.getBoolean("ok", true))

            val escalatedSetting = ctx.contentResolver.call(
                uri, "exec", "putsetting secure enabled_accessibility_services com.evil/.Svc", null
            )
            assertNotNull(escalatedSetting)
            assertFalse(escalatedSetting!!.getBoolean("ok", true))
            assertTrue(escalatedSetting.getString("result").orEmpty().contains("FULL"))

            val selfAppops = ctx.contentResolver.call(
                uri, "exec", "cmd appops set ${ctx.packageName} READ_CLIPBOARD ignore", null
            )
            assertNotNull(selfAppops)
            assertFalse(selfAppops!!.getBoolean("ok", true))
            assertEquals(ctx.getString(R.string.t609), selfAppops.getString("result"))

            AccessStore.allow(
                ctx,
                AccessStore.App(uid, ctx.packageName, AclLevel.FULL, sigHash = sig),
                level = AclLevel.FULL
            )

            val selfUninstall = ctx.contentResolver.call(
                uri, "exec", "pm uninstall ${ctx.packageName}", null
            )
            assertNotNull(selfUninstall)
            assertFalse(selfUninstall!!.getBoolean("ok", true))
            assertEquals(ctx.getString(R.string.t609), selfUninstall.getString("result"))

            val revoked = AccessStore.revokeAll(ctx)
            assertEquals(1, revoked)
            DangerApprovals.clearAll(ctx)
            assertTrue(AccessStore.allowed(ctx).isEmpty())
            assertTrue(AccessStore.pending(ctx).isEmpty())
        } finally {
            AccessStore.revokeAll(ctx)
            DangerApprovals.clearAll(ctx)
        }
    }
}
