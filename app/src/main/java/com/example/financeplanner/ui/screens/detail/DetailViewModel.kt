package com.example.financeplanner.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.data.repository.MccRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val transaction: TransactionEntity? = null,
    val mccName: String = "",
    val mccIcon: String = "",
    val isLoading: Boolean = true
)

class DetailViewModel(
    private val repository: FinanceRepository,
    private val mccRepository: MccRepository,
    private val transactionId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadTransaction()
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            repository.getAllTransactions().collect { transactions ->
                val transaction = transactions.find { it.id == transactionId }
                val mccName = transaction?.let { mccRepository.getCategoryName(it.mccCode) } ?: ""
                val mccIcon = transaction?.let { mccRepository.getIcon(it.mccCode) } ?: ""

                _uiState.value = DetailUiState(
                    transaction = transaction,
                    mccName = mccName,
                    mccIcon = mccIcon,
                    isLoading = false
                )
            }
        }
    }
}