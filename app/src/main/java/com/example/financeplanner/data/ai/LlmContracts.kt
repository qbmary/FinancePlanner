package com.example.financeplanner.data.ai

/**
 * Состояние загрузки локальной GGUF-модели.
 *
 * Требование ЛР №5: отдельное состояние загрузки модели (Loading / Success / Error),
 * отдаваемое наружу через [kotlinx.coroutines.flow.StateFlow].
 */
sealed interface ModelStatus {

    /** Модель ещё не загружена в память. */
    data object NotLoaded : ModelStatus

    /** Модель читается с диска и инициализируется в llama.cpp. */
    data object Loading : ModelStatus

    /** Модель готова к генерации. */
    data class Ready(
        val displayName: String,
        val loadTimeMs: Long,
    ) : ModelStatus

    /** Загрузка не удалась (нет файла, не хватает памяти, неподдерживаемый ABI и т.п.). */
    data class Failed(val message: String) : ModelStatus
}

/** Состояние генерации: Loading / Streaming / Success / Error. */
sealed interface GenerationStatus {

    data object Idle : GenerationStatus

    /** Модель печатает ответ; [text] — уже сгенерированный фрагмент. */
    data class Streaming(
        val text: String,
        val tokens: Int,
    ) : GenerationStatus

    data class Success(
        val text: String,
        val tokens: Int,
        val durationMs: Long,
    ) : GenerationStatus

    data class Failed(val message: String) : GenerationStatus
}

/** Откуда взялся GGUF-файл. */
enum class ModelOrigin {
    /** Пользователь выбрал файл через системный диалог (ActivityResultContracts.OpenDocument). */
    PICKED,

    /** Файл лежит в приватной папке приложения files/models/. */
    APP_STORAGE,

    /** Файл скопирован из assets (сборка с -PbundleModel=true). */
    BUNDLED,
}

/** Описание найденного файла модели. */
data class ModelFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val origin: ModelOrigin,
) {
    val sizeMb: Long get() = sizeBytes / (1024L * 1024L)
}

/**
 * Результат проверки совместимости устройства с моделью.
 *
 * Требование ЛР №5, п.6 — «проверка совместимости устройства и graceful degradation».
 */
data class DeviceCompatibility(
    val abi: String,
    val supportedAbi: Boolean,
    val totalRamGb: Double,
    val freeStorageGb: Double,
    val warnings: List<String> = emptyList(),
    val blockers: List<String> = emptyList(),
) {
    val canRunModel: Boolean get() = blockers.isEmpty()
}
