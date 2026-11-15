package com.sitikplay.foxhole

import org.junit.Assert.assertEquals
import org.junit.Test

// Тесты расчёта высоты консоли терминала: базовый размер 300.dp — старт,
// выше консоль растёт под объём логов, но не выше доли высоты экрана.
class ConsoleSizingTest {

    @Test
    fun shortLogKeepsBaseHeight() {
        // Короткий вывод не растягивает консоль выше прежних фиксированных 300.dp.
        assertEquals(300, ConsoleSizing.heightDp(80, 800))
    }

    @Test
    fun mediumLogGrowsToItsSize() {
        // Вывод среднего размера: консоль растёт ровно под него.
        assertEquals(450, ConsoleSizing.heightDp(450, 800))
    }

    @Test
    fun hugeLogIsCappedByScreenFraction() {
        // Очень длинный лог: рост ограничен долей экрана (800 * 0.65 = 520),
        // дальше работает внутренняя прокрутка.
        assertEquals(520, ConsoleSizing.heightDp(900, 800))
    }

    @Test
    fun shortScreenDoesNotShrinkConsoleBelowBase() {
        // На низком экране потолок меньше базы: консоль не сжимается ниже 300.dp.
        assertEquals(300, ConsoleSizing.heightDp(400, 300))
    }

    @Test
    fun exactFractionBoundaryIsAllowed() {
        // Ровно на границе доли экрана рост ещё разрешён.
        assertEquals(520, ConsoleSizing.heightDp(520, 800))
    }
}
