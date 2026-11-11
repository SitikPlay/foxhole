package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandProfilesTest {

    @Test
    fun builtinTemplatesCoverServerAliases() {

        val allowed = setOf("grant", "revoke", "appop", "getsetting", "putsetting")
        CommandProfiles.BUILTINS.forEach { p ->
            val first = p.template.trim().substringBefore(' ')
            assertTrue("первое слово $first", first in allowed)
            assertFalse(CommandProfiles.isUnsafeValue(p.template))
        }
    }

    @Test
    fun slotsParsedInOrder() {
        assertEquals(
            listOf("package", "op", "mode"),
            CommandProfiles.slots("appop {package} {op} {mode}")
        )
        assertEquals(
            listOf("namespace", "key", "key"),
            CommandProfiles.slots("getsetting {namespace} {key} {key}")
        )
    }

    @Test
    fun composeSubstitutesValues() {
        assertEquals(
            "grant com.example.app android.permission.CAMERA",
            CommandProfiles.compose(
                "grant {package} {permission}",
                mapOf("package" to "com.example.app", "permission" to "android.permission.CAMERA")
            )
        )
        assertEquals(
            "putsetting secure test_key hello world",
            CommandProfiles.compose(
                "putsetting {namespace} {key} {value}",
                mapOf("namespace" to "secure", "key" to "test_key", "value" to "hello world")
            )
        )
    }

    @Test
    fun composeFailsWithoutValue() {
        assertNull(CommandProfiles.compose("grant {package} {permission}", mapOf("package" to "com.a")))
        assertNull(CommandProfiles.compose("grant {package} {permission}", emptyMap()))
    }

    @Test
    fun composeFailsOnInjectionAttempts() {
        val values = { v: String ->
            mapOf("package" to v, "permission" to "android.permission.CAMERA")
        }

        listOf(
            "com.a; rm -rf /",
            "com.a && reboot",
            "com.a | cat /data",
            "com.a\u0022file",
            "com.a;poweroff",
            "com.\$(id)",
            "com.\$id",
            "com.a`id`",
            "rid\nrm -rf",
            "rid\rm -rf",
            "${" ".repeat(3)}com.a",
            "com." + "a".repeat(520)
        ).forEach { v ->
            assertNull("значение «$v» должно быть отклонено", CommandProfiles.compose(
                "grant {package} {permission}", values(v)
            ))
        }
    }

    @Test
    fun composeAllowsOrdinaryValues() {
        assertEquals(
            "appop com.a READ_PHONE_STATE foreground",
            CommandProfiles.compose(
                "appop {package} {op} {mode}",
                mapOf("package" to "com.a", "op" to "READ_PHONE_STATE", "mode" to "foreground")
            )
        )
        assertTrue(
            CommandProfiles.isUnsafeValue("value: просто текст 42") == false
        )
    }

    @Test
    fun composeFailsIfSlotsLeft() {

        assertNull(
            CommandProfiles.compose(
                "getsetting {namespace} {key}",
                mapOf("namespace" to "global", "key" to "{package}")
            )
        )
    }
}
