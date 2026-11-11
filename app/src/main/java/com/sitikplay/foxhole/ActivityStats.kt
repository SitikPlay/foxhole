package com.sitikplay.foxhole

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ActivityStats {

    enum class Kind { OK, DENIED, EVENT }

    data class Entry(val timeMs: Long, val pkg: String, val text: String, val kind: Kind)

    data class AppStats(
        val pkg: String,
        val ok: Int,
        val denied: Int,
        val lastCommands: List<String>,
        val lastMs: Long
    )

    private fun timeFormat(locale: Locale) = SimpleDateFormat("dd.MM HH:mm:ss", locale)

    fun parse(lines: List<String>, nowMs: Long, locale: Locale = Locale.getDefault()): List<Entry> {
        val entries = ArrayList<Entry>(lines.size)
        val cal = Calendar.getInstance()
        val thisYear = cal.get(Calendar.YEAR)
        for (line in lines) {
            val t = line.trim()
            if (t.length < 16) continue
            val timePart = t.substring(0, 15)
            val rest = t.substring(15).trim()
            if (rest.isEmpty()) continue
            val ms = try {
                val d = timeFormat(locale).apply { isLenient = false }.parse(timePart)
                if (d == null) continue
                cal.time = d
                cal.set(thisYear, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))

                if (cal.timeInMillis > nowMs + 24L * 60L * 60L * 1000L) {
                    cal.add(Calendar.YEAR, -1)
                }
                cal.timeInMillis
            } catch (e: ParseException) {
                continue
            }
            val pkg = rest.substringBefore(' ')
            val text = rest.substringAfter(' ', "")
            entries.add(Entry(ms, pkg, text, kindOf(text)))
        }
        return entries
    }

    private fun kindOf(text: String): Kind = when {
        text.contains("ОТКАЗ") || text.contains("ОТКЛОНЕНО") -> Kind.DENIED
        text.contains(" -> ") -> Kind.OK
        else -> Kind.EVENT
    }

    fun summary(entries: List<Entry>, nowMs: Long, windowMs: Long = 7L * 24L * 60L * 60L * 1000L): List<AppStats> {
        val from = nowMs - windowMs
        val grouped = LinkedHashMap<String, MutableList<Entry>>()
        for (e in entries) {
            if (e.timeMs < from || e.timeMs > nowMs) continue
            grouped.getOrPut(e.pkg) { ArrayList() }.add(e)
        }
        return grouped.map { (pkg, list) ->
            val sorted = list.sortedBy { it.timeMs }
            val commands = sorted.asSequence()
                .filter { it.kind == Kind.OK }
                .map { it.text.substringBefore(" -> ").take(60) }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
                .takeLast(3)
            AppStats(
                pkg = pkg,
                ok = list.count { it.kind == Kind.OK },
                denied = list.count { it.kind == Kind.DENIED },
                lastCommands = commands,
                lastMs = sorted.last().timeMs
            )
        }.sortedByDescending { it.lastMs }
    }

    fun filter(
        entries: List<Entry>,
        query: String,
        pkg: String?,
        kind: Kind?
    ): List<Entry> {
        val q = query.trim().lowercase()
        return entries.filter { e ->
            (pkg == null || e.pkg == pkg) &&
                (kind == null || e.kind == kind) &&
                (q.isEmpty() || e.pkg.lowercase().contains(q) || e.text.lowercase().contains(q))
        }
    }

    fun commandOf(text: String): String =
        text.substringBefore(" -> ").take(60)

    fun formatTime(ms: Long, locale: Locale = Locale.getDefault()): String =
        timeFormat(locale).format(Date(ms))

    fun looksLikeEntry(line: String, locale: Locale = Locale.getDefault()): Boolean =
        try {
            timeFormat(locale).apply { isLenient = false }.parse(line.trim().take(15)) != null &&
                line.trim().length > 16
        } catch (e: ParseException) {
            false
        }
}
