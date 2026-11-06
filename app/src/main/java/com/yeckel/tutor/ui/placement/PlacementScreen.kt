package com.yeckel.tutor.ui.placement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.rememberAppContainer
import com.yeckel.tutor.ui.common.Bubble
import com.yeckel.tutor.ui.common.SendBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: PlacementViewModel = viewModel { PlacementViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) {
            listState.scrollToItem(state.transcript.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Place my level") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val verdict = state.verdict
        if (state.phase == PlacementPhase.RESULT && verdict != null) {
            PlacementResult(
                level = verdict.level,
                reason = verdict.reason,
                knownWords = verdict.knownWords,
                applied = state.applied,
                busy = state.busy,
                onApply = viewModel::apply,
                onBack = onBack,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.phase == PlacementPhase.LOADING) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Generating questions…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(count = state.transcript.size) { index ->
                    Bubble(state.transcript[index])
                }
                if (state.phase == PlacementPhase.SCORING) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Scoring your answers…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            state.error?.let { error ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::retry) { Text("Try again") }
                }
            }

            SendBar(
                value = state.input,
                busy = state.busy || state.phase != PlacementPhase.ASKING,
                placeholder = "Answer in the language you practice…",
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::answer,
            )
        }
    }
}

@Composable
private fun PlacementResult(
    level: String,
    reason: String,
    knownWords: List<String>,
    applied: Boolean,
    busy: Boolean,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Your level",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            level.ifBlank { "A1" },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (reason.isNotBlank()) {
            Text(reason, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (knownWords.isNotEmpty()) {
            Text(
                "Words recorded as known: " + knownWords.take(12).joinToString(", ") +
                    if (knownWords.size > 12) " …" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (applied) {
                    Text(
                        "Applied — your vocabulary level was updated and the words were recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                } else {
                    Button(
                        onClick = onApply,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Apply level ${level.ifBlank { "A1" }}")
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack) { Text("Maybe later") }
    }
}
