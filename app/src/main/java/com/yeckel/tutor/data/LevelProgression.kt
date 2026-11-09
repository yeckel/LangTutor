package com.yeckel.tutor.data

import android.util.Log
import kotlin.math.ceil

/** Progress of the learner in one vocabulary tier. */
data class TierProgress(
    val level: String,
    val totalWords: Int,
    val tracked: Int,
    val mastered: Int,
) {
    val masteredRatio: Float
        get() = if (tracked == 0) 0f else mastered.toFloat() / tracked
}

/**
 * Computes tier mastery from the tracked words: a word counts as mastered
 * once the learner has demonstrably used it (chat or flash cards).
 * A tier is complete when enough of its words are engaged in practice and
 * most of those are mastered - then the next tier can be unlocked.
 */
object LevelProgression {

    const val MIN_ENGAGED_TO_COMPLETE = 12
    const val MASTER_RATIO = 0.8

    val TIER_ORDER = listOf("A0", "A1", "A2", "B1")

    fun tierStats(
        languageKey: String,
        knownWords: List<ProgressWord>,
        vocab: VocabRepository,
    ): List<TierProgress> {
        val tracked = knownWords
            .filter { it.language == languageKey }
            .associateBy { it.word }
        return TIER_ORDER.map { tier ->
            val tierWords = vocab.wordsFor(languageKey, tier)
            Log.i("LevelProg2", "tier $tier '$languageKey': tierWords=${tierWords.size}")
            val engaged = tierWords.mapNotNull { tracked[it] }
            TierProgress(
                level = tier,
                totalWords = tierWords.size,
                tracked = engaged.size,
                mastered = engaged.count { it.usedByLearner >= 1 || it.trainCorrect >= 1 },
            )
        }
    }

    /** The next tier's progress item when the current tier is complete. */
    fun nextUnlockable(vocabLevel: String, stats: List<TierProgress>): TierProgress? {
        val idx = TIER_ORDER.indexOf(vocabLevel)
        if (idx < 0 || idx >= TIER_ORDER.lastIndex) return null // Off → none; B1 → top reached
        val current = stats.find { it.level == vocabLevel } ?: return null
        return if (isComplete(current)) stats.find { it.level == TIER_ORDER[idx + 1] } else null
    }

    fun isComplete(tp: TierProgress): Boolean =
        tp.tracked >= MIN_ENGAGED_TO_COMPLETE &&
            tp.mastered >= ceil(MASTER_RATIO * tp.tracked)
}
