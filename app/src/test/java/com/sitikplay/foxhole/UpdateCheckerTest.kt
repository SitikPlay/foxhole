package com.sitikplay.foxhole

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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

    @Test
    fun releaseTagFilter_ignoresAdbCompanionReleases() {
        assertTrue(UpdateChecker.isAndroidReleaseTag("v1.4.0"))
        assertTrue(UpdateChecker.isAndroidReleaseTag("1.3.2"))
        assertFalse(UpdateChecker.isAndroidReleaseTag("adb-v1.2.0"))
        assertFalse(UpdateChecker.isAndroidReleaseTag("ADB-v2.0.0"))
        assertFalse(UpdateChecker.isAndroidReleaseTag(""))
        assertFalse(UpdateChecker.isAndroidReleaseTag("draft"))
    }

    @Test
    fun formatReleaseNotes_stripsMarkdownAndHtml() {
        val raw = """
            ### Что нового:
            - **Щит безопасности**: проверка `READ_CLIPBOARD` и <b>Panic Lockdown</b><br>
            - Подробнее: [релиз](https://example.com)
        """.trimIndent()
        val formatted = UpdateChecker.formatReleaseNotes(raw)
        assertFalse(formatted.contains("###"))
        assertFalse(formatted.contains("**"))
        assertFalse(formatted.contains("`"))
        assertFalse(formatted.contains("<b>"))
        assertTrue(formatted.contains("Щит безопасности"))
        assertTrue(formatted.contains("Panic Lockdown"))
        assertTrue(formatted.contains("релиз"))

        val longMd = (1..20).joinToString("\n") { "- **Пункт $it**: длинное описание изменения номер $it для теста усечения текста" }
        val condensed = UpdateChecker.formatReleaseNotes(longMd, maxLines = 6, maxChars = 300)
        assertTrue(condensed.endsWith("…"))
        assertTrue(condensed.lines().size <= 7)
    }

    @Test
    fun isValidApkFile_checksZipMagicAndSize() {
        val valid = File.createTempFile("valid_apk", ".apk")
        val bad = File.createTempFile("bad_html", ".apk")
        try {
            val bytes = ByteArray(2048)
            bytes[0] = 0x50; bytes[1] = 0x4B; bytes[2] = 0x03; bytes[3] = 0x04
            valid.writeBytes(bytes)
            bad.writeText("<html>404</html>")
            assertTrue(UpdateChecker.isValidApkFile(valid))
            assertFalse(UpdateChecker.isValidApkFile(bad))
        } finally {
            valid.delete()
            bad.delete()
        }
    }
}
