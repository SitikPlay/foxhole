package com.sitikplay.foxhole

/**
 * Расчёт динамической высоты консоли терминала.
 *
 * Изначально консоль компактна по размеру содержимого и плавно растёт
 * по мере появления вывода, упираясь в заранее заданный потолок (300.dp);
 * дальше работает внутренняя прокрутка.
 */
object ConsoleSizing {

    /** Минимальная высота компактной пустой консоли в dp. */
    const val MIN_HEIGHT_DP = 48

    /** Максимальная высота консоли в dp — заранее заданный лимит роста. */
    const val MAX_HEIGHT_DP = 300

    /** На очень компактных экранах дополнительно ограничиваем потолок долей экрана. */
    const val MAX_SCREEN_FRACTION = 0.65f

    /**
     * Итоговая высота консоли в dp.
     *
     * @param contentHeightDp полная высота текста лога с паддингами в dp
     * @param screenHeightDp высота экрана в dp
     */
    fun heightDp(contentHeightDp: Int, screenHeightDp: Int): Int {
        val ceiling = if (screenHeightDp > 0) {
            minOf(MAX_HEIGHT_DP, maxOf(MIN_HEIGHT_DP, (screenHeightDp * MAX_SCREEN_FRACTION).toInt()))
        } else {
            MAX_HEIGHT_DP
        }
        return contentHeightDp.coerceIn(MIN_HEIGHT_DP, ceiling)
    }
}
