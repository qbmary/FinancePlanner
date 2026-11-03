package com.example.financeplanner.domain.util

/**
 * Разбор чисел из текста голосового ввода.
 *
 * Служба распознавания часто возвращает числа цифрами («потратила 25 рублей»),
 * но может вернуть их и словами («двадцать пять рублей»), поэтому нужны оба пути.
 */
object VoiceNumberParser {

    private val units = mapOf(
        "ноль" to 0.0, "нуль" to 0.0,
        "один" to 1.0, "одна" to 1.0, "одно" to 1.0,
        "два" to 2.0, "две" to 2.0,
        "три" to 3.0, "четыре" to 4.0, "пять" to 5.0, "шесть" to 6.0,
        "семь" to 7.0, "восемь" to 8.0, "девять" to 9.0,
        "полтора" to 1.5, "полторы" to 1.5, "половина" to 0.5,
    )

    private val teens = mapOf(
        "десять" to 10.0, "одиннадцать" to 11.0, "двенадцать" to 12.0,
        "тринадцать" to 13.0, "четырнадцать" to 14.0, "пятнадцать" to 15.0,
        "шестнадцать" to 16.0, "семнадцать" to 17.0, "восемнадцать" to 18.0,
        "девятнадцать" to 19.0,
    )

    private val tens = mapOf(
        "двадцать" to 20.0, "тридцать" to 30.0, "сорок" to 40.0,
        "пятьдесят" to 50.0, "шестьдесят" to 60.0, "семьдесят" to 70.0,
        "восемьдесят" to 80.0, "девяносто" to 90.0,
    )

    private val hundreds = mapOf(
        "сто" to 100.0, "двести" to 200.0, "триста" to 300.0,
        "четыреста" to 400.0, "пятьсот" to 500.0, "шестьсот" to 600.0,
        "семьсот" to 700.0, "восемьсот" to 800.0, "девятьсот" to 900.0,
    )

    private val scales = mapOf(
        "тысяча" to 1_000.0, "тысячи" to 1_000.0, "тысяч" to 1_000.0,
        "миллион" to 1_000_000.0, "миллиона" to 1_000_000.0, "миллионов" to 1_000_000.0,
    )

    private val decimalMarkers = setOf("целых", "целая", "целое", "запятая")

    /** Цифры с пробелами-разделителями тысяч и запятой/точкой в дробной части. */
    private val digitRegex = Regex(
        "(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,3})?)|(\\d+(?:[.,]\\d{1,3})?)"
    )

    /**
     * Возвращает первое найденное число или null.
     * Сначала ищутся цифры, затем — числительные словами.
     */
    fun parse(text: String): Double? {
        digits(text)?.let { return it }
        return words(text)
    }

    fun digits(text: String): Double? {
        val match = digitRegex.find(text) ?: return null
        val raw = match.value
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace(',', '.')
        return raw.toDoubleOrNull()
    }

    fun words(text: String): Double? {
        val tokens = text
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-я\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return null

        // Дробная часть: «двадцать пять целых пять десятых».
        val markerIndex = tokens.indexOfFirst { it in decimalMarkers }
        if (markerIndex > 0) {
            val integerPart = sumTokens(tokens.subList(0, markerIndex))
            val fractionDigits = tokens
                .drop(markerIndex + 1)
                .mapNotNull { token ->
                    when {
                        token in units -> (units.getValue(token).toInt() % 10).toString()
                        token in teens -> teens.getValue(token).toInt().toString()
                        else -> null
                    }
                }
                .joinToString("")

            if (integerPart != null && fractionDigits.isNotEmpty()) {
                return (integerPart.toInt().toString() + "." + fractionDigits).toDoubleOrNull()
            }
        }

        return sumTokens(tokens)
    }

    private fun sumTokens(tokens: List<String>): Double? {
        var total = 0.0
        var current = 0.0
        var matched = false

        tokens.forEach { token ->
            when {
                token in units -> {
                    current += units.getValue(token)
                    matched = true
                }

                token in teens -> {
                    current += teens.getValue(token)
                    matched = true
                }

                token in tens -> {
                    current += tens.getValue(token)
                    matched = true
                }

                token in hundreds -> {
                    current += hundreds.getValue(token)
                    matched = true
                }

                token in scales -> {
                    val scale = scales.getValue(token)
                    if (current == 0.0) current = 1.0
                    total += current * scale
                    current = 0.0
                    matched = true
                }
            }
        }

        return if (matched) total + current else null
    }
}
