package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

// Тесты классификации ошибок ADB. На 1.3.0 функции adbErrorKind не было:
// "Could not exchange peer info" (неверный код спаривания) показывался
// пользователю сырым исключением, что подтверждено на устройстве.
class AdbErrorsKindTest {

    @Test
    fun wrongPairingCodeIsFriendly() {
        val e = IOException("Could not exchange peer info.")
        assertEquals(AdbErrorKind.StalePairing, adbErrorKind(e))
    }

    @Test
    fun peerInfoIsRecognizedInsideWrappedMessage() {
        // future.get() оборачивает причину в сообщение верхнего исключения
        val e = RuntimeException("java.io.IOException: Could not exchange peer info.")
        assertEquals(AdbErrorKind.StalePairing, adbErrorKind(e))
    }

    @Test
    fun closedPortKeepsItsOwnKind() {
        val e = IOException("Failed to connect to /26.26.26.1:37013")
        assertEquals(AdbErrorKind.PortClosed, adbErrorKind(e))
    }

    @Test
    fun timeoutIsStale() {
        assertEquals(AdbErrorKind.StaleTimeout, adbErrorKind(SocketTimeoutException()))
        assertEquals(AdbErrorKind.StaleTimeout, adbErrorKind(TimeoutException()))
    }

    @Test
    fun authFailureIsKeyRejected() {
        val e = IOException("handshake failed")
        assertEquals(AdbErrorKind.KeyRejected, adbErrorKind(e))
    }

    @Test
    fun otherMessageStaysRaw() {
        val e = RuntimeException("что-то конкретное")
        assertEquals(AdbErrorKind.Raw, adbErrorKind(e))
    }

    @Test
    fun emptyMessageIsUnknown() {
        assertEquals(AdbErrorKind.Unknown, adbErrorKind(RuntimeException(null as String?)))
    }
}
