package com.sitikplay.foxhole

enum class AclLevel {
    READ,
    PERMISSIONS,
    FULL;

    val label: String
        get() = when (this) {
            READ -> "READ"
            PERMISSIONS -> "PERMISSIONS"
            FULL -> "FULL"
        }
}

object Acl {

    private val READ_WORDS = setOf("ping", "getsetting")
    private val PERM_WORDS = setOf("grant", "revoke", "appop", "putsetting")

    private val SPECIAL = charArrayOf(
        ';', '|', '&', '<', '>', '$', '`', '*', '?', '[', ']', '(', ')', '{', '}',
        '\\', '"', '\'', '!', '~', '^', '=', ',', '#'
    )

    private val SENSITIVE_SETTINGS = setOf(
        "enabled_accessibility_services",
        "accessibility_enabled",
        "adb_enabled",
        "adb_wifi_enabled",
        "private_dns_mode",
        "private_dns_specifier",
        "http_proxy",
        "global_http_proxy_host",
        "global_http_proxy_port",
        "global_http_proxy_exclusion_list",
        "install_non_market_apps",
        "device_provisioned",
        "development_settings_enabled",
        "default_input_method",
        "sms_default_application",
        "package_verifier_enable",
        "verifier_verify_adb_installs",
        "settings_enable_monitor_phantom_procs"
    )

    private val SENSITIVE_PERMS_OR_OPS = setOf(
        "write_secure_settings",
        "dump",
        "read_logs",
        "manage_device_admins",
        "request_install_packages",
        "system_alert_window",
        "get_usage_stats",
        "run_any_in_background",
        "read_clipboard",
        "write_clipboard",
        "write_settings"
    )

    private val SELF_MUTATING_WORDS = Regex(
        "(?i)\\b(uninstall|disable(-user)?|clear|suspend|hide|revoke|appop|force-stop|kill|crash|rm|mv|dd|truncate|chmod|chown|eval)\\b"
    )

    fun classify(cmd: String): AclLevel {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return AclLevel.FULL
        if (trimmed.any { it in SPECIAL }) return AclLevel.FULL
        val tokens = trimmed.split(Regex("\\s+"))
        val first = tokens.firstOrNull().orEmpty()
        return when {
            first in READ_WORDS -> AclLevel.READ
            first in PERM_WORDS -> {
                if (requiresFullEscalationGuard(first, tokens)) AclLevel.FULL else AclLevel.PERMISSIONS
            }
            else -> AclLevel.FULL
        }
    }

    /** Prevents PERMISSIONS-only callers from granting powerful privileges or hijacking critical settings. */
    private fun requiresFullEscalationGuard(first: String, tokens: List<String>): Boolean {
        if (first == "putsetting") {
            val key = tokens.getOrNull(2)?.lowercase() ?: return true
            return key in SENSITIVE_SETTINGS
        }
        val permOrOp = tokens.getOrNull(2)?.lowercase()?.removePrefix("android.permission.") ?: return true
        return permOrOp in SENSITIVE_PERMS_OR_OPS
    }

    /** Blocks third-party bridge commands aimed at modifying, stopping, or deleting Foxhole itself. */
    fun targetsSelfPackage(cmd: String, selfPkg: String): Boolean {
        if (selfPkg.isBlank()) return false
        val lowCmd = DangerousCmd.normalizeObfuscation(cmd).lowercase()
        val lowSelf = selfPkg.lowercase()
        if (!lowCmd.contains(lowSelf)) return false
        return SELF_MUTATING_WORDS.containsMatchIn(lowCmd) ||
            lowCmd.contains("/data/data/$lowSelf") ||
            lowCmd.contains("/android/data/$lowSelf") ||
            lowCmd.contains(">")
    }
}
