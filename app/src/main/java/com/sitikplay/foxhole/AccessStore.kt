package com.sitikplay.foxhole

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccessStore {

    data class App(val uid: Int, val pkg: String, val level: AclLevel = AclLevel.FULL)

    private const val ALLOWED = "allowed"
    private const val PENDING = "pending"
    private const val LOG = "log"
    private const val MAX_PENDING = 20
    private const val MAX_LOG = 200

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("foxhole_access", Context.MODE_PRIVATE)

    private fun encode(a: App) = "${a.uid}|${a.pkg}|${a.level.name}"

    private fun decode(s: String): App? {
        val parts = s.split('|')
        if (parts.size < 2) return null
        val uid = parts[0].toIntOrNull() ?: return null
        val level = if (parts.size >= 3) {
            try {
                AclLevel.valueOf(parts[2])
            } catch (e: Exception) {
                AclLevel.FULL
            }
        } else {
            AclLevel.FULL
        }
        return App(uid, parts[1], level)
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
    fun isAllowed(ctx: Context, uid: Int): Boolean =
        allowed(ctx).any { it.uid == uid }

    @Synchronized
    fun level(ctx: Context, uid: Int): AclLevel =
        allowed(ctx).firstOrNull { it.uid == uid }?.level ?: AclLevel.FULL

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
    fun allow(ctx: Context, app: App, level: AclLevel = AclLevel.FULL) {
        val pending = read(ctx, PENDING)
        pending.removeAll { decode(it)?.uid == app.uid }
        write(ctx, PENDING, pending)
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        allowed.add(encode(App(app.uid, app.pkg, level)))
        write(ctx, ALLOWED, allowed)
        if (level == AclLevel.FULL) {
            log(ctx, "${app.pkg} РАЗРЕШЕНО пользователем")
        } else {
            log(ctx, "${app.pkg} РАЗРЕШЕНО пользователем (уровень ${level.label})")
        }
    }

    @Synchronized
    fun setLevel(ctx: Context, app: App, level: AclLevel) {
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        allowed.add(encode(App(app.uid, app.pkg, level)))
        write(ctx, ALLOWED, allowed)
        log(ctx, "${app.pkg} уровень доступа: ${level.label}")
    }

    @Synchronized
    fun deny(ctx: Context, app: App) {
        val pending = read(ctx, PENDING)
        pending.removeAll { decode(it)?.uid == app.uid }
        write(ctx, PENDING, pending)
        log(ctx, "${app.pkg} ОТКЛОНЕНО пользователем")
    }

    @Synchronized
    fun revoke(ctx: Context, app: App) {
        val allowed = read(ctx, ALLOWED)
        allowed.removeAll { decode(it)?.uid == app.uid }
        write(ctx, ALLOWED, allowed)
        log(ctx, "${app.pkg} ДОСТУП ОТОЗВАН")
    }

    @Synchronized
    fun log(ctx: Context, line: String) {
        val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
        val clean = line.replace('\n', ' ').replace('\r', ' ').take(160)
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

