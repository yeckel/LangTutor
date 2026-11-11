package com.yeckel.tutor.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class VocabFile(
    val level: String,
    val words: List<String>,
)

/**
 * Loads tiered "known words" lists that are bundled as app assets and built by
 * tools/build_vocab.py (app/src/main/assets/vocab/<lang>/<LEVEL>.json).
 * Languages or levels without an asset degrade gracefully to an empty list,
 * which the tutor prompt then replaces by generic simple-vocabulary rules.
 */
class VocabRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<String, List<String>>()

    /** Word list for the level, or an empty list when no asset covers it. */
    @Synchronized
    fun wordsFor(language: String, level: String): List<String> {
        if (level == "Off") return emptyList()
        val code = languageCode(language) ?: return emptyList()
        val key = "$code/$level"
        cache[key]?.let { return it }

        val words: List<String> = runCatching {
            val text = context.assets.open("vocab/$code/$level.json")
                .bufferedReader()
                .use { it.readText() }
            json.decodeFromString<VocabFile>(text).words
        }.onFailure { Log.e("VocabRepo", "load failed for $key: $it") }
            .getOrDefault(emptyList())

        cache[key] = words
        return words
    }

    companion object {
        val LEVELS = listOf("A0", "A1", "A2", "B1", "Off")

        /** Maps a human language name (or a code) to the asset folder code. */
        fun languageCode(language: String): String? {
            val name = language.trim().lowercase()
            if (name in setOf("es", "en", "de", "fr", "it", "cs", "pl", "sk")) return name
            return when {
                name.startsWith("spanish") || name.startsWith("-es") -> "es"
                name.startsWith("english") -> "en"
                name.startsWith("german") || name.startsWith("deutsche") -> "de"
                name.startsWith("french") || name.startsWith("français") || name.startsWith("francais") -> "fr"
                name.startsWith("italian") || name.startsWith("italiano") -> "it"
                name.startsWith("czech") || name.startsWith("cesk") || name.startsWith("češ") -> "cs"
                name.startsWith("polish") || name.startsWith("polsk") -> "pl"
                name.startsWith("slovak") || name.startsWith("slovensk") -> "sk"
                else -> null
            }
        }
    }
}
