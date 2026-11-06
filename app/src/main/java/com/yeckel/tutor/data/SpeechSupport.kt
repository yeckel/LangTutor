package com.yeckel.tutor.data

import java.text.Normalizer
import java.util.Locale

/** Helpers for the flash-card spoken and written answer checks. */
object SpeechSupport {

    /** Speech recognition locales per supported language key. */
    fun localeFor(languageKey: String): Locale? = when (languageKey) {
        "es" -> Locale("es", "ES")
        "en" -> Locale("en", "US")
        "de" -> Locale("de", "DE")
        "fr" -> Locale("fr", "FR")
        "it" -> Locale("it", "IT")
        else -> null
    }

    private val LEADING_ARTICLES =
        setOf("el", "la", "los", "las", "un", "una", "unos", "unas",
            "der", "die", "das", "ein", "eine",
            "le", "les", "une", "il", "lo", "the", "to")

    /** Lowercase, strip accents and punctuation, drop leading articles. */
    fun normalize(text: String): String {
        val flat = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val tokens = flat.split(" ").filter { it.isNotBlank() }
        val withoutArticles = tokens.dropWhile { it in LEADING_ARTICLES }
        return withoutArticles.joinToString(" ").ifBlank { flat }
    }

    /** Does the spoken transcript plausibly contain the target word? */
    fun spokenMatches(word: String, transcript: String): Boolean {
        val target = normalize(word)
        if (target.isBlank() || transcript.isBlank()) return false
        val spoken = normalize(transcript)
        return spoken.split(" ").any { it == target || (target.length > 4 && it.startsWith(target)) }
    }

    /** Does the typed meaning match the stored translation? */
    fun writtenMatches(input: String, translation: String): Boolean {
        val typed = normalize(input)
        val expected = normalize(translation.removePrefix("to "))
        if (typed.isBlank() || expected.isBlank()) return false
        return typed == expected || typed.split(" ").contains(expected) ||
            (expected.length > 5 && typed.contains(expected))
    }
}
