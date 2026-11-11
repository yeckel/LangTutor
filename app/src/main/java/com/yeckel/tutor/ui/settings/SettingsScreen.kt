package com.yeckel.tutor.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.R
import com.yeckel.tutor.data.LanguageOption
import com.yeckel.tutor.data.SettingsRepository
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text(stringResource(R.string.settings_ollama_url)) },
                supportingText = {
                    Text(
                        "Emulator: http://10.0.2.2:11434 · " +
                            "On your phone: http://<computer-ip>:11434",
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text(stringResource(R.string.settings_api_key)) },
                supportingText = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PracticeLanguageDropdown(state, viewModel)

            AppLanguageDropdown()

            Text(
                stringResource(R.string.settings_vocab_level),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsRepository.VOCAB_LEVELS.forEach { level ->
                    FilterChip(
                        selected = state.vocabLevel == level,
                        onClick = { viewModel.onVocabLevelChange(level) },
                        label = { Text(level) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_vocab_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val tracked by viewModel.trackedWordCount.collectAsState()
            Text(
                stringResource(R.string.settings_tracked_words, tracked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HelpLanguageDropdown(state, viewModel)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_read_aloud))
                    Text(
                        stringResource(R.string.settings_read_aloud_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.readAloud, onCheckedChange = viewModel::toggleReadAloud)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_speak_words))
                    Text(
                        stringResource(R.string.settings_speak_words_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.speakSource, onCheckedChange = viewModel::toggleSpeakSource)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_speak_meanings))
                    Text(
                        stringResource(R.string.settings_speak_meanings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.speakMeanings, onCheckedChange = viewModel::toggleSpeakMeanings)
            }

            ModelDropdown(state, viewModel)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save))
            }

            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Practice-language dropdown: the language the tutor speaks and teaches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeLanguageDropdown(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val stored = state.language
    val currentDisplay = if (stored.isBlank()) "" else practiceLanguageDisplay(stored)

    // Keep a previously typed custom value visible and selectable.
    val customOptions = if (
        stored.isNotBlank() &&
        SettingsRepository.LEARNABLE_LANGUAGES.none { it.native == currentDisplay }
    ) {
        listOf(LanguageOption("", currentDisplay, stored))
    } else {
        emptyList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentDisplay,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_practice_language)) },
            supportingText = { Text(stringResource(R.string.settings_practice_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (SettingsRepository.LEARNABLE_LANGUAGES + customOptions).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.native) },
                    trailingIcon = { if (option.native == currentDisplay) SelectedIcon() },
                    onClick = {
                        viewModel.selectLanguage(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** App-language dropdown: System default + every language the UI is translated into. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLanguageDropdown() {
    var expanded by remember { mutableStateOf(false) }
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .split(",")
        .firstOrNull()
        ?.substringBefore("-")
        .orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = appLanguageDisplay(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_app_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_app_language_system)) },
                trailingIcon = { if (currentTag.isBlank()) SelectedIcon() },
                onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    expanded = false
                },
            )
            SettingsRepository.SUPPORTED_LANGUAGES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.native) },
                    trailingIcon = { if (option.code == currentTag) SelectedIcon() },
                    onClick = {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(option.code),
                        )
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Help-language dropdown: the language glosses and tutor notes are written in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpLanguageDropdown(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val stored = state.helpLanguage
    val currentDisplay = if (stored.isBlank()) "" else helpLanguageDisplay(stored)

    // Keep a previously typed custom value visible and selectable.
    val customOptions = if (
        stored.isNotBlank() &&
        SettingsRepository.SUPPORTED_LANGUAGES.none { it.native == currentDisplay }
    ) {
        listOf(LanguageOption("", currentDisplay, stored))
    } else {
        emptyList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentDisplay,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_help_language)) },
            supportingText = { Text(stringResource(R.string.settings_help_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (SettingsRepository.SUPPORTED_LANGUAGES + customOptions).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.native) },
                    trailingIcon = { if (option.native == currentDisplay) SelectedIcon() },
                    onClick = {
                        viewModel.selectHelpLanguage(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Model combo box: shows the server's models in a dropdown, still allows typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }

    // Load the server's model list the first time the menu opens, so the
    // user sees the real options instead of an empty field.
    LaunchedEffect(expanded) {
        if (expanded && state.models.isEmpty() && !state.loadingModels) {
            viewModel.refreshModels()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = state.model,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_model)) },
            supportingText = { Text(stringResource(R.string.settings_model_hint)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (state.loadingModels) {
                                R.string.settings_fetching_models
                            } else {
                                R.string.settings_fetch_models
                            },
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = { if (!state.loadingModels) viewModel.refreshModels() },
            )
            when {
                state.loadingModels ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_fetching_models)) },
                        onClick = {},
                        enabled = false,
                    )

                state.models.isEmpty() ->
                    DropdownMenuItem(
                        text = { Text(state.message ?: "No models found on the server") },
                        onClick = {},
                        enabled = false,
                    )

                else ->
                    state.models.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            trailingIcon = { if (name == state.model) SelectedIcon() },
                            onClick = {
                                viewModel.pickModel(name)
                                expanded = false
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun SelectedIcon() {
    Icon(Icons.Filled.Check, contentDescription = null)
}

@Composable
private fun appLanguageDisplay(): String {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when {
        tags.isBlank() -> stringResource(R.string.settings_app_language_system)
        else -> {
            val tag = tags.split(",")[0].substringBefore("-")
            SettingsRepository.SUPPORTED_LANGUAGES.firstOrNull { it.code == tag }?.native ?: tags
        }
    }
}

/** Stored help-language value ("Czech", "čeština"…) shown as a native name. */
private fun helpLanguageDisplay(stored: String): String {
    val supported = SettingsRepository.SUPPORTED_LANGUAGES
    supported.firstOrNull { it.value.equals(stored.trim(), ignoreCase = true) }
        ?.let { return it.native }
    VocabRepository.languageCode(stored)?.let { code ->
        supported.firstOrNull { it.code == code }?.let { return it.native }
    }
    return stored // custom value typed before the dropdown existed
}

/** Stored practice-language value ("Spanish", "es"…) shown as a native name. */
private fun practiceLanguageDisplay(stored: String): String {
    val supported = SettingsRepository.LEARNABLE_LANGUAGES
    supported.firstOrNull { it.value.equals(stored.trim(), ignoreCase = true) }
        ?.let { return it.native }
    VocabRepository.languageCode(stored)?.let { code ->
        supported.firstOrNull { it.code == code }?.let { return it.native }
    }
    return stored // custom value typed before the dropdown existed
}
