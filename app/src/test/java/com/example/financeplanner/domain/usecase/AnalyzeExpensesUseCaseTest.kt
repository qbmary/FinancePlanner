package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.ai.DeviceCompatibility
import com.example.financeplanner.data.ai.GenerationStatus
import com.example.financeplanner.data.ai.LlmRepository
import com.example.financeplanner.data.ai.ModelFile
import com.example.financeplanner.data.ai.ModelStatus
import com.example.financeplanner.data.local.LlmCacheDao
import com.example.financeplanner.data.local.LlmCacheEntity
import com.example.financeplanner.data.local.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Проверка UseCase лабораторной №5: формирование промпта, кэширование
 * и постобработка ответа модели.
 */
class AnalyzeExpensesUseCaseTest {

    private val zone = ZoneId.systemDefault()

    private fun tx(
        description: String,
        amount: Double,
        category: String,
        date: LocalDate,
    ) = TransactionEntity(
        amount = amount,
        description = description,
        mccCode = "5411",
        category = category,
        date = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        walletId = 1,
    )

    private fun currentMonthTransactions(): List<TransactionEntity> {
        val today = LocalDate.now(zone)
        val current = today.withDayOfMonth(1)
        val previous = current.minusMonths(1)

        return listOf(
            tx("Пятёрочка", -500.0, "Супермаркеты", current.withDayOfMonth(2)),
            tx("Кофемания", -120.0, "Кафе и рестораны", current.withDayOfMonth(3)),
            tx("Ozon", -300.0, "Маркетплейсы", previous.withDayOfMonth(10)),
            tx("Такси", -80.0, "Такси", previous.withDayOfMonth(11)),
        )
    }

    @Test
    fun `строит промпт в формате ChatML и чистит служебные токены`() = runBlocking {
        val llm = FakeLlmRepository("Отчёт модели<|im_end|>")
        val cache = FakeLlmCacheDao()
        val useCase = AnalyzeExpensesUseCase(llm, cache)

        val result = useCase(currentMonthTransactions())

        assertEquals("Отчёт модели", result.text)
        assertFalse(result.fromCache)
        assertTrue(result.prompt.contains("<|im_start|>system"))
        assertTrue(result.prompt.contains("<|im_start|>user"))
        assertTrue(result.prompt.trimEnd().endsWith("<|im_start|>assistant"))
        assertTrue(result.prompt.contains("Текущий месяц:"))
        assertTrue(result.prompt.contains("Предыдущий месяц:"))
        // В промпт попадают реальные суммы из БД.
        assertTrue(result.prompt.contains("500,00"))
        assertTrue(result.prompt.contains("300,00"))
        assertEquals(1, cache.items.size)
    }

    @Test
    fun `второй вызов берёт ответ из кэша Room`() = runBlocking {
        val llm = FakeLlmRepository("Отчёт модели")
        val cache = FakeLlmCacheDao()
        val useCase = AnalyzeExpensesUseCase(llm, cache)

        val transactions = currentMonthTransactions()
        useCase(transactions)

        val second = useCase(transactions)

        assertTrue(second.fromCache)
        assertEquals("Отчёт модели", second.text)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `forceRefresh игнорирует кэш`() = runBlocking {
        val llm = FakeLlmRepository("Отчёт модели")
        val cache = FakeLlmCacheDao()
        val useCase = AnalyzeExpensesUseCase(llm, cache)

        val transactions = currentMonthTransactions()
        useCase(transactions)
        val refreshed = useCase(transactions, forceRefresh = true)

        assertFalse(refreshed.fromCache)
        assertEquals(2, llm.calls)
    }

    @Test
    fun `без расходов возвращает понятную ошибку`() = runBlocking {
        val useCase = AnalyzeExpensesUseCase(FakeLlmRepository("x"), FakeLlmCacheDao())

        val error = runCatching { useCase(emptyList()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("Нет расходов"))
    }

    @Test
    fun `ошибка генерации оборачивается в IllegalStateException`() = runBlocking {
        val llm = FakeLlmRepository("", failure = IllegalStateException("нет памяти"))
        val useCase = AnalyzeExpensesUseCase(llm, FakeLlmCacheDao())

        val error = runCatching { useCase(currentMonthTransactions()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("нет памяти", error!!.message)
    }

    // ------------------------------------------------------------------ fakes

    private class FakeLlmRepository(
        private val response: String,
        private val failure: Exception? = null,
    ) : LlmRepository {

        var calls = 0
            private set

        override val modelStatus: MutableStateFlow<ModelStatus> =
            MutableStateFlow(ModelStatus.Ready("test.gguf", 1L))

        override val generationStatus: MutableStateFlow<GenerationStatus> =
            MutableStateFlow(GenerationStatus.Idle)

        override fun deviceCompatibility() = DeviceCompatibility(
            abi = "arm64-v8a",
            supportedAbi = true,
            totalRamGb = 8.0,
            freeStorageGb = 64.0,
        )

        override suspend fun loadModel(model: ModelFile): Result<Unit> = Result.success(Unit)

        override suspend fun generate(prompt: String): Result<String> {
            calls++
            return if (failure != null) Result.failure(failure) else Result.success(response)
        }

        override fun stopGeneration() = Unit

        override fun release() = Unit
    }

    private class FakeLlmCacheDao : LlmCacheDao {
        val items = mutableMapOf<String, LlmCacheEntity>()

        override suspend fun findByHash(hash: String): LlmCacheEntity? = items[hash]

        override suspend fun save(item: LlmCacheEntity) {
            items[item.promptHash] = item
        }
    }
}
