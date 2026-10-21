/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SitikPlay
 */

package com.sitikplay.foxhole

import android.content.Context

/** Пользовательские настройки (SharedPreferences "foxhole_settings"). */
object Settings {
    const val DEFAULT_PORT = 27183
    private const val PREFS = "foxhole_settings"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Порт локального сервера. Применяется после перезапуска сервера. */
    fun port(ctx: Context): Int = prefs(ctx).getInt("port", DEFAULT_PORT).coerceIn(1024, 65535)

    fun setPort(ctx: Context, port: Int) {
        prefs(ctx).edit().putInt("port", port.coerceIn(1024, 65535)).apply()
    }

    /** Проверять сервер автоматически при открытии экрана. */
    fun autoCheck(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_check", true)

    fun setAutoCheck(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("auto_check", value).apply()
    }

    /** Спрашивать подтверждение перед выдачей доступа приложению. */
    fun confirmAllow(ctx: Context): Boolean = prefs(ctx).getBoolean("confirm_allow", true)

    fun setConfirmAllow(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean("confirm_allow", value).apply()
    }
}
