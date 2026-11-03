package com.example.financeplanner.data.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Абстракция над llama.cpp (обёртка над LlamaHelper из llamacpp-kotlin).
 *
 * Слой данных ничего не знает про Compose и ViewModel: наружу отдаются
 * только [StateFlow] и suspend-функции. Вся тяжёлая работа выполняется
 * на Dispatchers.IO внутри реализации.
 */
interface LlmRepository {

    /** Состояние загрузки GGUF-модели. */
    val modelStatus: StateFlow<ModelStatus>

    /** Состояние текущей генерации (стриминг токенов). */
    val generationStatus: StateFlow<GenerationStatus>

    /** Проверка, потянет ли устройство Qwen-2.5-1.5B-Instruct Q4_K_M. */
    fun deviceCompatibility(): DeviceCompatibility

    /**
     * Загружает GGUF-модель в память. Повторный вызов с уже загруженной
     * моделью — no-op. Возвращает [Result], чтобы ViewModel не ловил исключения.
     */
    suspend fun loadModel(model: ModelFile): Result<Unit>

    /** Синхронная генерация: собирает все токены и отдаёт итоговый текст. */
    suspend fun generate(prompt: String): Result<String>

    /** Просит модель остановиться (частичный ответ сохраняется). */
    fun stopGeneration()

    /** Освобождает внешние ресурсы llama.cpp. После вызова репозиторий непригоден. */
    fun release()
}
