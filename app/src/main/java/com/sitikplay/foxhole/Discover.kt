package com.sitikplay.foxhole

import android.content.Context
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
        // ВАЖНО: без собственного multicast lock! На HyperOS multicast-локи
        // эксклюзивные: когда приложение берёт lock, система отпускает свой —
        // и сессия спаривания (AdbPairingThread) умирает на глазах
        // («Pairing failed» сразу после acquire). NsdManager-поиск сам
        // управляет сокетами системы, приложению lock не нужен.
        mdns.start()
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get()
        } finally {
            mdns.stop()
        }
    }

    /**
     * А4x-фолбэк: на части прошивок (например HyperOS) NsdManager не видит
     * сервис спаривания даже при открытом окне с кодом — приём мультикаста
     * сломан. Тогда ищем порт спаривания прямым сканированием собственных
     * адресов: окно с кодом держит TLS-порт открытым около 2 минут.
     *
     * 1) connect-скан эфемерного диапазона портов на своём IP;
     * 2) TLS-фильтр: порт спаривания — TLS-сервер, прочие открытые порты
     *    молчат или не отвечают по TLS.
     *
     * Возвращает порты-кандидаты по возрастанию; вызывающий пробует
     * спаривание по каждому, пока какой-то не примет код.
     */
    fun scanPairingPorts(
        hosts: List<String>,
        exclude: Set<Int> = emptySet(),
        budgetMs: Long = 40_000
    ): List<Pair<String, Int>> {
        val deadline = System.currentTimeMillis() + budgetMs
        // Loopback первым: живой тест показал, что соединение с собственного
        // адреса интерфейса HyperOS отклоняет, а 127.0.0.1 работает.
        val ordered = listOf("127.0.0.1") + hosts.distinct().filter { it != "127.0.0.1" }
        for (host in ordered) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val scanned = scanHost(host, exclude, left)
            if (scanned.isNotEmpty()) {
                return scanned.map { host to it }
            }
        }
        return emptyList()
    }

    private fun scanHost(host: String, exclude: Set<Int>, budgetMs: Long): List<Int> {
        val deadline = System.currentTimeMillis() + budgetMs
        val range = ephemeralRange()
        val ports = range.filter { it !in exclude }
        val tls = Collections.synchronizedList(ArrayList<Int>())
        val pool = Executors.newFixedThreadPool(96)
        try {
            // ВАЖНО: соединение с портом спаривания нельзя «трогать впустую» —
            // сервер спаривания одноразовый и умирает от подключения без данных.
            // Поэтому probe встроен в сам скан: сразу после connect отправляем
            // TLS ClientHello и читаем ответ. Отказ в connect — порт закрыт,
            // обмен TLS-записью — кандидат на порт спаривания.
            for (p in ports) {
                if (System.currentTimeMillis() > deadline) break
                pool.execute {
                    if (probeTls(host, p)) tls.add(p)
                }
            }
            pool.shutdown()
            val left = deadline - System.currentTimeMillis()
            if (left > 0) pool.awaitTermination(left, TimeUnit.MILLISECONDS)
        } finally {
            pool.shutdownNow()
        }
        val result = tls.sorted()
        return result
    }

    /** Диапазон эфемерных портов ядра: adbd выбирает порт спаривания из него. */
    fun ephemeralRange(): IntRange = parsePortRange(
        try {
            File("/proc/sys/net/ipv4/ip_local_port_range").readText().trim()
        } catch (e: Exception) {
            null
        }
    )

    /** Разбор «от до» из /proc (37000 50000). Некорректный ввод — запасной диапазон. */
    fun parsePortRange(text: String?): IntRange {
        val parts = text?.trim()?.split(Regex("\\s+")).orEmpty()
        val from = parts.getOrNull(0)?.toIntOrNull()
        val to = parts.getOrNull(1)?.toIntOrNull()
        return if (from != null && to != null && from in 1024..65535 && to in from..65535) {
            from..to
        } else {
            32768..60999
        }
    }

    /** Локальные IPv4-адреса без loopback: приватные (192.168/10/172.16) первыми. */
    fun localIpv4s(): List<String> = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .toList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Подключается к порту и спрашивает TLS: отвечает TLS-записью — кандидат. */
    private fun probeTls(host: String, port: Int): Boolean {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), 250)
            socket.soTimeout = 400
            socket.getOutputStream().write(clientHello())
            val first = socket.getInputStream().read()
            // Любая TLS-запись: 20 handshake2, 21 alert, 22 handshake, 23 appdata
            first in 20..23
        } catch (e: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Минимальный TLS 1.2 ClientHello. От порта спаривания ждём в ответ любую
     * TLS-запись (ServerHello или alert) — этого достаточно, чтобы отличить
     * adbd-порт спаривания от прочих открытых портов.
     */
    private fun clientHello(): ByteArray {
        fun u16(v: Int) = byteArrayOf(
            ((v shr 8) and 0xff).toByte(),
            (v and 0xff).toByte()
        )
        // server_name отсутствует; группы x25519, форматы точек, алгоритмы подписи
        val extensions = byteArrayOf(
            0x00, 0x0a, 0x00, 0x04, 0x00, 0x02, 0x00, 0x1d, // supported_groups: x25519
            0x00, 0x0b, 0x00, 0x02, 0x01, 0x00,             // ec_point_formats: uncompressed
            0x00, 0x0d, 0x00, 0x06, 0x00, 0x04, 0x04, 0x01, 0x08, 0x04 // signature_algorithms
        )
        val random = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = byteArrayOf(0xc0.toByte(), 0x30.toByte()) // TLS_ECDHE_RSA_WITH_AES_256_GCM
        val body = byteArrayOf(0x03, 0x03) + random +
            byteArrayOf(0x00) + u16(cipher.size) + cipher +
            byteArrayOf(0x01, 0x00) +
            u16(extensions.size) + extensions
        val handshake = byteArrayOf(0x01) +
            byteArrayOf(0x00, ((body.size shr 8) and 0xff).toByte(), (body.size and 0xff).toByte()) + body
        return byteArrayOf(0x16, 0x03, 0x01) + u16(handshake.size) + handshake
    }
}
