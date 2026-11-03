package com.example.financeplanner.data.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Состояния голосового интерфейса (требование ЛР №6, п.3).
 *
 * Наружу отдаётся только через [StateFlow], поэтому UI никогда не работает
 * с SpeechRecognizer/TextToSpeech напрямую.
 */
sealed interface VoiceUiState {

    data object Idle : VoiceUiState

    data object Listening : VoiceUiState

    data object Processing : VoiceUiState

    data class Error(
        val message: String,
        val kind: VoiceErrorKind = VoiceErrorKind.UNKNOWN,
    ) : VoiceUiState
}

/** Причина ошибки — чтобы UI мог подсказать пользователю следующий шаг. */
enum class VoiceErrorKind {
    PERMISSION,
    RECOGNIZER_BUSY,
    NO_SERVICE,
    LANGUAGE_NOT_SUPPORTED,
    NETWORK,
    NO_MATCH,
    SPEECH_TIMEOUT,
    TTS_NOT_READY,
    UNKNOWN,
}

/**
 * Абстракция над системными сервисами речи:
 *  - распознавание: [android.speech.SpeechRecognizer];
 *  - синтез: [android.speech.tts.TextToSpeech].
 *
 * Задача слоя — полностью изолировать платформенные классы от Compose.
 */
interface VoiceRepository {

    /** Текущее состояние микрофона. */
    val state: StateFlow<VoiceUiState>

    /** Распознанные фразы (по одной на каждую успешную сессию). */
    val recognizedText: SharedFlow<String>

    /** Промежуточная гипотеза распознавания — для «живой» подсказки в UI. */
    val partialText: StateFlow<String>

    /** Читает ли сейчас синтезатор речи. */
    val isSpeaking: StateFlow<Boolean>

    /** Начинает прослушивание (разрешение RECORD_AUDIO должно быть выдано). */
    fun startListening()

    /** Останавливает запись и ждёт финальный результат. */
    fun stopListening()

    /** Озвучивает текст. Вызовы не блокируют UI-поток. */
    fun speak(text: String)

    /** Прерывает озвучивание. */
    fun stopSpeaking()

    /** Освобождает SpeechRecognizer и TextToSpeech. */
    fun destroy()
}
