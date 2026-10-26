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
}

