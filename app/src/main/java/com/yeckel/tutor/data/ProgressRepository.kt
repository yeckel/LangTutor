package com.yeckel.tutor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent learner progress:
 *  - known/learning words per language (augmenting the bundled level lists)
 *  - the active lesson (article + full conversation) for easy continuation
 *
 * Backed by the app DataStore as JSON documents; small enough for JSON while
 * the data lives at chat-chat scale. Writes are serialized with a mutex.
 */
class ProgressRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val writeMutex = Mutex()

    private object Keys {
        val PROGRESS_WORDS = stringPreferencesKey("progress_words_json")
        val ACTIVE_LESSON = stringPreferencesKey("active_lesson_json")
    }

    val knownWords = context.tutorDataStore.data.map { prefs ->
        prefs[Keys.PROGRESS_WORDS]?.let { decodeList<ProgressWord>(it) } ?: emptyList()
    }

    val activeLesson = context.tutorDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_LESSON]?.let { decode<LessonProgress>(it) }
    }

    suspend fun readKnownWords(): List<ProgressWord> = knownWords.first()
    suspend fun readLesson(): LessonProgress? = activeLesson.first()

    /**
     * Records the vocabulary the tutor just used. Words outside the bundled
     * level list are treated as newly taught and get an encounter count.
     */
    suspend fun recordAssistantMessage(languageKey: String, listWords: Set<String>, message: String) {
        if (languageKey.isBlank() || message.isBlank()) return
        writeMutex.withLock {
            val words = readKnownWords().toMutableList()
            for (token in tokenize(message).toSet()) {
                if (token in listWords) continue
                val existing = words.find { it.word == token && it.language == languageKey }
                words.remove(existing)
                words.add(
                    ProgressWord(
                        word = token,
                        language = languageKey,
                        introducedByTutor = (existing?.introducedByTutor ?: 0) + 1,
                        usedByLearner = existing?.usedByLearner ?: 0,
                        firstAdded = existing?.firstAdded ?: System.currentTimeMillis(),
                    ),
                )
            }
            writeWords(words)
        }
    }

    /**
     * Records the message the learner wrote: words that are in the level list
     * or already tracked count as actively known; unknown typos are ignored.
     */
    suspend fun recordLearnerMessage(languageKey: String, listWords: Set<String>, message: String) {
        if (languageKey.isBlank() || message.isBlank()) return
        writeMutex.withLock {
            val words = readKnownWords().toMutableList()
            var changed = false
            for (token in tokenize(message).toSet()) {
                val inList = token in listWords
                val existing = words.find { it.word == token && it.language == languageKey }
                if (!inList && existing == null) continue
                changed = true
                words.remove(existing)
                words.add(
                    ProgressWord(
                        word = token,
                        language = languageKey,
                        introducedByTutor = existing?.introducedByTutor ?: 0,
                        usedByLearner = (existing?.usedByLearner ?: 0) + 1,
                        firstAdded = existing?.firstAdded ?: System.currentTimeMillis(),
                    ),
                )
            }
            if (changed) writeWords(words)
        }
    }

    /** Replaces the active lesson (new conversation started). */
    suspend fun startLesson(lesson: LessonProgress) = writeMutex.withLock {
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_LESSON] = json.encodeToString(lesson)
        }
    }

    /** Appends one turn to the active lesson, if it still is the same one. */
    suspend fun appendTurn(lessonId: Long, turn: LessonTurn) = writeMutex.withLock {
        val lesson = readLesson() ?: return@withLock
        if (lesson.id != lessonId) return@withLock
        val updated = lesson.copy(
            turns = (lesson.turns + turn).takeLast(MAX_TURNS),
            updatedAt = System.currentTimeMillis(),
        )
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_LESSON] = json.encodeToString(updated)
        }
    }

    /** Persists the difficulty nudge so it survives app restarts. */
    suspend fun updateLessonDifficulty(lessonId: Long, difficulty: String) = writeMutex.withLock {
        val lesson = readLesson() ?: return@withLock
        if (lesson.id != lessonId) return@withLock
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_LESSON] = json.encodeToString(lesson.copy(difficulty = difficulty))
        }
    }

    private suspend fun writeWords(words: List<ProgressWord>) {
        context.tutorDataStore.edit { prefs ->
            prefs[Keys.PROGRESS_WORDS] = json.encodeToString(words.takeLast(MAX_WORDS_TRACKED))
        }
    }

    private inline fun <reified T> decode(value: String): T? =
        runCatching { json.decodeFromString<T>(value) }.getOrNull()

    private inline fun <reified T> decodeList(value: String): List<T> =
        decode<List<T>>(value) ?: emptyList()

    companion object {
        private const val MAX_TURNS = 250
        private const val MAX_WORDS_TRACKED = 5000
    }
}
