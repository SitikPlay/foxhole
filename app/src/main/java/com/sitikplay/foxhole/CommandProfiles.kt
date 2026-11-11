package com.sitikplay.foxhole

import android.content.Context

object CommandProfiles {

    data class Profile(val name: String, val template: String)

    private val SLOT = Regex("\\{([a-z_]+)\\}")

    val BUILTINS = listOf(
        Profile("grant", "grant {package} {permission}"),
        Profile("revoke", "revoke {package} {permission}"),
        Profile("appop", "appop {package} {op} {mode}"),
        Profile("getsetting", "getsetting {namespace} {key}"),
        Profile("putsetting", "putsetting {namespace} {key} {value}")
    )

    private const val PREFS = "foxhole_profiles"
    private const val KEY = "custom"
    private const val MAX_CUSTOM = 20
    const val MAX_TEMPLATE_LEN = 512
    const val MAX_VALUE_LEN = 512

    private const val UNSAFE_CHARS = "\"'\u0060\\\u0024|;&<>\n\r\u0000"

    fun isUnsafeValue(v: String): Boolean =
        v.isBlank() || v.length > MAX_VALUE_LEN || v != v.trim() || v.any { it in UNSAFE_CHARS }

    fun slots(template: String): List<String> =
        SLOT.findAll(template).map { it.groupValues[1] }.toList()

    fun compose(template: String, values: Map<String, String>): String? {
        var out = template
        for (key in slots(template)) {
            val value = values[key] ?: return null
            if (isUnsafeValue(value)) return null
            out = out.replace("{$key}", value)
        }
        if (SLOT.containsMatchIn(out)) return null
        return out.trim().ifEmpty { null }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun custom(ctx: Context): List<Profile> =
        (prefs(ctx).getString(KEY, null) ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val i = line.indexOf('|')
                if (i <= 0) return@mapNotNull null
                val name = line.substring(0, i)
                val template = line.substring(i + 1)
                if (name.isBlank() || template.isBlank()) null
                else Profile(name, template)
            }

    @Synchronized
    fun saveCustom(ctx: Context, name: String, template: String): Boolean {
        val n = name.trim()
        val t = template.trim()

        if (n.isEmpty() || t.isEmpty() || n.contains('\n') || n.contains('|') ||
            n.length > 64 || t.length > MAX_TEMPLATE_LEN
        ) return false
        val cur = custom(ctx).filterNot { it.name == n }
        if (cur.size >= MAX_CUSTOM) return false
        val lines = (cur + Profile(n, t)).joinToString("\n") { "${it.name}|${it.template}" }
        prefs(ctx).edit().putString(KEY, lines).apply()
        return true
    }

    @Synchronized
    fun deleteCustom(ctx: Context, name: String) {
        val rest = custom(ctx).filterNot { it.name == name }
        val lines = rest.joinToString("\n") { "${it.name}|${it.template}" }
        prefs(ctx).edit().putString(KEY, lines).apply()
    }
}
