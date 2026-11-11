package com.yeckel.tutor.data

import android.content.Context

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yeckel.tutor.data.tutorDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A language the app fully speaks: UI translation, help glosses, locale tags. */
data class LanguageOption(
    /** Locale tag used by the per-app locale switch, e.g. "cs". */
    val code: String,
    /** Name shown in the UI, written in that language. */
    val native: String,
    /** Name stored in the config and used in tutor prompts, e.g. "Czech". */
    val value: String,
)

/** User configuration for server connection and tutoring setup. */
data class TutorConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val language: String,
    val vocabLevel: String,
    val helpLanguage: String,
    /** Speak the tutor's chat replies progressively as they stream. */
    val readAloud: Boolean = false,
    /** Speak each source word when its card appears. */
    val speakSource: Boolean = false,
    /** Speak the meaning when it is revealed. */
    val speakMeanings: Boolean = false,
    /** Start listening automatically after the word is spoken. */
    val autoListen: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val LANGUAGE = stringPreferencesKey("language")
        val VOCAB_LEVEL = stringPreferencesKey("vocab_level")
        val HELP_LANGUAGE = stringPreferencesKey("help_language")
        val READ_ALOUD = booleanPreferencesKey("read_aloud")
        val SPEAK_SOURCE = booleanPreferencesKey("speak_source")
        val SPEAK_MEANINGS = booleanPreferencesKey("speak_meanings")
        val SPEAK_LISTEN = booleanPreferencesKey("speak_listen")
    }

    val config: Flow<TutorConfig> = context.tutorDataStore.data.map { prefs ->
        TutorConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "",
            language = prefs[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE,
            vocabLevel = prefs[Keys.VOCAB_LEVEL] ?: DEFAULT_VOCAB_LEVEL,
            helpLanguage = prefs[Keys.HELP_LANGUAGE] ?: DEFAULT_HELP_LANGUAGE,
            readAloud = prefs[Keys.READ_ALOUD] ?: false,
            speakSource = prefs[Keys.SPEAK_SOURCE] ?: false,
            speakMeanings = prefs[Keys.SPEAK_MEANINGS] ?: false,
            autoListen = prefs[Keys.SPEAK_LISTEN] ?: false,
        )
    }

    suspend fun current(): TutorConfig = config.first()

    suspend fun save(config: TutorConfig) {
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = config.baseUrl.trim()
            prefs[Keys.API_KEY] = config.apiKey.trim()
            prefs[Keys.MODEL] = config.model.trim()
            prefs[Keys.LANGUAGE] = config.language.trim()
            prefs[Keys.VOCAB_LEVEL] = config.vocabLevel.trim()
            prefs[Keys.HELP_LANGUAGE] = config.helpLanguage.trim()
            prefs[Keys.READ_ALOUD] = config.readAloud
            prefs[Keys.SPEAK_SOURCE] = config.speakSource
            prefs[Keys.SPEAK_MEANINGS] = config.speakMeanings
            prefs[Keys.SPEAK_LISTEN] = config.autoListen
        }
    }

    companion object {
        /** Default points at the host machine when running on the Android emulator. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:11434"
        const val DEFAULT_LANGUAGE = "Spanish"
        const val DEFAULT_VOCAB_LEVEL = "A1"
        const val DEFAULT_HELP_LANGUAGE = "English"

        /** Tiered known-word levels, in learning order. */
        val VOCAB_LEVELS = listOf("A0", "A1", "A2", "B1", "Off")

        /** Languages the app is translated into; offered as app + help language. */
        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("en", "English", "English"),
            LanguageOption("cs", "Čeština", "Czech"),
            LanguageOption("es", "Español", "Spanish"),
            LanguageOption("de", "Deutsch", "German"),
            LanguageOption("pl", "Polski", "Polish"),
            LanguageOption("sk", "Slovenčina", "Slovak"),
        )
    }
}
