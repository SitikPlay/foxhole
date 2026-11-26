package com.sitikplay.foxhole

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
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
                val body = json.optString("body", "").trim()

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

    /**
     * Запускает скачивание APK через системный DownloadManager или браузер.
     */
    fun downloadApk(ctx: Context, apkUrl: String, fileName: String = "Foxhole-update.apk"): Boolean {
        return try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                    setTitle(fileName)
                    setDescription("Foxhole Update")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setMimeType("application/vnd.android.package-archive")
                }
                dm.enqueue(req)
                true
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
