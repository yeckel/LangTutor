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

    /** Articles, pronouns and auxiliary verbs that may precede a meaning. */
    private val LEADING_FILLER = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "der", "die", "das", "ein", "eine", "le", "les", "une", "il",
        "the", "to", "a", "an",
        "i", "you", "he", "she", "it", "we", "they",
        "my", "me", "his", "her", "their", "our", "your",
        "is", "are", "am", "yo", "mi", "tu", "usted",
    )

    /** Lowercase, strip accents and punctuation, drop leading filler words. */
    fun normalize(text: String): String {
        val flat = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val tokens = flat.split(" ").filter { it.isNotBlank() }
        val withoutFiller = tokens.dropWhile { it in LEADING_FILLER }
        return withoutFiller.joinToString(" ").ifBlank { flat }
    }

    /** Small edit distance (typing/ASR slips). */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    /** Lowercase tokens of a normalized string. */
    private fun tokensOf(text: String): List<String> = text.split(" ").filter { it.isNotBlank() }

    /**
     * Fast meaning check across several stored senses and learned synonyms:
     * raw/normalized equality, pronoun-prefixed phrases ("I have" for
     * "have"), token-superset phrasing and word containment.
     */
    fun matchesAnyMeaning(input: String, meanings: List<String>, accepted: List<String> = emptyList()): Boolean {
        val typed = input.trim().lowercase()
        if (typed.isEmpty()) return false
        val typedNorm = normalize(typed)
        val typedTokens = tokensOf(typedNorm)
        val candidates = (accepted + meanings).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return candidates.any { candidate ->
            if (typed == candidate) {
                true
            } else {
                val candNorm = normalize(candidate)
                if (candNorm.isBlank() || typedNorm.isBlank()) {
                    false
                } else {
                    val candTokens = tokensOf(candNorm)
                    (candTokens.isNotEmpty() && typedTokens.containsAll(candTokens)) ||
                        (candNorm.length > 5 && typedNorm.contains(candNorm))
                }
            }
        }
    }

    /** Does the spoken transcript plausibly contain the target word? */
    fun spokenMatches(word: String, transcript: String): Boolean {
        val target = normalize(word)
        if (target.isBlank() || transcript.isBlank()) return false
        val targetTokens = target.split(" ")
        val spokenTokens = normalize(transcript).split(" ")
        return spokenTokens.any { spokenToken ->
            targetTokens.any { wanted ->
                spokenToken == wanted ||
                    (wanted.length > 4 && spokenToken.startsWith(wanted)) ||
                    (wanted.length >= 6 && editDistance(spokenToken, wanted) <= 1)
            }
        }
    }
}
