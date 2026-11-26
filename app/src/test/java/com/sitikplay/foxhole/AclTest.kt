package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test

class AclTest {

    @Test
    fun readWordsAreRead() {
        assertEquals(AclLevel.READ, Acl.classify("ping"))
        assertEquals(AclLevel.READ, Acl.classify("getsetting global adb_enabled"))
    }

    @Test
    fun permissionWordsArePermissions() {
        assertEquals(AclLevel.PERMISSIONS, Acl.classify("grant com.example.app android.permission.CAMERA"))
        assertEquals(AclLevel.PERMISSIONS, Acl.classify("revoke com.example.app android.permission.CAMERA"))
        assertEquals(AclLevel.PERMISSIONS, Acl.classify("appop com.example.app CAMERA allow"))
        assertEquals(AclLevel.PERMISSIONS, Acl.classify("putsetting secure installed_apps 1"))
    }

    @Test
    fun unknownFirstWordIsFull() {
        assertEquals(AclLevel.FULL, Acl.classify("pm list packages"))
        assertEquals(AclLevel.FULL, Acl.classify("dumpsys battery"))
        assertEquals(AclLevel.FULL, Acl.classify("rm -rf /data/local/tmp/x"))
        assertEquals(AclLevel.FULL, Acl.classify(""))
    }

    @Test
    fun separatorsForceFullEvenInsideReadCommand() {
        assertEquals(AclLevel.FULL, Acl.classify("getsetting global x; rm -rf /"))
        assertEquals(AclLevel.FULL, Acl.classify("ping && ls"))
        assertEquals(AclLevel.FULL, Acl.classify("getsetting global $(id)"))
        assertEquals(AclLevel.FULL, Acl.classify("ping | sh"))
        assertEquals(AclLevel.FULL, Acl.classify("ping > /sdcard/x"))
        assertEquals(AclLevel.FULL, Acl.classify("getsetting global 'x'"))
        assertEquals(AclLevel.FULL, Acl.classify("ping\${IFS}x"))
    }

    @Test
    fun caseAndTrimmingMatter() {
        assertEquals(AclLevel.FULL, Acl.classify("PING"))
        assertEquals(AclLevel.FULL, Acl.classify("Grant com.example.app x"))
        assertEquals(AclLevel.READ, Acl.classify("   ping   "))
    }

    @Test
    fun sensitiveSettingsAndPrivilegesRequireFull() {
        assertEquals(AclLevel.FULL, Acl.classify("putsetting secure enabled_accessibility_services com.evil/.Svc"))
        assertEquals(AclLevel.FULL, Acl.classify("putsetting global adb_wifi_enabled 1"))
        assertEquals(AclLevel.FULL, Acl.classify("putsetting global private_dns_mode hostname"))
        assertEquals(AclLevel.FULL, Acl.classify("grant com.evil.app android.permission.WRITE_SECURE_SETTINGS"))
        assertEquals(AclLevel.FULL, Acl.classify("grant com.evil.app android.permission.DUMP"))
        assertEquals(AclLevel.FULL, Acl.classify("appop com.evil.app READ_CLIPBOARD allow"))
    }

    @Test
    fun targetsSelfPackageDetectsSelfAttacks() {
        assertEquals(true, Acl.targetsSelfPackage("pm uninstall com.sitikplay.foxhole", "com.sitikplay.foxhole"))
        assertEquals(true, Acl.targetsSelfPackage("am force-stop com.sitikplay.foxhole", "com.sitikplay.foxhole"))
        assertEquals(true, Acl.targetsSelfPackage("rm -rf /data/data/com.sitikplay.foxhole", "com.sitikplay.foxhole"))
        assertEquals(false, Acl.targetsSelfPackage("pm uninstall com.other.app", "com.sitikplay.foxhole"))
    }
}
