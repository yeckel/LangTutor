package com.yeckel.tutor.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeckel.tutor.R
import com.yeckel.tutor.rememberAppContainer

@Composable
fun StartScreen(
    onStartChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindLevel: () -> Unit,
    onOpenWords: () -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: StartViewModel = viewModel { StartViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val lastLesson by viewModel.lastLesson.collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tutor", style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.start_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            lastLesson?.let { lesson ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.start_continue_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(
                            "${lesson.turns.size} messages · ${lesson.vocabLevel} · ${lesson.language}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                viewModel.continueLastLesson()
                                onStartChat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (!state.usePasted) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    label = { Text(stringResource(R.string.start_webpage_link)) },
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::loadArticle,
                    enabled = state.url.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            if (state.article == null) {
                                R.string.start_load_article
                            } else {
                                R.string.start_reload_article
                            },
                        ),
                    )
                }
                TextButton(onClick = { viewModel.onTogglePasted(true) }) {
                    Text(stringResource(R.string.start_paste_text))
                }
            } else {
                OutlinedTextField(
                    value = state.pastedText,
                    onValueChange = viewModel::onPastedTextChange,
                    label = { Text(stringResource(R.string.start_paste_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                )
                Button(
                    onClick = viewModel::usePastedArticle,
                    enabled = state.pastedText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_paste_use))
                }
                TextButton(onClick = { viewModel.onTogglePasted(false) }) {
                    Text(stringResource(R.string.start_link_instead))
                }
            }

            state.article?.let { article ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(article.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            article.text.take(280) + if (article.text.length > 280) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start_start_chat))
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onFindLevel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.start_find_level))
                }
                TextButton(onClick = onOpenWords, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.start_my_words))
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.nav_settings))
                }
            }
        }
    }
}
