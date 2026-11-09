package com.yeckel.tutor.ui.chat

import android.Manifest
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.R
import com.yeckel.tutor.rememberAppContainer
import com.yeckel.tutor.ui.common.SendBar
import com.yeckel.tutor.data.Difficulty
import com.yeckel.tutor.data.SpeechSupport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val viewModel: ChatViewModel = viewModel { ChatViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (container.pendingContinue) {
            container.pendingContinue = false
            viewModel.continueLast()
        } else {
            viewModel.ensureStarted(container.currentArticle)
        }
    }

    // TTS ready for progressive reading; silenced when leaving the chat.
    LaunchedEffect(Unit) { container.tts.warmup() }
    DisposableEffect(Unit) { onDispose { container.tts.stop() } }

    // ---- Voice conversation mode -------------------------------------------
    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && !viewModel.uiState.value.voice) viewModel.toggleVoice(true)
        }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var listeningLocally by remember { androidx.compose.runtime.mutableStateOf(false) }
    val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
        remember { SpeechRecognizer.createSpeechRecognizer(context) }
    } else {
        remember { null }
    }

    fun micTag(): String =
        SpeechSupport.localeFor(state.languageKey)?.toLanguageTag() ?: "en-US"

    fun startVoiceListen() {
        val rec = recognizer ?: return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, micTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        viewModel.onVoiceListening(true)
        listeningLocally = true
        rec.startListening(intent)
    }

    fun stopVoiceListen(silent: Boolean = false) {
        recognizer?.stopListening()
        if (!silent) viewModel.onVoiceListening(false)
        listeningLocally = false
    }

    /** Reopens the mic when nothing was understood — conversation keeps going. */
    fun rescheduleVoiceListen(delayMs: Long) {
        mainHandler.postDelayed({
            val st = viewModel.uiState.value
            if (st.voice && !st.busy && !st.speakingReply && st.error == null &&
                !st.voicePaused && !st.voiceListening
            ) {
                startVoiceListen()
            }
        }, delayMs)
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}

            override fun onEndOfSpeech() {
                listeningLocally = false
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onBeginningOfSpeech() {}

            override fun onError(error: Int) {
                listeningLocally = false
                viewModel.onVoiceListening(false)
                when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Unit // the turn effects recover
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        rescheduleVoiceListen(1000L)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        rescheduleVoiceListen(1000L) // silence while the user thinks
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        viewModel.onInputChange("") // permission path handled by the button
                    else ->
                        rescheduleVoiceListen(1500L)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                listeningLocally = false
                viewModel.onVoiceListening(false)
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!best.isNullOrBlank()) viewModel.onVoiceFinal(best)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) viewModel.onVoicePartial(partial)
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        onDispose {
            recognizer?.run { stopListening(); destroy() }
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    // Mic must never be open while the model answers or while the reply is
    // spoken: otherwise the recognizer hears the TTS itself (echo loop).
    // Paused: stop everything until resume.
    LaunchedEffect(state.busy) {
        if (state.busy && state.voice) stopVoiceListen()
    }
    LaunchedEffect(state.speakingReply) {
        if (state.speakingReply && state.voice) stopVoiceListen()
    }
    LaunchedEffect(state.voicePaused) {
        if (state.voicePaused) stopVoiceListen()
    }

    // Reopen the mic when voice mode starts and after each spoken reply.
    LaunchedEffect(state.voice, state.voiceTurn, state.busy, state.error) {
        if (!state.voice || state.busy || state.error != null) return@LaunchedEffect
        if (state.voicePaused || listeningLocally) return@LaunchedEffect
        // Settle time: make sure the tail of the spoken reply is out of the
        // speaker before the mic opens (echo protection).
        kotlinx.coroutines.delay(600)
        val st = viewModel.uiState.value
        if (st.voice && !st.busy && !st.speakingReply && !st.voicePaused &&
            st.error == null && !listeningLocally
        ) {
            startVoiceListen()
        }
    }

    // Keep the newest text in view, also while streaming into the last bubble.
    val lastContentLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(key1 = state.messages.size, key2 = lastContentLength) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    // Explanation card for a tapped gloss ("el abrigo (coat)" spans).
    var glossSheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    var glossAdded by remember { mutableStateOf(false) }
    glossSheet?.let { (word, glossMeaning) ->
        ModalBottomSheet(onDismissRequest = { glossSheet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    word,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(glossMeaning, style = MaterialTheme.typography.bodyLarge)
                if (glossAdded) {
                    Text(
                        stringResource(R.string.gloss_added),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = {
                        glossAdded = true
                        viewModel.addGlossWord(word)
                    }) {
                        Text(stringResource(R.string.gloss_add))
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.articleTitle.ifBlank { "Tutor" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.vocabLabel.isNotBlank()) {
                            Text(
                                state.vocabLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleReadAloud(!state.readAloud) }) {
                        Text(if (state.readAloud) stringResource(R.string.chat_read_on) else stringResource(R.string.chat_read_off))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.difficulty == Difficulty.TOO_HARD,
                    onClick = { viewModel.onDifficultyChange(Difficulty.TOO_HARD) },
                    label = { Text(stringResource(R.string.chat_too_hard)) },
                )
                FilterChip(
                    selected = state.difficulty == Difficulty.JUST_RIGHT,
                    onClick = { viewModel.onDifficultyChange(Difficulty.JUST_RIGHT) },
                    label = { Text(stringResource(R.string.chat_just_right)) },
                )
                FilterChip(
                    selected = state.difficulty == Difficulty.TOO_EASY,
                    onClick = { viewModel.onDifficultyChange(Difficulty.TOO_EASY) },
                    label = { Text(stringResource(R.string.chat_too_easy)) },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.messages.isEmpty() && !state.busy) {
                    item {
                        Text(
                            stringResource(R.string.chat_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(count = state.messages.size) { index ->
                    Bubble(state.messages[index], onGlossTap = { word, gloss ->
                        glossAdded = false
                        glossSheet = word to gloss
                    })
                }
            }

            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    // Regenerate the last reply (drops the failed attempt).
                    TextButton(onClick = viewModel::retry) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

            // Voice mode state: listening / thinking / speaking.
            if (state.voice) {
                val pulse = rememberInfiniteTransition(label = "voice-pulse")
                val scale by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                    label = "voice-scale",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = when {
                        state.voiceListening -> stringResource(R.string.cards_listening)
                        state.speakingReply -> stringResource(R.string.chat_voice_speaking)
                        state.busy -> stringResource(R.string.chat_voice_thinking)
                        state.voicePaused -> stringResource(R.string.chat_voice_paused)
                        else -> stringResource(R.string.chat_voice_speaking)
                    }
                    Text(
                        "🎙",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = if (state.voiceListening) {
                            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                        } else {
                            Modifier
                        },
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            InputBar(
                input = state.input,
                busy = state.busy,
                voice = state.voice,
                paused = state.voicePaused,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                onMicToggle = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.toggleVoice(!state.voice)
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPauseToggle = {
                    viewModel.setVoicePaused(!state.voicePaused)
                },
            )
        }
    }
}

@Composable
private fun Bubble(
    message: UiMessage,
    onGlossTap: (word: String, gloss: String) -> Unit = { _, _ -> },
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (message.role == "assistant" && message.content.isEmpty()) {
                    Text("…", style = MaterialTheme.typography.bodyMedium)
                } else if (isUser) {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                } else {
                    GlossText(message.content, onGlossTap)
                }
            }
        }
    }
}

/** Matches glossed items like "el abrigo (coat)": up to 3 words + short gloss. */
private val glossPattern = Regex("(\\p{L}[\\p{L}’'-]*(?:\\s\\p{L}[\\p{L}’'-]*){0,2})\\s*\\(([^()]{1,40})\\)")

/** Chat text where glossed spans (word + parenthesized meaning) are tappable. */
@Composable
private fun GlossText(content: String, onGlossTap: (String, String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val layout = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val annotated = remember(content) {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(content)
        glossPattern.findAll(content).forEach { m ->
            val wordPart = m.groupValues[1].trim()
            val glossPart = m.groupValues[2].trim()
            // Corrections or parenthetical prose are not glosses: require a
            // short, clean gloss without digits.
            val isGloss =
                wordPart.split(" ").size <= 3 &&
                    glossPart.isNotBlank() &&
                    glossPart.none { it.isDigit() }
            if (isGloss) {
                builder.addStyle(
                    SpanStyle(color = primary, textDecoration = TextDecoration.Underline),
                    m.range.first,
                    m.range.last + 1,
                )
                builder.addStringAnnotation(
                    "gloss",
                    "$wordPart\u0000$glossPart",
                    m.range.first,
                    m.range.last + 1,
                )
            }
        }
        builder.toAnnotatedString()
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layout.value = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val l = layout.value ?: return@detectTapGestures
                val offset = l.getOffsetForPosition(position)
                val hit = annotated.getStringAnnotations("gloss", offset, offset).firstOrNull()
                if (hit != null) {
                    val parts = hit.item.split("\u0000")
                    if (parts.size == 2) onGlossTap(parts[0], parts[1])
                }
            }
        },
    )
}

@Composable
private fun InputBar(
    input: String,
    busy: Boolean,
    voice: Boolean,
    paused: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    onPauseToggle: () -> Unit,
) {
    SendBar(
        value = input,
        busy = busy,
        placeholder = stringResource(R.string.chat_answer_placeholder),
        onValueChange = onInputChange,
        onSend = onSend,
        micSlot = {
            if (voice) {
                IconButton(onClick = onPauseToggle) {
                    Text(
                        if (paused) "▶" else "⏸",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onMicToggle) {
                Text(
                    "🎙️",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (voice && !paused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )
}
