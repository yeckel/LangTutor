package com.yeckel.tutor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yeckel.tutor.data.Article
import com.yeckel.tutor.data.ArticleFetcher
import com.yeckel.tutor.data.OllamaClient
import com.yeckel.tutor.data.SettingsRepository
import com.yeckel.tutor.data.VocabRepository

/**
 * Application-wide dependencies. `currentArticle` is session state: the page
 * picked on the start screen and picked up by the chat screen.
 */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context.applicationContext)
    val ollama = OllamaClient(settings)
    val articles = ArticleFetcher()
    val vocab = VocabRepository(context.applicationContext)

    var currentArticle: Article? = null
}

@Composable
fun rememberAppContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as TutorApp
    return remember { app.container }
}
