package com.example.financeplanner.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper

/**
 * Реализация [LlmRepository] поверх LlamaHelper (llamacpp-kotlin).
 *
 * Особенности библиотеки, которые здесь учтены:
 *  - `LlamaHelper.load()` асинхронный: результат приходит в лямбду-callback,
 *    а ошибки инициализации не выбрасываются наружу — поэтому загрузка
 *    ограничена таймаутом и сопровождается понятным сообщением;
 *  - `LlamaHelper` не даёт задать параметры генерации (n_predict, stop-последовательности),
 *    поэтому длина ответа ограничивается промптом, а хвост обрезается постобработкой;
 *  - событие `Done` содержит накопленный за все вызовы текст (особенность 0.2.0),
 *    поэтому итог собирается из событий `Ongoing` текущей генерации.
 */
class LlmRepositoryImpl(
    context: Context,
) : LlmRepository {

    private val appContext = context.applicationContext
    private val tag = "LlmRepository"

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    override val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _generationStatus = MutableStateFlow<GenerationStatus>(GenerationStatus.Idle)
    override val generationStatus: StateFlow<GenerationStatus> = _generationStatus.asStateFlow()

    /**
     * Отдельный scope для безопасного освобождения ресурсов: он не отменяется
     * вместе с движком и позволяет дождаться конца генерации перед release().
     */
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val loadMutex = Mutex()
    private val generationMutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var stopRequested = false

    /**
     * Движок llama.cpp. Создаётся на каждую загрузку модели и полностью
     * уничтожается в [unload], чтобы не терять нативную память между запусками.
     */
    private class Engine(context: Context) {

        /**
         * CoroutineExceptionHandler обязателен: внутри LlamaHelper 0.2.0 есть
         * дефекты (например, stopPrediction() бросает NPE, если модель ещё
         * не загружена). Без обработчика такое исключение убивает процесс.
         */
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
                Log.w("LlmRepository", "Внутренняя ошибка llama.cpp", throwable)
            }
        )

        val events = MutableSharedFlow<LlamaHelper.LLMEvent>(
            extraBufferCapacity = 1024,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )

        val helper = LlamaHelper(context.contentResolver, scope, events)

        /** Очередь без потерь: из неё генерация читает события. */
        val queue = Channel<LlamaHelper.LLMEvent>(Channel.UNLIMITED)

        val pump = scope.launch {
            events.collect { queue.trySend(it) }
        }

        fun close() {
            // abort() из llamacpp-kotlin 0.2.0 опасен: если модель ещё не
            // загружена, он вызывает stopCompletion(null!!) и роняет процесс.
            // release() проверяет currentContext на null и безопасен.
            runCatching { helper.release() }
            pump.cancel()
            scope.cancel()
            queue.close()
        }
    }

    override fun deviceCompatibility(): DeviceCompatibility {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val supported = abi == "arm64-v8a"

        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamGb = memoryInfo.totalMem / 1_000_000_000.0

        val stat = StatFs(appContext.filesDir.absolutePath)
        val freeStorageGb = stat.availableBytes / 1_000_000_000.0

        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!supported) {
            blockers += "Архитектура $abi не поддерживается: нативные библиотеки " +
                "llama.cpp в llamacpp-kotlin собраны только под arm64-v8a. " +
                "Запустите приложение на телефоне (не на x86-эмуляторе)."
        }

        if (totalRamGb < 3.0) {
            blockers += "ОЗУ ${"%.1f".format(totalRamGb)} ГБ мало для модели Q4_K_M " +
                "(≈1.2 ГБ весов + KV-кэш). Нужно минимум 3 ГБ."
        } else if (totalRamGb < 4.0) {
            warnings += "ОЗУ ${"%.1f".format(totalRamGb)} ГБ — на пределе. " +
                "Закройте лишние приложения перед генерацией."
        }

        if (freeStorageGb < 1.5) {
            warnings += "Свободно ${"%.1f".format(freeStorageGb)} ГБ — лучше освободить место " +
                "под модель и кэш."
        }

        return DeviceCompatibility(
            abi = abi,
            supportedAbi = supported,
            totalRamGb = totalRamGb,
            freeStorageGb = freeStorageGb,
            warnings = warnings,
            blockers = blockers,
        )
    }

    override suspend fun loadModel(model: ModelFile): Result<Unit> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (loaded) {
                return@withLock Result.success(Unit)
            }

            val compatibility = deviceCompatibility()
            if (!compatibility.canRunModel) {
                val message = compatibility.blockers.joinToString(" ")
                _modelStatus.value = ModelStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message))
            }

            _modelStatus.value = ModelStatus.Loading
            _generationStatus.value = GenerationStatus.Idle

            val started = SystemClock.elapsedRealtime()
            val newEngine = Engine(appContext)
            engine = newEngine

            val ready = CompletableDeferred<Long>()

            try {
                // LlamaHelper.load сам открывает fd по пути/URI (file:// и content://).
                newEngine.helper.load(
                    path = model.uri,
                    contextLength = CONTEXT_LENGTH,
                ) { contextId ->
                    ready.complete(contextId)
                }
            } catch (t: Throwable) {
                // Синхронные ошибки: нет доступа к URI, файл не открывается.
                Log.e(tag, "Не удалось начать загрузку модели", t)
                newEngine.close()
                engine = null
                val message = "Не удалось открыть файл модели: " +
                    (t.message ?: t::class.java.simpleName)
                _modelStatus.value = ModelStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message, t))
            }

            val contextId = withTimeoutOrNull(LOAD_TIMEOUT_MS) { ready.await() }

            if (contextId == null) {
                newEngine.close()
                engine = null
                val message = "Модель не загрузилась за ${LOAD_TIMEOUT_MS / 1000} с. " +
                    "Вероятные причины: не хватает ОЗУ, повреждён GGUF-файл или " +
                    "устройство не поддерживается."
                _modelStatus.value = ModelStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message))
            }

            loaded = true
            _modelStatus.value = ModelStatus.Ready(
                displayName = model.displayName,
                loadTimeMs = SystemClock.elapsedRealtime() - started,
            )
            return@withLock Result.success(Unit)
        }
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            if (!loaded) {
                val message = "Сначала загрузите GGUF-модель"
                _generationStatus.value = GenerationStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message))
            }

            val activeEngine = engine
                ?: return@withLock Result.failure(IllegalStateException("Модель не инициализирована"))

            // Убираем события, оставшиеся от предыдущих генераций.
            while (activeEngine.queue.tryReceive().isSuccess) {
                // drain
            }

            stopRequested = false
            val started = SystemClock.elapsedRealtime()
            val builder = StringBuilder()
            var tokens = 0

            _generationStatus.value = GenerationStatus.Streaming("", 0)

            try {
                activeEngine.helper.predict(prompt)
            } catch (t: Throwable) {
                Log.e(tag, "predict() не запустился", t)
                val message = "Не удалось запустить генерацию: " +
                    (t.message ?: "модель занята предыдущим запросом")
                _generationStatus.value = GenerationStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message, t))
            }

            val deadline = started + GENERATION_TIMEOUT_MS

            while (true) {
                if (stopRequested) {
                    val partial = builder.toString().trim()
                    if (partial.isNotEmpty()) {
                        _generationStatus.value = GenerationStatus.Success(
                            text = partial,
                            tokens = tokens,
                            durationMs = SystemClock.elapsedRealtime() - started,
                        )
                        return@withLock Result.success(partial)
                    }
                }

                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    val partial = builder.toString().trim()
                    val message = if (partial.isEmpty()) {
                        "Генерация не уложилась в ${GENERATION_TIMEOUT_MS / 1000} с"
                    } else {
                        "Генерация прервана по таймауту, показан неполный ответ"
                    }
                    if (partial.isEmpty()) {
                        _generationStatus.value = GenerationStatus.Failed(message)
                        return@withLock Result.failure(IllegalStateException(message))
                    }
                    _generationStatus.value = GenerationStatus.Success(
                        text = partial,
                        tokens = tokens,
                        durationMs = SystemClock.elapsedRealtime() - started,
                    )
                    return@withLock Result.success(partial)
                }

                val event = withTimeoutOrNull(remaining) { activeEngine.queue.receive() }
                    ?: continue

                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        builder.append(event.word)
                        tokens = event.tokenCount.coerceAtLeast(tokens)
                        _generationStatus.value = GenerationStatus.Streaming(
                            text = builder.toString(),
                            tokens = tokens,
                        )
                        // Qwen2.5-Instruct закрывает ответ служебным токеном.
                        if (builder.contains("<|im_end|>") || builder.contains("</s>")) {
                            break
                        }
                    }

                    is LlamaHelper.LLMEvent.Done -> {
                        tokens = event.tokenCount
                        break
                    }

                    is LlamaHelper.LLMEvent.Error -> {
                        val message = "Ошибка генерации: ${event.message}"
                        _generationStatus.value = GenerationStatus.Failed(message)
                        return@withLock Result.failure(IllegalStateException(message))
                    }

                    else -> Unit
                }
            }

            val text = builder.toString()
            val cleaned = LlmTextPostProcessor.clean(text)
            val result = cleaned.ifBlank { text.trim() }

            if (result.isBlank()) {
                val message = "Модель вернула пустой ответ. Попробуйте сформулировать запрос иначе."
                _generationStatus.value = GenerationStatus.Failed(message)
                return@withLock Result.failure(IllegalStateException(message))
            }

            _generationStatus.value = GenerationStatus.Success(
                text = result,
                tokens = tokens,
                durationMs = SystemClock.elapsedRealtime() - started,
            )
            Result.success(result)
        }
    }

    override fun stopGeneration() {
        // Останавливаем ожидание результата. Нативный вызов llama.cpp
        // прерывается только по EOS или переполнению контекста — API
        // llamacpp-kotlin 0.2.0 не даёт корректного stop (stopPrediction
        // содержит инвертированное условие), поэтому не вызываем его вовсе.
        stopRequested = true
    }

    override fun release() {
        stopRequested = true
        val activeEngine = engine ?: return

        engine = null
        loaded = false
        _modelStatus.value = ModelStatus.NotLoaded
        _generationStatus.value = GenerationStatus.Idle

        // Если модель сейчас генерирует, освобождаем контекст только после
        // завершения нативного вызова — иначе возможен use-after-free в JNI.
        releaseScope.launch {
            generationMutex.withLock { activeEngine.close() }
        }
    }

    private companion object {
        /** Qwen-2.5-1.5B-Instruct: 2048 хватает на промпт-отчёт и ответ. */
        const val CONTEXT_LENGTH = 2048
        const val LOAD_TIMEOUT_MS = 120_000L
        const val GENERATION_TIMEOUT_MS = 300_000L
    }
}
