package com.sitikplay.foxhole

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object DangerApprovals {

    enum class Kind { EXECUTE, WAIT, DENIED, NEW }

    enum class Status { PENDING, APPROVED, DENIED }

    data class Request(
        val id: String,
        val uid: Int,
        val cmdHash: String,
        val createdAtMs: Long,
        val status: Status
    )

    data class Resolution(val kind: Kind, val id: String, val request: Request)

    const val TTL_MS = 60_000L

    private const val PREFS = "foxhole_danger"
    private const val KEY = "requests"
    private const val MAX_REQUESTS = 10

    fun hashCmd(uid: Int, cmd: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$uid\n$cmd".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun newId(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun resolve(
        rec: Request?,
        uid: Int,
        cmdHash: String,
        nowMs: Long,
        ttlMs: Long = TTL_MS
    ): Resolution {
        val fresh = rec != null && rec.uid == uid && rec.cmdHash == cmdHash &&
            nowMs - rec.createdAtMs <= ttlMs
        if (!fresh) {
            val request = Request(newId(), uid, cmdHash, nowMs, Status.PENDING)
            return Resolution(Kind.NEW, request.id, request)
        }
        return when (rec!!.status) {
            Status.PENDING -> Resolution(Kind.WAIT, rec.id, rec)
            Status.APPROVED -> Resolution(Kind.EXECUTE, rec.id, rec)
            Status.DENIED -> Resolution(Kind.DENIED, rec.id, rec)
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun all(ctx: Context): List<Request> =
        (prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet())
            .mapNotNull { decode(it) }

    @Synchronized
    private fun save(ctx: Context, list: List<Request>) {
        prefs(ctx).edit()
            .putStringSet(KEY, list.takeLast(MAX_REQUESTS).map { encode(it) }.toSet())
            .apply()
    }

    fun find(ctx: Context, uid: Int, cmdHash: String): Request? =
        all(ctx).firstOrNull { it.uid == uid && it.cmdHash == cmdHash }

    fun findUid(ctx: Context, id: String): Int? =
        all(ctx).firstOrNull { it.id == id }?.uid

    @Synchronized
    fun addPending(ctx: Context, request: Request) {
        val rest = all(ctx).filterNot { it.uid == request.uid && it.cmdHash == request.cmdHash }
        save(ctx, rest + request)
    }

    @Synchronized
    fun setDecision(ctx: Context, id: String, approved: Boolean): Boolean {
        val cur = all(ctx)
        val rec = cur.firstOrNull { it.id == id && it.status == Status.PENDING } ?: return false
        save(
            ctx,
            cur - rec + rec.copy(status = if (approved) Status.APPROVED else Status.DENIED)
        )
        return true
    }

    @Synchronized
    fun consume(ctx: Context, uid: Int, cmdHash: String) {
        save(ctx, all(ctx).filterNot { it.uid == uid && it.cmdHash == cmdHash })
    }

    @Synchronized
    fun prune(ctx: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val cur = all(ctx)
        val fresh = cur.filter { nowMs - it.createdAtMs <= TTL_MS }
        if (fresh.size == cur.size) return 0
        save(ctx, fresh)
        return cur.size - fresh.size
    }

    private fun encode(r: Request): String =
        "${r.id}|${r.uid}|${r.cmdHash}|${r.createdAtMs}|" + when (r.status) {
            Status.PENDING -> "p"
            Status.APPROVED -> "a"
            Status.DENIED -> "d"
        }

    private fun decode(s: String): Request? {
        val p = s.split('|')
        if (p.size != 5) return null
        val uid = p[1].toIntOrNull() ?: return null
        val at = p[3].toLongOrNull() ?: return null
        val status = when (p[4]) {
            "a" -> Status.APPROVED
            "d" -> Status.DENIED
            "p" -> Status.PENDING
            else -> return null
        }
        return Request(p[0], uid, p[2], at, status)
    }
}
