package com.sitikplay.foxhole

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
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

    enum class ConnectStatus {
        CONNECTED,
        PAIRING_REQUIRED,
        AUTH_FAILED,
        WIFI_DISABLED,
        FAILED
    }

    data class ConnectResult(
        val status: ConnectStatus,
        val host: String? = null,
        val port: Int = -1,
        val lastError: Exception? = null
    )

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
        return try {
            mdns.start()
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mdns.stop() }
        }
    }

    /**
     * Подключение к локальному беспроводному ADB с многоступенчатым фолбэком:
     * 1) стандартный mDNS (`_adb-tls-connect._tcp`);
     * 2) сохранённый пользователем адрес `host:port` из настроек;
     * 3) прямое TLS-сканирование эфемерных портов (обход сломанного mDNS на HyperOS/MIUI/ColorOS).
     */
    fun connectWithFallback(
        ctx: Context,
        adb: FoxholeAdb,
        mdnsTimeoutMs: Long = 5_000L,
        scanBudgetMs: Long = 20_000L,
        excludePorts: Set<Int> = setOf(Settings.port(ctx)),
        onProgress: ((String) -> Unit)? = null
    ): ConnectResult {
        var lastError: Exception? = null

        fun tryConnect(host: String, port: Int): ConnectResult? {
            return try {
                adb.resetConnection()
                if (adb.connect(host, port)) {
                    FoxholeAdb.markPaired(ctx)
                    ConnectResult(ConnectStatus.CONNECTED, host, port)
                } else {
                    null
                }
            } catch (e: AdbPairingRequiredException) {
                ConnectResult(ConnectStatus.PAIRING_REQUIRED, host, port, e)
            } catch (e: AdbAuthenticationFailedException) {
                ConnectResult(ConnectStatus.AUTH_FAILED, host, port, e)
            } catch (e: Exception) {
                lastError = e
                null
            }
        }

        // 1. mDNS-поиск
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mdnsFound = service(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, mdnsTimeoutMs)
            if (mdnsFound != null) {
                tryConnect(mdnsFound.first, mdnsFound.second)?.let { return it }
            }
        }

        // 2. Ручной адрес из настроек (если задан)
        Settings.parseWdsAddress(Settings.wdsAddress(ctx))?.let { (h, p) ->
            tryConnect(h, p)?.let { return it }
        }

        // 3. Проверка включённой беспроводной отладки перед сканированием портов
        val wifiDebugOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.provider.Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1
            } catch (e: Exception) {
                true
            }
        } else {
            false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !wifiDebugOn) {
            return ConnectResult(ConnectStatus.WIFI_DISABLED, lastError = lastError)
        }

        // 4. Прямой TLS-скан открытых эфемерных портов (спасение при мёртвом mDNS)
        onProgress?.invoke(ctx.getString(R.string.t472))
        val hostIp = runCatching { AndroidUtils.getHostIpAddress(ctx) }.getOrNull()
        val hosts = (localIpv4s() + listOfNotNull(hostIp)).distinct()
        val candidates = scanTlsPorts(hosts, excludePorts, scanBudgetMs, collectAllHosts = true)
        for ((host, port) in candidates) {
            tryConnect(host, port)?.let { return it }
        }

        return ConnectResult(ConnectStatus.FAILED, lastError = lastError)
    }

    /**
     * А4x-фолбэк: на части прошивок (например HyperOS) NsdManager не видит
     * сервис спаривания даже при открытом окне с кодом — приём мультикаста
     * сломан. Тогда ищем порт спаривания прямым сканированием собственных
     * адресов: окно с кодом держит TLS-порт открытым около 2 минут.
     */
    fun scanPairingPorts(
        hosts: List<String>,
        exclude: Set<Int> = emptySet(),
        budgetMs: Long = 40_000
    ): List<Pair<String, Int>> = scanTlsPorts(hosts, exclude, budgetMs, collectAllHosts = false)

    fun scanTlsPorts(
        hosts: List<String>,
        exclude: Set<Int> = emptySet(),
        budgetMs: Long = 40_000,
        collectAllHosts: Boolean = false
    ): List<Pair<String, Int>> {
        val deadline = System.currentTimeMillis() + budgetMs
        // Loopback первым: живой тест показал, что соединение с собственного
        // адреса интерфейса HyperOS отклоняет, а 127.0.0.1 работает.
        val ordered = listOf("127.0.0.1") + hosts.distinct().filter { it.isNotBlank() && it != "127.0.0.1" }
        val results = ArrayList<Pair<String, Int>>()
        for (host in ordered) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val scanned = scanHost(host, exclude, if (collectAllHosts) minOf(left, 6_000L) else left)
            if (scanned.isNotEmpty()) {
                val mapped = scanned.map { host to it }
                if (!collectAllHosts) return mapped
                results.addAll(mapped)
                // Если проверили 127.0.0.1 и первый реальный LAN-интерфейс — достаточно
                if (host != "127.0.0.1") break
            }
        }
        return results
    }

    private fun scanHost(host: String, exclude: Set<Int>, budgetMs: Long): List<Int> {
        val deadline = System.currentTimeMillis() + budgetMs
        val range = ephemeralRange()
        val ports = range.filter { it !in exclude }
        val tls = Collections.synchronizedList(ArrayList<Int>())
        val pool = Executors.newFixedThreadPool(96)
        try {
            // ВАЖНО: соединение с портом нельзя «трогать впустую» —
            // сервер спаривания одноразовый и умирает от подключения без данных.
            // Поэтому probe встроен в сам скан: сразу после connect отправляем
            // TLS ClientHello и читаем ответ. Отказ в connect — порт закрыт,
            // обмен TLS-записью — кандидат на TLS-порт adbd.
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
        return tls.sorted()
    }

    /** Диапазон эфемерных портов ядра: adbd выбирает порт из него. */
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

    /** Локальные IPv4-адреса без loopback: сначала активные Wi-Fi/локальные интерфейсы. */
    fun localIpv4s(): List<String> = try {
        val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        ifaces.asSequence()
            .filter { iface -> runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) }
            .flatMap { iface ->
                val isWlan = iface.name.startsWith("wlan", ignoreCase = true)
                val notP2p = runCatching { !iface.isPointToPoint }.getOrDefault(true)
                Collections.list(iface.inetAddresses).asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { addr -> Triple(addr, isWlan && notP2p, addr.isSiteLocalAddress) }
            }
            .sortedWith(
                compareByDescending<Triple<Inet4Address, Boolean, Boolean>> { it.second }
                    .thenByDescending { it.third }
            )
            .map { it.first.hostAddress }
            .distinct()
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
     * Минимальный TLS 1.2 ClientHello. От порта ждём в ответ любую
     * TLS-запись (ServerHello или alert) — этого достаточно, чтобы отличить
     * TLS-порт adbd от прочих открытых портов.
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
