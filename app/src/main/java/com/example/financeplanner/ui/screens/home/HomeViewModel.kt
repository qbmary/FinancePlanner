package com.example.financeplanner.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

data class HomeUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalBalance: Double = 0.0,
    val expensesByCategory: Map<String, Double> = emptyMap(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val repository: FinanceRepository,
    private val limit: Int = 0,
    private val isHistoryMode: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            combine(
                repository.getAllTransactions(),
                _selectedCategory
            ) { transactions, category ->
                // Фильтрация по категории
                val filtered = if (category == null) transactions
                else transactions.filter { it.category == category }

                // Ограничение по количеству (для главной — 5)
                val limited = if (limit > 0) filtered.take(limit) else filtered

                // Расходы по категориям (всегда по полному списку)
                val expensesByCategory = transactions
                    .filter { it.amount < 0 }
                    .groupBy { it.category }
                    .mapValues { (_, list) -> abs(list.sumOf { it.amount }) }

                HomeUiState(
                    transactions = limited,
                    totalBalance = transactions.sumOf { it.amount },
                    expensesByCategory = expensesByCategory,
                    selectedCategory = category,
                    isLoading = false,
                    error = null
                )
            }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Ошибка загрузки"
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun addTestTransaction() {
        if (isHistoryMode) return
        viewModelScope.launch {
            val samples = listOf(
                Triple("Пятёрочка", "Супермаркеты", -450.0),
                Triple("Кофемания", "Кафе и рестораны", -320.0),
                Triple("Зарплата", "Доход", 85000.0),
                Triple("Wildberries", "Одежда", -2340.0),
                Triple("Яндекс.Такси", "Транспорт", -580.0),
                Triple("Аптека 36.6", "Здоровье", -890.0),
                Triple("Ozon", "Маркетплейсы", -1560.0)
            )
            val sample = samples.random()

            repository.addTransaction(
                TransactionEntity(
                    amount = sample.third,
                    description = sample.first,
                    mccCode = "5411",
                    category = sample.second,
                    date = System.currentTimeMillis(),
                    walletId = 1
                )
            )
        }
    }
}