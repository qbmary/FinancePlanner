package com.example.financeplanner.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.financeplanner.data.ai.LlmTextPostProcessor
import com.example.financeplanner.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Реализация [VoiceRepository] на SpeechRecognizer + TextToSpeech.
 *
 * Правила, соблюдаемые здесь:
 *  - SpeechRecognizer создаётся, используется и уничтожается строго на main-потоке
 *    (внутри приложения все вызовы проксируются через Handler);
 *  - инициализация TextToSpeech асинхронная: если [speak] вызван раньше готовности,
 *    фраза запоминается и произносится после инициализации;
 *  - ошибки распознавания превращаются в понятные сообщения с [VoiceErrorKind];
 *  - ресурсы освобождаются в [destroy] (вызывается из ViewModel.onCleared()).
 */
class VoiceRepositoryImpl(
    context: Context,
    private val settingsRepository: SettingsRepository,
) : VoiceRepository {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recognizer: SpeechRecognizer? = null
    private var recognizerIsOnDevice = false

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var ttsLanguageSupported = false

    @Volatile
    private var preferOffline = false

    @Volatile
    private var destroyed = false

    private var pendingSpeech: String? = null
    private var speechSession = 0
    private val pendingUtterances = AtomicInteger(0)

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    override val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    override val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        scope.launch {
            settingsRepository.preferOfflineRecognition.collect { preferOffline = it }
        }
        mainHandler.post { initTts() }
    }

    // ------------------------------------------------------------------ TTS

    private fun initTts() {
        if (destroyed) return

        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                configureVoiceLanguage()
                pendingSpeech?.let { text ->
                    pendingSpeech = null
                    speakInternal(text)
                }
            } else {
                ttsReady = false
                _state.value = VoiceUiState.Error(
                    message = "Синтез речи не инициализирован на этом устройстве. " +
                        "Установите системный компонент «Синтез речи» (Google TTS).",
                    kind = VoiceErrorKind.TTS_NOT_READY,
                )
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (pendingUtterances.decrementAndGet() <= 0) {
                    pendingUtterances.set(0)
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingUtterances.set(0)
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "Ошибка синтеза речи: $errorCode")
                pendingUtterances.set(0)
                _isSpeaking.value = false
            }
        })
    }

    private fun configureVoiceLanguage() {
        val engine = tts ?: return
        val russian = Locale.forLanguageTag("ru-RU")
        val result = engine.setLanguage(russian)

        ttsLanguageSupported = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED

        if (!ttsLanguageSupported) {
            // Пытаемся хотя бы на системном языке — лучше, чем молчание.
            val fallback = engine.setLanguage(Locale.getDefault())
            ttsLanguageSupported = fallback != TextToSpeech.LANG_MISSING_DATA &&
                fallback != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ttsLanguageSupported) {
                _state.value = VoiceUiState.Error(
                    message = "Русский голос не установлен. Откройте настройки телефона → " +
                        "«Синтез речи» → установите русский язык. Пока ответы не озвучиваются.",
                    kind = VoiceErrorKind.LANGUAGE_NOT_SUPPORTED,
                )
            }
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
    }

    override fun speak(text: String) {
        mainHandler.post { speakInternal(text) }
    }

    private fun speakInternal(text: String) {
        val normalized = LlmTextPostProcessor.forSpeech(text)
        if (normalized.isBlank()) return

        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech = normalized
            initTtsIfNeeded()
            return
        }

        if (!ttsLanguageSupported) {
            _state.value = VoiceUiState.Error(
                message = "Озвучивание недоступно: в системе нет русского голоса. " +
                    "Ответ показан на экране.",
                kind = VoiceErrorKind.LANGUAGE_NOT_SUPPORTED,
            )
            return
        }

        engine.stop()
        speechSession++
        pendingUtterances.set(0)

        val chunks = normalized.chunked(MAX_TTS_CHUNK)
        pendingUtterances.set(chunks.size)
        _isSpeaking.value = true

        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "finance_tts_${speechSession}_$index"
            val result = engine.speak(chunk, queueMode, null, utteranceId)
            if (result != TextToSpeech.SUCCESS && index == 0) {
                pendingUtterances.set(0)
                _isSpeaking.value = false
                _state.value = VoiceUiState.Error(
                    message = "Не удалось запустить озвучивание (код $result).",
                    kind = VoiceErrorKind.TTS_NOT_READY,
                )
            }
        }
    }

    private fun initTtsIfNeeded() {
        if (tts == null) initTts()
    }

    override fun stopSpeaking() {
        mainHandler.post {
            pendingSpeech = null
            pendingUtterances.set(0)
            _isSpeaking.value = false
            tts?.stop()
        }
    }

    // ------------------------------------------------------- SpeechRecognizer

    override fun startListening() {
        mainHandler.post {
            if (destroyed) return@post

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = VoiceUiState.Error(
                    message = "На устройстве нет службы распознавания речи. " +
                        "Установите Google / «Речевые сервисы Google» либо включите " +
                        "голосовой ввод в настройках языка и ввода.",
                    kind = VoiceErrorKind.NO_SERVICE,
                )
                return@post
            }

            _partialText.value = ""

            val service = obtainRecognizer()
            if (service == null) {
                _state.value = VoiceUiState.Error(
                    message = "Не удалось создать SpeechRecognizer. " +
                        "Проверьте, что на устройстве включён сервис распознавания речи.",
                    kind = VoiceErrorKind.NO_SERVICE,
                )
                return@post
            }

            _state.value = VoiceUiState.Listening

            try {
                service.startListening(buildRecognizerIntent())
            } catch (e: SecurityException) {
                _state.value = VoiceUiState.Error(
                    message = "Нет разрешения RECORD_AUDIO: разрешите доступ к микрофону.",
                    kind = VoiceErrorKind.PERMISSION,
                )
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                _state.value = VoiceUiState.Error(
                    message = "Не удалось включить микрофон: ${e.message ?: "неизвестная ошибка"}",
                    kind = VoiceErrorKind.UNKNOWN,
                )
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            if (_state.value is VoiceUiState.Listening) {
                _state.value = VoiceUiState.Processing
            }
            runCatching { recognizer?.stopListening() }
        }
    }

    private fun obtainRecognizer(): SpeechRecognizer? {
        val useOnDevice = preferOffline &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isOnDeviceAvailable()

        val existing = recognizer
        if (existing != null && recognizerIsOnDevice == useOnDevice) {
            return existing
        }

        existing?.let { runCatching { it.destroy() } }
        recognizer = null

        val created = try {
            if (useOnDevice) {
                createOnDeviceRecognizer()
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось создать SpeechRecognizer", e)
            null
        }

        if (created != null) {
            created.setRecognitionListener(listener)
            recognizer = created
            recognizerIsOnDevice = useOnDevice
        }

        return created
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isOnDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }
            .getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createOnDeviceRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE_RU)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE_RU)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, LOCALE_RU)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // В офлайн-режиме просим движок работать без сети; не все сервисы это умеют.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceUiState.Listening
        }

        override fun onBeginningOfSpeech() {
            _state.value = VoiceUiState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = VoiceUiState.Processing
        }

        override fun onError(error: Int) {
            val (message, kind) = describeRecognizerError(error)
            _partialText.value = ""

            // Некоторые ошибки ломают экземпляр — пересоздаём его на следующий раз.
            if (error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            ) {
                recognizer?.let { runCatching { it.destroy() } }
                recognizer = null
            }

            _state.value = VoiceUiState.Error(message = message, kind = kind)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()

            _partialText.value = ""

            if (text.isBlank()) {
                _state.value = VoiceUiState.Error(
                    message = "Не удалось распознать речь. Повторите попытку.",
                    kind = VoiceErrorKind.NO_MATCH,
                )
                return
            }

            _state.value = VoiceUiState.Idle
            _recognizedText.tryEmit(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _partialText.value = text
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describeRecognizerError(error: Int): Pair<String, VoiceErrorKind> =
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Нет разрешения RECORD_AUDIO. Разрешите доступ к микрофону в настройках." to
                    VoiceErrorKind.PERMISSION

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Микрофон занят: распознавание уже запущено в другом приложении. " +
                    "Закройте его и повторите." to VoiceErrorKind.RECOGNIZER_BUSY

            SpeechRecognizer.ERROR_AUDIO ->
                "Ошибка записи аудио. Проверьте, не занят ли микрофон другим приложением." to
                    VoiceErrorKind.UNKNOWN

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER ->
                "Службе распознавания не удалось получить результат. " +
                    "Проверьте интернет или включите офлайн-распознавание." to
                    VoiceErrorKind.NETWORK

            SpeechRecognizer.ERROR_CLIENT ->
                "Сессия распознавания прервана. Попробуйте ещё раз." to
                    VoiceErrorKind.UNKNOWN

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Речь не обнаружена: произнесите команду громче и ближе к микрофону." to
                    VoiceErrorKind.SPEECH_TIMEOUT

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Не удалось разобрать фразу. Скажите, например: " +
                    "«потратила двадцать пять рублей на такси»." to
                    VoiceErrorKind.NO_MATCH

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "Русский язык не поддерживается установленной службой распознавания." to
                    VoiceErrorKind.LANGUAGE_NOT_SUPPORTED

            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "Русский язык не загружен в службу распознавания. Загрузите его " +
                    "в настройках «Язык и ввод» → «Голосовой ввод»." to
                    VoiceErrorKind.LANGUAGE_NOT_SUPPORTED

            else ->
                "Ошибка распознавания (код $error)." to VoiceErrorKind.UNKNOWN
        }

    override fun destroy() {
        if (destroyed) return
        destroyed = true

        mainHandler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null

            pendingSpeech = null
            pendingUtterances.set(0)
            _isSpeaking.value = false

            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false

            _state.value = VoiceUiState.Idle
        }

        scope.cancel()
    }

    private companion object {
        const val TAG = "VoiceRepository"

        /** Ограничение TextToSpeech.getMaxSpeechInputLength() ≈ 4000 символов. */
        const val MAX_TTS_CHUNK = 3000
        const val LOCALE_RU = "ru-RU"
    }
}
