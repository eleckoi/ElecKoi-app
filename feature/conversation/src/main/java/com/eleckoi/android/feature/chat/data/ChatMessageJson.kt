package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatContextWindowUsage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.chat.model.MessageRole
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ChatMessageJson(
    val id: String = "",
    val role: String = "assistant",
    val content: String = "",
    @SerialName("reasoning_content")
    val reasoningContent: String = "",
    val provider: String = "",
    val model: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("variable_state_json")
    val variableStateJson: String = "",
    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCallJson> = emptyList(),
    @SerialName("image_attachments")
    val imageAttachments: List<ChatImageAttachmentJson> = emptyList(),
    @SerialName("input_image_attachments")
    val inputImageAttachments: List<ChatUserImageAttachmentJson> = emptyList(),
    @SerialName("runtime_thread_id")
    val runtimeThreadId: String = "",
    @SerialName("runtime_turn_id")
    val runtimeTurnId: String = "",
    @SerialName("turn_started_at_millis")
    val turnStartedAtMillis: Long = 0L,
    @SerialName("turn_completed_at_millis")
    val turnCompletedAtMillis: Long? = null,
    @SerialName("generation_metrics")
    val generationMetrics: ChatGenerationMetricsJson = ChatGenerationMetricsJson(),
    @SerialName("context_window_usage")
    val contextWindowUsage: ChatContextWindowUsageJson? = null,
) {
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            role = role.toMessageRole(),
            content = content,
            reasoningContent = reasoningContent,
            provider = provider,
            model = model,
            createdAt = createdAt,
            variableStateJson = variableStateJson,
            toolCalls = toolCalls.map { it.toDomain() },
            imageAttachments = imageAttachments.map { it.toDomain() },
            inputImageAttachments = inputImageAttachments.map { it.toDomain() },
            runtimeThreadId = runtimeThreadId,
            runtimeTurnId = runtimeTurnId,
            turnStartedAtMillis = turnStartedAtMillis,
            turnCompletedAtMillis = turnCompletedAtMillis,
            generationMetrics = generationMetrics.toDomain(),
            contextWindowUsage = contextWindowUsage?.toDomain(),
        )
    }

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageJson {
            return ChatMessageJson(
                id = message.id,
                role = message.role.storageValue(),
                content = message.content,
                reasoningContent = message.reasoningContent,
                provider = message.provider,
                model = message.model,
                createdAt = message.createdAt,
                variableStateJson = message.variableStateJson,
                toolCalls = message.toolCalls.map(ChatToolCallJson::fromDomain),
                imageAttachments = message.imageAttachments.map(ChatImageAttachmentJson::fromDomain),
                inputImageAttachments = message.inputImageAttachments.map(ChatUserImageAttachmentJson::fromDomain),
                runtimeThreadId = message.runtimeThreadId,
                runtimeTurnId = message.runtimeTurnId,
                turnStartedAtMillis = message.turnStartedAtMillis,
                turnCompletedAtMillis = message.turnCompletedAtMillis,
                generationMetrics = ChatGenerationMetricsJson.fromDomain(message.generationMetrics),
                contextWindowUsage = message.contextWindowUsage?.let(ChatContextWindowUsageJson::fromDomain),
            )
        }
    }
}

