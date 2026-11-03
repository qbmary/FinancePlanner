package com.example.financeplanner.domain.usecase

import com.example.financeplanner.domain.model.MccProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка разбора голосовой команды о трате (вариант 4, ЛР №6):
 * сумма + категория → MCC-код из справочника → запись в Room.
 */
class ParseExpenseVoiceUseCaseTest {

    private val mccProvider = object : MccProvider {
        private val names = mapOf(
            "4121" to "Такси",
            "5411" to "Супермаркеты",
            "5812" to "Кафе и рестораны",
            "5912" to "Аптеки",
            "9999" to "Прочее",
        )

        override fun getCategoryName(mccCode: String): String = names[mccCode] ?: "Неизвестно"

        override fun getIcon(mccCode: String): String = "❓"
    }

    private val useCase = ParseExpenseVoiceUseCase(mccProvider)

    @Test
    fun `распознаёт сумму цифрами и категорию по ключевому слову`() {
        val parsed = useCase("потратила 25 рублей на такси").getOrThrow()

        assertEquals(25.0, parsed.amount, 0.001)
        assertEquals("4121", parsed.mccCode)
        assertEquals("Такси", parsed.category)
    }

    @Test
    fun `распознаёт сумму словами`() {
        val parsed = useCase("двадцать пять рублей на продукты").getOrThrow()

        assertEquals(25.0, parsed.amount, 0.001)
        assertEquals("5411", parsed.mccCode)
        assertEquals("Супермаркеты", parsed.category)
    }

    @Test
    fun `категория по умолчанию — Прочее`() {
        val parsed = useCase("потратил 10 рублей").getOrThrow()

        assertEquals(10.0, parsed.amount, 0.001)
        assertEquals("9999", parsed.mccCode)
        assertEquals("Прочее", parsed.category)
    }

    @Test
    fun `дробная сумма из голоса`() {
        val parsed = useCase("кофе двенадцать целых пять десятых рублей").getOrThrow()

        assertEquals(12.5, parsed.amount, 0.001)
        assertEquals("5812", parsed.mccCode)
    }

    @Test
    fun `без суммы возвращает ошибку с подсказкой`() {
        val result = useCase("просто купил что-то")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Скажите"))
    }

    @Test
    fun `нулевая сумма отклоняется`() {
        val result = useCase("потратил 0 рублей")

        assertTrue(result.isFailure)
    }
}
