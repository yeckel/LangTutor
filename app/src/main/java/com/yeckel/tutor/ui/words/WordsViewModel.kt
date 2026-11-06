package com.yeckel.tutor.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.ProgressWord
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
        WordsUiState(
            words = words
                .filter { it.language == key }
                .sortedWith(
                    compareByDescending<ProgressWord> { it.isKnown }
                        .thenBy { it.word },
                ),
            languageKey = key,
            languageName = config.language,
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
}
