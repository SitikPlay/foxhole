package com.sitikplay.foxhole

import android.content.Context
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

object ServerClient {

    private val tokenLock = Any()

    @Volatile
    private var cachedToken: String? = null

    private const val UNAVAILABLE_PREFIX = "ERR|"

    fun token(ctx: Context): String = synchronized(tokenLock) {
        cachedToken?.let { return@synchronized it }
        SecretVault.migrate(ctx)
        val blob = SecretVault.getSecureString(ctx, "server_token")
        val t = blob?.let { SecretVault.decryptString(it) }?.takeIf { it.isNotBlank() } ?: newToken(ctx)
        cachedToken = t
        t
    }

    fun newToken(ctx: Context): String = synchronized(tokenLock) {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        SecretVault.putSecureString(ctx, "server_token", SecretVault.encryptString(t))
        cachedToken = t
        t
    }

    fun resetToken(ctx: Context): String = newToken(ctx)

    // Клиентская ошибка «до сервера не достучались» (сервер не отвечает — это не
    // его ответ). Стабильный машинный префикс "ERR|" отличает её от ответов
    // сервера ("OK …" / "ERR code=…"), по нему UI решает, гасить ли статус.
    // А9: раньше здесь был захардкоженный русский текст — на других локалях
    // проверки по подстроке ломались.
    fun isUnavailable(res: String): Boolean = res.startsWith(UNAVAILABLE_PREFIX)

    fun request(ctx: Context, cmd: String): String {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", Settings.port(ctx)), 2000)
                s.soTimeout = 25_000
                val w = s.getOutputStream().bufferedWriter()
                w.write(token(ctx)); w.newLine()
                w.write(cmd); w.newLine()
                w.flush()
                s.getInputStream().bufferedReader().readText().trim()
            }
        } catch (e: Exception) {
            UNAVAILABLE_PREFIX + ctx.getString(R.string.t461, e.message ?: "")
        }
    }
}
