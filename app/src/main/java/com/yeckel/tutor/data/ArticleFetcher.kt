package com.yeckel.tutor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Downloads a webpage and extracts (title, article text) using readability-style
 * heuristics: strip boilerplate, prefer `<article>`/`<main>`, fall back to body.
 */
class ArticleFetcher {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(rawUrl: String): Result<Article> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl.trim()
            } else {
                "https://${rawUrl.trim()}"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} while loading the page")
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) error("The page returned no content")
                extract(url, html)
            }
        }
    }

    private fun extract(url: String, html: String): Article {
        val doc = Jsoup.parse(html, url)

        doc.select("script, style, nav, footer, header, aside, form, noscript, button, iframe")
            .remove()

        val title = doc.title().trim().ifBlank { url }
        val container = pickMainContainer(doc)

        val text = container.select("h1, h2, h3, p")
            .map { element -> element.text().trim() }
            .filter { it.length >= 2 }
            .joinToString("\n\n")
            .replace(Regex("\\[\\d+\\]"), "")
            .trim()

        if (text.length < MIN_TEXT_LENGTH) {
            error("Could not find readable article text on this page")
        }

        val truncated = if (text.length > MAX_TEXT_LENGTH) {
            text.take(MAX_TEXT_LENGTH) + "…"
        } else {
            text
        }

        return Article(url = url, title = title, text = truncated)
    }

    private fun pickMainContainer(doc: Document): Element {
        val preferred = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")

        if (preferred != null && preferred.text().length >= MIN_TEXT_LENGTH) return preferred

        val body = doc.body()
        if (body != null && body.text().length >= MIN_TEXT_LENGTH) return body

        error("Could not find readable article text on this page")
    }

    private companion object {
        const val MIN_TEXT_LENGTH = 200
        const val MAX_TEXT_LENGTH = 12_000

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
