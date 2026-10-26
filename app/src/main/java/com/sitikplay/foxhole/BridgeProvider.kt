package com.sitikplay.foxhole

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

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

        if (cmd.contains('\n') || cmd.contains('\r')) {
            AccessStore.log(ctx, "$pkg ОТКАЗ (многострочная команда)")
            return fail("ERR команда должна быть одной строкой")
        }

        if (!AccessStore.isAllowed(ctx, uid)) {
            val wasPending = AccessStore.isPending(ctx, uid)
            val added = AccessStore.addPending(ctx, AccessStore.App(uid, pkg))
            AccessStore.log(ctx, "$pkg ОТКАЗ (нет разрешения): $cmd")
            if (added && !wasPending) {
                AccessNotifications.notifyPending(ctx, AccessStore.App(uid, pkg))
            }
            return fail(
                if (added) "ERR нет разрешения: открой Foxhole и разреши это приложение"
                else "ERR нет разрешения, список ожидания переполнен"
            )
        }

        if (cmd.split(Regex("\\s+")).first() == "stop") {
            AccessStore.log(ctx, "$pkg ОТКАЗ (запрещённая операция): $cmd")
            return fail("ERR операция запрещена")
        }

        val need = Acl.classify(cmd)
        val have = AccessStore.level(ctx, uid)
        if (need == AclLevel.PERMISSIONS && have == AclLevel.READ ||
            need == AclLevel.FULL && have != AclLevel.FULL
        ) {
            AccessStore.log(
                ctx, "$pkg ОТКАЗ (нужен уровень ${need.label}, есть ${have.label}): $cmd"
            )
            return fail("ERR требуется уровень доступа ${need.label} (у вас: ${have.label})")
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

