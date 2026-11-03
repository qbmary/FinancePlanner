package com.example.financeplanner.ui.screens.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeplanner.data.ai.DeviceCompatibility
import com.example.financeplanner.data.ai.GenerationStatus
import com.example.financeplanner.data.ai.LlmRepository
import com.example.financeplanner.data.ai.ModelFile
import com.example.financeplanner.data.ai.ModelProvider
import com.example.financeplanner.data.ai.ModelStatus
import com.example.financeplanner.data.local.MeterReadingEntity
import com.example.financeplanner.data.prefs.SettingsRepository
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.data.repository.MeterRepository
import com.example.financeplanner.data.voice.VoiceRepository
import com.example.financeplanner.data.voice.VoiceUiState
import com.example.financeplanner.domain.model.MeterType
import com.example.financeplanner.domain.usecase.AddVoiceExpenseUseCase
import com.example.financeplanner.domain.usecase.AnalyzeExpensesUseCase
import com.example.financeplanner.domain.usecase.ParseExpenseVoiceUseCase
import com.example.financeplanner.domain.usecase.RecordMeterReadingUseCase
import com.example.financeplanner.domain.usecase.SeedDemoDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Что именно диктует пользователь: трату или показания счётчика. */
enum class VoiceInputMode {
    EXPENSE,
    METER,
}

/** Сводка по одному счётчику для экрана. */
data class MeterSummary(
    val type: MeterType,
    val value: Double?,
    val periodLabel: String?,
    val delta: Double?,
)

data class AssistantUiState(
    val balance: Double = 0.0,
    val compatibility: DeviceCompatibility? = null,
    val modelStatus: ModelStatus = ModelStatus.NotLoaded,
    val model: ModelFile? = null,
    val bundledModelAvailable: Boolean = false,
    val isPreparingModel: Boolean = false,
    val prepareProgress: Int = 0,
    val generation: GenerationStatus = GenerationStatus.Idle,
    val report: String = "",
    val reportFromCache: Boolean = false,
    val isAnalyzing: Boolean = false,
    val voiceState: VoiceUiState = VoiceUiState.Idle,
    val partialVoiceText: String = "",
    val recognizedText: String = "",
    val voiceMode: VoiceInputMode = VoiceInputMode.EXPENSE,
    val selectedMeter: MeterType = MeterType.ELECTRICITY,
    val voiceResult: String = "",
    val meters: List<MeterSummary> = emptyList(),
    val isSpeaking: Boolean = false,
    val preferOfflineRecognition: Boolean = false,
    val speakAnswers: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
) {
    val modelReady: Boolean get() = modelStatus is ModelStatus.Ready
    val isLoadingModel: Boolean get() = modelStatus is ModelStatus.Loading || isPreparingModel
}

/**
 * ViewModel экрана «Ассистент»: объединяет AI-функцию (ЛР №5) и голосовой
 * интерфейс (ЛР №6). UI получает только [AssistantUiState] и вызывает
 * методы-события — однонаправленный поток данных (UDF).
 */
