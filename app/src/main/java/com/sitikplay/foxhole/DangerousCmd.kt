package com.sitikplay.foxhole

object DangerousCmd {

    enum class DangerKind {
        UNINSTALL,
        FILE_DESTROY,
        SYSTEM_CHANGE,
        REBOOT,
        NETWORK_OFF,
        APP_STOP
    }

    data class Danger(val kind: DangerKind, val match: String)

    // Граница команды: начало строки и символы, после которых начинается
    // новая команда. Включаем $, ` и ( — иначе `sh -c "echo $(pm uninstall …)"`
    // исполняет вложенную команду реально, а подтверждение не запрашивалось.
    private val AT_CMD = """(?i)(?:^|[;|&"'\r\n`$(])[;|&"'`\s$(]*"""

    private val RULES: List<Pair<DangerKind, Regex>> = listOf(
        DangerKind.UNINSTALL to Regex(
            AT_CMD + "pm\\s+(uninstall|clear|disable(-user)?|hide|suspend)\\b"
        ),
        DangerKind.UNINSTALL to Regex(
            AT_CMD + "cmd\\s+package\\s+(uninstall(-system-updates)?|clear|disable(-user)?|set-home-activity|hide|suspend)\\b"
        ),

        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+-\\w*[rf]\\w*"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+\\S+\\s+-\\w*[rf]\\w*\\s*$"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+--(recursive|force|interactive)\\b"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+\\S+\\s+--(recursive|force|interactive)\\b"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "dd\\b[^;|&\r\n]*\\bof\\s*="),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "mk(fs|e2fs)(\\.\\w+)?\\b"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "find\\b[^;|&\r\n]*-delete\\b"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "truncate\\b"),

        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "settings\\s+(put|delete)\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "putsetting\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "cmd\\s+appops\\s+set\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "appop\\s+\\S+\\s+\\S+"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "device_config\\s+(put|delete)\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "setprop\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "eval\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "wm\\s+(size|density)\\s+(?!reset\\b)\\S+"),

        DangerKind.REBOOT to Regex(AT_CMD + "(reboot|shutdown|poweroff)\\b"),
        DangerKind.REBOOT to Regex(AT_CMD + "svc\\s+power\\s+(shutdown|reboot)\\b"),

        DangerKind.NETWORK_OFF to Regex(AT_CMD + "svc\\s+(wifi|data|usb)\\s+disable\\b"),

        DangerKind.APP_STOP to Regex(AT_CMD + "am\\s+(force-stop|kill|kill-all|crash)\\b")
    )

    /** Normalizes trivial shell obfuscation (backslashes in binary names, inline empty quotes, bin paths, wrappers). */
    fun normalizeObfuscation(cmd: String): String = cmd
        .replace(Regex("""/(?:system|vendor|product|apex/[^/]+)/(?:bin|xbin)/"""), "")
        .replace(Regex("""\\([a-zA-Z0-9_-])"""), "$1")
        .replace("\"\"", "")
        .replace("''", "")
        .replace(Regex("""(?i)(^|[;|&`(\s])(busybox|toybox|command|exec)\s+"""), "$1")

    fun analyze(cmd: String): Danger? =
        scanRules(cmd) ?: normalizeObfuscation(cmd).takeIf { it != cmd }?.let { scanRules(it) }

    private fun scanRules(input: String): Danger? {
        for ((kind, regex) in RULES) {
            val m = regex.find(input) ?: continue
            val frag = m.value.trim(';', '|', '&', '"', '\'', ' ', '\t', '\r', '\n', '$', '`', '(')
            return Danger(kind, frag)
        }
        return null
    }
}
