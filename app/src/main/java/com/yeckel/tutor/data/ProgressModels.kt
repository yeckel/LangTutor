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
    /** Help-language meanings, cached from the model for flash cards. */
    val meanings: List<String> = emptyList(),
    /** Legacy single meaning (kept so old records keep working). */
    val translation: String? = null,
    /** Alternative meanings the learner already got away with (synonyms). */
    val acceptedMeanings: List<String> = emptyList(),
    /** Flash-card training results. */
    val trainCorrect: Int = 0,
    val trainMissed: Int = 0,
    val firstAdded: Long,
)

/** All cached meanings of the word, newest format plus legacy field. */
val ProgressWord.meaningList: List<String>
    get() = (meanings + listOfNotNull(translation)).distinct().filter { it.isNotBlank() }

/** True when the learner has demonstrably used/knows the word. */
val ProgressWord.isKnown: Boolean
    get() = usedByLearner >= 1

/** Model verdict for a flash-card meaning check. */
@Serializable
data class MeaningCheck(
    val correct: Boolean = false,
    val explanation: String = "",
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
