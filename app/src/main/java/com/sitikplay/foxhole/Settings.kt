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

    fun bootStart(ctx: Context): Boolean = prefs(ctx).getBoolean("boot_start", false)

    fun setBootStart(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("boot_start", value).apply()
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

