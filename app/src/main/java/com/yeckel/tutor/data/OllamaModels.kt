package com.yeckel.tutor.data

import kotlinx.serialization.Serializable

/** One message in the conversation, roles: system / user / assistant. */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    /** Keeps long word lists + long articles inside the model's context window. */
    val options: ChatOptions? = null,
    /** Non-null forces JSON output (Ollama `format: "json"`). */
    val format: String? = null,
)

@Serializable
data class ChatOptions(
    val num_ctx: Int = 8192,
)

@Serializable
data class ChatChunk(
    val message: ChatChunkMessage? = null,
    val done: Boolean = false,
)

@Serializable
data class ChatChunkMessage(
    val content: String = "",
)

@Serializable
data class ModelListResponse(
    val models: List<ModelEntry> = emptyList(),
)

@Serializable
data class ModelEntry(
    val name: String,
)

/** Events emitted while a chat response is being streamed. */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Failure(val message: String) : StreamEvent
    data object Done : StreamEvent
}
