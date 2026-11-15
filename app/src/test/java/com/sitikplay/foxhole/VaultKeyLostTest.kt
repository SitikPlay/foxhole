package com.sitikplay.foxhole

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.UnrecoverableKeyException

// Тесты распознавания невосстановимой потери ключа Keystore (А3).
// На 1.3.0 функции vaultKeyLost не было: битый ключ Keystore ронял приложение
// при каждом обращении к секретам, вместо пересоздания ключа.
class VaultKeyLostTest {

    @Test
    fun unrecoverableKeyExceptionIsLost() {
        assertTrue(vaultKeyLost(UnrecoverableKeyException("user key reset")))
    }

    @Test
    fun lostKeyIsDetectedThroughCauseChain() {
        val e = RuntimeException("getEntry failed", UnrecoverableKeyException("invalidated"))
        assertTrue(vaultKeyLost(e))
    }

    @Test
    fun transientErrorsAreNotLost() {
        assertFalse(vaultKeyLost(IOException("temporary keystore glitch")))
        assertFalse(vaultKeyLost(RuntimeException("null")))
        assertFalse(vaultKeyLost(IllegalStateException()))
    }
}
