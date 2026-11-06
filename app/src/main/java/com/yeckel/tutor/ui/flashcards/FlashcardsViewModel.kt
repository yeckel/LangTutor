package com.yeckel.tutor.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.ChatMessage
import com.yeckel.tutor.data.MeaningCheck
import com.yeckel.tutor.data.ProgressWord
import com.yeckel.tutor.data.SpeechSupport
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.data.extractJsonPayload
import com.yeckel.tutor.data.meaningList
import com.yeckel.tutor.data.tokenize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val helpLanguage: String = "English",
    val error: String? = null,
)

/** Shape(s) the model may use when translating: lists or single strings. */
@kotlinx.serialization.Serializable
private data class TranslationsList(
    val translations: Map<String, List<String>> = emptyMap(),
)

@kotlinx.serialization.Serializable
private data class TranslationsSingle(
    val translations: Map<String, String> = emptyMap(),
)

/**
 * Flow-style flash-card training:
 *  - as soon as the typed meaning matches any stored sense or learned
 *    synonym, the card is accepted and advances by itself;
 *  - else Enter (or Check) asks the tutor model, which allows synonyms
 *    and judges the answer with an explanation;
 *  - "I don't know" shows the meaning and moves on;
 *  - "Say it" accepts pronunciation of the word itself or of any meaning.
 */
