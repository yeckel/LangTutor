package com.yeckel.tutor.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-build helper: applies a configuration preset from one adb command,
 * instead of typing values into the settings screen on each device.
 *
 * adb shell am broadcast -a com.yeckel.tutor.DEBUG_CONFIG \
 *   --es url "https://ollama.com" \
 *   --es key "API_KEY" \
 *   --es model "gemma4:31b" \
 *   --ez read_aloud true
 *
 * Only non-null extras are changed; omitted values keep their current state.
 * Registered only when BuildConfig.DEBUG.
 */
class DebugPresetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION && intent.action != ACTION_SEED) return
        val settings = SettingsRepository(context.applicationContext)
        val progress = ProgressRepository(context.applicationContext)
        if (intent.action == ACTION_SEED) {
            val joined = intent.getStringExtra(EXTRA_SEED_WORDS) ?: return
            val languageKey = intent.getStringExtra(EXTRA_LANGUAGE_CODE) ?: "es"
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Each comma-separated word is marked as introduced once;
                    // counters re-earn honestly through practice.
                    progress.recordAssistantMessage(languageKey, emptySet(), joined)
                    Log.i(TAG, "seeded words: $joined")
                } catch (e: Exception) {
                    Log.e(TAG, "seed failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action != ACTION) return
        val presetPending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val current = settings.current()
                val updated = current.copy(
                    baseUrl = intent.getStringExtra(EXTRA_URL)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.baseUrl,
                    apiKey = intent.getStringExtra(EXTRA_KEY)?.takeIf { it.isNotEmpty() }
                        ?: current.apiKey,
                    model = intent.getStringExtra(EXTRA_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.model,
                    helpLanguage = intent.getStringExtra(EXTRA_HELP)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.helpLanguage,
                    language = intent.getStringExtra(EXTRA_PRACTICE)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.language,
                    vocabLevel = intent.getStringExtra(EXTRA_LEVEL)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.vocabLevel,
                    readAloud = if (intent.hasExtra(EXTRA_READ_ALOUD)) {
                        intent.getBooleanExtra(EXTRA_READ_ALOUD, current.readAloud)
                    } else {
                        current.readAloud
                    },
                    speakSource = if (intent.hasExtra(EXTRA_SPEAK_WORDS)) {
                        intent.getBooleanExtra(EXTRA_SPEAK_WORDS, current.speakSource)
                    } else {
                        current.speakSource
                    },
                    speakMeanings = if (intent.hasExtra(EXTRA_SPEAK_MEANINGS)) {
                        intent.getBooleanExtra(EXTRA_SPEAK_MEANINGS, current.speakMeanings)
                    } else {
                        current.speakMeanings
                    },
                    autoListen = if (intent.hasExtra(EXTRA_AUTO_LISTEN)) {
                        intent.getBooleanExtra(EXTRA_AUTO_LISTEN, current.autoListen)
                    } else {
                        current.autoListen
                    },
                )
                settings.save(updated)
                Log.i(
                    TAG,
                    "preset applied: url=${updated.baseUrl} model=${updated.model} " +
                        "readAloud=${updated.readAloud} speakSource=${updated.speakSource} " +
                        "speakMeanings=${updated.speakMeanings} autoListen=${updated.autoListen}",
                )
            } catch (e: Exception) {
                Log.e(TAG, "preset failed", e)
            } finally {
                presetPending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.yeckel.tutor.DEBUG_CONFIG"
        const val ACTION_SEED = "com.yeckel.tutor.DEBUG_SEED_WORDS"
        const val TAG = "DebugPreset"

        const val EXTRA_URL = "url"
        const val EXTRA_KEY = "key"
        const val EXTRA_MODEL = "model"
        const val EXTRA_HELP = "help"
        const val EXTRA_PRACTICE = "practice"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_READ_ALOUD = "read_aloud"
        const val EXTRA_SPEAK_WORDS = "speak_words"
        const val EXTRA_SPEAK_MEANINGS = "speak_meanings"
        const val EXTRA_AUTO_LISTEN = "auto_listen"
        const val EXTRA_SEED_WORDS = "seed_words"
        const val EXTRA_LANGUAGE_CODE = "lang"
    }
}
