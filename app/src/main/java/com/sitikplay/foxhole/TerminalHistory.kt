package com.sitikplay.foxhole

import android.content.Context

object TerminalHistory {

    private const val PREFS = "foxhole_terminal"
    private const val KEY = "history"
    private const val MAX = 30

    fun mask(cmd: String): String = SecretMask.mask(cmd)

    fun record(ctx: Context, cmd: String) {
        if (!Settings.terminalHistory(ctx)) return
        val raw = cmd.trim()
        if (raw.isEmpty() || raw.contains('\n')) return
        // В prefs кладём маскированную команду: открытый пароль/токен не должен
        // лежать в хранилище. Повторный запуск из истории требует вписать
        // секрет заново — осознанный компромисс в пользу безопасности.
        val c = SecretMask.mask(raw)
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

    // Миграция старых записей: раньше команды с секретами хранились открытым
    // текстом (маскирование было только при показе). Переписываем всё
    // маскированными строками, чтобы секреты не оставались в prefs.
    @Synchronized
    fun migrateToMasked(ctx: Context) {
        val items = all(ctx)
        if (items.none { SecretMask.mask(it) != it }) return
        save(ctx, items.map { SecretMask.mask(it) })
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(ctx: Context, items: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, items.joinToString("\n")).apply()
    }
}
