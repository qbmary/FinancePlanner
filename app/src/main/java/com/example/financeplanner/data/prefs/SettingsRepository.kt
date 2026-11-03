package com.example.financeplanner.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Единственный экземпляр DataStore на процесс (делегат верхнего уровня). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "finance_settings"
)

/**
 * Вспомогательное хранилище настроек на Jetpack DataStore.
 *
 * Здесь лежат:
 *  - URI выбранного пользователем GGUF-файла (чтобы не выбирать его каждый запуск);
 *  - режим распознавания речи (офлайн/онлайн);
 *  - признак «озвучивать ответы».
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val modelUri: Flow<String?> = dataStore.data.map { it[KEY_MODEL_URI] }

    val preferOfflineRecognition: Flow<Boolean> =
        dataStore.data.map { it[KEY_PREFER_OFFLINE] ?: false }

    val speakAnswers: Flow<Boolean> =
        dataStore.data.map { it[KEY_SPEAK_ANSWERS] ?: true }

    suspend fun setModelUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_MODEL_URI) else prefs[KEY_MODEL_URI] = uri
        }
    }

    suspend fun setPreferOfflineRecognition(enabled: Boolean) {
        dataStore.edit { it[KEY_PREFER_OFFLINE] = enabled }
    }

    suspend fun setSpeakAnswers(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEAK_ANSWERS] = enabled }
    }

    private companion object {
        val KEY_MODEL_URI = stringPreferencesKey("model_uri")
        val KEY_PREFER_OFFLINE = booleanPreferencesKey("prefer_offline_recognition")
        val KEY_SPEAK_ANSWERS = booleanPreferencesKey("speak_answers")
    }
}
