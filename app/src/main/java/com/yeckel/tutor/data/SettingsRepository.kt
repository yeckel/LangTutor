package com.yeckel.tutor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yeckel.tutor.data.tutorDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** User configuration for server connection and tutoring setup. */
data class TutorConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val language: String,
    val vocabLevel: String,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val LANGUAGE = stringPreferencesKey("language")
        val VOCAB_LEVEL = stringPreferencesKey("vocab_level")
    }

    val config: Flow<TutorConfig> = context.tutorDataStore.data.map { prefs ->
        TutorConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "",
            language = prefs[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE,
            vocabLevel = prefs[Keys.VOCAB_LEVEL] ?: DEFAULT_VOCAB_LEVEL,
        )
    }

    suspend fun current(): TutorConfig = config.first()

    suspend fun save(baseUrl: String, apiKey: String, model: String, language: String, vocabLevel: String) {
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.MODEL] = model.trim()
            prefs[Keys.LANGUAGE] = language.trim()
            prefs[Keys.VOCAB_LEVEL] = vocabLevel.trim()
        }
    }

    companion object {
        /** Default points at the host machine when running on the Android emulator. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:11434"
        const val DEFAULT_LANGUAGE = "Spanish"
        const val DEFAULT_VOCAB_LEVEL = "A1"

        /** Tiered known-word levels, in learning order. */
        val VOCAB_LEVELS = listOf("A0", "A1", "A2", "B1", "Off")
    }
}
