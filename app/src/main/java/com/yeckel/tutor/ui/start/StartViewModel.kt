package com.yeckel.tutor.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.R
import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.LessonProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StartUiState(
    val url: String = "",
    val pastedText: String = "",
    val usePasted: Boolean = false,
    val article: Article? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class StartViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    /** The most recent recorded lesson, offered as "Continue last conversation". */
    val lastLesson: StateFlow<LessonProgress?> = container.progress.activeLesson
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onUrlChange(value: String) = _uiState.update { it.copy(url = value, error = null) }

    fun onPastedTextChange(value: String) =
        _uiState.update { it.copy(pastedText = value, error = null) }

    fun onTogglePasted(usePasted: Boolean) =
        _uiState.update { it.copy(usePasted = usePasted, error = null) }

    fun loadArticle() {
        val current = _uiState.value
        if (current.url.isBlank() || current.loading) return

        _uiState.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            container.articles.fetch(current.url)
                .fold(
                    onSuccess = { article ->
                        container.currentArticle = article
                        container.pendingContinue = false
                        _uiState.update { it.copy(article = article, loading = false) }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(loading = false, error = e.message ?: container.string(R.string.start_error_load))
                        }
                    },
                )
        }
    }

    fun usePastedArticle() {
        val text = _uiState.value.pastedText.trim()
        if (text.isEmpty()) return

        val title = text.lines().firstOrNull { it.isNotBlank() }?.take(80) ?: "Pasted text"
        val article = Article(url = "pasted:${title.hashCode()}", title = title, text = text)
        container.currentArticle = article
        container.pendingContinue = false
        _uiState.update { it.copy(article = article) }
    }

    /** Marks the next chat entry as a continuation of the recorded lesson. */
    fun continueLastLesson() {
        container.currentArticle = null
        container.pendingContinue = true
    }
}
