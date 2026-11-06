package com.yeckel.tutor.data

/**
 * Very small tokenizer for progress tracking: lowercase, split on anything
 * that is not a letter (unicode-aware, so accents survive), drop 1-letter
 * leftovers and long junk.
 */
fun tokenize(text: String): List<String> =
    text.lowercase()
        .split(Regex("[^\\p{L}]+"))
        .filter { it.length in 2..20 }
