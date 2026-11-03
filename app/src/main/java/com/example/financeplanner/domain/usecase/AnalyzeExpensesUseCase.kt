package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.ai.LlmRepository
import com.example.financeplanner.data.ai.LlmTextPostProcessor
import com.example.financeplanner.data.local.LlmCacheDao
import com.example.financeplanner.data.local.LlmCacheEntity
import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.domain.util.RussianPlurals
import java.security.MessageDigest
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * AI-функция варианта 4: аналитика расходов.
 *
 * UseCase отвечает за всё, что не должно попадать в UI и ViewModel:
 *  1. агрегирует транзакции за текущий и предыдущий месяц;
 *  2. формирует промпт в формате ChatML (Qwen-2.5-Instruct);
 *  3. проверяет кэш в Room по хэшу промпта;
 *  4. вызывает локальную модель через [LlmRepository];
 *  5. чистит служебные токены в ответе.
 */
class AnalyzeExpensesUseCase(
    private val llmRepository: LlmRepository,
    private val llmCacheDao: LlmCacheDao,
) {

    data class AnalysisResult(
        val text: String,
        val fromCache: Boolean,
        val cachedAt: Long?,
        val prompt: String,
    )

    data class CategoryStat(
        val category: String,
        val current: Double,
        val previous: Double,
    ) {
        val delta: Double get() = current - previous
    }

    suspend operator fun invoke(
        transactions: List<TransactionEntity>,
        forceRefresh: Boolean = false,
    ): AnalysisResult {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now(zone)
        val previousMonth = currentMonth.minusMonths(1)

        val summary = buildSummary(transactions, currentMonth, previousMonth)
            ?: throw IllegalStateException(
                "Нет расходов за текущий и предыдущий месяц. " +
                    "Добавьте траты (вручную, голосом или импортом выписки) и повторите анализ."
            )

        val prompt = summary.prompt
        val cacheKey = sha256(prompt)

        if (!forceRefresh) {
            llmCacheDao.findByHash(cacheKey)?.let { cached ->
                return AnalysisResult(
                    text = cached.response,
                    fromCache = true,
                    cachedAt = cached.createdAt,
                    prompt = prompt,
                )
            }
        }

        val generated = llmRepository.generate(prompt)

        val response = generated.getOrElse { error ->
            throw IllegalStateException(
                error.message ?: "Локальная модель не смогла сформировать отчёт",
            )
        }

        val cleaned = LlmTextPostProcessor.clean(response)
        if (cleaned.isBlank()) {
            throw IllegalStateException("Модель вернула пустой отчёт. Повторите попытку.")
        }

        llmCacheDao.save(
            LlmCacheEntity(
                promptHash = cacheKey,
                prompt = prompt,
                response = cleaned,
            )
        )

        return AnalysisResult(
            text = cleaned,
            fromCache = false,
            cachedAt = null,
            prompt = prompt,
        )
    }

    /** Позволяет экрану показать, есть ли уже готовый отчёт за этот месяц. */
    suspend fun cachedReport(transactions: List<TransactionEntity>): AnalysisResult? {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now(zone)
        val previousMonth = currentMonth.minusMonths(1)
        val summary = buildSummary(transactions, currentMonth, previousMonth) ?: return null

        val cached = llmCacheDao.findByHash(sha256(summary.prompt)) ?: return null
        return AnalysisResult(
            text = cached.response,
            fromCache = true,
            cachedAt = cached.createdAt,
            prompt = summary.prompt,
        )
    }

    // ------------------------------------------------------------------ prompt

    private data class Summary(
        val prompt: String,
        val stats: List<CategoryStat>,
        val currentTotal: Double,
        val previousTotal: Double,
    )

    private fun buildSummary(
        transactions: List<TransactionEntity>,
        currentMonth: YearMonth,
        previousMonth: YearMonth,
    ): Summary? {
        val zone = ZoneId.systemDefault()

        fun monthOf(transaction: TransactionEntity): YearMonth =
            YearMonth.from(Instant.ofEpochMilli(transaction.date).atZone(zone).toLocalDate())

        val expenses = transactions.filter { it.amount < 0 }

        fun totalsFor(month: YearMonth): Map<String, Double> =
            expenses
                .filter { monthOf(it) == month }
                .groupBy { it.category.ifBlank { "Без категории" } }
                .mapValues { (_, items) -> items.sumOf { abs(it.amount) } }

        val current = totalsFor(currentMonth)
        val previous = totalsFor(previousMonth)

        if (current.isEmpty() && previous.isEmpty()) return null

        val currentTotal = current.values.sum()
        val previousTotal = previous.values.sum()
        val currentCount = expenses.count { monthOf(it) == currentMonth }

        val categories = current.keys + previous.keys
        val stats = categories
            .map { category ->
                CategoryStat(
                    category = category,
                    current = current[category] ?: 0.0,
                    previous = previous[category] ?: 0.0,
                )
            }
            .sortedByDescending { maxOf(it.current, it.previous) }

        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))

        val topCategories = stats
            .filter { it.current > 0.0 }
            .take(MAX_CATEGORIES)
            .joinToString("\n") { stat ->
                val share = if (currentTotal > 0) stat.current / currentTotal * 100 else 0.0
                val shareText = String.format(Locale.forLanguageTag("ru"), "%.0f", share)
                "- ${stat.category}: ${RussianPlurals.amount(stat.current)} BYN ($shareText %)"
            }
            .ifBlank { "- расходов в этом месяце нет" }

        val previousTop = stats
            .filter { it.previous > 0.0 }
            .take(MAX_CATEGORIES)
            .joinToString(", ") { "${it.category} ${RussianPlurals.amount(it.previous)} BYN" }
            .ifBlank { "нет данных" }

        val grown = stats
            .filter { it.delta > 0.5 }
            .sortedByDescending { it.delta }
            .take(5)
            .joinToString(", ") { "${it.category} +${RussianPlurals.amount(it.delta)} BYN" }
            .ifBlank { "существенного роста нет" }

        val reduced = stats
            .filter { it.delta < -0.5 }
            .sortedBy { it.delta }
            .take(5)
            .joinToString(", ") { "${it.category} ${RussianPlurals.amount(it.delta)} BYN" }
            .ifBlank { "снижения нет" }

        val totalDelta = currentTotal - previousTotal
        val totalDeltaPercent = if (previousTotal > 0) {
            totalDelta / previousTotal * 100
        } else {
            null
        }

        val trend = buildString {
            when {
                abs(totalDelta) < 0.01 -> append("без изменений")
                totalDelta > 0 -> append("рост на ${RussianPlurals.amount(totalDelta)} BYN")
                else -> append("снижение на ${RussianPlurals.amount(abs(totalDelta))} BYN")
            }
            if (totalDeltaPercent != null) {
                append(" (")
                append(String.format(Locale.forLanguageTag("ru"), "%+.1f", totalDeltaPercent))
                append(" %)")
            }
        }

        val prompt = """
            |<|im_start|>system
            |Ты — персональный финансовый аналитик приложения FinancePlanner.
            |Пиши только по-русски, без иероглифов, эмодзи и markdown-разметки.
            |Используй только числа из данных ниже, ничего не выдумывай. Без вступлений и обращений.
            |Структура ответа: 1) общая картина за месяц; 2) где траты выросли; 3) какие категории доминируют; 4) ровно три нумерованные рекомендации по экономии.
            |Объём — не более 130 слов.
            |<|im_end|>
            |<|im_start|>user
            |Проанализируй траты за месяц и напиши краткий отчёт: где выросли траты, какие категории доминируют, что можно оптимизировать. В конце — 3 короткие рекомендации.
            |
            |Текущий месяц: ${currentMonth.format(formatter)}. Всего расходов: ${RussianPlurals.amount(currentTotal)} BYN, операций: $currentCount.
            |Расходы по категориям (текущий месяц):
            |$topCategories
            |
            |Предыдущий месяц: ${previousMonth.format(formatter)}. Всего расходов: ${RussianPlurals.amount(previousTotal)} BYN.
            |Категории прошлого месяца: $previousTop
            |Изменение общих расходов: $trend.
            |Выросли: $grown.
            |Снизились: $reduced.
            |<|im_end|>
            |<|im_start|>assistant
            |
        """.trimMargin()

        return Summary(
            prompt = prompt,
            stats = stats,
            currentTotal = currentTotal,
            previousTotal = previousTotal,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Сколько категорий попадает в промпт: контекст модели ограничен 2048 токенами. */
        const val MAX_CATEGORIES = 8
    }
}
