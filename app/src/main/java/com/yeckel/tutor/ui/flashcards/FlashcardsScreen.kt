package com.yeckel.tutor.ui.flashcards

import android.Manifest
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.data.SpeechSupport
import com.yeckel.tutor.data.VocabRepository
import com.yeckel.tutor.data.meaningList
import com.yeckel.tutor.rememberAppContainer

private val placeholderMeaningHint = "Meaning in English"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: FlashcardsViewModel = viewModel { FlashcardsViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingSpeak by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val texts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        viewModel.onSpoken(texts?.firstOrNull())
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true && pendingSpeak) {
            pendingSpeak = false
            startSpeech(context, state.languageName, speechLauncher)
        }
    }

    fun onSayIt() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) {
            startSpeech(context, state.languageName, speechLauncher)
        } else {
            pendingSpeak = true
            micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.phase == FlashcardPhase.STUDY && state.words.isNotEmpty()) {
                            "Flash cards · ${state.index + 1}/${state.words.size}"
                        } else {
                            "Flash cards"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.phase == FlashcardPhase.LOADING -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Fetching meanings…", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.phase == FlashcardPhase.RESULT -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Training done", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "You knew ${state.correct} of ${state.words.size} cards.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                }
            }

            else -> {
                val word = state.words.getOrNull(state.index)
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    word?.let { current ->
                        Text(
                            current.word,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )

                        if (state.revealed) {
                            Text(
                                word.meaningList.take(3).ifEmpty { listOf("(none)") }.joinToString(" / "),
                                style = MaterialTheme.typography.headlineSmall,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                            )
                        }

                        state.outcome?.let { outcome ->
                            Text(
                                outcome,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (state.outcomeCorrect == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                textAlign = TextAlign.Center,
                            )
                        }

                        if (!state.revealed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = state.input,
                                    onValueChange = viewModel::onTyped,
                                    label = { Text("Meaning in ${state.helpLanguage.ifBlank { "English" }}") },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onDone = { viewModel.checkMeaning() },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = viewModel::checkMeaning,
                                    enabled = state.input.isNotBlank() && !state.checking,
                                ) {
                                    Text("Check")
                                }
                            }

                            if (state.checking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text("Asking the tutor…", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            ) {
                                Button(onClick = { onSayIt() }) {
                                    Text("Say it")
                                }
                                TextButton(onClick = viewModel::giveUp) {
                                    Text("I don't know")
                                }
                            }

                            state.spokenText?.let { spoken ->
                                Text(
                                    "Heard: “$spoken”" + when (state.spokenMatch) {
                                        true -> " ✓"
                                        false -> " — not quite, try again"
                                        null -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        state.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }

                        if (state.revealed) {
                            Button(
                                onClick = viewModel::next,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (state.index + 1 >= state.words.size) "Finish" else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Launches the system speech recognizer in the practice language. */
private fun startSpeech(
    context: Context,
    languageName: String,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
) {
    val languageKey = VocabRepository.languageCode(languageName) ?: ""
    val locale = SpeechSupport.localeFor(languageKey)?.toString() ?: "en-US"

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the word")
    }
    runCatching { launcher.launch(intent) }
}
