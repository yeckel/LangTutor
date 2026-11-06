package com.yeckel.tutor.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-lifetime text-to-speech wrapper: initializes lazily, then speaks with a
 * per-utterance language. Word playback flushes the queue, meaning playback
 * queues behind it. An optional on-done callback (posted on the main thread)
 * lets the flash cards chain "speak the word, then start listening".
 */
class TtsManager(private val context: Context) {

    private val utteranceCounter = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val doneCallbacks = HashMap<String, () -> Unit>()
    private var engine: TextToSpeech? = null
    private var ready = false

    /** Warm up the engine so later speak() calls are instant. */
    @Synchronized
    fun warmup() {
        if (engine != null) return
        val tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val callback = utteranceId?.let { doneCallbacks.remove(it) }
                if (callback != null) mainHandler.post { callback() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val callback = utteranceId?.let { doneCallbacks.remove(it) }
                if (callback != null) mainHandler.post { callback() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
        engine = tts
    }

    val isReady: Boolean get() = ready

    fun speak(text: String, languageTag: String, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        val tts = engine
        if (tts == null || !ready) return
        tts.language = Locale.forLanguageTag(languageTag)
        val utteranceId = "tutor-${utteranceCounter.incrementAndGet()}"
        if (onDone != null) doneCallbacks[utteranceId] = onDone
        tts.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            utteranceId,
        )
    }

    /** Silences everything currently being said (also cancels done-callbacks). */
    fun stop() {
        doneCallbacks.clear()
        engine?.stop()
    }
}
