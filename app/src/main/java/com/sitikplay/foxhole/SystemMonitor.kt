package com.sitikplay.foxhole

import android.content.Context
import kotlin.math.roundToInt

/**
 * Reads and parses live device telemetry (battery, RAM, /data storage, uptime, and top processes)
 * through the privileged UID 2000 Foxhole server.
 */
object SystemMonitor {

    enum class ChargeState { CHARGING, FULL, DISCHARGING }

    data class BatteryInfo(
        val level: Int,
        val state: ChargeState,
        val tempC: Float,
        val voltageMv: Int,
        val tech: String
    )

    data class MemInfo(
        val totalMb: Int,
        val availableMb: Int,
        val usedMb: Int,
        val usedPercent: Int
    )

    data class StorageInfo(
        val totalGb: Float,
        val availGb: Float,
        val usedPercent: Int
    )

    data class ProcInfo(
        val rssMb: Int,
        val name: String,
        val stoppablePkg: String?
    )

    data class MonitorSnapshot(
        val battery: BatteryInfo?,
        val mem: MemInfo?,
        val storage: StorageInfo?,
        val uptime: String,
        val procs: List<ProcInfo>
    )

    private val NON_STOPPABLE_PKGS = setOf(
        "com.sitikplay.foxhole",
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings"
    )

    fun parseBattery(raw: String): BatteryInfo? {
        val body = stripOk(raw) ?: return null
        val values = LinkedHashMap<String, String>()
        for (line in body.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim().lowercase()
            val v = line.substring(idx + 1).trim()
            values[k] = v
        }
        val level = values["level"]?.toIntOrNull() ?: return null
        val statusCode = values["status"]?.toIntOrNull() ?: 3
        val state = when (statusCode) {
            2 -> ChargeState.CHARGING
            5 -> ChargeState.FULL
            else -> ChargeState.DISCHARGING
        }
        val tempTenths = values["temperature"]?.toIntOrNull() ?: 0
        val voltage = values["voltage"]?.toIntOrNull() ?: 0
        val tech = values["technology"]?.ifEmpty { "?" } ?: "?"
        return BatteryInfo(
            level = level.coerceIn(0, 100),
            state = state,
            tempC = tempTenths / 10f,
            voltageMv = voltage,
            tech = tech
        )
    }

    fun parseMemInfo(raw: String): MemInfo? {
        val body = stripOk(raw) ?: return null
        val kbMap = HashMap<String, Long>()
        for (line in body.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim()
            val num = line.substring(idx + 1).trim().substringBefore(' ').toLongOrNull() ?: continue
            kbMap[k] = num
        }
        val totalKb = kbMap["MemTotal"] ?: return null
        if (totalKb <= 0L) return null
        val availKb = kbMap["MemAvailable"] ?: ((kbMap["MemFree"] ?: 0L) + (kbMap["Cached"] ?: 0L))
        val clampedAvail = availKb.coerceIn(0L, totalKb)
        val totalMb = (totalKb / 1024L).toInt().coerceAtLeast(1)
        val availMb = (clampedAvail / 1024L).toInt()
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val pct = ((usedMb.toFloat() / totalMb) * 100f).roundToInt().coerceIn(0, 100)
        return MemInfo(totalMb = totalMb, availableMb = availMb, usedMb = usedMb, usedPercent = pct)
    }

    fun parseStorage(raw: String): StorageInfo? {
        val body = stripOk(raw) ?: return null
        val dataLine = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Filesystem", ignoreCase = true) }
            .lastOrNull() ?: return null
        val cols = dataLine.split(Regex("\\s+"))
        if (cols.size < 5) return null
        val totalKb = cols[1].toLongOrNull() ?: return null
        val availKb = cols[3].toLongOrNull() ?: return null
        if (totalKb <= 0L) return null
        val pct = cols[4].removeSuffix("%").toIntOrNull()
            ?: (((totalKb - availKb).coerceAtLeast(0L).toFloat() / totalKb) * 100f).roundToInt()
        val kbPerGb = 1024f * 1024f
        return StorageInfo(
            totalGb = totalKb / kbPerGb,
            availGb = availKb.coerceAtLeast(0L) / kbPerGb,
            usedPercent = pct.coerceIn(0, 100)
        )
    }

