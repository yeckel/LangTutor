package com.yeckel.tutor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Minimal Ollama HTTP client:
 * - GET  {base}/api/tags -> list installed models
 * - POST {base}/api/chat -> NDJSON (or SSE) stream of chat chunks
 */
class OllamaClient(private val settings: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    /** Fetch the list of models installed on the Ollama server.
     *  Overrides let the settings screen test the values currently in its fields. */
    suspend fun listModels(
        baseUrlOverride: String? = null,
        apiKeyOverride: String? = null,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = settings.current()
            val base = normalizeBaseUrl(baseUrlOverride ?: config.baseUrl)
            val apiKey = apiKeyOverride ?: config.apiKey
            val request = Request.Builder()
                .url("$base/api/tags")
                .get()

            apiKey.takeIf { it.isNotBlank() }?.let {
                request.header("Authorization", "Bearer $it")
            }

            http.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} while listing models")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<ModelListResponse>(body)
                    .models
                    .map { it.name }
                    .sorted()
            }
        }
    }

    /**
     * Stream a chat completion. Emits [StreamEvent.Delta] for each text piece,
     * then a final [StreamEvent.Done]. Handles both Ollama's NDJSON lines and
     * SSE-style `data:` lines.
     */
    fun chatStream(model: String, messages: List<ChatMessage>): Flow<StreamEvent> = callbackFlow {
        val callRef = AtomicReference<Call?>(null)

        launch(Dispatchers.IO) {
            try {
                val config = settings.current()
                val payload = json.encodeToString(
                    ChatRequest(
                        model = model,
                        messages = messages,
                        // Cloud models manage their own context; grow it for local models
                        // so the article + word list + history fit.
                        options = if (model.endsWith("-cloud", ignoreCase = true)) null else ChatOptions(),
                    ),
                )
                val request = Request.Builder()
                    .url("${normalizeBaseUrl(config.baseUrl)}/api/chat")
                    .post(payload.toRequestBody("application/json".toMediaType()))

                config.apiKey.takeIf { it.isNotBlank() }?.let {
                    request.header("Authorization", "Bearer $it")
                }

                val call = http.newCall(request.build())
                callRef.set(call)

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        trySend(StreamEvent.Failure("HTTP ${response.code} from the Ollama server"))
                        return@launch
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        trySend(StreamEvent.Failure("Empty response from the Ollama server"))
                        return@launch
                    }
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue

                        val raw = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
                        if (raw == "[DONE]") break

                        val chunk = runCatching { json.decodeFromString<ChatChunk>(raw) }
                            .getOrNull() ?: continue

                        chunk.message?.content?.takeIf { it.isNotEmpty() }?.let {
                            trySendBlocking(StreamEvent.Delta(it))
                        }
                        if (chunk.done) break
                    }
                }
                trySendBlocking(StreamEvent.Done)
            } catch (e: IOException) {
                trySend(StreamEvent.Failure(e.message ?: "Network error"))
            } catch (e: Exception) {
                trySend(StreamEvent.Failure(e.message ?: e.javaClass.simpleName))
            } finally {
                close()
            }
        }

        awaitClose { callRef.get()?.cancel() }
    }

    companion object {
        /**
         * Accepts sloppy input like "192.168.1.5:11434" and turns it into a
         * usable base URL.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) url = SettingsRepository.DEFAULT_BASE_URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            return url.trimEnd('/')
        }
    }
}
