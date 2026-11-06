package com.yeckel.tutor.ui.placement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.ChatMessage
import com.yeckel.tutor.data.PlacementVerdict
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.data.clampVocabLevel
import com.yeckel.tutor.data.extractJsonPayload
import com.yeckel.tutor.ui.chat.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PlacementPhase { LOADING, ASKING, SCORING, RESULT }

data class PlacementUiState(
    val phase: PlacementPhase = PlacementPhase.LOADING,
    val questionIndex: Int = 0,
    val questions: List<String> = emptyList(),
    val transcript: List<UiMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val verdict: PlacementVerdict? = null,
    val applied: Boolean = false,
    val error: String? = null,
)

/**
 * A short placement chat: the model generates graded questions, the learner
 * answers, and the model produces a level verdict that gets applied to the
 * vocabulary level setting and merged into tracked known words.
 */
class PlacementViewModel(private val container: AppContainer) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(PlacementUiState())
    val uiState: StateFlow<PlacementUiState> = _uiState.asStateFlow()

    private var answers: MutableList<String> = mutableListOf()
    private var language = ""
    private var languageKey = ""

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            val config = container.settings.current()
            if (config.model.isBlank()) {
                _uiState.update {
                    it.copy(error = "No model selected. Pick one in Settings first.")
                }
                return@launch
            }
            language = config.language
            languageKey = VocabRepository.languageCode(config.language) ?: config.language.lowercase()

            _uiState.update { it.copy(phase = PlacementPhase.LOADING, error = null) }

            container.ollama.chatOnce(config.model, questionMessages(language)).fold(
                onSuccess = { text ->
                    val questions = parseQuestions(text)
                    if (questions.isEmpty()) {
                        _uiState.update {
                            it.copy(error = "The model returned no questions; try again or use another model.")
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                questions = questions,
                                phase = PlacementPhase.ASKING,
                                transcript = listOf(UiMessage("assistant", questions.first())),
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message ?: "Could not reach the Ollama server") }
                },
            )
        }
    }

    fun retry() = start()

    fun onInputChange(value: String) =
        _uiState.update { it.copy(input = value, error = null) }

    /** Submits the answer for the current question (or finishes the quiz). */
    fun answer() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() || state.busy) return
        if (state.phase != PlacementPhase.ASKING) return
        if (state.questionIndex >= state.questions.size) return

        answers.add(text)
        _uiState.update {
            it.copy(
                input = "",
                transcript = it.transcript + UiMessage("user", text),
            )
        }

        val nextIndex = state.questionIndex + 1
        if (nextIndex < state.questions.size) {
            _uiState.update {
                it.copy(
                    questionIndex = nextIndex,
                    transcript = it.transcript + UiMessage("assistant", it.questions[nextIndex]),
                )
            }
        } else {
            viewModelScope.launch { score() }
        }
    }

    private suspend fun score() {
        val state = _uiState.value
        val config = container.settings.current()

        _uiState.update { it.copy(phase = PlacementPhase.SCORING, busy = true) }

        val transcriptText = state.questions.zip(answers)
            .joinToString("\n\n") { (question, answer) -> "Q: $question\nA: $answer" }

        container.ollama.chatOnce(config.model, verdictMessages(language, transcriptText), expectJson = true)
            .fold(
                onSuccess = { text ->
                    val parsed = runCatching {
                        json.decodeFromString<PlacementVerdict>(extractJsonPayload(text))
                    }
                    if (parsed.isFailure) {
                        _uiState.update {
                            it.copy(busy = false, error = "Could not read the level verdict — try again.")
                        }
                        return
                    }
                    _uiState.update {
                        it.copy(
                            busy = false,
                            phase = PlacementPhase.RESULT,
                            verdict = parsed.getOrThrow(),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(busy = false, error = e.message ?: "Could not score the quiz")
                    }
                },
            )
    }

    /** Saves the estimated level into settings and merges the tracked words. */
    fun apply() {
        val verdict = _uiState.value.verdict ?: return
        viewModelScope.launch {
            val level = clampVocabLevel(verdict.level)
            val config = container.settings.current()
            container.settings.save(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model,
                language = config.language,
                vocabLevel = level,
                helpLanguage = config.helpLanguage,
            )
            for (word in verdict.knownWords.take(20)) {
                val clean = word.trim().lowercase()
                if (clean.length in 2..20) {
                    container.progress.addWord(languageKey, clean, known = true)
                }
            }
            _uiState.update { it.copy(applied = true) }
        }
    }

    private fun questionMessages(language: String) = listOf(
        ChatMessage(
            role = "system",
            content = "You are a language placement examiner. The user is learning $language. " +
                "Write exactly 8 short questions in $language with ascending difficulty: two very basic " +
                "vocabulary questions (A0), three everyday questions (A1), two intermediate questions (A2) " +
                "and two demanding questions (B1). Questions ask the user to produce language " +
                "(answer, complete or describe), not multiple choice. " +
                "Reply ONLY with a JSON array of 8 strings, without numbering.",
        ),
        ChatMessage(
            role = "user",
            content = "Generate the 8 placement questions now.",
        ),
    )

    private fun verdictMessages(language: String, transcript: String) = listOf(
        ChatMessage(
            role = "system",
            content = "You are a placement examiner for $language. From the transcript, estimate the " +
                "learner's overall level. Choose exactly one of: A0, A1, A2, B1. " +
                "Also list lowercase words of $language the learner clearly used correctly (at most 20). " +
                "Reply ONLY with JSON in this shape: {\"level\": \"A1\", \"knownWords\": [\"...\"], " +
                "\"reason\": \"1-2 short sentences in English explaining the estimate\"}.",
        ),
        ChatMessage(
            role = "user",
            content = "Placement transcript:\n\n$transcript\n\nGive your verdict.",
        ),
    )

    private fun parseQuestions(text: String): List<String> {
        val bare = extractJsonPayload(text)
        val fromJson = runCatching { json.decodeFromString<List<String>>(bare) }.getOrNull()
        if (fromJson != null) return fromJson.map { it.trim() }.filter { it.length > 5 }.take(8)

        return bare.lines()
            .map { it.replace(Regex("^[0-9]+[.)]\\s*"), "").trim() }
            .filter { it.length > 15 }
            .take(8)
    }
}
