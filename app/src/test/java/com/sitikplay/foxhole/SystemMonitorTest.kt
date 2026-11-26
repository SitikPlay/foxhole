package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMonitorTest {

    @Test
    fun parseBatteryExtractsTelemetry() {
        val sample = """
            OK Current Battery Service state:
              status: 5
              level: 98
              voltage: 4415
              temperature: 289
              technology: Li-poly
        """.trimIndent()
        val b = SystemMonitor.parseBattery(sample)
        assertNotNull(b)
        assertEquals(98, b!!.level)
        assertEquals(SystemMonitor.ChargeState.FULL, b.state)
        assertEquals(28.9f, b.tempC, 0.05f)
        assertEquals(4415, b.voltageMv)
        assertEquals("Li-poly", b.tech)
        assertNull(SystemMonitor.parseBattery("ERR denied"))
    }

    @Test
    fun parseMemInfoComputesUsedAndAvailableMb() {
        val sample = """
            OK MemTotal:        7684832 kB
            MemFree:          137632 kB
            MemAvailable:    2921028 kB
        """.trimIndent()
        val m = SystemMonitor.parseMemInfo(sample)
        assertNotNull(m)
        assertEquals(7504, m!!.totalMb)
        assertEquals(2852, m.availableMb)
        assertEquals(4652, m.usedMb)
        assertEquals(62, m.usedPercent)
    }

    @Test
    fun parseStorageExtractsDfValues() {
        val sample = """
            OK Filesystem       1K-blocks     Used Available Use% Mounted on
            /dev/block/dm-45 112273388 30728792  81413524  28% /storage/emulated/0/Android/obb
        """.trimIndent()
        val s = SystemMonitor.parseStorage(sample)
        assertNotNull(s)
        assertEquals(28, s!!.usedPercent)
        assertTrue(s.totalGb > 100f)
        assertTrue(s.availGb > 70f)
    }

    @Test
    fun parseProcessesFiltersKernelThreadsAndSortsDescending() {
        val sample = """
            OK    RSS NAME
              4224 init
                 0 [kthreadd]
            418212 system_server
            391436 com.zhiliaoapp.musically
            223856 com.sitikplay.foxhole
            290232 com.google.android.apps.wellbeing:worker
        """.trimIndent()
        val procs = SystemMonitor.parseProcesses(sample, limit = 4)
        assertEquals(4, procs.size)
        assertEquals("system_server", procs[0].name)
        assertNull(procs[0].stoppablePkg)
        assertEquals("com.zhiliaoapp.musically", procs[1].name)
        assertEquals("com.zhiliaoapp.musically", procs[1].stoppablePkg)
        assertEquals("com.google.android.apps.wellbeing", procs[2].stoppablePkg)
        assertEquals("com.sitikplay.foxhole", procs[3].name)
        assertNull(procs[3].stoppablePkg)
    }
}
