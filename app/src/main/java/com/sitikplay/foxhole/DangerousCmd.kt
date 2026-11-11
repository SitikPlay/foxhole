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

    private val AT_CMD = "(?i)(?:^|[;|&\"'\r\n])[;|&\"'\\s]*"

    private val RULES: List<Pair<DangerKind, Regex>> = listOf(

        DangerKind.UNINSTALL to Regex(
            AT_CMD + "pm\\s+(uninstall|clear|disable(-user)?|hide|suspend)\\b"
        ),
        DangerKind.UNINSTALL to Regex(
            AT_CMD + "cmd\\s+package\\s+(uninstall|clear|disable|hide|suspend)\\b"
        ),

        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+-\\w*[rf]\\w*"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "rm\\s+\\S+\\s+-\\w*[rf]\\w*\\s*$"),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "dd\\b[^;|&\r\n]*\\bof\\s*="),
        DangerKind.FILE_DESTROY to Regex(AT_CMD + "mk(fs|e2fs)(\\.\\w+)?\\b"),

        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "settings\\s+(put|delete)\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "putsetting\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "cmd\\s+appops\\s+set\\b"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "appop\\s+\\S+\\s+\\S+"),
        DangerKind.SYSTEM_CHANGE to Regex(AT_CMD + "wm\\s+(size|density)\\s+(?!reset\\b)\\S+"),

        DangerKind.REBOOT to Regex(AT_CMD + "(reboot|shutdown|poweroff)\\b"),

        DangerKind.NETWORK_OFF to Regex(AT_CMD + "svc\\s+(wifi|data|usb)\\s+disable\\b"),

        DangerKind.APP_STOP to Regex(AT_CMD + "am\\s+(force-stop|kill|kill-all|crash)\\b")
    )

    fun analyze(cmd: String): Danger? {
        for ((kind, regex) in RULES) {
            val m = regex.find(cmd) ?: continue
            val frag = m.value.trim(';', '|', '&', '"', '\'', ' ', '\t', '\r', '\n')
            return Danger(kind, frag)
        }
        return null
    }
}
