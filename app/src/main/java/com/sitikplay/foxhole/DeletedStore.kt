package com.sitikplay.foxhole

import android.content.Context
import java.io.File

object DeletedStore {

    data class Record(
        val pkg: String,
        val label: String,
        val system: Boolean,
        val removedAt: Long,
        val apkDir: String?
    ) {
        val restorePossible: Boolean get() = !system && apkDir != null
    }

    private const val KEY = "records"
    private const val SEP = '\u0001'

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("foxhole_deleted", Context.MODE_PRIVATE)

    private fun escape(s: String?): String =
        (s ?: "").replace(SEP, ' ')

    private fun encode(r: Record): String =
        "${r.pkg}$SEP${escape(r.label)}$SEP${if (r.system) "1" else "0"}$SEP" +
            "${r.removedAt}$SEP${escape(r.apkDir)}"

    private fun decode(s: String): Record? {
        val p = s.split(SEP)
        if (p.size != 5) return null
        val at = p[3].toLongOrNull() ?: return null
        val dir = p[4].takeIf { it.isNotBlank() }
        return Record(p[0], p[1], p[2] == "1", at, dir)
    }

    @Synchronized
    fun all(ctx: Context): List<Record> =
        (prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet())
            .mapNotNull { decode(it) }
            .sortedByDescending { it.removedAt }

    @Synchronized
    fun add(ctx: Context, record: Record) {
        val set = HashSet(prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet())
        set.removeAll { decode(it)?.pkg == record.pkg }
        set.add(encode(record))
        prefs(ctx).edit().putStringSet(KEY, set).apply()
    }

    @Synchronized
    fun remove(ctx: Context, pkg: String) {
        val set = HashSet(prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet())
        set.removeAll { decode(it)?.pkg == pkg }
        prefs(ctx).edit().putStringSet(KEY, set).apply()
    }

    fun apkDirFor(ctx: Context, pkg: String): File =
        File(ctx.getExternalFilesDir("apks"), pkg)

    @Synchronized
    fun cleanup(ctx: Context): Int {
        val now = System.currentTimeMillis()
        val stale = all(ctx).filter { UninstallCommands.isExpired(it.removedAt, now) }
        stale.forEach { r ->
            if (!r.system && r.apkDir != null) {
                try {
                    File(r.apkDir).deleteRecursively()
                } catch (e: Exception) {
                    AccessStore.log(ctx, "корзина: не удалось удалить APK ${r.pkg}: ${e.message}")
                }
            }
            remove(ctx, r.pkg)
        }
        return stale.size
    }

    fun bytes(ctx: Context): Long =
        all(ctx).sumOf { UninstallCommands.dirSize(it.apkDir?.let { d -> File(d) }) }
}

