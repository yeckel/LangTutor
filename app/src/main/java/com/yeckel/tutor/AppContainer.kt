package com.yeckel.tutor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.ArticleFetcher
import com.yeckel.tutor.data.OllamaClient
import com.yeckel.tutor.data.ProgressRepository
import com.yeckel.tutor.data.SettingsRepository
import com.yeckel.tutor.data.TtsManager
import com.yeckel.tutor.data.VocabRepository

/**
 * Application-wide dependencies. `currentArticle` is session state: the page
 * picked on the start screen and picked up by the chat screen.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsRepository(appContext)
    val ollama = OllamaClient(settings)
    val articles = ArticleFetcher()
    val vocab = VocabRepository(appContext)
    val progress = ProgressRepository(appContext)
    val tts = TtsManager(appContext)

    /** Localized string for ViewModels (screen strings use stringResource). */
    fun string(resourceId: Int, vararg args: Any): String = appContext.getString(resourceId, *args)

    var currentArticle: Article? = null
    var pendingContinue: Boolean = false
}

@Composable
fun rememberAppContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as TutorApp
    return remember { app.container }
}
