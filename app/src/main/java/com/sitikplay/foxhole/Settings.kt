package com.sitikplay.foxhole

import android.content.Context

object Settings {
    const val DEFAULT_PORT = 27183
    private const val PREFS = "foxhole_settings"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun port(ctx: Context): Int = prefs(ctx).getInt("port", DEFAULT_PORT).coerceIn(1024, 65535)

    fun setPort(ctx: Context, port: Int) {
        prefs(ctx).edit().putInt("port", port.coerceIn(1024, 65535)).apply()
    }

    fun autoCheck(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_check", true)

    fun setAutoCheck(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("auto_check", value).apply()
    }

    fun confirmAllow(ctx: Context): Boolean = prefs(ctx).getBoolean("confirm_allow", true)

    fun setConfirmAllow(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("confirm_allow", value).apply()
    }

    fun confirmDevice(ctx: Context): Boolean = prefs(ctx).getBoolean("confirm_device", true)

    fun setConfirmDevice(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("confirm_device", value).apply()
    }

    fun dangerousWarn(ctx: Context): Boolean = prefs(ctx).getBoolean("dangerous_warn", true)

    fun setDangerousWarn(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("dangerous_warn", value).apply()
    }

    fun confirmWindowSec(ctx: Context): Int = prefs(ctx).getInt("confirm_window_sec", 120)

    fun setConfirmWindowSec(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("confirm_window_sec", value.coerceIn(15, 600)).apply()
    }

    fun flagSecureScreens(ctx: Context): Boolean = prefs(ctx).getBoolean("flag_secure_screens", true)

    fun setFlagSecureScreens(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("flag_secure_screens", value).apply()
    }

    fun terminalHistory(ctx: Context): Boolean = prefs(ctx).getBoolean("terminal_history", true)

    fun setTerminalHistory(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("terminal_history", value).apply()
    }

    fun langChosen(ctx: Context): Boolean = prefs(ctx).getBoolean("lang_chosen", false)

    fun setLangChosen(ctx: Context) {
        prefs(ctx).edit().putBoolean("lang_chosen", true).apply()
    }

    fun bootStart(ctx: Context): Boolean = prefs(ctx).getBoolean("boot_start", false)

    fun setBootStart(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("boot_start", value).apply()
    }

    fun wdsAddress(ctx: Context): String = prefs(ctx).getString("wds_address", "") ?: ""

    fun setWdsAddress(ctx: Context, value: String) {
        prefs(ctx).edit().putString("wds_address", value.trim()).apply()
    }

    fun parseWdsAddress(value: String): Pair<String, Int>? {
        val v = value.trim()
        val idx = v.lastIndexOf(':')
        if (idx <= 0 || idx == v.length - 1) return null
        val host = v.substring(0, idx).trim()
        val port = v.substring(idx + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || host.contains(' ')) return null
        if (port < 1024 || port > 65535) return null
        return Pair(host, port)
    }

    fun bootDisableWifi(ctx: Context): Boolean = prefs(ctx).getBoolean("boot_disable_wifi", true)

    fun setBootDisableWifi(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("boot_disable_wifi", value).apply()
    }

    fun stoppedBootCount(ctx: Context): Int = prefs(ctx).getInt("stopped_boot_count", -1)

    fun setStoppedBootCount(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("stopped_boot_count", value).apply()
    }

    fun autostartBootCount(ctx: Context): Int = prefs(ctx).getInt("autostart_boot_count", -1)

    fun setAutostartBootCount(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("autostart_boot_count", value).apply()
    }

    fun notifAsked(ctx: Context): Boolean = prefs(ctx).getBoolean("notif_asked", false)

    fun setNotifAsked(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("notif_asked", value).apply()
    }

    fun foldExpanded(ctx: Context, key: String, def: Boolean): Boolean =
        prefs(ctx).getBoolean("fold_$key", def)

    fun setFoldExpanded(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean("fold_$key", value).apply()
    }
}
