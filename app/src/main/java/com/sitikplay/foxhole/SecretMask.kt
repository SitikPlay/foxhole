package com.sitikplay.foxhole

object SecretMask {

    private val SECRET = Regex(

        "(?i)(?<![\\p{L}\\p{N}])(pass(?:word|wd)?|pwd|token|secret|credential|api[_-]?key)([=: ]+)(\\S+)"
    )

    fun mask(s: String): String =
        s.replace(SECRET) { m -> m.groupValues[1] + m.groupValues[2] + "•••" }
}
