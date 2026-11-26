package com.sitikplay.foxhole

import android.content.Context

/**
 * Reads and toggles quick system tweaks (animations, Private DNS, DPI, show touches,
 * stay awake, Phantom Process Killer, and cache trim) through the Foxhole server.
 */
object SystemTweaks {

    const val ADGUARD_DNS = "dns.adguard-dns.com"
    const val CLOUDFLARE_DNS = "one.one.one.one"
    const val MIN_DPI = 240
    const val MAX_DPI = 720

    enum class DnsPreset(val mode: String, val host: String?) {
        AUTO("opportunistic", null),
        ADGUARD("hostname", ADGUARD_DNS),
        CLOUDFLARE("hostname", CLOUDFLARE_DNS),
        OFF("off", null)
    }

    data class DensityInfo(
        val physical: Int,
        val overrideDpi: Int?
    ) {
        val effective: Int get() = overrideDpi ?: physical
    }

    data class TweaksState(
        val animScale: String,
        val dnsPreset: DnsPreset,
        val stayAwakeOn: Boolean,
        val showTouchesOn: Boolean,
        val phantomDisabled: Boolean,
        val density: DensityInfo?
    ) {
        val adguardDnsOn: Boolean get() = dnsPreset == DnsPreset.ADGUARD
    }

    fun parseSettingValue(raw: String): String {
        if (!raw.startsWith("OK")) return ""
        val v = raw.removePrefix("OK").trim()
        return if (v == "null") "" else v
    }

    fun parseDnsPreset(mode: String, specifier: String): DnsPreset {
        val m = mode.trim().lowercase()
        val s = specifier.trim().lowercase()
        return when {
            m == "off" -> DnsPreset.OFF
            m == "hostname" && s == ADGUARD_DNS -> DnsPreset.ADGUARD
            m == "hostname" && s == CLOUDFLARE_DNS -> DnsPreset.CLOUDFLARE
            else -> DnsPreset.AUTO
        }
    }

    fun parseDensity(raw: String): DensityInfo? {
        if (!raw.startsWith("OK")) return null
        val body = raw.removePrefix("OK").trim()
        var physical: Int? = null
        var overrideVal: Int? = null
        for (line in body.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("Physical density:", ignoreCase = true) ->
                    physical = t.substringAfter(':').trim().toIntOrNull()
                t.startsWith("Override density:", ignoreCase = true) ->
                    overrideVal = t.substringAfter(':').trim().toIntOrNull()
            }
        }
        val phys = physical ?: return null
        return DensityInfo(physical = phys, overrideDpi = overrideVal)
    }

    fun clampDpi(dpi: Int): Int = dpi.coerceIn(MIN_DPI, MAX_DPI)

    fun readState(ctx: Context): TweaksState? {
        val ping = ServerClient.request(ctx, "ping")
        if (!ping.startsWith("OK pong")) return null

        val anim = parseSettingValue(ServerClient.request(ctx, "getsetting global window_animation_scale"))
            .ifEmpty { "1.0" }
        val dnsMode = parseSettingValue(ServerClient.request(ctx, "getsetting global private_dns_mode"))
        val dnsSpec = parseSettingValue(ServerClient.request(ctx, "getsetting global private_dns_specifier"))
        val stayAwake = parseSettingValue(ServerClient.request(ctx, "getsetting global stay_on_while_plugged_in"))
            .toIntOrNull() ?: 0
        val showTouches = parseSettingValue(ServerClient.request(ctx, "getsetting system show_touches"))
            .toIntOrNull() ?: 0
        val phantom = parseSettingValue(
            ServerClient.request(ctx, "getsetting global settings_enable_monitor_phantom_procs")
        )
        val density = parseDensity(ServerClient.request(ctx, "wm density"))

        return TweaksState(
            animScale = anim,
            dnsPreset = parseDnsPreset(dnsMode, dnsSpec),
            stayAwakeOn = stayAwake > 0,
            showTouchesOn = showTouches > 0,
            phantomDisabled = phantom == "0" || phantom.equals("false", ignoreCase = true),
            density = density
        )
    }

    fun setAnimationScale(ctx: Context, scale: String): Boolean {
        val keys = listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale"
        )
        return keys.all { k ->
            ServerClient.request(ctx, "putsetting global $k $scale").startsWith("OK")
        }
    }

    fun setDnsPreset(ctx: Context, preset: DnsPreset): Boolean {
        if (preset.host != null) {
            val r1 = ServerClient.request(ctx, "putsetting global private_dns_specifier ${preset.host}")
            if (!r1.startsWith("OK")) return false
        }
        return ServerClient.request(ctx, "putsetting global private_dns_mode ${preset.mode}").startsWith("OK")
    }

    fun setAdguardDns(ctx: Context, enable: Boolean): Boolean =
        setDnsPreset(ctx, if (enable) DnsPreset.ADGUARD else DnsPreset.AUTO)

    fun setStayAwake(ctx: Context, enable: Boolean): Boolean {
        val value = if (enable) "3" else "0"
        return ServerClient.request(ctx, "putsetting global stay_on_while_plugged_in $value").startsWith("OK")
    }

    fun setShowTouches(ctx: Context, enable: Boolean): Boolean {
        val value = if (enable) "1" else "0"
        return ServerClient.request(ctx, "putsetting system show_touches $value").startsWith("OK")
    }

    fun setPhantomKillerDisabled(ctx: Context, disabled: Boolean): Boolean {
        val value = if (disabled) "0" else "1"
        return ServerClient.request(
            ctx,
            "putsetting global settings_enable_monitor_phantom_procs $value"
        ).startsWith("OK")
    }

    fun setDensity(ctx: Context, dpi: Int): Boolean {
        val clamped = clampDpi(dpi)
        return ServerClient.request(ctx, "wm density $clamped").startsWith("OK")
    }

    fun resetDensity(ctx: Context): Boolean =
        ServerClient.request(ctx, "wm density reset").startsWith("OK")

    fun trimCaches(ctx: Context): Boolean =
        ServerClient.request(ctx, "pm trim-caches 107374182400").startsWith("OK")
}
