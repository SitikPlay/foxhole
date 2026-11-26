package com.sitikplay.foxhole

import android.content.Context

object SystemTweaks {

    const val ADGUARD_DNS = "dns.adguard-dns.com"

    data class TweaksState(
        val animScale: String,
        val adguardDnsOn: Boolean,
        val stayAwakeOn: Boolean
    )

    fun parseSettingValue(raw: String): String {
        if (!raw.startsWith("OK")) return ""
        val v = raw.removePrefix("OK").trim()
        return if (v == "null") "" else v
    }

    fun readState(ctx: Context): TweaksState? {
        val ping = ServerClient.request(ctx, "ping")
        if (!ping.startsWith("OK pong")) return null

        val anim = parseSettingValue(ServerClient.request(ctx, "getsetting global window_animation_scale"))
            .ifEmpty { "1.0" }
        val dnsMode = parseSettingValue(ServerClient.request(ctx, "getsetting global private_dns_mode"))
        val dnsSpec = parseSettingValue(ServerClient.request(ctx, "getsetting global private_dns_specifier"))
        val stayAwake = parseSettingValue(ServerClient.request(ctx, "getsetting global stay_on_while_plugged_in"))
            .toIntOrNull() ?: 0

        return TweaksState(
            animScale = anim,
            adguardDnsOn = dnsMode == "hostname" && dnsSpec.equals(ADGUARD_DNS, ignoreCase = true),
            stayAwakeOn = stayAwake > 0
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

    fun setAdguardDns(ctx: Context, enable: Boolean): Boolean {
        return if (enable) {
            val r1 = ServerClient.request(ctx, "putsetting global private_dns_specifier $ADGUARD_DNS")
            val r2 = ServerClient.request(ctx, "putsetting global private_dns_mode hostname")
            r1.startsWith("OK") && r2.startsWith("OK")
        } else {
            ServerClient.request(ctx, "putsetting global private_dns_mode opportunistic").startsWith("OK")
        }
    }

    fun setStayAwake(ctx: Context, enable: Boolean): Boolean {
        val value = if (enable) "3" else "0"
        return ServerClient.request(ctx, "putsetting global stay_on_while_plugged_in $value").startsWith("OK")
    }

    fun trimCaches(ctx: Context): Boolean =
        ServerClient.request(ctx, "pm trim-caches 107374182400").startsWith("OK")
}
