package com.example.financeplanner.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Склонение числительных для озвучивания (TextToSpeech). */
class RussianPluralsTest {

    @Test
    fun formatsAmountWithRussianSeparators() {
        assertEquals("1 234,56", RussianPlurals.amount(1234.56))
        assertEquals("1 250", RussianPlurals.integer(1250.0))
    }

    @Test
    fun choosesCorrectRubleForm() {
        assertEquals("рубль", RussianPlurals.plural(1.0, "рубль", "рубля", "рублей"))
        assertEquals("рубля", RussianPlurals.plural(2.0, "рубль", "рубля", "рублей"))
        assertEquals("рублей", RussianPlurals.plural(5.0, "рубль", "рубля", "рублей"))
    }

    @Test
    fun handlesTeensAndHundreds() {
        assertEquals("рублей", RussianPlurals.plural(11.0, "рубль", "рубля", "рублей"))
        assertEquals("рубль", RussianPlurals.plural(21.0, "рубль", "рубля", "рублей"))
        assertEquals("рублей", RussianPlurals.plural(112.0, "рубль", "рубля", "рублей"))
        assertEquals("рубля", RussianPlurals.plural(122.0, "рубль", "рубля", "рублей"))
    }

    @Test
    fun formatsSignedValues() {
        assertEquals("+42,00", RussianPlurals.signed(42.0))
        assertEquals("−42,00", RussianPlurals.signed(-42.0))
    }
}
