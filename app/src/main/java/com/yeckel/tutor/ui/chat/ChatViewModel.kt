package com.yeckel.tutor.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.ChatMessage
import com.yeckel.tutor.data.StreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** role is "user" or "assistant"; the system message is kept separately. */
data class UiMessage(
    val role: String,
    val content: String,
)

/** Comprehension feedback from the learner; nudges the tutor's difficulty. */
enum class Difficulty {
    TOO_HARD,
    JUST_RIGHT,
    TOO_EASY,
}

data class ChatUiState(
    val articleTitle: String = "",
    val vocabLabel: String = "",
    val difficulty: Difficulty = Difficulty.JUST_RIGHT,
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Runs the tutoring conversation with vocabulary control:
 * the active known-word list (per level) is embedded in the system prompt and
 * comprehension feedback nudges the difficulty between requests.
 * Article context + the last [MAX_HISTORY] turns are sent on every request.
 */
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var systemMessage: ChatMessage? = null
    private var history: MutableList<ChatMessage> = mutableListOf()
    private var loadedArticleUrl: String? = null

    // Prompt inputs, cached when the conversation starts so the prompt can be
    // rebuilt on the fly (e.g. when the learner reports the difficulty).
    private var article: Article? = null
    private var language: String = ""
    private var vocabLevel: String = ""
    private var vocabWords: List<String> = emptyList()

    /**
     * Called when the chat screen opens. (Re)starts the conversation when the
     * article changed since last time.
     */
    fun ensureStarted(article: Article?) {
        if (article == null || loadedArticleUrl == article.url) return

        loadedArticleUrl = article.url
        history = mutableListOf()
        systemMessage = null

        _uiState.value = ChatUiState(articleTitle = article.title)

        viewModelScope.launch {
            val config = container.settings.current()
            this@ChatViewModel.article = article
            this@ChatViewModel.language = config.language
            this@ChatViewModel.vocabLevel = config.vocabLevel
            this@ChatViewModel.vocabWords = container.vocab.wordsFor(config.language, config.vocabLevel)

            systemMessage = buildSystemPrompt(Difficulty.JUST_RIGHT)

            _uiState.update {
                it.copy(
                    vocabLabel = if (vocabWords.isEmpty()) {
                        "simple vocabulary"
                    } else {
                        "$vocabLevel vocabulary · ${config.language}"
                    },
                )
            }

            history.add(
                ChatMessage(
                    role = "user",
                    content = "Begin the conversation now: greet the user briefly and ask your first question about the article.",
                ),
            )
            _uiState.update { it.copy(busy = true) }
            generate()
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value, error = null) }

    fun onDifficultyChange(newDifficulty: Difficulty) {
        if (_uiState.value.busy) {
            return
        }
        systemMessage = buildSystemPrompt(newDifficulty)
        _uiState.update { it.copy(difficulty = newDifficulty, error = null) }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.busy) return

        history.add(ChatMessage(role = "user", content = text))
        _uiState.update {
            it.copy(input = "", messages = it.messages + UiMessage("user", text), busy = true)
        }

        viewModelScope.launch { generate() }
    }

    private suspend fun generate() {
        val system = systemMessage ?: return
        val config = container.settings.current()

        if (config.model.isBlank()) {
            _uiState.update {
                it.copy(busy = false, error = "No model selected. Open Settings, fetch the model list and pick one.")
            }
            return
        }

        val request = listOf(system) + history.takeLast(MAX_HISTORY)

        _uiState.update { it.copy(messages = it.messages + UiMessage("assistant", "")) }

        val buffer = StringBuilder()
        try {
            container.ollama.chatStream(config.model, request).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        buffer.append(event.text)
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            messages[messages.lastIndex] = UiMessage("assistant", buffer.toString())
                            state.copy(messages = messages, error = null)
                        }
                    }

                    is StreamEvent.Failure -> _uiState.update { it.copy(error = event.message) }
                    StreamEvent.Done -> Unit
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message ?: "The request failed") }
        } finally {
            if (buffer.isNotEmpty()) {
                history.add(ChatMessage(role = "assistant", content = buffer.toString()))
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun buildSystemPrompt(difficulty: Difficulty): ChatMessage {
        val a = article ?: return ChatMessage("system", "You are a friendly, encouraging tutor.")
        return ChatMessage(
            role = "system",
            content = tutorPrompt(a, language, vocabWords, difficulty),
        )
    }

    private companion object {
        const val MAX_HISTORY = 12
    }
}

private fun tutorPrompt(
    article: Article,
    language: String,
    vocabWords: List<String>,
    difficulty: Difficulty,
): String {
    val vocabRules = if (vocabWords.isNotEmpty()) {
        """
        Known-vocabulary control.
        The learner knows the words in this list:
        ${vocabWords.joinToString(", ")}

        Vocabulary rules:
        - Write mostly with words from this list, plus basic grammar words you are sure the learner knows.
        - Introduce at most 1-2 NEW words per message that are not in the list.
        - A new word is allowed only if its meaning is clear from the surrounding context, or if you add a short gloss right after it in round brackets, written in English, e.g. el abrigo (coat).
        - Prefer rephrasing with known words to difficult vocabulary.
        - Reuse recently introduced new words soon, so the learner can absorb them.
        """.trimIndent()
    } else {
        """
        Vocabulary control: the exact known-word list is unavailable for this language.
        Use very simple, high-frequency vocabulary suitable for a beginner (roughly A1 level),
        keep sentences short, and gloss any harder word in round brackets with its English meaning.
        """.trimIndent()
    }

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

    return """
        You are a friendly language tutor. The user is a learner of $language.

        Rules:
        - Write every reply in $language only, with vocabulary an intermediate learner can follow.
        - Never switch to English, even if the user writes in English; model correct, simple $language instead.
        - Topic of this conversation: the article below.
        - Keep replies short (2-4 sentences).
        - End every reply with exactly ONE question that continues the conversation about the article.
        - When the user writes an answer: if it contains mistakes (grammar, spelling or word choice), first add a short correction block. Start it with the $language word for "Correction" (in Spanish: "Corrección"), then list up to three fixes in the form "wrong -> right", each with a one-line explanation in $language. After the corrections, respond to the content of what the user said and ask your next question.
        - Be encouraging. Praise good phrases occasionally.

        $vocabRules

        Adaptive difficulty: $difficultyRules

        Article: "${article.title}"

        ${article.text}
    """.trim() + "\n"
}
