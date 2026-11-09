package com.yeckel.tutor.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.R
import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.ChatMessage
import com.yeckel.tutor.data.Difficulty
import com.yeckel.tutor.data.LessonProgress
import com.yeckel.tutor.data.LessonTurn
import com.yeckel.tutor.data.StreamEvent
import com.yeckel.tutor.data.SpeechSupport
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.data.tokenize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** role is "user" or "assistant"; the system message is built per request. */
data class UiMessage(
    val role: String,
    val content: String,
)

data class ChatUiState(
    val articleTitle: String = "",
    val vocabLabel: String = "",
    val difficulty: Difficulty = Difficulty.JUST_RIGHT,
    val readAloud: Boolean = false,
    val languageKey: String = "",
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val continuing: Boolean = false,
    val error: String? = null,
    // Voice conversation mode (talk <-> listen <-> reply loop).
    val voice: Boolean = false,
    val voiceListening: Boolean = false,
    /** True while the reply's read-aloud is still playing (mic must stay closed). */
    val speakingReply: Boolean = false,
    /** Bumped when the reply was fully spoken so the mic can reopen. */
    val voiceTurn: Long = 0,
)

/**
 * Runs the tutoring conversation with vocabulary control and progress
 * tracking:
 *  - the active known-word list (per level) is embedded in the system prompt,
 *    extended with words the learner already used (progress store);
 *  - every visible turn is recorded so the lesson survives app restarts;
 *  - comprehension feedback nudges the difficulty between requests.
 */
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var loadedArticleUrl: String? = null

    // Prompt inputs, cached so the system message can be rebuilt per request.
    private var lessonId: Long = 0
    private var article: Article? = null
    private var language: String = ""
    private var vocabLevel: String = ""
    private var vocabWords: List<String> = emptyList()
    private var languageKey: String = ""
    private var speakBuffer: StringBuilder = StringBuilder()
    private var firstUtteranceOfReply = false
    private var history: MutableList<ChatMessage> = mutableListOf()
    /**
     * TTS-side bookkeeping for the voice loop: every queued read-aloud
     * utterance increments [spokenPending] and decrements it in its done
     * callback. The mic may only re-open when [spokenPending] reaches zero
     * AFTER the reply finished streaming — otherwise the mic would open while
     * the app is still speaking and hear itself (endless echo loop).
     */
    private var spokenPending = 0
    private var reopenAfterSpeech = false

    /**
     * Called when the chat screen opens with a freshly loaded article:
     * starts a new, recorded lesson.
     */
    fun ensureStarted(article: Article?) {
        if (article == null || loadedArticleUrl == article.url) return
        viewModelScope.launch { startLessonInternal(article) }
    }

    /** Called when the chat screen opens via "Continue last conversation". */
    fun continueLast() {
        viewModelScope.launch {
            val lesson = container.progress.readLesson() ?: return@launch
            if (lesson.url == loadedArticleUrl) return@launch // already showing it

            loadedArticleUrl = lesson.url
            article = Article(lesson.url, lesson.title, lesson.text)
            lessonId = lesson.id

            val config = container.settings.current()
            language = lesson.language.ifBlank { config.language }
            vocabLevel = lesson.vocabLevel.ifBlank { config.vocabLevel }
            vocabWords = container.vocab.wordsFor(language, vocabLevel)
            languageKey = languageKeyFor(language)

            history = lesson.turns
                .map { ChatMessage(role = it.role, content = it.content) }
                .toMutableList()

            val difficulty = runCatching { Difficulty.valueOf(lesson.difficulty) }
                .getOrDefault(Difficulty.JUST_RIGHT)

            _uiState.value = ChatUiState(
                articleTitle = lesson.title,
                vocabLabel = vocabLabel(),
                difficulty = difficulty,
                readAloud = container.settings.current().readAloud,
                languageKey = languageKey,
                messages = history.map { UiMessage(it.role, it.content) },
                continuing = true,
            )
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value, error = null) }

    fun onDifficultyChange(newDifficulty: Difficulty) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(difficulty = newDifficulty, error = null) }
        viewModelScope.launch {
            container.progress.updateLessonDifficulty(lessonId, newDifficulty.name)
        }
    }

    /** Speaker toggle in the chat top bar; persists immediately. */
    fun toggleReadAloud(checked: Boolean) {
        _uiState.update { it.copy(readAloud = checked) }
        viewModelScope.launch {
            val config = container.settings.current()
            container.settings.save(config.copy(readAloud = checked))
        }
    }

    // ---- Voice conversation mode -------------------------------------------

    /**
     * Turns the spoken conversation on/off. Enabling it also force-enables
     * read-aloud (a voice conversation needs spoken replies) and persists it;
     * disabling silences any ongoing speech. The mic lifecycle itself lives in
     * the screen; here we keep the state and the auto-send.
     */
    fun toggleVoice(on: Boolean) {
        _uiState.update {
            it.copy(
                voice = on,
                voiceListening = false,
                speakingReply = if (on) it.speakingReply else false,
            )
        }
        if (!on) {
            spokenPending = 0
            reopenAfterSpeech = false
            container.tts.stop() // also cancels the pending reopen callback
            markSpeaking(false)
        }
        viewModelScope.launch {
            val config = container.settings.current()
            val newReadAloud = if (on) true else config.readAloud
            container.settings.save(config.copy(readAloud = newReadAloud))
            if (on) _uiState.update { it.copy(readAloud = true) }
        }
    }

    fun onVoiceListening(open: Boolean) {
        _uiState.update { it.copy(voiceListening = open) }
    }

    /** Live transcript while the mic is open: shows it in the input field. */
    fun onVoicePartial(text: String) {
        if (!_uiState.value.busy) _uiState.update { it.copy(input = text) }
    }

    /** Final recognized sentence: types it in and sends it automatically. */
    fun onVoiceFinal(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _uiState.value.busy) return
        _uiState.update { it.copy(input = "") }
        sendInternal(clean)
    }

    fun send() {
        sendInternal(_uiState.value.input)
    }

    private fun sendInternal(text0: String) {
        val text = text0.trim()
        if (text.isEmpty() || _uiState.value.busy) return

        // Stop any ongoing reading so the new exchange starts in silence.
        container.tts.stop()
        markSpeaking(false)
        speakBuffer = StringBuilder()
        spokenPending = 0
        reopenAfterSpeech = false

        history.add(ChatMessage(role = "user", content = text))
        _uiState.update {
            it.copy(input = "", messages = it.messages + UiMessage("user", text), busy = true)
        }
        recordTurn("user", text)

        viewModelScope.launch { generate() }
    }

    private suspend fun startLessonInternal(article: Article) {
        val config = container.settings.current()
        loadedArticleUrl = article.url
        this.article = article

        language = config.language
        vocabLevel = config.vocabLevel
        vocabWords = container.vocab.wordsFor(language, vocabLevel)
        languageKey = languageKeyFor(language)

        val lesson = LessonProgress(
            id = System.currentTimeMillis(),
            url = article.url,
            title = article.title,
            text = article.text,
            language = language,
            vocabLevel = vocabLevel,
            difficulty = Difficulty.JUST_RIGHT.name,
            turns = emptyList(),
            updatedAt = System.currentTimeMillis(),
        )
        lessonId = lesson.id
        container.progress.startLesson(lesson)

        history = mutableListOf(
            ChatMessage(
                role = "user",
                content = "Begin the conversation now: greet the user briefly and ask your first question about the article.",
            ),
        )
        // The hidden starter stays in memory only; visible turns get recorded.
        _uiState.value = ChatUiState(
            articleTitle = article.title,
            vocabLabel = vocabLabel(),
            difficulty = Difficulty.JUST_RIGHT,
            readAloud = config.readAloud,
            languageKey = languageKey,
        )
        _uiState.update { it.copy(busy = true) }
        generate()
    }

    private suspend fun generate() {
        val config = container.settings.current()
        val currentArticle = article ?: return

        if (config.model.isBlank()) {
            _uiState.update {
                it.copy(busy = false, error = container.string(R.string.chat_error_model))
            }
            return
        }

        val progressWords = container.progress.readKnownWords()
            .filter { it.language == languageKey }
        val knownNow = progressWords
            .filter { it.usedByLearner >= 1 }
            .map { it.word }
            .takeLast(EXTRA_KNOWN_LIMIT)
        val taught = progressWords
            .filter { it.introducedByTutor >= 1 && it.usedByLearner == 0 }
            .map { it.word }
            .takeLast(TAUGHT_LIMIT)

        val system = ChatMessage(
            role = "system",
            content = tutorPrompt(
                article = currentArticle,
                language = language,
                helpLanguage = config.helpLanguage,
                vocabWords = vocabWords,
                difficulty = _uiState.value.difficulty,
                extraKnown = knownNow,
                beingTaught = taught,
            ),
        )
        val messagesForModel = listOf(system) + history.takeLast(MAX_HISTORY)

        _uiState.update { it.copy(messages = it.messages + UiMessage("assistant", "")) }

        val buffer = StringBuilder()
        speakBuffer = StringBuilder()
        spokenPending = 0
        reopenAfterSpeech = false
        firstUtteranceOfReply = _uiState.value.readAloud
        try {
            container.ollama.chatStream(config.model, messagesForModel).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        buffer.append(event.text)
                        speakProgressive(event.text)
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
            _uiState.update { it.copy(error = e.message ?: container.string(R.string.chat_error_request)) }
        } finally {
            val reply = buffer.toString()
            if (reply.isNotEmpty()) {
                speakRemaining()
                history.add(ChatMessage(role = "assistant", content = reply))
                recordTurn("assistant", reply)
                container.progress.recordAssistantMessage(languageKey, vocabWords.toSet(), reply)
            }
            _uiState.update { it.copy(busy = false) }
            // Voice mode: the mic re-opens when nothing is left to be spoken.
            // If read-aloud utterances are still queued, defer the reopen to
            // the moment the TTS queue drains (utterance done-callbacks).
            if (_uiState.value.voice) {
                if (!_uiState.value.readAloud || spokenPending == 0) {
                    markSpeaking(false)
                    _uiState.update { it.copy(voiceTurn = it.voiceTurn + 1) }
                } else {
                    reopenAfterSpeech = true
                }
            }
        }
    }

    private fun recordTurn(role: String, content: String) {
        val turn = LessonTurn(role, content, System.currentTimeMillis())
        viewModelScope.launch {
            container.progress.appendTurn(lessonId, turn)
        }
        if (role == "user") {
            viewModelScope.launch {
                container.progress.recordLearnerMessage(languageKey, vocabWords.toSet(), content)
            }
        }
    }

    private fun vocabLabel(): String =
        if (vocabWords.isEmpty()) {
            "simple vocabulary"
        } else {
            "$vocabLevel vocabulary · ${language.ifBlank { "?" }}"
        }

    private fun languageKeyFor(language: String): String =
        VocabRepository.languageCode(language) ?: language.lowercase()

    /** Queues one read-aloud utterance; decrements the pending counter on done. */
    private fun queueReadAloud(utterance: String, tag: String, flush: Boolean) {
        spokenPending += 1
        markSpeaking(true)
        container.tts.speak(utterance, tag, flush = flush) {
            spokenPending = (spokenPending - 1).coerceAtLeast(0)
            // The reopen fires exactly when the TTS queue drains and the
            // reply already finished streaming.
            if (spokenPending == 0) {
                markSpeaking(false)
                if (reopenAfterSpeech) {
                    reopenAfterSpeech = false
                    if (_uiState.value.voice) {
                        _uiState.update { it.copy(voiceTurn = it.voiceTurn + 1) }
                    }
                }
            }
        }
    }

    /** Mic must stay closed while the app speaks. */
    private fun markSpeaking(playing: Boolean) {
        if (_uiState.value.speakingReply != playing) {
            _uiState.update { it.copy(speakingReply = playing) }
        }
    }

    /** Progressive read-aloud: queue one utterance per finished sentence. */
    private fun speakProgressive(deltaText: String) {
        if (!_uiState.value.readAloud) return
        speakBuffer.append(deltaText)

        val text = speakBuffer.toString()
        val sentenceEnd = Regex("[.!?…](?=\\s|$)").find(text)
        if (sentenceEnd != null) {
            val cut = sentenceEnd.range.last + 1
            val utterance = text.take(cut).trim()
            speakBuffer = StringBuilder(text.substring(cut))
            if (utterance.isNotBlank()) {
                val tag = SpeechSupport.localeFor(languageKey)?.toLanguageTag() ?: return
                queueReadAloud(utterance, tag, flush = firstUtteranceOfReply)
                firstUtteranceOfReply = false
            }
        }
    }

    /** Speaks whatever sentence fragment is left when the reply ends. */
    private fun speakRemaining() {
        if (!_uiState.value.readAloud) return
        val rest = speakBuffer.toString().trim()
        speakBuffer = StringBuilder()
        if (rest.isNotBlank()) {
            val tag = SpeechSupport.localeFor(languageKey)?.toLanguageTag() ?: return
            queueReadAloud(rest, tag, flush = false)
        }
        firstUtteranceOfReply = false
    }

    private companion object {
        const val MAX_HISTORY = 12
        const val EXTRA_KNOWN_LIMIT = 150
        const val TAUGHT_LIMIT = 15
    }
}
