package com.sitikplay.foxhole

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val apkUrl: String?,
    val isNewer: Boolean
)

object UpdateChecker {
    private const val GITHUB_API_RELEASES = "https://api.github.com/repos/SitikPlay/foxhole/releases?per_page=15"
    private const val MAX_REDIRECTS = 6
    private val HEADING_PREFIX = Regex("(?m)^\\s*#{1,6}\\s*")
    private val MD_LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val HTML_TAG = Regex("<[^>]+>")
    private val MULTI_BLANK = Regex("\n{3,}")

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        object Cancelled : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /**
     * Strips Markdown/HTML markers and condenses long GitHub release notes into a concise
     * plain-text summary suitable for the in-app Android update dialog.
     */
    fun formatReleaseNotes(raw: String, maxLines: Int = 10, maxChars: Int = 700): String {
        if (raw.isBlank()) return ""
        var s = raw.replace("\r\n", "\n").replace("\r", "\n")
        s = MD_LINK.replace(s, "$1")
        s = HEADING_PREFIX.replace(s, "")
        s = HTML_TAG.replace(s, "")
        s = s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
        s = s.replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("`", "")
        s = MULTI_BLANK.replace(s, "\n\n").trim()

        val out = StringBuilder()
        var linesCount = 0
        var truncated = false
        for (line in s.lineSequence()) {
            val cleanLine = line.trimEnd()
            if (cleanLine.isBlank()) {
                if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
                continue
            }
            val nextLen = out.length + cleanLine.length + (if (out.isEmpty()) 0 else 1)
            if (linesCount >= maxLines || (out.isNotEmpty() && nextLen > maxChars)) {
                truncated = true
                break
            }
            if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
            out.append(cleanLine)
            linesCount++
        }
        val text = out.toString().trim()
        return if (truncated) "$text\n…" else text
    }

    /**
     * Проверяет, относится ли тег к Android-приложению Foxhole
     * (игнорирует релизы компаньона foxhole-adb с тегами `adb-v*`).
     */
    fun isAndroidReleaseTag(tagName: String): Boolean {
        val clean = tagName.trim()
        if (clean.isEmpty()) return false
        if (clean.startsWith("adb-", ignoreCase = true)) return false
        val stripped = clean.removePrefix("v").removePrefix("V")
        return stripped.firstOrNull()?.isDigit() == true
    }

    /**
     * Проверяет последнюю Android-версию на GitHub Releases.
     * Возвращает ReleaseInfo или null при ошибке/отсутствии сети.
     */
    fun checkLatestRelease(currentVersion: String): ReleaseInfo? {
        return try {
            val url = URL(GITHUB_API_RELEASES)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Foxhole-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val releases = JSONArray(response)
            var fallbackCandidate: ReleaseInfo? = null
            for (i in 0 until releases.length()) {
                val json = releases.optJSONObject(i) ?: continue
                if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) continue
                val tagName = json.optString("tag_name", "").trim()
                if (!isAndroidReleaseTag(tagName)) continue

                val name = json.optString("name", tagName).trim()
                val body = formatReleaseNotes(json.optString("body", ""))

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.optJSONObject(j) ?: continue
                        val aName = asset.optString("name", "")
                        if (aName.endsWith(".apk", ignoreCase = true) && !aName.contains("debug", ignoreCase = true)) {
                            val dl = asset.optString("browser_download_url", "")
                            if (dl.isNotEmpty()) {
                                apkUrl = dl
                                break
                            }
                        }
                    }
                    if (apkUrl == null) {
                        for (j in 0 until assets.length()) {
                            val asset = assets.optJSONObject(j) ?: continue
                            val aName = asset.optString("name", "")
                            if (aName.endsWith(".apk", ignoreCase = true)) {
                                val dl = asset.optString("browser_download_url", "")
                                if (dl.isNotEmpty()) {
                                    apkUrl = dl
                                    break
                                }
                            }
                        }
                    }
                }

                val info = ReleaseInfo(
                    tagName = tagName,
                    name = if (name.isNotEmpty()) name else "Foxhole $tagName",
                    body = body,
                    apkUrl = apkUrl,
                    isNewer = isVersionNewer(tagName, currentVersion)
                )
                if (apkUrl != null) return info
                if (fallbackCandidate == null) fallbackCandidate = info
            }
            fallbackCandidate
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Сравнивает семантические версии, например v1.3.2 vs 1.3.1.
     */
    fun isVersionNewer(remoteTag: String, localVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V")

        val remoteCore = cleanRemote.substringBefore("-").substringBefore("+")
        val localCore = cleanLocal.substringBefore("-").substringBefore("+")

        val remoteNums = remoteCore.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val localNums = localCore.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteNums.size, localNums.size)
        for (i in 0 until maxLen) {
            val r = remoteNums.getOrElse(i) { 0 }
            val l = localNums.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        val remoteHasPrerelease = cleanRemote.contains("-")
        val localHasPrerelease = cleanLocal.contains("-")

        if (!remoteHasPrerelease && localHasPrerelease) {
            return true
        }
        if (remoteHasPrerelease && !localHasPrerelease) {
            return false
        }

        return false
    }

    fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 1024L) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { input ->
                val read = input.read(header)
                read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the release APK directly into the app's private cache directory (`cache/updates`),
     * bypassing public Downloads / Files app, and validates the ZIP/APK signature bytes.
     */
    fun downloadApkInApp(
        ctx: Context,
        apkUrl: String,
        fileName: String = "Foxhole-update.apk",
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): DownloadResult {
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            return DownloadResult.Error("Non-HTTPS download URL blocked")
        }
        val updatesDir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { old -> old.delete() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Foxhole-update.apk" }
        val partFile = File(updatesDir, "$safeName.part")
        val targetFile = File(updatesDir, safeName)

        var conn: HttpURLConnection? = null
        return try {
            var currentUrl = apkUrl
            var hops = 0
            while (true) {
                if (isCancelled()) return DownloadResult.Cancelled
                val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Foxhole-App")
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")?.trim().orEmpty()
                    c.disconnect()
                    if (loc.isEmpty() || !loc.startsWith("https://", ignoreCase = true) || ++hops > MAX_REDIRECTS) {
                        return DownloadResult.Error("Invalid redirect ($code)")
                    }
                    currentUrl = loc
                    continue
                }
                conn = c
                if (code != HttpURLConnection.HTTP_OK) {
                    return DownloadResult.Error("HTTP $code")
                }
                break
            }

            val total = conn!!.contentLengthLong
            var downloaded = 0L
            var lastUiMs = 0L
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { out ->
                    while (true) {
                        if (isCancelled()) {
                            partFile.delete()
                            return DownloadResult.Cancelled
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastUiMs >= 120L) {
                            lastUiMs = now
                            onProgress(downloaded, total)
                        }
                    }
                    out.flush()
                }
            }
            onProgress(downloaded, total)

            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile) || !isValidApkFile(targetFile)) {
                partFile.delete()
                targetFile.delete()
                return DownloadResult.Error("Corrupted APK archive")
            }
            DownloadResult.Success(targetFile)
        } catch (e: Exception) {
            partFile.delete()
            DownloadResult.Error(e.message ?: "Download error")
        } finally {
            conn?.disconnect()
        }
    }

    fun canInstallPackages(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.packageManager.canRequestPackageInstalls()
        else true

    fun openInstallPermissionSettings(ctx: Context): Boolean = try {
        val intent = Intent(
            SysSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ctx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    fun launchInstaller(ctx: Context, apkFile: File): Boolean = try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    fun openExternalUrl(ctx: Context, url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
