package com.sitikplay.foxhole

import android.content.Context

object TerminalHistory {

    private const val PREFS = "foxhole_terminal"
    private const val KEY = "history"
    private const val MAX = 30

    fun mask(cmd: String): String = SecretMask.mask(cmd)

    fun record(ctx: Context, cmd: String) {
        if (!Settings.terminalHistory(ctx)) return
        val c = cmd.trim()
        if (c.isEmpty() || c.contains('\n')) return
        val cur = all(ctx).toMutableList()

        cur.removeAll { it == c }
        cur.add(0, c)
        if (cur.size > MAX) cur.subList(MAX, cur.size).clear()
        save(ctx, cur)
    }

    fun all(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(ctx: Context, items: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, items.joinToString("\n")).apply()
    }
}
