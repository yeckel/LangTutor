package com.yeckel.tutor.data

/**
 * An article to practice on: either fetched from a webpage or pasted by hand.
 * `text` may be truncated by the fetcher for very long pages.
 */
data class Article(
    val url: String,
    val title: String,
    val text: String,
)
