package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerApprovalsTest {

    private val NOW = 1_000_000L

    private fun rec(
        uid: Int = 10,
        hash: String = "h",
        created: Long = NOW - 1000,
        status: DangerApprovals.Status = DangerApprovals.Status.PENDING
    ) = DangerApprovals.Request("id-1", uid, hash, created, status)

    @Test
    fun noRecordCreatesFreshPending() {
        val r = DangerApprovals.resolve(null, 10, "h", NOW)
        assertEquals(DangerApprovals.Kind.NEW, r.kind)
        assertEquals(DangerApprovals.Status.PENDING, r.request.status)
        assertTrue(r.request.id.isNotBlank())
        assertEquals(NOW, r.request.createdAtMs)
    }

    @Test
    fun pendingRecordWaits() {
        val r = DangerApprovals.resolve(rec(), 10, "h", rec().createdAtMs + 1000)
        assertEquals(DangerApprovals.Kind.WAIT, r.kind)
        assertEquals("id-1", r.id)
    }

    @Test
    fun approvedRecordExecutes() {
        val r = DangerApprovals.resolve(
            rec(status = DangerApprovals.Status.APPROVED), 10, "h", rec().createdAtMs + 1000
        )
        assertEquals(DangerApprovals.Kind.EXECUTE, r.kind)
        assertEquals("id-1", r.id)
    }

    @Test
    fun deniedRecordDenied() {
        val r = DangerApprovals.resolve(
            rec(status = DangerApprovals.Status.DENIED), 10, "h", rec().createdAtMs + 1000
        )
        assertEquals(DangerApprovals.Kind.DENIED, r.kind)
    }

    @Test
    fun expiredPendingRequiresNewConfirmation() {

        val old = rec(created = NOW - DangerApprovals.TTL_MS - 1000)
        val r = DangerApprovals.resolve(old, 10, "h", NOW)
        assertEquals(DangerApprovals.Kind.NEW, r.kind)
        assertNotEquals("id-1", r.id)
    }

    @Test
    fun ttlBoundaryIsInside() {

        val rec = rec(created = NOW - DangerApprovals.TTL_MS)
        val r = DangerApprovals.resolve(rec, 10, "h", NOW)
        assertEquals(DangerApprovals.Kind.WAIT, r.kind)
    }

    @Test
    fun othersUidDoesNotMatch() {
        val r = DangerApprovals.resolve(rec(), 99, "h", rec().createdAtMs + 1000)
        assertEquals(DangerApprovals.Kind.NEW, r.kind)
    }

    @Test
    fun otherCommandDoesNotMatch() {
        val r = DangerApprovals.resolve(rec(), 10, "other", rec().createdAtMs + 1000)
        assertEquals(DangerApprovals.Kind.NEW, r.kind)
    }

    @Test
    fun hashCmdBindsUidAndCommand() {

        assertNotEquals(DangerApprovals.hashCmd(10, "reboot"), DangerApprovals.hashCmd(11, "reboot"))
        assertEquals(DangerApprovals.hashCmd(10, "reboot"), DangerApprovals.hashCmd(10, "reboot"))
        assertEquals(64, DangerApprovals.hashCmd(10, "reboot").length)
    }
}