@Serializable
internal data class ChatGenerationMetricsJson(
    val turns: Int = 0,
    val steps: Int = 0,
    @SerialName("llm_duration_millis") val llmDurationMillis: Long = 0L,
    @SerialName("tool_duration_millis") val toolDurationMillis: Long = 0L,
    @SerialName("first_token_delay_millis") val firstTokenDelayMillis: Long = 0L,
    @SerialName("first_token_samples") val firstTokenSamples: Int = 0,
    @SerialName("decode_duration_millis") val decodeDurationMillis: Long = 0L,
    @SerialName("decode_output_tokens") val decodeOutputTokens: Long = 0L,
    @SerialName("input_tokens") val inputTokens: Long = 0L,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long = 0L,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Long = 0L,
    @SerialName("cache_usage_reported") val cacheUsageReported: Boolean = false,
    @SerialName("output_tokens") val outputTokens: Long = 0L,
) {
    fun toDomain() = ChatGenerationMetrics(
        turns = turns,
        steps = steps,
        llmDurationMillis = llmDurationMillis,
        toolDurationMillis = toolDurationMillis,
        firstTokenDelayMillis = firstTokenDelayMillis,
        firstTokenSamples = firstTokenSamples,
        decodeDurationMillis = decodeDurationMillis,
        decodeOutputTokens = decodeOutputTokens,
        inputTokens = inputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens,
        cacheUsageReported = cacheUsageReported,
        outputTokens = outputTokens,
    )

    companion object {
        fun fromDomain(metrics: ChatGenerationMetrics) = ChatGenerationMetricsJson(
            turns = metrics.turns,
            steps = metrics.steps,
            llmDurationMillis = metrics.llmDurationMillis,
            toolDurationMillis = metrics.toolDurationMillis,
            firstTokenDelayMillis = metrics.firstTokenDelayMillis,
            firstTokenSamples = metrics.firstTokenSamples,
            decodeDurationMillis = metrics.decodeDurationMillis,
            decodeOutputTokens = metrics.decodeOutputTokens,
            inputTokens = metrics.inputTokens,
            cacheReadTokens = metrics.cacheReadTokens,
            cacheWriteTokens = metrics.cacheWriteTokens,
            cacheUsageReported = metrics.cacheUsageReported,
            outputTokens = metrics.outputTokens,
        )
    }
}

@Serializable
internal data class ChatContextWindowUsageJson(
    @SerialName("latest_tokens") val latestTokens: Long = 0L,
    @SerialName("total_tokens") val totalTokens: Long = 0L,
    @SerialName("model_context_window") val modelContextWindow: Long? = null,
    @SerialName("system_tokens") val systemTokens: Long? = null,
    @SerialName("tools_tokens") val toolsTokens: Long? = null,
    @SerialName("message_tokens") val messageTokens: Long? = null,
) {
    fun toDomain() = ChatContextWindowUsage(
        latestTokens = latestTokens,
        totalTokens = totalTokens,
        modelContextWindow = modelContextWindow,
        systemTokens = systemTokens,
        toolsTokens = toolsTokens,
        messageTokens = messageTokens,
    )

    companion object {
        fun fromDomain(usage: ChatContextWindowUsage) = ChatContextWindowUsageJson(
            latestTokens = usage.latestTokens,
            totalTokens = usage.totalTokens,
            modelContextWindow = usage.modelContextWindow,
            systemTokens = usage.systemTokens,
            toolsTokens = usage.toolsTokens,
            messageTokens = usage.messageTokens,
        )
    }
}

@Serializable
internal data class ChatSessionGenerationStatsJson(
    val version: Int = 1,
    @SerialName("runtime_thread_id") val runtimeThreadId: String = "",
    val metrics: ChatGenerationMetricsJson = ChatGenerationMetricsJson(),
    @SerialName("context_window_usage")
    val contextWindowUsage: ChatContextWindowUsageJson? = null,
    @SerialName("step_totals_by_turn") val stepTotalsByTurn: Map<String, Int> = emptyMap(),
) {
    fun toDomain() = ChatSessionGenerationStats(
        runtimeThreadId = runtimeThreadId,
        metrics = metrics.toDomain(),
        contextWindowUsage = contextWindowUsage?.toDomain(),
        stepTotalsByTurn = stepTotalsByTurn,
    )

    companion object {
        fun fromDomain(stats: ChatSessionGenerationStats) = ChatSessionGenerationStatsJson(
            runtimeThreadId = stats.runtimeThreadId,
            metrics = ChatGenerationMetricsJson.fromDomain(stats.metrics),
            contextWindowUsage = stats.contextWindowUsage?.let(ChatContextWindowUsageJson::fromDomain),
            stepTotalsByTurn = stats.stepTotalsByTurn,
        )
    }
}

private fun String.toMessageRole(): MessageRole {
    return when (this) {
        "user" -> MessageRole.User
        "system" -> MessageRole.System
        else -> MessageRole.Assistant
    }
}

private fun MessageRole.storageValue(): String {
    return when (this) {
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
        MessageRole.System -> "system"
    }
}
