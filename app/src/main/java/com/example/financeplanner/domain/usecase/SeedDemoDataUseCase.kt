package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.domain.model.MccProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Демонстрационный набор операций для проверки AI-отчёта и фильтров.
 *
 * Зачем нужен: отчёт анализирует текущий и предыдущий календарный месяц,
 * поэтому на «чистой» установке модель нечего анализировать. Кнопка на экране
 * «Ассистент» заполняет базу правдоподобными тратами за два месяца.
 */
class SeedDemoDataUseCase(
    private val financeRepository: FinanceRepository,
    private val mccProvider: MccProvider,
) {

    suspend operator fun invoke(): Int {
        val existing = financeRepository.getAllTransactions().first()
        if (existing.size >= MIN_EXISTING) {
            return 0
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentMonth = today.withDayOfMonth(1)
        val previousMonth = currentMonth.minusMonths(1)

        val demo = listOf(
            // --- текущий месяц ---
            DemoTx("Пятёрочка", 58.40, "5411", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 3))),
            DemoTx("Green", 42.10, "5411", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 6))),
            DemoTx("Кофемания", 12.80, "5812", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 4))),
            DemoTx("Яндекс.Такси", 18.50, "4121", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 7))),
            DemoTx("Wildberries", 128.90, "5399", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 8))),
            DemoTx("Аптека 36.6", 23.70, "5912", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 9))),
            DemoTx("АЗС Белнефтехим", 95.00, "5541", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 10))),
            DemoTx("Кино Silver Screen", 24.00, "7832", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 11))),
            DemoTx("Яндекс Плюс", 15.99, "4899", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 2))),
            DemoTx("Зарплата", 2500.00, "6012", currentMonth.withDayOfMonth(minOf(today.dayOfMonth, 1)), income = true),

            // --- предыдущий месяц ---
            DemoTx("Пятёрочка", 71.20, "5411", previousMonth.withDayOfMonth(5)),
            DemoTx("Кофемания", 9.60, "5812", previousMonth.withDayOfMonth(8)),
            DemoTx("Ozon", 210.30, "5399", previousMonth.withDayOfMonth(12)),
            DemoTx("Яндекс.Такси", 32.40, "4121", previousMonth.withDayOfMonth(15)),
            DemoTx("Аптека 36.6", 15.10, "5912", previousMonth.withDayOfMonth(18)),
            DemoTx("АЗС Белнефтехим", 60.00, "5541", previousMonth.withDayOfMonth(21)),
            DemoTx("Зарплата", 2500.00, "6012", previousMonth.withDayOfMonth(1), income = true),
        )

        val entities = demo.map { item ->
            TransactionEntity(
                amount = if (item.income) item.amount else -item.amount,
                description = item.description,
                mccCode = item.mcc,
                category = mccProvider.getCategoryName(item.mcc)
                    .takeIf { it.isNotBlank() } ?: "Прочее",
                date = item.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                walletId = 1,
            )
        }

        financeRepository.addTransactions(entities)
        return entities.size
    }

    private data class DemoTx(
        val description: String,
        val amount: Double,
        val mcc: String,
        val date: LocalDate,
        val income: Boolean = false,
    )

    private companion object {
        /** Если операций уже достаточно, повторно демо-данные не добавляем. */
        const val MIN_EXISTING = 20
    }
}
