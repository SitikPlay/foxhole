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

    fun classify(cmd: String): AclLevel {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return AclLevel.FULL
        if (trimmed.any { it in SPECIAL }) return AclLevel.FULL
        val firstEnd = trimmed.indexOfFirst { it.isWhitespace() }
        val first = if (firstEnd < 0) trimmed else trimmed.substring(0, firstEnd)
        return when {
            first in READ_WORDS -> AclLevel.READ
            first in PERM_WORDS -> AclLevel.PERMISSIONS
            else -> AclLevel.FULL
        }
    }
}