    fun parseUptime(raw: String): String {
        val body = stripOk(raw) ?: return ""
        return body.trim().replace(Regex("\\s+"), " ")
    }

    fun parseProcesses(raw: String, limit: Int = 8): List<ProcInfo> {
        val body = stripOk(raw) ?: return emptyList()
        val entries = ArrayList<Pair<Int, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("RSS", ignoreCase = true)) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) continue
            val rssKb = parts[0].toIntOrNull() ?: continue
            val name = parts[1].trim()
            if (rssKb <= 0 || name.isEmpty() || name.startsWith("[")) continue
            entries.add(rssKb to name)
        }
        return entries
            .sortedByDescending { it.first }
            .distinctBy { it.second }
            .take(limit)
            .map { (rssKb, name) ->
                val basePkg = name.substringBefore(':')
                val stoppable = if (AppInspector.isValidPackage(basePkg) &&
                    basePkg !in NON_STOPPABLE_PKGS
                ) {
                    basePkg
                } else {
                    null
                }
                ProcInfo(rssMb = (rssKb / 1024f).roundToInt().coerceAtLeast(1), name = name, stoppablePkg = stoppable)
            }
    }

    fun read(ctx: Context): MonitorSnapshot? {
        if (!ServerClient.request(ctx, "ping").startsWith("OK pong")) return null
        val battery = parseBattery(ServerClient.request(ctx, "dumpsys battery"))
        val mem = parseMemInfo(ServerClient.request(ctx, "head -n 8 /proc/meminfo"))
        val storage = parseStorage(ServerClient.request(ctx, "df -k /data"))
        val uptime = parseUptime(ServerClient.request(ctx, "uptime"))
        val procs = parseProcesses(ServerClient.request(ctx, "ps -A -o RSS,NAME"))
        return MonitorSnapshot(
            battery = battery,
            mem = mem,
            storage = storage,
            uptime = uptime,
            procs = procs
        )
    }

    fun formatSummary(ctx: Context, snap: MonitorSnapshot): String {
        val lines = mutableListOf<String>()
        snap.battery?.let { b ->
            val stateLabel = ctx.getString(
                when (b.state) {
                    ChargeState.CHARGING -> R.string.t530
                    ChargeState.FULL -> R.string.t531
                    ChargeState.DISCHARGING -> R.string.t532
                }
            )
            lines.add(
                ctx.getString(R.string.t518) + ": " +
                    ctx.getString(R.string.t519, b.level, stateLabel, b.tempC, b.voltageMv, b.tech)
            )
        }
        snap.mem?.let { m ->
            lines.add(
                ctx.getString(R.string.t520) + ": " +
                    ctx.getString(R.string.t521, m.availableMb, m.totalMb, m.usedPercent)
            )
        }
        snap.storage?.let { s ->
            lines.add(
                ctx.getString(R.string.t522) + ": " +
                    ctx.getString(R.string.t523, s.availGb, s.totalGb, s.usedPercent)
            )
        }
        if (snap.uptime.isNotEmpty()) {
            lines.add(ctx.getString(R.string.t524) + ": " + snap.uptime)
        }
        if (snap.procs.isNotEmpty()) {
            lines.add("")
            lines.add(ctx.getString(R.string.t525) + ":")
            snap.procs.forEach { p ->
                lines.add("• ${p.name} (${p.rssMb} MB)")
            }
        }
        return lines.joinToString("\n")
    }

    private fun stripOk(raw: String): String? =
        if (raw.startsWith("OK")) raw.removePrefix("OK").trimStart() else null
}
