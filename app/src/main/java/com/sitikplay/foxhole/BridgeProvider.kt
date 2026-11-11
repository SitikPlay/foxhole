package com.sitikplay.foxhole

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

class BridgeProvider : ContentProvider() {

    companion object {

        private const val MAX_CMD_LENGTH = 16_384
    }

    override fun onCreate(): Boolean = true

    private fun fail(msg: String): Bundle = Bundle().apply {
        putBoolean("ok", false)
        putString("result", msg)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val uid = Binder.getCallingUid()

        val pkgs = try {
            ctx.packageManager.getPackagesForUid(uid)?.filter { it.isNotBlank() }.orEmpty()
        } catch (e: Exception) {
            emptyList<String>()
        }
        val pkg = pkgs.firstOrNull() ?: "uid:$uid"

        val limited = RateLimiter.tryAcquire(uid)
        if (limited != null) {
            if (RateLimiter.shouldLog(uid)) {
                AccessStore.log(ctx, "$pkg ОТКАЗ (превышен лимит запросов)")
                RateLimiter.markLogged(uid)
            }
            return fail(limited)
        }

        try {
            if (method != "exec") return fail("ERR неверный вызов")

            val cmd = arg?.trim().orEmpty()
            if (cmd.isEmpty()) return fail("ERR неверный вызов")

            if (cmd.length > MAX_CMD_LENGTH) {
                AccessStore.log(ctx, "$pkg ОТКАЗ (команда длиннее $MAX_CMD_LENGTH символов)")
                return fail("ERR команда слишком длинная")
            }

            if (cmd.contains('\n') || cmd.contains('\r')) {
                AccessStore.log(ctx, "$pkg ОТКАЗ (многострочная команда)")
                return fail("ERR команда должна быть одной строкой")
            }

            AccessStore.pruneExpired(ctx)

            val sig = PkgSignature.hashOf(ctx, pkgs)
            val entry = AccessStore.findByUid(ctx, uid)
            if (entry != null) {
                if (entry.sigHash != null && sig != null && entry.sigHash != sig) {
                    AccessStore.resetToPending(
                        ctx, AccessStore.App(uid, entry.pkg, entry.level, sig)
                    )
                    AccessNotifications.notifyPending(ctx, AccessStore.App(uid, entry.pkg))
                    return fail(
                        "ERR подпись приложения изменилась — доступ сброшен, нужно повторное разрешение"
                    )
                }

                if (entry.sigHash == null && sig != null) {
                    AccessStore.bindSignature(ctx, uid, sig)
                }
                if (AccessStore.isExpired(ctx, uid)) {
                    AccessStore.revoke(ctx, entry)
                    AccessStore.log(ctx, "$pkg ДОСТУП ИСТЁК (срок доступа вышел)")
                    return fail("ERR срок доступа истёк: попроси пользователя выдать доступ заново")
                }
            }

            if (!AccessStore.isAllowed(ctx, uid)) {
                val wasPending = AccessStore.isPending(ctx, uid)
                val added = AccessStore.addPending(
                    ctx, AccessStore.App(uid, pkgs.joinToString(","), sigHash = sig)
                )
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

            if (have == AclLevel.FULL) {
                val danger = DangerousCmd.analyze(cmd)
                if (danger != null) {
                    val denied = confirmDanger(ctx, uid, pkg, cmd, danger)
                    if (denied != null) return denied
                }
            }

            val result = ServerClient.request(ctx, cmd)
            AccessStore.log(ctx, "$pkg $cmd -> ${result.take(60)}")
            return Bundle().apply {
                putBoolean("ok", result.startsWith("OK"))
                putString("result", result)
            }
        } finally {
            RateLimiter.release(uid)
        }
    }

    private fun confirmDanger(
        ctx: android.content.Context,
        uid: Int,
        pkg: String,
        cmd: String,
        danger: DangerousCmd.Danger
    ): Bundle? {
        DangerApprovals.prune(ctx)
        val hash = DangerApprovals.hashCmd(uid, cmd)
        val resolution = DangerApprovals.resolve(
            DangerApprovals.find(ctx, uid, hash), uid, hash, System.currentTimeMillis()
        )
        return when (resolution.kind) {
            DangerApprovals.Kind.NEW -> {
                DangerApprovals.addPending(ctx, resolution.request)

                AccessNotifications.cancelDanger(ctx, uid)
                AccessNotifications.notifyDangerConfirm(ctx, uid, resolution.id, riskText(danger))
                AccessStore.log(ctx, "$pkg ОЖИДАЕТ ПОДТВЕРЖДЕНИЯ: ${SecretMask.mask(cmd)}")
                fail(
                    "ERR подтверждение требуется (id=${resolution.id}) — " +
                        "повтори тот же запрос в течение минуты после решения пользователя"
                )
            }
            DangerApprovals.Kind.WAIT ->
                fail(
                    "ERR подтверждение требуется (id=${resolution.id}) — " +
                        "запрос всё ещё ждёт решения пользователя"
                )
            DangerApprovals.Kind.DENIED -> {
                DangerApprovals.consume(ctx, uid, hash)
                AccessStore.log(ctx, "$pkg ОТКАЗ (пользователь отклонил опасную команду)")
                fail("ERR пользователь отклонил команду")
            }
            DangerApprovals.Kind.EXECUTE -> {

                DangerApprovals.consume(ctx, uid, hash)
                AccessStore.log(ctx, "$pkg ПОДТВЕРЖДЕНО ПОЛЬЗОВАТЕЛЕМ: ${SecretMask.mask(cmd)}")
                null
            }
        }
    }

    private fun riskText(danger: DangerousCmd.Danger): String {
        val ctx = context ?: return ""
        return when (danger.kind) {
            DangerousCmd.DangerKind.UNINSTALL -> ctx.getString(R.string.t374)
            DangerousCmd.DangerKind.FILE_DESTROY -> ctx.getString(R.string.t375)
            DangerousCmd.DangerKind.SYSTEM_CHANGE -> ctx.getString(R.string.t376)
            DangerousCmd.DangerKind.REBOOT -> ctx.getString(R.string.t377)
            DangerousCmd.DangerKind.NETWORK_OFF -> ctx.getString(R.string.t378)
            DangerousCmd.DangerKind.APP_STOP -> ctx.getString(R.string.t379)
        }
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
