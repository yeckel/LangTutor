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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

private val placeholderMeaningHint = "Meaning in English"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: FlashcardsViewModel = viewModel { FlashcardsViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingSpeak by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

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

    // TTS warmup so auto-speech is ready by the first card.
    LaunchedEffect(Unit) { container.tts.warmup() }
    DisposableEffect(Unit) { onDispose { container.tts.stop() } }

    // In-app recognizer (no dialog); fall back to the intent dialog otherwise.
    val recognizer = if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
        remember { android.speech.SpeechRecognizer.createSpeechRecognizer(context) }
    } else {
        remember { null }
    }

    var silentRounds by remember { mutableStateOf(0) }
    fun startListen(tag: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the word")
        }
        val rec = recognizer
        if (rec != null) {
            rec.startListening(intent)
        } else {
            runCatching { speechLauncher.launch(intent) }.onFailure { viewModel.onSpeechUnavailable() }
        }
    }

    DisposableEffect(Unit) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                silentRounds = 0
                listening = true
            }

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onBeginningOfSpeech() {}

            override fun onError(error: Int) {
                if (error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY) return
                // Silence/no-speech during auto-listen: retry once, then wait for manual input.
                if (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                    error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    listening = false
                    if (silentRounds < 1 && viewModel.uiState.value.autoListen && !viewModel.uiState.value.revealed) {
                        silentRounds += 1
                        mainHandler.postDelayed({
                            val st = viewModel.uiState.value
                            if (!st.revealed && st.autoListen) {
                                SpeechSupport.localeFor(st.languageKey)?.toLanguageTag()?.let { startListen(it) }
                            }
                        }, 700)
                    }
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val best = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                viewModel.onSpoken(best)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        onDispose {
            recognizer?.run { stopListening(); destroy() }
        }
    }


    // The card cycle for spoken practice: speak the word (if switched on), then
    // (when auto-listen is on) start listening once the speech finished.
    LaunchedEffect(
        state.index, state.phase, state.revealed, state.autoListen, state.speakSource, state.paused,
    ) {
        if (state.phase != FlashcardPhase.STUDY || state.revealed || !state.autoListen || state.paused) {
            recognizer?.stopListening()
            return@LaunchedEffect
        }
        val word = state.words.getOrNull(state.index) ?: return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect // the Say-it button handles the permission flow

        val tag = SpeechSupport.localeFor(state.languageKey)?.toLanguageTag() ?: return@LaunchedEffect
        if (state.speakSource && container.tts.isReady) {
            container.tts.speak(word.word, tag, flush = true) { startListen(tag) }
        } else {
            delay(600)
            startListen(tag)
        }
    }

    // Stop auto-listening once an answer exists.
    LaunchedEffect(state.spokenMatch, state.revealed) {
        if (state.revealed) recognizer?.stopListening()
    }

    // Auto-speak the source word on every new card when auto-listen is off
    // (with auto-listen the orchestrator above does the speaking).
    LaunchedEffect(state.index, state.phase, state.paused) {
        if (state.phase == FlashcardPhase.STUDY && state.speakSource && !state.revealed && !state.autoListen && !state.paused) {
            val word = state.words.getOrNull(state.index) ?: return@LaunchedEffect
            SpeechSupport.localeFor(state.languageKey)?.toLanguageTag()?.let { tag ->
                container.tts.speak(word.word, tag)
            }
        }
    }

    // Auto-speak the meaning when it is revealed.
    LaunchedEffect(state.index, state.revealed, state.paused) {
        if (state.phase == FlashcardPhase.STUDY && state.revealed && state.speakMeanings && !state.paused) {
            val word = state.words.getOrNull(state.index) ?: return@LaunchedEffect
            val meaning = word.meaningList.firstOrNull() ?: return@LaunchedEffect
            val tag = SpeechSupport.localeFor(
                VocabRepository.languageCode(state.helpLanguage) ?: state.languageKey,
            )?.toLanguageTag() ?: return@LaunchedEffect
            container.tts.speak(meaning, tag, flush = false)
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
                actions = {
                    TextButton(onClick = viewModel::togglePause, enabled = state.phase == FlashcardPhase.STUDY) {
                        Text(if (state.paused) "▶ Resume" else "⏸ Pause")
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
                        if (listening && !state.paused) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PulsingDot()
                                Text(
                                    "Listening…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (!state.revealed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("word", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = state.speakSource, onCheckedChange = viewModel::toggleSpeakSource)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("meaning", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = state.speakMeanings, onCheckedChange = viewModel::toggleSpeakMeanings)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("listen", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = state.autoListen, onCheckedChange = viewModel::toggleAutoListen)
                                }
                            }
                        } else {
                            Spacer(Modifier.height(70.dp))
                        }

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
