package com.yeckel.tutor.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.LevelProgression
import com.yeckel.tutor.data.ProgressWord
import com.yeckel.tutor.data.TierProgress
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.data.isKnown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordsUiState(
    val words: List<ProgressWord> = emptyList(),
    val languageKey: String = "",
    val languageName: String = "",
    val vocabLevel: String = "",
    val tiers: List<TierProgress> = emptyList(),
    val unlockable: TierProgress? = null,
)

/** List, edit and train the learner's tracked words (per practice language). */
class WordsViewModel(private val container: AppContainer) : ViewModel() {

    private val _newWord = MutableStateFlow("")
    val newWordText: StateFlow<String> = _newWord.asStateFlow()

    val uiState: StateFlow<WordsUiState> = combine(
        container.progress.knownWords,
        container.settings.config,
    ) { words, config ->
        val key = VocabRepository.languageCode(config.language) ?: config.language.lowercase()
        val filtered = words.filter { it.language == key }
        val tiers = LevelProgression.tierStats(key, words, container.vocab)
        WordsUiState(
            words = filtered
                .sortedWith(
                    compareByDescending<ProgressWord> { it.isKnown }
                        .thenBy { it.word },
                ),
            languageKey = key,
            languageName = config.language,
            vocabLevel = config.vocabLevel,
            tiers = tiers,
            unlockable = LevelProgression.nextUnlockable(config.vocabLevel.ifBlank { "A1" }, tiers),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WordsUiState())

    private fun languageKey(): String = uiState.value.languageKey

    fun onNewWordChange(value: String) {
        _newWord.value = value
    }

    fun addWord() {
        val word = _newWord.value.trim()
        if (word.isEmpty()) return
        viewModelScope.launch {
            container.progress.addWord(languageKey(), word, known = false)
            _newWord.value = ""
        }
    }

    fun toggleKnown(word: ProgressWord) {
        viewModelScope.launch {
            container.progress.setKnown(languageKey(), word.word, known = !word.isKnown)
        }
    }

    fun delete(word: ProgressWord) {
        viewModelScope.launch {
            container.progress.removeWord(languageKey(), word.word)
        }
    }

    /** Unlocks the next tier once the current one is complete. */
    fun unlockNext() {
        val state = uiState.value
        val next = state.unlockable ?: return
        viewModelScope.launch {
            val config = container.settings.current()
            container.settings.save(config.copy(vocabLevel = next.level))
        }
    }
}
