package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test

// Тесты динамической высоты консоли: старт компактный, рост под вывод
// с ограничением по заранее заданному лимиту 300.dp.
class ConsoleSizingTest {

    @Test
    fun emptyOrTinyOutputStartsCompact() {
        assertEquals(ConsoleSizing.MIN_HEIGHT_DP, ConsoleSizing.heightDp(0, 800))
        assertEquals(ConsoleSizing.MIN_HEIGHT_DP, ConsoleSizing.heightDp(40, 800))
    }

    @Test
    fun outputGrowsDynamicallyUpToLimit() {
        assertEquals(140, ConsoleSizing.heightDp(140, 800))
        assertEquals(260, ConsoleSizing.heightDp(260, 800))
    }

    @Test
    fun hugeLogStopsAtMaxLimit() {
        assertEquals(ConsoleSizing.MAX_HEIGHT_DP, ConsoleSizing.heightDp(300, 800))
        assertEquals(ConsoleSizing.MAX_HEIGHT_DP, ConsoleSizing.heightDp(900, 800))
    }

    @Test
    fun tinyScreenCapsAtFractionWithoutCollapsingBelowMin() {
        assertEquals(208, ConsoleSizing.heightDp(400, 320))
        assertEquals(ConsoleSizing.MIN_HEIGHT_DP, ConsoleSizing.heightDp(400, 40))
    }
}
