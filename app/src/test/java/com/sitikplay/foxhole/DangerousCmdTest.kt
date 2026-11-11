package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DangerousCmdTest {

    private fun kind(cmd: String): DangerousCmd.DangerKind? =
        DangerousCmd.analyze(cmd)?.kind

    @Test
    fun pmUninstallDetected() {
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("pm uninstall com.example"))
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("pm uninstall -k --user 0 com.example"))
    }

    @Test
    fun pmClearAndDisableDetected() {
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("PM CLEAR com.example"))
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("pm disable-user --user 0 com.example"))
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("pm hide com.example"))
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("pm suspend com.example"))
    }

    @Test
    fun cmdPackageUninstallDetected() {
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("cmd package uninstall com.example"))
    }

    @Test
    fun safePmCommandsNotFlagged() {
        assertNull(kind("pm list packages"))
        assertNull(kind("pm path com.example"))
        assertNull(kind("pm install /sdcard/app.apk"))
        assertNull(kind("pm install-existing com.example"))
        assertNull(kind("pm grant com.example android.permission.CAMERA"))
    }

    @Test
    fun rmRecursiveDetected() {
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("rm -rf /sdcard/DCIM"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("rm -r /data/local/tmp/x"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("rm -f note.txt"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("rm /sdcard/x.txt -rf"))
    }

    @Test
    fun ddWriteDetected() {
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("dd if=/dev/zero of=/dev/block/sda"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("dd if=x of=y bs=1M"))
    }

    @Test
    fun mkfsDetected() {
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("mkfs.ext4 /dev/block/sda"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("mke2fs /dev/block/mmcblk0"))
    }

    @Test
    fun safeFileCommandsNotFlagged() {

        assertNull(kind("rm note.txt"))
        assertNull(kind("dd if=/sdcard/backup.img"))
        assertNull(kind("cat /sdcard/file.txt"))
        assertNull(kind("ls /sdcard"))
    }

    @Test
    fun settingsPutDetected() {
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("settings put global airplane_mode_on 1"))
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("settings delete secure brightness"))
    }

    @Test
    fun putsettingDetected() {
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("putsetting global x y"))
    }

    @Test
    fun appopsChangeDetected() {
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("cmd appops set com.x VIBRATE allow"))
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("appop com.x ACCESS_FINE_LOCATION allow"))
    }

    @Test
    fun wmChangeDetected() {
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("wm size 1080x2400"))
        assertEquals(DangerousCmd.DangerKind.SYSTEM_CHANGE, kind("wm density 480"))
    }

    @Test
    fun safeSettingsCommandsNotFlagged() {
        assertNull(kind("settings get global wifi_on"))
        assertNull(kind("getsetting global x"))
        assertNull(kind("wm size"))
        assertNull(kind("wm density"))
        assertNull(kind("wm size reset"))
        assertNull(kind("wm density reset"))
        assertNull(kind("cmd appops get com.x"))
    }

    @Test
    fun rebootDetected() {
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("reboot"))
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("reboot -p"))
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("shutdown"))
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("poweroff"))
    }

    @Test
    fun rebootMentionNotFlagged() {

        assertNull(kind("getprop | grep reboot"))
        assertNull(kind("echo reboot"))
    }

    @Test
    fun svcDisableDetected() {
        assertEquals(DangerousCmd.DangerKind.NETWORK_OFF, kind("svc wifi disable"))
        assertEquals(DangerousCmd.DangerKind.NETWORK_OFF, kind("svc data disable"))
    }

    @Test
    fun svcEnableNotFlagged() {
        assertNull(kind("svc wifi enable"))
        assertNull(kind("svc data enable"))
    }

    @Test
    fun amKillDetected() {
        assertEquals(DangerousCmd.DangerKind.APP_STOP, kind("am force-stop com.example"))
        assertEquals(DangerousCmd.DangerKind.APP_STOP, kind("am kill com.example"))
        assertEquals(DangerousCmd.DangerKind.APP_STOP, kind("am kill-all"))
        assertEquals(DangerousCmd.DangerKind.APP_STOP, kind("am crash com.example"))
    }

    @Test
    fun amSafeCommandsNotFlagged() {
        assertNull(kind("am start -n com.example/.MainActivity"))
        assertNull(kind("am broadcast -a android.intent.action.BOOT_COMPLETED"))
    }

    @Test
    fun chainedCommandsDetected() {
        assertEquals(DangerousCmd.DangerKind.UNINSTALL, kind("ping localhost; pm uninstall com.x"))
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("ping localhost && reboot"))
        assertEquals(DangerousCmd.DangerKind.REBOOT, kind("ping localhost || reboot"))
        assertEquals(DangerousCmd.DangerKind.FILE_DESTROY, kind("ping localhost|rm -rf /sdcard/x"))
    }

    @Test
    fun quotedAndMultilineDetected() {
        assertEquals(
            DangerousCmd.DangerKind.FILE_DESTROY,
            kind("sh -c \"rm -rf /sdcard/x\"")
        )
        assertEquals(
            DangerousCmd.DangerKind.FILE_DESTROY,
            kind("ping localhost\nrm -rf /sdcard/x")
        )
    }

    @Test
    fun matchFragmentIsClean() {
        val d = DangerousCmd.analyze("ping localhost; pm uninstall com.x")
        assertEquals("pm uninstall", d?.match)
    }

    @Test
    fun emptyAndBenignCommandsNotFlagged() {
        assertNull(kind(""))
        assertNull(kind("ping"))
        assertNull(kind("id"))
        assertNull(kind("dumpsys battery"))
        assertNull(kind("logcat -d -t 50"))
        assertNull(kind("input tap 100 100"))
        assertNull(kind("screencap -p /sdcard/Download/shot.png"))
    }
}
