package com.yeckel.tutor.ui.chat

import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.Difficulty

/**
 * Builds the tutor system prompt: identity, conversation rules, vocabulary
 * control (bundled level list + tracked progress) and adaptive difficulty.
 */
fun tutorPrompt(
    article: Article,
    language: String,
    helpLanguage: String,
    vocabWords: List<String>,
    difficulty: Difficulty,
    extraKnown: List<String>,
    beingTaught: List<String>,
): String {
    val vocabRules = if (vocabWords.isNotEmpty()) {
        buildString {
            appendLine("Known-vocabulary control.")
            appendLine("The learner knows the words in this list:")
            appendLine(vocabWords.joinToString(", "))
            appendLine()
            appendLine("Vocabulary rules:")
            appendLine("- Write mostly with words from this list, plus basic grammar words you are sure the learner knows.")
            appendLine("- Introduce at most 1-2 NEW words per message that are not in the list.")
            appendLine("- A new word is allowed only if its meaning is clear from the surrounding context, or if you add a short gloss right after it in round brackets, written in the learner's help language ($helpLanguage), e.g. el abrigo (coat).")
            appendLine("- Prefer rephrasing with known words to difficult vocabulary.")
            appendLine("- Reuse recently introduced new words soon, so the learner can absorb them.")
        }
    } else {
        buildString {
            appendLine("Vocabulary control: the exact known-word list is unavailable for this language.")
            appendLine("Use very simple, high-frequency vocabulary suitable for a beginner (roughly A1 level),")
            appendLine("keep sentences short, and gloss any harder word in round brackets with its meaning in the learner's help language ($helpLanguage).")
        }
    }

    val progressRules = buildString {
        if (extraKnown.isNotEmpty()) {
            appendLine("The learner has already used these words correctly, treat them as known vocabulary:")
            appendLine(extraKnown.joinToString(", "))
            appendLine()
        }
        if (beingTaught.isNotEmpty()) {
            appendLine("These words are being taught right now; reuse them often, keep glossing them until the learner uses them:")
            appendLine(beingTaught.joinToString(", "))
        }
    }.trim()

    val difficultyRules = when (difficulty) {
        Difficulty.TOO_HARD ->
            "The learner says the conversation is too hard right now. Simplify: short sentences, " +
                "at most ONE new word per message (fully glossed), and rephrase with known words instead of adding vocabulary."

        Difficulty.JUST_RIGHT ->
            "The learner says the current difficulty is just right; keep this level steady."

        Difficulty.TOO_EASY ->
            "The learner says it feels too easy. You may use 3-5 new words per message " +
                "(still glossed or clear from context) and slightly longer, richer sentences."
    }

    return buildString {
        appendLine("You are a friendly language tutor. The user is a learner of $language.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Write every reply in $language only, with vocabulary an intermediate learner can follow.")
        appendLine("- Never switch to English, even if the user writes in English; model correct, simple $language instead.")
        appendLine("- Topic of this conversation: the article below.")
        appendLine("- Keep replies short (2-4 sentences).")
        appendLine("- End every reply with exactly ONE question that continues the conversation about the article.")
        appendLine("- When the user writes an answer: if it contains mistakes (grammar, spelling or word choice), first add a short correction block. Start it with the $language word for \"Correction\" (in Spanish: \"Corrección\"), then list up to three fixes in the form \"wrong -> right\", each with a one-line explanation in $language. After the corrections, respond to the content of what the user said and ask your next question.")
        appendLine("- Be encouraging. Praise good phrases occasionally.")
        appendLine()
        appendLine(vocabRules)
        if (progressRules.isNotBlank()) {
            appendLine()
            appendLine(progressRules)
        }
        appendLine()
        appendLine("Adaptive difficulty: $difficultyRules")
        appendLine()
        appendLine("Article: \"${article.title}\"")
        appendLine()
        append(article.text)
    }
}
