package com.sitikplay.foxhole

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object Server {
    const val PORT = 27183

    private const val MAX_WORKERS = 8
    private const val CMD_TIMEOUT_MS = 20_000L
    private const val MAX_OUTPUT = 128 * 1024

    private val PKG = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)*$")
    private val PERM = Regex("^[a-zA-Z0-9_.]+$")
    private val OP = Regex("^[A-Za-z0-9_.:-]+$")
    private val KEY = Regex("^[a-zA-Z0-9_.]+$")
    private val VALUE = Regex("^[^\\p{Cntrl}]+$")
    private val MODES = setOf("allow", "ignore", "deny", "default", "foreground")
    private val NAMESPACES = setOf("global", "secure", "system")
    private val WS = Regex("\\s+")

    @JvmStatic
    fun main(args: Array<String>) {
        val token = args.getOrNull(0) ?: return
        val port = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: PORT
        val server = ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))
        println("server started on $port, uid=${android.os.Process.myUid()}")
        val pool = Executors.newFixedThreadPool(MAX_WORKERS)
        while (true) {
            val socket = server.accept()
            pool.execute { handle(socket, token) }
        }
    }

    private fun handle(socket: Socket, token: String) {
        socket.use { s ->
            s.soTimeout = 5000
            val reader = s.getInputStream().bufferedReader()
            val writer = s.getOutputStream().bufferedWriter()
            val reply = try {
                val got = reader.readLine() ?: ""
                val expected = token.toByteArray(Charsets.UTF_8)
                if (!MessageDigest.isEqual(got.toByteArray(Charsets.UTF_8), expected)) {
                    "ERR bad token"
                } else {
                    execute(reader.readLine() ?: "")
                }
            } catch (e: Exception) {
                System.err.println("handle failed: $e")
                "ERR internal"
            }
            writer.write(reply)
            writer.newLine()
            writer.flush()
            if (reply == "OK stopping") {
                Thread.sleep(200)
                System.exit(0)
            }
        }
    }

    private fun execute(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return "ERR empty command"
        val p = trimmed.split(WS)
        val bad = "ERR bad args"
        return when (p[0]) {
            "ping" -> if (p.size == 1) "OK pong uid=${android.os.Process.myUid()}" else bad

            "stop" -> {
                if (p.size != 1) return bad
                "OK stopping"
            }

            "grant", "revoke" ->
                if (p.size == 3 && PKG.matches(p[1]) && PERM.matches(p[2]))
                    run("pm", p[0], p[1], p[2])
                else bad

            "appop" ->
                if (p.size == 4 && PKG.matches(p[1]) && OP.matches(p[2]) && p[3] in MODES)
                    run("cmd", "appops", "set", p[1], p[2], p[3])
                else bad

            "getsetting" ->
                if (p.size == 3 && p[1] in NAMESPACES && KEY.matches(p[2]))
                    run("settings", "get", p[1], p[2])
                else bad

            "putsetting" -> {
                val a = trimmed.split(WS, limit = 4)
                if (a.size == 4 && a[1] in NAMESPACES && KEY.matches(a[2]) && VALUE.matches(a[3]))
                    run("settings", "put", a[1], a[2], a[3])
                else bad
            }

            else -> run("sh", "-c", trimmed)
        }
    }

    private fun run(vararg cmd: String): String {
        return try {
            val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val text = AtomicReference("")
            val truncated = AtomicBoolean(false)
            val reader = Thread {
                try {
                    val (out, cut) = readLimited(proc.inputStream.bufferedReader())
                    text.set(out)
                    truncated.set(cut)
                } catch (e: Exception) {
                }
            }.apply { isDaemon = true; start() }
            if (!proc.waitFor(CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                reader.join(1000)
                return "ERR timeout"
            }
            reader.join(3000)
            val out = text.get()
            val marker = if (truncated.get()) "\n… (вывод обрезан)" else ""
            val code = proc.exitValue()
            (if (code == 0) "OK " else "ERR code=$code ") + out + marker
        } catch (e: Exception) {
            System.err.println("run failed: $e")
            "ERR internal"
        }
    }

    private fun readLimited(reader: java.io.Reader): Pair<String, Boolean> {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        var truncated = false
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            if (sb.length < MAX_OUTPUT) {
                val take = minOf(n, MAX_OUTPUT - sb.length)
                sb.append(buf, 0, take)
                if (take < n) truncated = true
            } else {
                truncated = true
            }
        }
        return sb.toString().trim() to truncated
    }
}

