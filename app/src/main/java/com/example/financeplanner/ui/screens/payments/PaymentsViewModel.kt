package com.example.financeplanner.ui.screens.payments

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.remote.dto.OperationDto
import com.example.financeplanner.data.remote.dto.StatementDto
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.data.repository.MccRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale

data class PaymentsUiState(
    val selectedFileName: String? = null,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val totalCount: Int = 0,
    val message: String? = null,
    val error: String? = null
)

class PaymentsViewModel(
    private val financeRepository: FinanceRepository,
    private val mccRepository: MccRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Вызывается, когда пользователь выбрал файл через OpenDocument.
     * Сразу запускает импорт.
     */
    fun onFileSelected(uri: Uri, fileName: String?) {
        _uiState.value = _uiState.value.copy(
            selectedFileName = fileName ?: uri.lastPathSegment ?: "выбранный файл",
            isImporting = true,
            message = null,
            error = null,
            importedCount = 0,
            totalCount = 0
        )

        viewModelScope.launch {
            importFromUri(uri)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        error = "Ошибка импорта: ${e.message ?: "неизвестная"}"
                    )
                }
                .collect { count ->
                    _uiState.value = _uiState.value.copy(
                        importedCount = count,
                        isImporting = false,
                        message = "Импортировано $count операций"
                    )
                }
        }
    }

    /**
     * Flow-конвейер: читаем файл → парсим JSON → валидируем → сопоставляем MCC → записываем в Room.
     */
    private fun importFromUri(uri: Uri) = flow {
        // Шаг 1: читаем файл по URI
        val rawJson = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalStateException("Не удалось открыть файл")

        // Шаг 2: парсим JSON
        val statement: StatementDto = json.decodeFromString(rawJson)

        // Шаг 3: валидация
        if (statement.operations.isEmpty()) {
            throw IllegalStateException("Файл не содержит операций")
        }

        _uiState.value = _uiState.value.copy(totalCount = statement.operations.size)

        // Шаг 4: конвертируем OperationDto → TransactionEntity
        val entities = statement.operations.map { op ->
            operationToEntity(op)
        }

        // Шаг 5: пакетная запись в Room
        financeRepository.addTransactions(entities)

        // Шаг 6: эмитим результат
        emit(entities.size)
    }.flowOn(Dispatchers.IO)

    private fun operationToEntity(op: OperationDto): TransactionEntity {
        // Парсим ISO-8601 дату
        val dateMillis = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(op.date)
                ?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        // Сопоставляем MCC с категорией
        val category = mccRepository.getCategoryName(op.mcc)

        return TransactionEntity(
            amount = op.amount,
            description = op.description,
            mccCode = op.mcc,
            category = category,
            date = dateMillis,
            walletId = 1
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}