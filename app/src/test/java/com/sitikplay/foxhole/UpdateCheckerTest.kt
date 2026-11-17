package com.sitikplay.foxhole

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun versionComparison_newerPatch() {
        assertTrue(UpdateChecker.isVersionNewer("v1.3.2", "1.3.1"))
        assertTrue(UpdateChecker.isVersionNewer("1.3.2", "1.3.1"))
    }

    @Test
    fun versionComparison_newerMinor() {
        assertTrue(UpdateChecker.isVersionNewer("v1.4.0", "1.3.1"))
        assertTrue(UpdateChecker.isVersionNewer("1.4.0", "1.3.2"))
    }

    @Test
    fun versionComparison_newerMajor() {
        assertTrue(UpdateChecker.isVersionNewer("v2.0.0", "1.3.2"))
    }

    @Test
    fun versionComparison_sameVersion() {
        assertFalse(UpdateChecker.isVersionNewer("v1.3.2", "1.3.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.3.2", "1.3.2"))
    }

    @Test
    fun versionComparison_olderVersion() {
        assertFalse(UpdateChecker.isVersionNewer("v1.3.0", "1.3.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.2.9", "1.3.2"))
    }

    @Test
    fun versionComparison_handlesPrefixesAndTags() {
        assertTrue(UpdateChecker.isVersionNewer("v1.3.3-beta", "1.3.2"))
        assertFalse(UpdateChecker.isVersionNewer("v1.3.2-rc1", "1.3.2"))
    }
}
