package com.yeckel.tutor.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.ChatMessage
import com.yeckel.tutor.data.MeaningCheck
import com.yeckel.tutor.data.ProgressWord
import com.yeckel.tutor.data.SpeechSupport
import com.yeckel.tutor.data.Translations
import com.yeckel.tutor.data.extractJsonPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class FlashcardPhase { LOADING, STUDY, RESULT }

data class FlashcardUiState(
    val phase: FlashcardPhase = FlashcardPhase.LOADING,
    val words: List<ProgressWord> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val input: String = "",
    val spokenText: String? = null,
    val spokenMatch: Boolean? = null,
    val outcome: String? = null,
    val outcomeCorrect: Boolean? = null,
    val checking: Boolean = false,
    val correct: Int = 0,
    val missed: Int = 0,
    val languageName: String = "",
    val error: String? = null,
)

/**
 * Flash-card training for the tracked words: write the meaning (checked
 * against a model-provided translation) or say the word aloud (checked via
 * speech recognition). Correct answers count towards word strength.
 */
class FlashcardsViewModel(private val container: AppContainer) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(FlashcardUiState())
    val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

    private var languageKey = ""

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            val config = container.settings.current()
            languageKey = config.let {
                com.yeckel.tutor.data.VocabRepository.languageCode(it.language) ?: it.language.lowercase()
            }

            _uiState.update { it.copy(phase = FlashcardPhase.LOADING, error = null) }

            val tracked = container.progress.readKnownWords()
                .filter { it.language == languageKey }
                .shuffled()
            if (tracked.isEmpty()) {
                _uiState.update {
                    it.copy(
                        phase = FlashcardPhase.RESULT,
                        languageName = config.language,
                        error = "No tracked words yet. Chat with the tutor or run the placement quiz first.",
                    )
                }
                return@launch
            }

            // Fetch meanings for words the model has not translated yet.
            val missing = tracked.filter { it.translation.isNullOrBlank() }
            if (missing.isNotEmpty()) {
                container.ollama.chatOnce(
                    config.model,
                    translateMessages(config.language, missing.map { it.word }),
                    expectJson = true,
                ).fold(
                    onSuccess = { text ->
                        runCatching { json.decodeFromString<Translations>(extractJsonPayload(text)) }
                            .getOrNull()
                            ?.translations
                            ?.forEach { (word, meaning) ->
                                container.progress.setTranslation(languageKey, word, meaning)
                            }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(error = "Meanings could not be fetched (${e.message}); " +
                                "cards work with reveal only.")
                        }
                    },
                )
            }

            val fresh = container.progress.readKnownWords()
                .filter { it.language == languageKey }
                .shuffled()

            _uiState.update {
                it.copy(phase = FlashcardPhase.STUDY, words = fresh, languageName = config.language)
            }
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value, error = null) }

    private fun current() = _uiState.value.words.getOrNull(_uiState.value.index)

    /** Learner typed the meaning; local fuzzy check first, then model judgement. */
    fun checkMeaning() {
        val state = _uiState.value
        val word = current() ?: return
        val answer = state.input.trim()
        if (answer.isEmpty() || state.checking) return
        val translation = word.translation

        if (translation.isNullOrBlank()) {
            _uiState.update {
                it.copy(error = "No meaning stored for this word — reveal it or use speech.")
            }
            return
        }

        if (SpeechSupport.writtenMatchesLocally(answer, translation, word.acceptedMeanings)) {
            acceptMeaning(word, answer, note = null)
            return
        }

        // Local check failed: ask the tutor model if the meaning is acceptable
        // (synonyms like huge/big or phrasing like "I have" for "have").
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            val config = container.settings.current()
            container.ollama.chatOnce(
                config.model,
                meaningCheckMessages(config.language, word.word, translation, answer),
                expectJson = true,
            ).fold(
                onSuccess = { text ->
                    val check = runCatching {
                        json.decodeFromString<MeaningCheck>(extractJsonPayload(text))
                    }.getOrNull()
                    when {
                        check == null ->
                            missMeaning(word, "expected: ${translation.removePrefix("to ")}")

                        check.correct -> acceptMeaning(word, answer, note = check.explanation.ifBlank {
                            translation.removePrefix("to ")
                        })

                        else -> missMeaning(word, check.explanation.ifBlank {
                            "expected: ${translation.removePrefix("to ")}"
                        })
                    }
                },
                onFailure = { e ->
                    missMeaning(word, "expected: ${translation.removePrefix("to ")} " +
                        "(the tutor could not be consulted: ${e.message})")
                },
            )
            _uiState.update { it.copy(checking = false) }
        }
    }

    private fun acceptMeaning(word: ProgressWord, typedAnswer: String, note: String?) {
        viewModelScope.launch {
            container.progress.recordTrainCorrect(languageKey, word.word)
            val stored = word.translation
            if (stored.isNullOrBlank() || !typedAnswer.equals(stored.trim(), ignoreCase = true)) {
                container.progress.addAcceptedMeaning(languageKey, word.word, typedAnswer)
            }
        }
        _uiState.update {
            it.copy(
                outcome = "✓ Accepted — ${word.word} = ${typedAnswer}" +
                    (note?.takeIf { n -> n.isNotBlank() }?.let { n -> " ($n)" } ?: ""),
                outcomeCorrect = true,
                correct = it.correct + 1,
                revealed = true,
                error = null,
            )
        }
    }

    private fun missMeaning(word: ProgressWord, note: String) {
        viewModelScope.launch { container.progress.recordTrainMissed(languageKey, word.word) }
        _uiState.update {
            it.copy(
                outcome = "✗ Not accepted — ${word.word}: $note",
                outcomeCorrect = false,
                missed = it.missed + 1,
                revealed = true,
            )
        }
    }

    /** Learner said something; accept the word, its meaning or a learned synonym. */
    fun onSpoken(transcript: String?) {
        val word = current() ?: return
        if (transcript.isNullOrBlank()) {
            _uiState.update { it.copy(spokenMatch = false, spokenText = null) }
            return
        }
        val matchesWord = SpeechSupport.spokenMatches(word.word, transcript)
        val matchesMeaning = SpeechSupport.writtenMatchesLocally(
            transcript,
            word.translation,
            word.acceptedMeanings,
        )
        val matches = matchesWord || matchesMeaning
        _uiState.update {
            it.copy(spokenText = transcript, spokenMatch = matches)
        }
        if (matches) {
            acceptMeaning(word, transcript, note = null)
        }
    }

    fun onMicPermissionDenied() =
        _uiState.update { it.copy(error = "Microphone access is needed for spoken practice.") }

    fun onSpeechUnavailable() =
        _uiState.update { it.copy(error = "Speech recognition is not available on this device.") }

    /**
     * Learner gives up on this card: counts as missed, shows the meaning.
     * When the tutor model could not be consulted, the card stays open and
     * the answer is NOT penalized (server hiccups are not lack of knowledge).
     */
    fun giveUp() {
        val state = _uiState.value
        val word = current() ?: return
        if (!state.revealed) {
            viewModelScope.launch { container.progress.recordTrainMissed(languageKey, word.word) }
        }
        _uiState.update {
            it.copy(
                revealed = true,
                missed = if (state.revealed) it.missed else it.missed + 1,
                outcome = "Meaning: ${word.translation?.removePrefix("to ") ?: "?"}",
                outcomeCorrect = false,
            )
        }
    }

    fun next() {
        val state = _uiState.value
        val nextIndex = state.index + 1
        if (nextIndex >= state.words.size) {
            _uiState.update { it.copy(phase = FlashcardPhase.RESULT) }
        } else {
            _uiState.update {
                it.copy(
                    index = nextIndex,
                    revealed = false,
                    input = "",
                    spokenText = null,
                    spokenMatch = null,
                    outcome = null,
                    outcomeCorrect = null,
                )
            }
        }
    }

    private fun translateMessages(language: String, words: List<String>) = listOf(
        ChatMessage(
            role = "system",
            content = "You translate single words from $language into English. " +
                "Reply ONLY with JSON of the shape {\"translations\": {\"word\": \"meaning\"}}. " +
                "Keep meanings short (1-2 words). Translate even inflected word forms " +
                "to their plain English meaning.",
        ),
        ChatMessage(role = "user", content = "Translate these words: ${Json.encodeToString(words)}"),
    )

    private fun meaningCheckMessages(language: String, word: String, expected: String, answer: String) =
        listOf(
            ChatMessage(
                role = "system",
                content = "You are a lenient vocabulary tutor for $language. The learner is " +
                    "training flash cards. Target word: \"$word\" in $language. The stored meaning " +
                    "was \"$expected\". The learner answered: \"$answer\". " +
                    "Accept the answer if it is a correct, reasonable English meaning of the word - " +
                    "synonyms are fine (big/huge/large), and inflected or pronoun-prefixed phrasing " +
                    "like \"I have\" for \"tengo\" is fine. Reject only clearly wrong meanings. " +
                    "Reply ONLY with JSON: {\"correct\": true, \"explanation\": \"1 very short " +
                    "sentence in English about the answer\"}.",
            ),
            ChatMessage(role = "user", content = "Judge the answer now."),
        )
}
