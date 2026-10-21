/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SitikPlay
 */

package com.sitikplay.foxhole

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/**
 * Точка входа для сторонних приложений:
 *   contentResolver.call(Uri.parse("content://com.sitikplay.foxhole.bridge"), "exec", "ping", null)
 * Ответ: Bundle с "ok" (Boolean) и "result" (String).
 *
 * Неизвестное приложение получает отказ и попадает в список ожидающих:
 * пользователь решает в самом Foxhole.
 */
class BridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val uid = Binder.getCallingUid()
        val pkg = callingPackage ?: "uid:$uid"
        val res = Bundle()

        fun fail(msg: String): Bundle {
            res.putBoolean("ok", false)
            res.putString("result", msg)
            return res
        }

        val cmd = arg?.trim().orEmpty()
        if (method != "exec" || cmd.isEmpty()) return fail("ERR неверный вызов")

        // Протокол — ровно одна строка; иначе вторая часть молча потеряется на сервере.
        if (cmd.contains('\n') || cmd.contains('\r')) {
            AccessStore.log(ctx, "$pkg ОТКАЗ (многострочная команда)")
            return fail("ERR команда должна быть одной строкой")
        }

        if (!AccessStore.isAllowed(ctx, uid)) {
            val added = AccessStore.addPending(ctx, AccessStore.App(uid, pkg))
            AccessStore.log(ctx, "$pkg ОТКАЗ (нет разрешения): $cmd")
            return fail(
                if (added) "ERR нет разрешения: открой Foxhole и разреши это приложение"
                else "ERR нет разрешения, список ожидания переполнен"
            )
        }

        // Останавливать сервер сторонним приложениям нельзя.
        if (cmd.split(Regex("\\s+")).first() == "stop") {
            AccessStore.log(ctx, "$pkg ОТКАЗ (запрещённая операция): $cmd")
            return fail("ERR операция запрещена")
        }

        val result = ServerClient.request(ctx, cmd)
        AccessStore.log(ctx, "$pkg $cmd -> ${result.take(60)}")
        res.putBoolean("ok", result.startsWith("OK"))
        res.putString("result", result)
        return res
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int = 0
}
