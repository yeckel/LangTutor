package com.yeckel.tutor.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.data.ProgressWord
import com.yeckel.tutor.R
import com.yeckel.tutor.data.TierProgress
import com.yeckel.tutor.data.isKnown
import com.yeckel.tutor.rememberAppContainer
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(
    onBack: () -> Unit,
    onTrain: () -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: WordsViewModel = viewModel { WordsViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val newWord by viewModel.newWordText.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Words · ${state.languageName.ifBlank { "-" }}") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.words_level_progress),
                style = MaterialTheme.typography.labelLarge,
            )
            state.tiers.forEach { tier ->
                TierRow(tier, current = tier.level == state.vocabLevel)
            }
            state.unlockable?.let {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Tier complete!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.words_unlock_text, state.vocabLevel, it.level),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = viewModel::unlockNext, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.words_unlock_button, it.level))
                        }
                    }
                }
            }

            Button(
                onClick = onTrain,
                enabled = state.words.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.words_train))
            }
            Text(
                "Tracked from conversations. Tap a word to toggle known/learning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = viewModel::onNewWordChange,
                    label = { Text(stringResource(R.string.words_add_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::addWord, enabled = newWord.isNotBlank()) {
                    Text(stringResource(R.string.common_add))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.words.isEmpty()) {
                    item {
                        Text(
                            "No words tracked yet. Chat with the tutor, run a placement quiz, " +
                                "or add words above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.words) { word ->
                    WordRow(
                        word = word,
                        onClick = { viewModel.toggleKnown(word) },
                        onDelete = { viewModel.delete(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TierRow(tier: TierProgress, current: Boolean) {
    val complete = com.yeckel.tutor.data.LevelProgression.isComplete(tier)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tier.level +
                    if (current) stringResource(R.string.words_tier_now)
                    else if (complete) stringResource(R.string.words_tier_done) else "",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            LinearProgressIndicator(
                progress = { tier.masteredRatio },
                modifier = Modifier.weight(1f).height(8.dp),
            )
            Text(
                stringResource(R.string.words_mastered_stat, tier.mastered, tier.tracked, tier.totalWords),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun WordRow(
    word: ProgressWord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (word.isKnown) "✓ " else "• ") + word.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (word.isKnown) FontWeight.SemiBold else FontWeight.Normal,
                )
                word.translation?.let { translation ->
                    Text(
                        translation,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "introduced ${word.introducedByTutor} · used ${word.usedByLearner} · " +
                        "cards ${word.trainCorrect}/${word.trainCorrect + word.trainMissed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) {
                Text("✕")
            }
        }
    }
}
