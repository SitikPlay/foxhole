package com.sitikplay.foxhole

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerScriptTest {

    @Test
    fun scriptSecuresLogPermissionsClearsTokenEarlyAndHidesFromArgv() {
        val sh = ServerScript.content(
            pkg = "com.sitikplay.foxhole",
            tokenPath = "/storage/emulated/0/Android/data/com.sitikplay.foxhole/files/start.token",
            port = 27183,
            noTokenMsg = "missing 'token'!"
        )
        assertTrue(sh.contains("umask 077"))
        assertTrue(sh.contains("chmod 600 /data/local/tmp/foxhole.log"))
        assertTrue(sh.contains("export FOXHOLE_TOK=\"\$TOK\""))
        assertTrue(sh.contains("unset TOK FOXHOLE_TOK"))
        assertTrue(sh.contains("com.sitikplay.foxhole.Server - 27183"))
        assertTrue(sh.contains("'missing '\\''token'\\''!'"))

        val rmIdx = sh.indexOf("rm -f \"")
        val launchIdx = sh.indexOf("app_process")
        assertTrue(rmIdx > 0 && rmIdx < launchIdx)
        assertFalse(sh.contains("Server \"\$TOK\""))
    }
}
