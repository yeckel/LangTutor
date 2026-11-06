package com.yeckel.tutor.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-lifetime text-to-speech wrapper: initializes lazily, then speaks with a
 * per-utterance language. Word playback flushes the queue, meaning playback
 * queues behind it so both can be heard in sequence.
 */
class TtsManager(private val context: Context) {

    private val utteranceCounter = AtomicInteger(0)
    private var engine: TextToSpeech? = null
    private var ready = false

    /** Warm up the engine so later speak() calls are instant. */
    @Synchronized
    fun warmup() {
        if (engine != null) return
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    fun speak(text: String, languageTag: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val tts = engine ?: return
        if (!ready) return
        tts.language = Locale.forLanguageTag(languageTag)
        val utteranceId = "tutor-${utteranceCounter.incrementAndGet()}"
        tts.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            utteranceId,
        )
    }

    /** Silences everything currently being said. */
    fun stop() {
        engine?.stop()
    }
}
