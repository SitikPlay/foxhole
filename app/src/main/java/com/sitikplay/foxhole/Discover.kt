package com.sitikplay.foxhole

import android.content.Context
import android.net.wifi.WifiManager
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object Discover {

    fun service(ctx: Context, serviceType: String, timeoutMs: Long): Pair<String, Int>? {
        val found = AtomicReference<Pair<String, Int>?>(null)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(ctx, serviceType) { address: InetAddress?, port: Int ->
            val host = address?.hostAddress
            if (host != null && port > 0) {
                found.set(host to port)
                latch.countDown()
            }
        }
        val wifi = ctx.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("foxhole-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        mdns.start()
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get()
        } finally {
            mdns.stop()
            runCatching { lock?.release() }
        }
    }
}

