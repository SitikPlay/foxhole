package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairInputTest {

    @Test
    fun parsesPortThenCode() {
        val parsed = parsePairInput("43135 842308", 27183)
        assertEquals(PairParse.Ok(43135, "842308"), parsed)
    }

    @Test
    fun parsesCodeThenPort() {
        val parsed = parsePairInput("код 842308, порт 43135", 27183)
        assertEquals(PairParse.Ok(43135, "842308"), parsed)
    }

    @Test
    fun rejectsMissingCode() {
        assertTrue(parsePairInput("43135", 27183) is PairParse.Error)
    }

    @Test
    fun rejectsServerPort() {
        assertTrue(parsePairInput("27183 842308", 27183) is PairParse.Error)
    }

    @Test
    fun rejectsInvalidPort() {
        assertTrue(parsePairInput("70000 842308", 27183) is PairParse.Error)
    }

    @Test
    fun pairPortErrorFlagsReservedPortOnly() {
        val err = pairPortError(27183, 27183)
        assertTrue(err?.contains("порт сервера") == true)
        assertEquals(null, pairPortError(41221, 27183))
    }
}

