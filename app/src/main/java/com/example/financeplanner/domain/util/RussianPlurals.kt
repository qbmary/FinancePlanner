package com.example.financeplanner.domain.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Форматирование денег и чисел для экрана и для озвучивания.
 */
object RussianPlurals {

    /** 1234.56 → "1 234,56" */
    fun amount(value: Double): String = normalize(
        String.format(Locale.forLanguageTag("ru-BY"), "%,.2f", value)
    )

    /** 1250.0 → "1 250" */
    fun integer(value: Double): String = normalize(
        String.format(Locale.forLanguageTag("ru-BY"), "%,.0f", value)
    )

    /** Заменяет неразрывные пробелы-разделители тысяч на обычные. */
    private fun normalize(value: String): String =
        value.replace('\u00A0', ' ').replace('\u202F', ' ')

    /** 1234.56 → "1 234,56 белорусских рубля" (для TTS). */
    fun rubles(value: Double): String {
        val formatted = amount(value).replace(" ", " ")
        return "$formatted ${plural(value, "белорусский рубль", "белорусских рубля", "белорусских рублей")}"
    }

    /** Короткая форма для подтверждений: "1 234,56 BYN". */
    fun byn(value: Double): String = "${amount(value)} BYN"

    /** "+1 234,56" / "−1 234,56" — со знаком для дельты. */
    fun signed(value: Double): String {
        val sign = when {
            value > 0.0001 -> "+"
            value < -0.0001 -> "−"
            else -> ""
        }
        return sign + amount(abs(value))
    }

    /**
     * Русское склонение: 1 рубль / 2 рубля / 5 рублей.
     */
    fun plural(value: Double, one: String, few: String, many: String): String {
        val rounded = abs(value.roundToInt())
        val lastTwo = rounded % 100
        val last = rounded % 10

        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }
}
