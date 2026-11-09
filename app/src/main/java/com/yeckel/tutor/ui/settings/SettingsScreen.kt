package com.yeckel.tutor.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.yeckel.tutor.data.SettingsRepository
import com.yeckel.tutor.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    var showAppLanguageDialog by remember { mutableStateOf(false) }

    val appLanguageOptions = listOf(
        "system" to stringResource(R.string.settings_app_language_system),
        "en" to "English",
        "cs" to "Čeština",
        "es" to "Español",
        "de" to "Deutsch",
        "pl" to "Polski",
        "sk" to "Slovenčina",
    )
    val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    if (showAppLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showAppLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_app_language)) },
            text = {
                Column {
                    appLanguageOptions.forEach { (tag, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (tag == "system") {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(tag)
                                        },
                                    )
                                    showAppLanguageDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == currentTags.split(",")[0].substringBefore("-") || (tag == "system" && currentTags.isBlank()), onClick = null)
                            Text(displayName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppLanguageDialog = false }) {
                    Text(stringResource(R.string.common_back))
                }
            },
        )
    }

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

            OutlinedTextField(
                value = state.language,
                onValueChange = viewModel::onLanguageChange,
                label = { Text(stringResource(R.string.settings_practice_language)) },
                supportingText = { Text(stringResource(R.string.settings_practice_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAppLanguageDialog = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(stringResource(R.string.settings_app_language))
                    Text(
                        appLanguageDisplay(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Vocabulary level",
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

            OutlinedTextField(
                value = state.helpLanguage,
                onValueChange = viewModel::onHelpLanguageChange,
                label = { Text(stringResource(R.string.settings_help_language)) },
                supportingText = { Text(stringResource(R.string.settings_help_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::selectModel,
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = viewModel::refreshModels,
                enabled = !state.loadingModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.loadingModels) stringResource(R.string.settings_fetching_models) else stringResource(R.string.settings_fetch_models))
            }

            state.models.forEach { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectModel(name) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = name == state.model,
                        onClick = null,
                    )
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                }
            }

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

@Composable
private fun appLanguageDisplay(): String {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val options = listOf(
        "en" to "English",
        "cs" to "Čeština",
        "es" to "Español",
        "de" to "Deutsch",
        "pl" to "Polski",
        "sk" to "Slovenčina",
    )
    return when {
        tags.isBlank() -> stringResource(R.string.settings_app_language_system)
        else -> {
            val tag = tags.split(",")[0].substringBefore("-")
            options.firstOrNull { it.first == tag }?.second ?: tags
        }
    }
}
