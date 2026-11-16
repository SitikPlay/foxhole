package com.sitikplay.foxhole

object SecretMask {

    private const val MASK = "•••"

    // Ключевое слово = значение. Lookbehind запрещает только цифру перед
    // ключом: буквы ("mytoken", "test_token") не мешают маскированию —
    // недо-маскинг опаснее овер-маскинга.
    private val KEYVAL = Regex(
        "(?i)(?<![\\p{N}])(pass(?:word|wd)?|pwd|token|secret|credential|api[_-]?key|psk|auth(?:orization)?|cookie)([=: ]+)(\\S+)"
    )

    // `authorization: Bearer X` — сначала съедаем сам Bearer-токен, иначе
    // KEYVAL замаскирует только слово Bearer и настоящий токен останется.
    private val BEARER = Regex("(?i)\\b(bearer|basic)(\\s+)(\\S+)")

    // Учётные данные в URL: http://user:pass@host — маскируем user:pass целиком.
    private val URL_CRED = Regex("(?i)((?:https?|ftp)://)[^/\\s:@]+:[^@\\s]+(@)")

    // Позиционный пароль Wi-Fi: `cmd wifi connect-network SSID wpa2 ПАРОЛЬ`.
    private val WIFI_PASS = Regex(
        "(?i)(connect-network\\s+\\S+\\s+(?:wpa2|wpa3)(?:\\s+\\S+)*?\\s+)(\\S+)"
    )

    fun mask(s: String): String =
        s.replace(BEARER) { m -> m.groupValues[1] + m.groupValues[2] + MASK }
            .replace(KEYVAL) { m -> m.groupValues[1] + m.groupValues[2] + MASK }
            .replace(URL_CRED) { m -> m.groupValues[1] + MASK + m.groupValues[2] }
            .replace(WIFI_PASS) { m -> m.groupValues[1] + MASK }
}