class AssistantViewModel(
    private val financeRepository: FinanceRepository,
    private val meterRepository: MeterRepository,
    private val llmRepository: LlmRepository,
    private val voiceRepository: VoiceRepository,
    private val modelProvider: ModelProvider,
    private val settingsRepository: SettingsRepository,
    private val analyzeExpensesUseCase: AnalyzeExpensesUseCase,
    private val parseExpenseVoiceUseCase: ParseExpenseVoiceUseCase,
    private val addVoiceExpenseUseCase: AddVoiceExpenseUseCase,
    private val recordMeterReadingUseCase: RecordMeterReadingUseCase,
    private val seedDemoDataUseCase: SeedDemoDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        observeFinance()
        observeMeters()
        observeVoice()
        observeSettings()
        observeModelStatus()
        prepareModel()
    }

    // ----------------------------------------------------------- наблюдение

    private fun observeFinance() {
        viewModelScope.launch {
            financeRepository.getAllTransactions()
                .catch { error -> setError(error.message ?: "Не удалось прочитать операции") }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(
                        balance = list.sumOf { it.amount },
                    )
                }
        }
    }

    private fun observeMeters() {
        viewModelScope.launch {
            meterRepository.observeAll()
                .catch { error -> setError(error.message ?: "Не удалось прочитать показания") }
                .collect { readings ->
                    _uiState.value = _uiState.value.copy(meters = buildMeterSummaries(readings))
                }
        }
    }

    private fun buildMeterSummaries(readings: List<MeterReadingEntity>): List<MeterSummary> {
        val now = YearMonth.now()
        val currentPeriod = now.toString()
        val previousPeriod = now.minusMonths(1).toString()
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))

        return MeterType.entries.map { type ->
            val current = readings
                .firstOrNull { it.type == type.id && it.period == currentPeriod }
                ?: readings.firstOrNull { it.type == type.id }

            val previous = readings.firstOrNull {
                it.type == type.id && it.period == previousPeriod
            }

            MeterSummary(
                type = type,
                value = current?.value,
                periodLabel = current?.period?.let { period ->
                    runCatching { YearMonth.parse(period).format(formatter) }.getOrDefault(period)
                },
                delta = if (current != null && previous != null) {
                    current.value - previous.value
                } else {
                    null
                },
            )
        }
    }

    private fun observeVoice() {
        viewModelScope.launch {
            voiceRepository.state.collect { state ->
                _uiState.value = _uiState.value.copy(voiceState = state)

                if (state is VoiceUiState.Error) {
                    _uiState.value = _uiState.value.copy(error = state.message)
                }
            }
        }

        viewModelScope.launch {
            voiceRepository.partialText.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(partialVoiceText = partial)
                }
            }
        }

        viewModelScope.launch {
            voiceRepository.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }

        viewModelScope.launch {
            voiceRepository.recognizedText.collect { text ->
                handleRecognizedText(text)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.preferOfflineRecognition,
                settingsRepository.speakAnswers,
            ) { offline, speak -> offline to speak }
                .collect { (offline, speak) ->
                    _uiState.value = _uiState.value.copy(
                        preferOfflineRecognition = offline,
                        speakAnswers = speak,
                    )
                }
        }
    }

    private fun observeModelStatus() {
        viewModelScope.launch {
            llmRepository.modelStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    modelStatus = status,
                    error = if (status is ModelStatus.Failed) status.message else _uiState.value.error,
                )
            }
        }

        viewModelScope.launch {
            llmRepository.generationStatus.collect { status ->
                val draft = (status as? GenerationStatus.Streaming)?.text
                _uiState.value = _uiState.value.copy(
                    generation = status,
                    report = draft ?: _uiState.value.report,
                )
            }
        }
    }

    // --------------------------------------------------------------- модель

    /** Ищет модель на устройстве и сразу загружает её в память. */
    fun prepareModel() {
        if (_uiState.value.isLoadingModel || _uiState.value.modelReady) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPreparingModel = true,
                prepareProgress = 0,
                error = null,
                compatibility = llmRepository.deviceCompatibility(),
                bundledModelAvailable = modelProvider.hasBundledModel(),
            )

            try {
                val model = modelProvider.findOrImportModel { percent ->
                    _uiState.value = _uiState.value.copy(prepareProgress = percent)
                }

                if (model == null) {
                    _uiState.value = _uiState.value.copy(
                        isPreparingModel = false,
                        modelStatus = ModelStatus.NotLoaded,
                        notice = "Модель не найдена. Выберите GGUF-файл на устройстве.",
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isPreparingModel = false,
                    model = model,
                    prepareProgress = 100,
                )

                loadModel(model)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPreparingModel = false,
                    modelStatus = ModelStatus.Failed(
                        e.message ?: "Не удалось подготовить модель"
                    ),
                    error = e.message ?: "Не удалось подготовить модель",
                )
            }
        }
    }

    /** Пользователь выбрал GGUF-файл в системном диалоге. */
    fun onModelPicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null, notice = null)

            try {
                val model = modelProvider.rememberPickedModel(uri)
                _uiState.value = _uiState.value.copy(
                    model = model,
                    notice = "Файл выбран: ${model.displayName} (${model.sizeMb} МБ)",
                )
                loadModel(model)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Не удалось прочитать выбранный файл",
                )
            }
        }
    }

    private suspend fun loadModel(model: ModelFile) {
        _uiState.value = _uiState.value.copy(error = null, notice = null)

        llmRepository.loadModel(model)
            .onSuccess {
                val status = llmRepository.modelStatus.value
                val info = (status as? ModelStatus.Ready)?.let {
                    "Модель загружена за ${it.loadTimeMs / 1000} с"
                } ?: "Модель готова"
                _uiState.value = _uiState.value.copy(notice = info)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Не удалось загрузить модель",
                )
            }
    }

    fun onReloadModel() {
        viewModelScope.launch {
            llmRepository.release()
            _uiState.value = _uiState.value.copy(modelStatus = ModelStatus.NotLoaded)
            prepareModel()
        }
    }

    fun onUnloadModel() {
        llmRepository.release()
        _uiState.value = _uiState.value.copy(
            modelStatus = ModelStatus.NotLoaded,
            report = "",
            generation = GenerationStatus.Idle,
            notice = "Модель выгружена, память освобождена",
        )
    }

    fun onForgetModel() {
        viewModelScope.launch {
            llmRepository.release()
            modelProvider.forgetPickedModel()
            _uiState.value = _uiState.value.copy(
                model = null,
                modelStatus = ModelStatus.NotLoaded,
                notice = "Ссылка на файл модели удалена",
            )
        }
    }

    // ----------------------------------------------------------------- отчёт

    fun analyzeExpenses(forceRefresh: Boolean = false) {
        if (_uiState.value.isAnalyzing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                error = null,
                notice = null,
                report = if (forceRefresh) "" else _uiState.value.report,
                reportFromCache = false,
            )

            try {
                if (!_uiState.value.modelReady) {
                    throw IllegalStateException(
                        "Сначала загрузите GGUF-модель: нажмите «Выбрать GGUF-файл»."
                    )
                }

                val items = financeRepository.getAllTransactions().first()
                val result = analyzeExpensesUseCase(items, forceRefresh)

                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    report = result.text,
                    reportFromCache = result.fromCache,
                    notice = if (result.fromCache) {
                        "Отчёт взят из кэша Room — генерация не выполнялась"
                    } else {
                        "Отчёт сгенерирован локально на устройстве"
                    },
                )

                if (_uiState.value.speakAnswers) {
                    voiceRepository.speak(result.text)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = e.message ?: "Не удалось сформировать отчёт",
                )
            }
        }
    }

    fun speakReport() {
        val report = _uiState.value.report
        if (report.isBlank()) {
            setError("Отчёт ещё не сформирован")
            return
        }
        voiceRepository.speak(report)
    }

    fun stopSpeaking() = voiceRepository.stopSpeaking()

    // ----------------------------------------------------------------- голос

    fun startVoiceInput(mode: VoiceInputMode) {
        _uiState.value = _uiState.value.copy(
            voiceMode = mode,
            error = null,
            voiceResult = "",
            recognizedText = "",
            partialVoiceText = "",
        )
        voiceRepository.startListening()
    }

    fun stopVoiceInput() = voiceRepository.stopListening()

    fun selectMeterType(type: MeterType) {
        _uiState.value = _uiState.value.copy(selectedMeter = type)
    }

    private fun handleRecognizedText(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                recognizedText = text,
                partialVoiceText = "",
                error = null,
            )

            when (_uiState.value.voiceMode) {
                VoiceInputMode.EXPENSE -> handleExpenseVoice(text)
                VoiceInputMode.METER -> handleMeterVoice(text)
            }
        }
    }

    private suspend fun handleExpenseVoice(text: String) {
        parseExpenseVoiceUseCase(text)
            .onSuccess { parsed ->
                try {
                    val result = addVoiceExpenseUseCase(parsed)
                    _uiState.value = _uiState.value.copy(
                        voiceResult = result.message,
                        balance = result.balanceAfter,
                    )
                    if (_uiState.value.speakAnswers) {
                        voiceRepository.speak(result.speech)
                    }
                } catch (e: Exception) {
                    setError("Не удалось сохранить трату: ${e.message}")
                }
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    voiceResult = "",
                    error = error.message ?: "Не удалось разобрать команду",
                )
            }
    }

    private suspend fun handleMeterVoice(text: String) {
        val fallback = _uiState.value.selectedMeter

        recordMeterReadingUseCase(text, fallback)
            .onSuccess { update ->
                _uiState.value = _uiState.value.copy(
                    voiceResult = update.message,
                    selectedMeter = update.type,
                )
                if (_uiState.value.speakAnswers) {
                    voiceRepository.speak(update.speech)
                }
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    voiceResult = "",
                    error = error.message ?: "Не удалось сохранить показания",
                )
            }
    }

    fun onPreferOfflineRecognitionChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferOfflineRecognition(enabled) }
    }

    fun onSpeakAnswersChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSpeakAnswers(enabled)
            if (!enabled) voiceRepository.stopSpeaking()
        }
    }

    /** Кнопка «демо-данные»: заполняет базу операциями за текущий и прошлый месяц. */
    fun seedDemoData() {
        viewModelScope.launch {
            try {
                val count = seedDemoDataUseCase()
                _uiState.value = _uiState.value.copy(
                    error = null,
                    notice = if (count == 0) {
                        "Демо-данные уже добавлены"
                    } else {
                        "Добавлено $count демо-операций за текущий и прошлый месяц"
                    },
                )
            } catch (e: Exception) {
                setError("Не удалось добавить демо-данные: ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, notice = null)
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    override fun onCleared() {
        // ЛР №6, п.5: обязательное освобождение SpeechRecognizer и TextToSpeech.
        voiceRepository.destroy()
        llmRepository.stopGeneration()
        super.onCleared()
    }
}
