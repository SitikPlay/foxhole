package com.sitikplay.foxhole

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object RateLimiter {

    private const val WINDOW_MS = 10_000L
    private const val MAX_REQUESTS_PER_WINDOW = 30
    private const val MAX_CONCURRENT_PER_UID = 2

    private class Window(val startMs: Long) {

        val count = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val windows = ConcurrentHashMap<Int, Window>()

    private val limitedSince = ConcurrentHashMap<Int, Long>()

    private val lastLogged = ConcurrentHashMap<Int, Long>()

    private val inFlight = ConcurrentHashMap<Int, AtomicInteger>()

    fun tryAcquire(uid: Int, nowMs: Long = System.currentTimeMillis()): String? {
        val w = windows.compute(uid) { _, cur ->
            if (cur == null || nowMs - cur.startMs >= WINDOW_MS) Window(nowMs) else cur
        }!!
        if (w.count.incrementAndGet() > MAX_REQUESTS_PER_WINDOW) {
            markLimited(uid, nowMs)
            return LIMIT_MSG
        }
        val flight = inFlight.computeIfAbsent(uid) { AtomicInteger(0) }
        if (flight.incrementAndGet() > MAX_CONCURRENT_PER_UID) {
            flight.decrementAndGet()
            markLimited(uid, nowMs)
            return BUSY_MSG
        }
        return null
    }

    private fun markLimited(uid: Int, nowMs: Long) {
        limitedSince.compute(uid) { _, prev ->
            if (prev == null || nowMs - prev >= WINDOW_MS) nowMs else prev
        }
    }

    fun release(uid: Int) {
        inFlight[uid]?.decrementAndGet()
    }

    fun shouldLog(uid: Int): Boolean {
        val since = limitedSince[uid] ?: return false
        val last = lastLogged[uid] ?: return true
        return last < since
    }

    fun markLogged(uid: Int, nowMs: Long = System.currentTimeMillis()) {
        lastLogged[uid] = nowMs
    }

    const val LIMIT_MSG = "ERR слишком много запросов: подожди немного и повтори"
    const val BUSY_MSG = "ERR предыдущий запрос ещё выполняется: подожди его завершения"
}
