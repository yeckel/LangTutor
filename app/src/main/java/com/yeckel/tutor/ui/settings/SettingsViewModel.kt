package com.yeckel.tutor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeckel.tutor.AppContainer
import com.yeckel.tutor.data.OllamaClient
import com.yeckel.tutor.data.SettingsRepository
import com.yeckel.tutor.data.TutorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val language: String = "",
    val vocabLevel: String = "",
    val helpLanguage: String = "",
    val readAloud: Boolean = false,
    val speakSource: Boolean = false,
    val speakMeanings: Boolean = false,
    val autoListen: Boolean = false,
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = container.settings.config.first()
            _uiState.update {
                it.copy(
                    baseUrl = config.baseUrl.ifBlank { SettingsRepository.DEFAULT_BASE_URL },
                    apiKey = config.apiKey,
                    model = config.model,
                    language = config.language.ifBlank { SettingsRepository.DEFAULT_LANGUAGE },
                    vocabLevel = config.vocabLevel.ifBlank { SettingsRepository.DEFAULT_VOCAB_LEVEL },
                    helpLanguage = config.helpLanguage.ifBlank { SettingsRepository.DEFAULT_HELP_LANGUAGE },
                    readAloud = config.readAloud,
                    speakSource = config.speakSource,
                    speakMeanings = config.speakMeanings,
                )
            }
        }
    }

    /** Words tracked from real chats (taught by the tutor, used by the learner). */
    val trackedWordCount: StateFlow<Int> = container.progress.knownWords
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onBaseUrlChange(value: String) =
        _uiState.update { it.copy(baseUrl = value, message = null) }

    fun onApiKeyChange(value: String) =
        _uiState.update { it.copy(apiKey = value, message = null) }

    /** Practice-language dropdown pick; persists immediately like the speak toggles. */
    fun selectLanguage(value: String) {
        _uiState.update { it.copy(language = value, message = null) }
        save()
    }

    /** Help-language dropdown pick; persists immediately like the speak toggles. */
    fun selectHelpLanguage(value: String) {
        _uiState.update { it.copy(helpLanguage = value, message = null) }
        save()
    }

    fun onVocabLevelChange(value: String) =
        _uiState.update { it.copy(vocabLevel = value, message = null) }

    /** Model-dropdown pick; persists immediately like the speak toggles. */
    fun pickModel(name: String) {
        _uiState.update { it.copy(model = name, message = null) }
        save()
    }

    fun refreshModels() {
        val current = _uiState.value
        if (current.loadingModels) return

        _uiState.update { it.copy(loadingModels = true, message = null) }

        viewModelScope.launch {
            // Test with the values as currently shown in the fields, so the
            // user can verify a new server URL without saving it first.
            container.ollama.listModels(
                baseUrlOverride = OllamaClient.normalizeBaseUrl(current.baseUrl),
                apiKeyOverride = current.apiKey,
            ).fold(
                onSuccess = { models ->
                    _uiState.update {
                        it.copy(
                            models = models,
                            loadingModels = false,
                            message = if (models.isEmpty()) {
                                "No models are installed on that server."
                            } else {
                                null
                            },
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            loadingModels = false,
                            message = e.message ?: "Could not reach the Ollama server",
                        )
                    }
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            container.settings.save(
                TutorConfig(
                    baseUrl = OllamaClient.normalizeBaseUrl(state.baseUrl),
                    apiKey = state.apiKey,
                    model = state.model,
                    language = state.language,
                    vocabLevel = state.vocabLevel,
                    helpLanguage = state.helpLanguage,
                    readAloud = state.readAloud,
                    speakSource = state.speakSource,
                    speakMeanings = state.speakMeanings,
                    autoListen = state.autoListen,
                ),
            )
            _uiState.update { it.copy(message = "Settings saved") }
        }
    }

    /** Speaker toggle for chat replies (top bar + settings). */
    fun toggleReadAloud(checked: Boolean) {
        _uiState.update { it.copy(readAloud = checked) }
        save()
    }

    /** Speak switches persist immediately when flipped. */
    fun toggleSpeakSource(checked: Boolean) {
        _uiState.update { it.copy(speakSource = checked) }
        save()
    }

    fun toggleSpeakMeanings(checked: Boolean) {
        _uiState.update { it.copy(speakMeanings = checked) }
        save()
    }
}
