package com.yeckel.tutor.ui.chat

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.rememberAppContainer
import com.yeckel.tutor.ui.common.SendBar
import com.yeckel.tutor.data.Difficulty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
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

    // Keep the newest text in view, also while streaming into the last bubble.
    val lastContentLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(key1 = state.messages.size, key2 = lastContentLength) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
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
                    label = { Text("Too hard") },
                )
                FilterChip(
                    selected = state.difficulty == Difficulty.JUST_RIGHT,
                    onClick = { viewModel.onDifficultyChange(Difficulty.JUST_RIGHT) },
                    label = { Text("Just right") },
                )
                FilterChip(
                    selected = state.difficulty == Difficulty.TOO_EASY,
                    onClick = { viewModel.onDifficultyChange(Difficulty.TOO_EASY) },
                    label = { Text("Too easy") },
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
                            "Load a webpage on the start screen to begin a conversation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(count = state.messages.size) { index ->
                    Bubble(state.messages[index])
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            InputBar(
                input = state.input,
                busy = state.busy,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
            )
        }
    }
}

@Composable
private fun Bubble(message: UiMessage) {
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
                } else {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    SendBar(
        value = input,
        busy = busy,
        placeholder = "Answer in the language you practice…",
        onValueChange = onInputChange,
        onSend = onSend,
    )
}
