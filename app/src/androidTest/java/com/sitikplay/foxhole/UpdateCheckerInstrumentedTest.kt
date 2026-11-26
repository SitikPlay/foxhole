package com.sitikplay.foxhole

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateCheckerInstrumentedTest {

    @Test
    fun testRealGitHubReleaseQuery_olderLocalVersion() {
        val release = UpdateChecker.checkLatestRelease("1.3.0")
        assertNotNull("Expected latest release from GitHub API, got null", release)
        release?.let {
            assertTrue("Expected tag name to be non-empty", it.tagName.isNotEmpty())
            assertTrue("Expected isNewer to be true when local version is 1.3.0", it.isNewer)
            assertNotNull("Expected APK URL in release assets", it.apkUrl)
            assertTrue("Expected APK URL to end with .apk", it.apkUrl!!.endsWith(".apk", ignoreCase = true))
            assertFalse("Release notes should be stripped of markdown headings", it.body.contains("###"))
        }
    }

    @Test
    fun testRealGitHubReleaseQuery_futureLocalVersion() {
        val release = UpdateChecker.checkLatestRelease("99.0.0")
        assertNotNull("Expected latest release from GitHub API, got null", release)
        release?.let {
            assertFalse("Expected isNewer to be false when local version is 99.0.0", it.isNewer)
        }
    }

    @Test
    fun fileProviderResolvesPrivateUpdateApkUri() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val updatesDir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "test-update.apk")
        try {
            val bytes = ByteArray(2048)
            bytes[0] = 0x50; bytes[1] = 0x4B; bytes[2] = 0x03; bytes[3] = 0x04
            apk.writeBytes(bytes)
            assertTrue(UpdateChecker.isValidApkFile(apk))
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            assertEquals("content", uri.scheme)
            assertEquals("${ctx.packageName}.fileprovider", uri.authority)
        } finally {
            apk.delete()
        }
    }
}
