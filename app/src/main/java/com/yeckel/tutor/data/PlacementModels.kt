package com.yeckel.tutor.data

import kotlinx.serialization.Serializable

/** Structured result of the placement quiz. */
@Serializable
data class PlacementVerdict(
    val level: String = "",
    val knownWords: List<String> = emptyList(),
    val reason: String = "",
)

/** Strips markdown code fences the model may add around JSON payloads. */
fun extractJsonPayload(text: String): String =
    text.trim()
        .replace("```json", "")
        .replace("```JSON", "")
        .replace("```", "")
        .trim()

/** Maps a raw model level string onto the supported tiers (A0, A1, A2, B1). */fun clampVocabLevel(level: String): String = when (level.trim().uppercase()) {
    "A0" -> "A0"
    "A1" -> "A1"
    "A2" -> "A2"
    "B2", "C1", "C2" -> "B1"
    else -> "A1"
}

/** Word translation map shape for model JSON responses. */
@Serializable
data class Translations(val translations: Map<String, String> = emptyMap())
