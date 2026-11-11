package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairInputTest {

    @Test
    fun parsesPortThenCode() {
        val parsed = parsePairInputKind("43135 842308", 27183)
        assertEquals(PairParseKind.Ok(43135, "842308"), parsed)
    }

    @Test
    fun parsesCodeThenPort() {
        val parsed = parsePairInputKind("код 842308, порт 43135", 27183)
        assertEquals(PairParseKind.Ok(43135, "842308"), parsed)
    }

    @Test
    fun rejectsMissingCode() {
        assertTrue(parsePairInputKind("43135", 27183) is PairParseKind.Invalid)
    }

    @Test
    fun rejectsServerPort() {
        assertTrue(parsePairInputKind("27183 842308", 27183) is PairParseKind.ReservedPort)
    }

    @Test
    fun rejectsInvalidPort() {
        assertTrue(parsePairInputKind("70000 842308", 27183) is PairParseKind.Invalid)
    }

    @Test
    fun reservedPortFlaggedOnlyForReserved() {
        assertTrue(parsePairInputKind("27183 842308", 27183) is PairParseKind.ReservedPort)
        assertTrue(parsePairInputKind("41221 842308", 27183) is PairParseKind.Ok)
    }
}
