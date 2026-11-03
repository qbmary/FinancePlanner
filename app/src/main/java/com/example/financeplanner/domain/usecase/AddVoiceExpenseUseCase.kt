package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.domain.util.RussianPlurals
import kotlinx.coroutines.flow.first

/**
 * Сохраняет трату, названную голосом, и готовит фразу с остатком баланса
 * (вариант 4: «пользователь голосом диктует сумму и категорию траты,
 * а приложение озвучивает остаток бюджета»).
 */
class AddVoiceExpenseUseCase(
    private val financeRepository: FinanceRepository,
) {

    data class ExpenseResult(
        val amount: Double,
        val category: String,
        val mccCode: String,
        val balanceAfter: Double,
        val message: String,
        val speech: String,
    )

    suspend operator fun invoke(parsed: ParseExpenseVoiceUseCase.ParsedExpense): ExpenseResult {
        financeRepository.addTransaction(
            TransactionEntity(
                amount = -parsed.amount,
                description = parsed.description,
                mccCode = parsed.mccCode,
                category = parsed.category,
                date = System.currentTimeMillis(),
                walletId = 1,
            )
        )

        val balance = financeRepository.getAllTransactions()
            .first()
            .sumOf { it.amount }

        val amountText = "${RussianPlurals.integer(parsed.amount)} " +
            RussianPlurals.plural(parsed.amount, "рубль", "рубля", "рублей")

        val balanceText = RussianPlurals.amount(balance) + " " +
            RussianPlurals.plural(balance, "белорусский рубль", "белорусских рубля", "белорусских рублей")

        return ExpenseResult(
            amount = parsed.amount,
            category = parsed.category,
            mccCode = parsed.mccCode,
            balanceAfter = balance,
            message = "Трата ${RussianPlurals.byn(parsed.amount)} сохранена • " +
                "категория «${parsed.category}» • остаток баланса ${RussianPlurals.byn(balance)}",
            speech = "Добавлена трата $amountText. Категория: ${parsed.category}. " +
                "Остаток баланса: $balanceText.",
        )
    }
}
