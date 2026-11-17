package com.sitikplay.foxhole

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
        }
    }

    @Test
    fun testRealGitHubReleaseQuery_currentLocalVersion() {
        val release = UpdateChecker.checkLatestRelease("1.3.2")
        assertNotNull("Expected latest release from GitHub API, got null", release)
        release?.let {
            assertFalse("Expected isNewer to be false when local version is 1.3.2", it.isNewer)
        }
    }
}
