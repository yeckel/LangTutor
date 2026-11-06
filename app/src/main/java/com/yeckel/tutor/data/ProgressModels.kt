package com.yeckel.tutor.data

import kotlinx.serialization.Serializable

/**
 * A word the tutor taught or the learner used, kept per language.
 *
 * `introducedByTutor` counts how often the tutor used the word outside the
 * bundled level list; `usedByLearner` how often the learner wrote it.
 * A word becomes "known" for prompt purposes as soon as the learner used it.
 */
@Serializable
data class ProgressWord(
    val word: String,
    val language: String,
    val introducedByTutor: Int = 0,
    val usedByLearner: Int = 0,
    val firstAdded: Long,
)

/** One saved chat turn of the active lesson. */
@Serializable
data class LessonTurn(
    val role: String, // "user" | "assistant"
    val content: String,
    val at: Long,
)

/** The resumable lesson: article, configuration and the whole conversation. */
@Serializable
data class LessonProgress(
    val id: Long,
    val url: String,
    val title: String,
    val text: String,
    val language: String,
    val vocabLevel: String,
    val difficulty: String = "JUST_RIGHT",
    val turns: List<LessonTurn> = emptyList(),
    val updatedAt: Long,
)