class FlashcardsViewModel(private val container: AppContainer) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val _uiState = MutableStateFlow(FlashcardUiState())
    val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

    private var languageKey = ""
    private var advanceJob: Job? = null
    private var judgeJob: Job? = null

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            val config = container.settings.current()
            languageKey = config.let {
                VocabRepository.languageCode(it.language) ?: it.language.lowercase()
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

            // Enrich words that have no multi-meaning data yet (either brand
            // new or recorded by an older app version with a single meaning).
            val missing = tracked.filter { it.meanings.isEmpty() }
            if (missing.isNotEmpty()) {
                container.ollama.chatOnce(
                    config.model,
                    translateMessages(config.language, config.helpLanguage, missing.map { it.word }),
                    expectJson = true,
                ).fold(
                    onSuccess = { text -> storeTranslations(missing, text) },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                error = "Meanings could not be fetched (${e.message}); " +
                                    "cards work with reveal only.",
                            )
                        }
                    },
                )
            }

            val fresh = container.progress.readKnownWords()
                .filter { it.language == languageKey }
                .shuffled()

            _uiState.update {
                it.copy(phase = FlashcardPhase.STUDY, words = fresh, languageName = config.language, helpLanguage = config.helpLanguage)
            }
        }
    }

    private suspend fun storeTranslations(missing: List<ProgressWord>, text: String) {
        val payload = extractJsonPayload(text)
        val listShape = runCatching { json.decodeFromString<TranslationsList>(payload) }.getOrNull()
        if (listShape != null) {
            listShape.translations.forEach { (word, meanings) ->
                container.progress.setMeanings(languageKey, word, meanings)
            }
            return
        }
        runCatching { json.decodeFromString<TranslationsSingle>(payload) }.getOrNull()
            ?.translations
            ?.forEach { (word, meaning) -> container.progress.setMeanings(languageKey, word, listOf(meaning)) }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value, error = null) }

    /**
     * Flow acceleration: while typing, an exact (or phrasing-tolerant) match
     * with any meaning accepts the card and advances automatically. Anything
     * else goes to the tutor model automatically after a short typing pause.
     */
    fun onTyped(value: String) {
        onInputChange(value)
        val state = _uiState.value
        val word = current() ?: return
        if (state.checking || state.revealed || value.isBlank()) {
            judgeJob?.cancel()
            return
        }

        if (SpeechSupport.matchesAnyMeaning(value, word.meaningList, word.acceptedMeanings)) {
            judgeJob?.cancel()
            acceptMeaning(word, value.trim(), note = null)
            return
        }

        judgeJob?.cancel()
        judgeJob = viewModelScope.launch {
            delay(TYPE_IDLE_MS)
            checkMeaning()
        }
    }

    private fun current() = _uiState.value.words.getOrNull(_uiState.value.index)

    /** Enter or Check: judge a non-matching answer with the tutor model. */
    fun checkMeaning() {
        judgeJob?.cancel()
        judgeJob = null
        val state = _uiState.value
        val word = current() ?: return
        val answer = state.input.trim()
        if (answer.isEmpty() || state.checking || state.revealed) return

        if (SpeechSupport.matchesAnyMeaning(answer, word.meaningList, word.acceptedMeanings)) {
            acceptMeaning(word, answer, note = null)
            return
        }

        if (word.meaningList.isEmpty()) {
            _uiState.update {
                it.copy(error = "No meaning stored for this word — 'I don't know' shows it.")
            }
            return
        }

        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            val config = container.settings.current()
            container.ollama.chatOnce(
                config.model,
                meaningCheckMessages(config.language, config.helpLanguage, word.word, word.meaningList, answer),
                expectJson = true,
            ).fold(
                onSuccess = { text ->
                    val check = runCatching {
                        json.decodeFromString<MeaningCheck>(extractJsonPayload(text))
                    }.getOrNull()
                    when {
                        check == null -> missMeaning(word, expectedText(word))
                        check.correct -> acceptMeaning(word, answer, note = check.explanation)
                        else -> missMeaning(word, check.explanation.ifBlank { expectedText(word) })
                    }
                },
                onFailure = { e ->
                    // Not the learner's fault — keep the card open, no penalty.
                    _uiState.update {
                        it.copy(
                            checking = false,
                            error = "Could not consult the tutor (${e.message}). " +
                                "Try again, or 'I don't know' shows the meaning.",
                        )
                    }
                },
            )
            _uiState.update { checking -> checking.copy(checking = false) }
        }
    }

    /** Learner said a word/meaning; accept either. */
    fun onSpoken(transcript: String?) {
        val word = current() ?: return
        if (transcript.isNullOrBlank()) {
            _uiState.update { it.copy(spokenMatch = false, spokenText = null) }
            return
        }
        val matchesWord = SpeechSupport.spokenMatches(word.word, transcript)
        val matchesMeaning = SpeechSupport.matchesAnyMeaning(
            transcript,
            word.meaningList,
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

    /** Learner gives up: counts as missed, shows all meanings. */
    fun giveUp() {
        val word = current() ?: return
        advanceJob?.cancel()
        advanceJob = null
        if (!_uiState.value.revealed) {
            viewModelScope.launch { container.progress.recordTrainMissed(languageKey, word.word) }
        }
        _uiState.update {
            it.copy(
                revealed = true,
                missed = it.missed + 1,
                outcome = "Meaning: ${word.meaningList.take(3).ifEmpty { listOf("(none)") }.joinToString(" / ")}",
                outcomeCorrect = false,
            )
        }
    }

    fun next() {
        advanceJob?.cancel()
        advanceJob = null
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

    private fun acceptMeaning(word: ProgressWord, typedOrSpoken: String, note: String?) {
        advanceJob?.cancel()
        val cardIndex = _uiState.value.index
        advanceJob = viewModelScope.launch {
            container.progress.recordTrainCorrect(languageKey, word.word)
            val normalizedTyped = typedOrSpoken.trim().lowercase()
            val matchesStored = word.meaningList.any { it.trim().lowercase() == normalizedTyped }
            if (!matchesStored && normalizedTyped.isNotBlank() && tokenize(normalizedTyped).isNotEmpty()) {
                container.progress.addAcceptedMeaning(languageKey, word.word, normalizedTyped)
            }
            delay(AUTO_ADVANCE_MS)
            if (_uiState.value.index == cardIndex) {
                next()
            }
        }
        _uiState.update {
            it.copy(
                outcome = "✓ Accepted — ${word.word} = ${typedOrSpoken.trim()}" +
                    (note?.takeIf { n -> n.isNotBlank() }?.let { n -> " ($n)" } ?: ""),
                outcomeCorrect = true,
                correct = it.correct + 1,
                revealed = true,
                error = null,
                spokenMatch = null,
            )
        }
    }

    private fun expectedText(word: ProgressWord): String =
        "expected: " + word.meaningList.take(2).ifEmpty { listOf("(none)") }.joinToString(" / ")

    private fun translateMessages(language: String, helpLanguage: String, words: List<String>) = listOf(
        ChatMessage(
            role = "system",
            content = "You translate single words from $language into $helpLanguage. " +
                "Reply ONLY with JSON of the shape {\"translations\": {\"word\": [\"meaning\"]}} " +
                "where each value is a JSON array with up to 3 common meanings in $helpLanguage, " +
                "the most common first. Use short meanings (1-2 words). Words like \"que\" " +
                "have several distinct senses, include them all (e.g. [\"that\", \"what\"]).",
        ),
        ChatMessage(
            role = "user",
            content = "Translate these words: ${Json.encodeToString(words)}",
        ),
    )

    private fun meaningCheckMessages(
        language: String,
        helpLanguage: String,
        word: String,
        meanings: List<String>,
        answer: String,
    ) = listOf(
        ChatMessage(
            role = "system",
            content = "You are a lenient vocabulary tutor for $language. The learner is " +
                "training flash cards. Target word: \"$word\" in $language. Known meanings: " +
                meaningsForPrompt(meanings) + ". The learner answered: \"$answer\". " +
                "Accept the answer if it is a correct, reasonable meaning of the word in " +
                "ANY of those senses - synonyms are fine (big/huge/large), and pronoun-prefixed " +
                "phrasing like \"I have\" for \"tengo\" is fine. Articles may be dropped " +
                "(\"from the\" ~ \"from\"). Reject only clearly wrong meanings. " +
                "Reply ONLY with JSON: {\"correct\": true, \"explanation\": \"1 very short " +
                "sentence in $helpLanguage about the answer\"}.",
        ),
        ChatMessage(role = "user", content = "Judge the answer now."),
    )

    private fun meaningsForPrompt(meanings: List<String>): String =
        meanings.joinToString(" / ").ifBlank { "(none)" }

    private companion object {
        const val AUTO_ADVANCE_MS = 900L
        const val TYPE_IDLE_MS = 1200L
    }
}
