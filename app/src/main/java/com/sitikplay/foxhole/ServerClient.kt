package com.sitikplay.foxhole

import android.content.Context
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

object ServerClient {

    private val tokenLock = Any()

    fun token(ctx: Context): String = synchronized(tokenLock) {
        val prefs = ctx.getSharedPreferences("foxhole", Context.MODE_PRIVATE)
        prefs.getString("token", null)?.let { return@synchronized it }
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("token", t).apply()
        t
    }

    fun resetToken(ctx: Context): String = synchronized(tokenLock) {
        ctx.getSharedPreferences("foxhole", Context.MODE_PRIVATE).edit().remove("token").apply()
        token(ctx)
    }

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
            "ERR сервер недоступен: ${e.message}"
        }
    }
}

