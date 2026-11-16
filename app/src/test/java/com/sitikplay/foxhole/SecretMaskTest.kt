package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretMaskTest {

    private fun masked(s: String) = SecretMask.mask(s)

    @Test
    fun masksValueAfterTokenEquals() {
        assertEquals("server token=•••", masked("server token=abc123"))
    }

    @Test
    fun masksValueAfterPasswordSpace() {

        assertEquals("password= •••", masked("password= hunter2"))
    }

    @Test
    fun masksValueAfterPwdColon() {
        assertEquals("pwd: •••", masked("pwd: s3cret"))
    }

    @Test
    fun masksApiKeyDashAndUnderscore() {
        assertEquals("api_key=•••", masked("api_key=ABCD1234"))
        assertEquals("API-KEY=•••", masked("API-KEY=ABCD1234"))
    }

    @Test
    fun masksCredentialCaseInsensitive() {
        assertEquals("CREDENTIAL: •••", masked("CREDENTIAL: top-secret"))
        assertEquals("Token=•••", masked("Token=abc"))
    }

    @Test
    fun doesNotTouchWordsContainingMarker() {

        assertEquals("input keyevent 4", masked("input keyevent 4"))
        assertEquals("input keydown", masked("input keydown"))
    }

    @Test
    fun noMarkerNoChange() {
        assertEquals(
            "settings get global adb_enabled",
            masked("settings get global adb_enabled")
        )
    }

    @Test
    fun markerWithoutValueUnchanged() {

        assertEquals("no token", masked("no token"))
    }

    @Test
    fun masksEachOccurrence() {

        assertEquals(
            "token=••• pwd=•••",
            masked("token=a pwd=b")
        )
    }

    @Test
    fun longHexValueMasked() {

        val cmd = "putsetting global test_token a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertTrue(!masked(cmd).contains("a1b2c3d4e5f60718293a4b5c6d7e8f90"))
        assertEquals("putsetting global test_token •••", masked(cmd))
    }

    @Test
    fun masksTokenInsideWord() {
        // Раньше lookbehind запрещал букву перед ключом — "mytoken" не маскировался.
        assertEquals("mytoken=•••", masked("mytoken=abc"))
    }

    @Test
    fun masksPsk() {
        assertEquals("psk=•••", masked("psk=Wi-Fi-Pa55word"))
        assertEquals("PSK: •••", masked("PSK: hunter2"))
    }

    @Test
    fun masksBearerChain() {
        // Сначала маскируется сам Bearer-токен, потом значение authorization.
        val out = masked("curl --header authorization:Bearer abc123secret")
        assertTrue(!out.contains("abc123secret"))
        assertEquals("curl --header authorization:••• •••", out)
        // Кавычка примыкает к токену — \S+ съедает её вместе со значением:
        // овер-маскинг, безопасный для журнала.
        val out2 = masked("curl -H \"authorization: Bearer abc123secret\"")
        assertTrue(!out2.contains("abc123secret"))
    }

    @Test
    fun masksPlainAuthorization() {
        assertEquals("authorization: •••", masked("authorization: abc123secret"))
    }

    @Test
    fun masksUrlCredentials() {
        assertEquals("http://•••@host/path", masked("http://user:pass@host/path"))
        assertTrue(!masked("https://admin:P%40ss@10.0.0.1:8080/x").contains("P%40ss"))
    }

    @Test
    fun masksWifiPositionalPassword() {
        assertEquals(
            "cmd wifi connect-network Home wpa2 •••",
            masked("cmd wifi connect-network Home wpa2 s3cret-pass")
        )
        // Открытая сеть — пароля нет, менять нечего.
        assertEquals(
            "cmd wifi connect-network Home open",
            masked("cmd wifi connect-network Home open")
        )
    }

    @Test
    fun masksCookie() {
        assertEquals("cookie=•••", masked("cookie=SESSION=xyz"))
    }
}
