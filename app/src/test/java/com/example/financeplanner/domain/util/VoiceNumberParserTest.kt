package com.example.financeplanner.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Проверка разбора чисел из голосового ввода (ЛР №6, контрольный вопрос 8). */
class VoiceNumberParserTest {

    @Test
    fun parsesPlainDigits() {
        assertEquals(25.0, VoiceNumberParser.parse("потратила 25 рублей на такси")!!, 0.001)
    }

    @Test
    fun parsesDecimalComma() {
        assertEquals(12.8, VoiceNumberParser.parse("кофе 12,80 рубля")!!, 0.001)
    }

    @Test
    fun parsesThousandsWithSpaces() {
        assertEquals(1_250.0, VoiceNumberParser.parse("показания 1 250")!!, 0.001)
        assertEquals(1_250.0, VoiceNumberParser.parse("показания 1\u00A0250")!!, 0.001)
    }

    @Test
    fun parsesSimpleRussianWords() {
        assertEquals(25.0, VoiceNumberParser.parse("двадцать пять рублей")!!, 0.001)
        assertEquals(125.0, VoiceNumberParser.parse("сто двадцать пять")!!, 0.001)
    }

    @Test
    fun parsesThousandsInWords() {
        assertEquals(1_250.0, VoiceNumberParser.parse("тысяча двести пятьдесят")!!, 0.001)
        assertEquals(2_340.0, VoiceNumberParser.parse("две тысячи триста сорок")!!, 0.001)
    }

    @Test
    fun parsesDecimalWords() {
        assertEquals(25.5, VoiceNumberParser.parse("двадцать пять целых пять десятых")!!, 0.001)
    }

    @Test
    fun ignoresTextWithoutNumbers() {
        assertNull(VoiceNumberParser.parse("просто текст без суммы"))
    }

    @Test
    fun prefersDigitsOverWords() {
        assertEquals(42.0, VoiceNumberParser.parse("42 рубля, а не пять")!!, 0.001)
    }
}
