package com.sitikplay.foxhole

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccessStore {

    data class App(
        val uid: Int,
        val pkg: String,
        val level: AclLevel = AclLevel.FULL,
        val sigHash: String? = null,
        val expiry: Long = PERMANENT
    ) {
        val pkgList: List<String> get() = pkg.split(',').filter { it.isNotBlank() }
        val expired: Boolean get() = expiry in 1..System.currentTimeMillis()

        companion object {
            const val PERMANENT = 0L
            const val ONE_HOUR_MS = 60L * 60L * 1000L
        }
    }

    private const val ALLOWED = "allowed"
    private const val PENDING = "pending"
    private const val LOG = "log"
    private const val MAX_PENDING = 20
    private const val MAX_LOG = 200

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("foxhole_access", Context.MODE_PRIVATE)

    private fun encode(a: App) = "${a.uid}|${a.pkg}|${a.level.name}|${a.sigHash ?: ""}|${a.expiry}"

    private fun decode(s: String): App? {
        val parts = s.split('|')
        if (parts.size < 2) return null
        val uid = parts[0].toIntOrNull() ?: return null
        val pkg = parts[1]
        val level = if (parts.size >= 3) {
            try {
                AclLevel.valueOf(parts[2])
            } catch (e: Exception) {
                AclLevel.FULL
            }
        } else {
            AclLevel.FULL
        }
        val sig = if (parts.size >= 4) parts[3].takeIf { it.isNotBlank() } else null
        val expiry = if (parts.size >= 5) parts[4].toLongOrNull() ?: 0L else 0L
        return App(uid, pkg, level, sig, expiry)
    }

    private fun read(ctx: Context, key: String): MutableSet<String> =
        HashSet(prefs(ctx).getStringSet(key, emptySet()) ?: emptySet())

    private fun write(ctx: Context, key: String, set: Set<String>) {
        prefs(ctx).edit().putStringSet(key, set).apply()
    }

    @Synchronized
    fun allowed(ctx: Context): List<App> =
        read(ctx, ALLOWED).mapNotNull { decode(it) }.sortedBy { it.pkg }

    @Synchronized
    fun pending(ctx: Context): List<App> =
        read(ctx, PENDING).mapNotNull { decode(it) }.sortedBy { it.pkg }

    @Synchronized
    fun findByUid(ctx: Context, uid: Int): App? =
        allowed(ctx).firstOrNull { it.uid == uid }

    @Synchronized
    fun isAllowed(ctx: Context, uid: Int): Boolean =
        allowed(ctx).any { it.uid == uid }

    @Synchronized
    fun level(ctx: Context, uid: Int): AclLevel =
        allowed(ctx).firstOrNull { it.uid == uid }?.level ?: AclLevel.FULL

    @Synchronized
    fun isExpired(ctx: Context, uid: Int): Boolean =
        findByUid(ctx, uid)?.expired == true

    @Synchronized
    fun isPending(ctx: Context, uid: Int): Boolean =
        pending(ctx).any { it.uid == uid }

    @Synchronized
    fun addPending(ctx: Context, app: App): Boolean {
        if (isAllowed(ctx, app.uid)) return false
        val set = read(ctx, PENDING)
        set.removeAll { decode(it)?.uid == app.uid }
        if (set.size >= MAX_PENDING) return false
        set.add(encode(app))
        write(ctx, PENDING, set)
        return true
    }

    @Synchronized
    fun allow(ctx: Context, app: App, level: AclLevel = AclLevel.FULL, durationMs: Long = 0L) {
        val expiry = if (durationMs > 0) System.currentTimeMillis() + durationMs else App.PERMANENT
        val pending = read(ctx, PENDING)
        pending.removeAll { decode(it)?.uid == app.uid }
        write(ctx, PENDING, pending)
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        allowed.add(encode(App(app.uid, app.pkg, level, app.sigHash, expiry)))
        write(ctx, ALLOWED, allowed)
        if (level == AclLevel.FULL) {
            log(ctx, ctx.getString(R.string.t316, app.pkg))
        } else {
            log(ctx, ctx.getString(R.string.t317, app.pkg, level.label))
        }
    }

    @Synchronized
    fun setLevel(ctx: Context, app: App, level: AclLevel) {
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        allowed.add(encode(App(app.uid, app.pkg, level, app.sigHash, app.expiry)))
        write(ctx, ALLOWED, allowed)
        log(ctx, ctx.getString(R.string.t318, app.pkg, level.label))
    }

    @Synchronized
    fun bindSignature(ctx: Context, uid: Int, sigHash: String) {
        val entry = allowed(ctx).firstOrNull { it.uid == uid } ?: return
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == uid }
        allowed.add(encode(entry.copy(sigHash = sigHash)))
        write(ctx, ALLOWED, allowed)
        log(ctx, ctx.getString(R.string.t319, entry.pkg))
    }

    @Synchronized
    fun resetToPending(ctx: Context, app: App) {
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        write(ctx, ALLOWED, allowed)
        addPending(ctx, app)
        log(ctx, ctx.getString(R.string.t320, app.pkg))
    }

    @Synchronized
    fun pruneExpired(ctx: Context): Int {
        val expired = allowed(ctx).filter { it.expired }
        if (expired.isEmpty()) return 0
        val allowed = read(ctx, ALLOWED)
        expired.forEach { entry ->
            allowed.removeAll { decode(it)?.uid == entry.uid }
            log(ctx, ctx.getString(R.string.t321, entry.pkg))
        }
        write(ctx, ALLOWED, allowed)
        return expired.size
    }

    @Synchronized
    fun deny(ctx: Context, app: App) {
        val pending = read(ctx, PENDING)
        pending.removeAll { decode(it)?.uid == app.uid }
        write(ctx, PENDING, pending)
        log(ctx, ctx.getString(R.string.t322, app.pkg))
    }

    @Synchronized
    fun revoke(ctx: Context, app: App) {
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        write(ctx, ALLOWED, allowed)
        log(ctx, ctx.getString(R.string.t323, app.pkg))
    }

    @Synchronized
    fun log(ctx: Context, line: String) {
        val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())

        val clean = SecretMask.mask(line)
            .replace('\n', ' ').replace('\r', ' ').take(160)
        val lines = readLog(ctx).lines().filter { it.isNotBlank() }.takeLast(MAX_LOG - 1)
        prefs(ctx).edit().putString(LOG, (lines + "$time $clean").joinToString("\n")).apply()
    }

    @Synchronized
    fun readLog(ctx: Context): String = prefs(ctx).getString(LOG, "") ?: ""

    @Synchronized
    fun clearLog(ctx: Context) {
        prefs(ctx).edit().remove(LOG).apply()
    }
}
