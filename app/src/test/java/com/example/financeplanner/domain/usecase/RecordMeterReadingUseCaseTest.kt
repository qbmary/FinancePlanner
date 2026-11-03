package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.local.MeterReadingEntity
import com.example.financeplanner.domain.model.MeterStore
import com.example.financeplanner.domain.model.MeterType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/**
 * Проверка сохранения показаний счётчика и расчёта разницы с прошлым
 * месяцем (вариант 4: «приложение озвучивает разницу с прошлым месяцем»).
 */
class RecordMeterReadingUseCaseTest {

    private val currentPeriod = YearMonth.now().toString()
    private val previousPeriod = YearMonth.now().minusMonths(1).toString()

    private class FakeMeterStore : MeterStore {
        val saved = mutableListOf<MeterReadingEntity>()
        var previousReading: MeterReadingEntity? = null
        var lastRequestedPeriod: String? = null

        override suspend fun previous(type: String, period: String): MeterReadingEntity? {
            lastRequestedPeriod = period
            return previousReading?.takeIf { it.type == type }
        }

        override suspend fun findByPeriod(type: String, period: String): MeterReadingEntity? =
            saved.firstOrNull { it.type == type && it.period == period }

        override suspend fun save(reading: MeterReadingEntity): Long {
            saved += reading
            return saved.size.toLong()
        }
    }

    @Test
    fun `рассчитывает разницу с прошлым месяцем и сохраняет показание`() = runBlocking {
        val store = FakeMeterStore().apply {
            previousReading = MeterReadingEntity(
                type = MeterType.ELECTRICITY.id,
                value = 1208.0,
                unit = "кВт·ч",
                period = previousPeriod,
            )
        }

        val update = RecordMeterReadingUseCase(store)
            .invoke("электроэнергия тысяча двести пятьдесят")
            .getOrThrow()

        assertEquals(MeterType.ELECTRICITY, update.type)
        assertEquals(1250.0, update.value, 0.001)
        assertEquals(42.0, update.delta!!, 0.001)
        // use case запрашивает показания строго до текущего периода
        assertEquals(currentPeriod, store.lastRequestedPeriod)

        // Показание сохранено в хранилище за текущий месяц.
        assertEquals(1, store.saved.size)
        assertEquals(currentPeriod, store.saved.first().period)

        // Текст для TextToSpeech содержит разницу и прошлое показание.
        assertTrue(update.speech.contains("Разница с прошлым месяцем"))
        assertTrue(update.speech.contains("плюс 42"))
        assertTrue(update.speech.contains("1 208"))
    }

    @Test
    fun `первые показания не сравниваются`() = runBlocking {
        val store = FakeMeterStore()

        val update = RecordMeterReadingUseCase(store)
            .invoke("вода холодная 120")
            .getOrThrow()

        assertEquals(MeterType.COLD_WATER, update.type)
        assertNull(update.delta)
        assertTrue(update.speech.contains("первые показания"))
    }

    @Test
    fun `тип счётчика берётся из текста`() = runBlocking {
        val update = RecordMeterReadingUseCase(FakeMeterStore())
            .invoke("газ 45")
            .getOrThrow()

        assertEquals(MeterType.GAS, update.type)
        assertEquals(45.0, update.value, 0.001)
    }

    @Test
    fun `если тип не назван — используется выбранный в интерфейсе`() = runBlocking {
        val update = RecordMeterReadingUseCase(FakeMeterStore())
            .invoke("показание 350", fallbackType = MeterType.HOT_WATER)
            .getOrThrow()

        assertEquals(MeterType.HOT_WATER, update.type)
        assertEquals(350.0, update.value, 0.001)
    }

    @Test
    fun `без числа возвращается понятная ошибка`() = runBlocking {
        val result = RecordMeterReadingUseCase(FakeMeterStore()).invoke("электроэнергия")

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message!!.contains(
                MeterType.ELECTRICITY.title.lowercase(Locale.forLanguageTag("ru"))
            )
        )
    }
}
