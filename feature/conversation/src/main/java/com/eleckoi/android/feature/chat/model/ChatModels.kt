package com.eleckoi.android.feature.chat.model

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.chat.model.content.ToolCallState
import com.eleckoi.android.engine.agent.api.AgentCommandAction
import com.eleckoi.android.engine.agent.api.AgentFileChange
import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.modelconfig.model.ModelParameters

const val OpeningMessageId: String = "opening"
const val DefaultGeneratedImageWidth: Int = 832
const val DefaultGeneratedImageHeight: Int = 1216

enum class MessageRole {
    User,
    Assistant,
    System,
}

enum class ChatImageStatus {
    Generating,
    Ready,
    Failed,
}

data class ChatImageAttachment(
    val id: String,
    /** Stable execution identity. Regeneration keeps [id] and replaces only this value. */
    val generationAttemptId: String = "",
    val localPath: String = "",
    val status: ChatImageStatus = ChatImageStatus.Generating,
    val errorMessage: String = "",
    /** Raw scene fields are retained so this bitmap can be regenerated without another chat request. */
    val prompt: String = "",
    val negativePrompt: String = "",
    /** One-based final-reply paragraph after which this story frame belongs. */
    val afterParagraph: Int = Int.MAX_VALUE,
    val frameIndex: Int = 1,
    val frameCount: Int = 1,
    /** Requested canvas dimensions; presentation keeps this ratio in every lifecycle state. */
    val imageWidth: Int = DefaultGeneratedImageWidth,
    val imageHeight: Int = DefaultGeneratedImageHeight,
)

/** App-private source image attached by the user and durably admitted into the DSH session. */
data class ChatUserImageAttachment(
    val id: String,
    val localPath: String,
    val mediaType: String,
    val displayName: String = "",
    /** Conversation-owned reference accepted by creator media tools; blank in ordinary role chat. */
    val creatorMediaReference: String = "",
    val bytes: Long = 0L,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String = "",
    val provider: String = "",
    val model: String = "",
    val createdAt: String = "",
    val pending: Boolean = false,
    val variableStateJson: String = "",
    val toolCalls: List<ChatToolCallRecord> = emptyList(),
    val imageAttachments: List<ChatImageAttachment> = emptyList(),
    val inputImageAttachments: List<ChatUserImageAttachment> = emptyList(),
    /** Harness-native conversation and turn identities that own this visible reply. */
    val runtimeThreadId: String = "",
    val runtimeTurnId: String = "",
    /** Whole Agent turn, including startup, tools, reasoning, and final answer streaming. */
    val turnStartedAtMillis: Long = 0L,
    val turnCompletedAtMillis: Long? = null,
    /** Native request timing and usage retained with this reply for the chat statistics strip. */
    val generationMetrics: ChatGenerationMetrics = ChatGenerationMetrics(),
    /** Latest native request sample for the model context-window inspector. */
    val contextWindowUsage: ChatContextWindowUsage? = null,
    /** Exact native Agent history items retained in Room but excluded by role-chat projection. */
    val modelHistoryItems: List<String> = emptyList(),
    /** Product speaker identity; provider/model above describes execution instead. */
    val speakerId: String = "",
    val speakerKind: String = "",
    val speakerName: String = "",
    val speakerAvatarAssetId: String = "",
)

/**
 * Durable measurements for one visible assistant reply.
 *
 * Every counter is optional-at-source rather than estimated: a provider that does not return a
 * cache field leaves it at zero, and the UI suppresses that fact instead of presenting a false 0%.
 */
data class ChatGenerationMetrics(
    val turns: Int = 0,
    val steps: Int = 0,
    val llmDurationMillis: Long = 0L,
    val toolDurationMillis: Long = 0L,
    val firstTokenDelayMillis: Long = 0L,
    val firstTokenSamples: Int = 0,
    val decodeDurationMillis: Long = 0L,
    val decodeOutputTokens: Long = 0L,
    val inputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L,
    val cacheUsageReported: Boolean = false,
    val outputTokens: Long = 0L,
) {
    val billedInputTokens: Long
        get() = inputTokens + cacheReadTokens + cacheWriteTokens

    val cacheHitPercent: Int?
        get() = billedInputTokens.takeIf { cacheUsageReported && it > 0L }
            ?.let { total -> ((cacheReadTokens * 100L + total / 2L) / total).toInt() }

    operator fun plus(other: ChatGenerationMetrics) = ChatGenerationMetrics(
        turns = turns + other.turns,
        steps = steps + other.steps,
        llmDurationMillis = llmDurationMillis + other.llmDurationMillis,
        toolDurationMillis = toolDurationMillis + other.toolDurationMillis,
        firstTokenDelayMillis = firstTokenDelayMillis + other.firstTokenDelayMillis,
        firstTokenSamples = firstTokenSamples + other.firstTokenSamples,
        decodeDurationMillis = decodeDurationMillis + other.decodeDurationMillis,
        decodeOutputTokens = decodeOutputTokens + other.decodeOutputTokens,
        inputTokens = inputTokens + other.inputTokens,
        cacheReadTokens = cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
        cacheUsageReported = cacheUsageReported || other.cacheUsageReported,
        outputTokens = outputTokens + other.outputTokens,
    )
}

/**
 * The most recent provider-reported context sample for a visible assistant reply.
 *
 * This intentionally stores native token accounting instead of estimating from character count:
 * cache reads, system instructions, and provider-side tool context are all invisible to text-based
 * estimates but still consume the model's actual request budget.
 */
data class ChatContextWindowUsage(
    val latestTokens: Long,
    val totalTokens: Long,
    val modelContextWindow: Long? = null,
)

fun Iterable<ChatMessage>.generationMetrics(): ChatGenerationMetrics =
    filter { it.role == MessageRole.Assistant }
        .fold(ChatGenerationMetrics()) { total, message -> total + message.generationMetrics }

/**
 * The single presentation boundary for assistant rows.
 *
 * Pending is transport state, not UI content. An assistant does not enter the conversation list
 * until the provider has emitted something the user can actually see.
 */
fun ChatMessage.hasRenderableContent(): Boolean {
    return role != MessageRole.Assistant ||
        content.isNotBlank() ||
        reasoningContent.isNotBlank() ||
        toolCalls.any { call -> call.workItemType != AgentWorkItemType.Request } ||
        imageAttachments.isNotEmpty()
}

/** True only before the active Harness emits its first visible reasoning, work item, or answer. */
fun ChatMessage.isAwaitingFirstAgentEvent(): Boolean {
    return role == MessageRole.Assistant &&
        pending &&
        !hasRenderableContent()
}

data class ChatToolCallRecord(
    val callId: String,
    val name: String,
    val arguments: String = "",
    val result: String = "",
    val state: ToolCallState = ToolCallState.Pending,
    val workItemType: AgentWorkItemType? = null,
    val narrative: Boolean = false,
    val fileChanges: List<AgentFileChange> = emptyList(),
    val paths: List<String> = emptyList(),
    val diff: String = "",
    val turnDiffObserved: Boolean = false,
    val messagePhase: AgentMessagePhase? = null,
    val phaseHeader: AgentMessagePhase? = null,
    val toolName: String = "",
    val delegatedModel: String = "",
    val childCalls: List<ChatToolCallRecord> = emptyList(),
    val delegatedSessionId: String = "",
    val rawCommand: String = "",
    val commandActions: List<AgentCommandAction> = emptyList(),
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    /** True when a successful execution remains staged until the enclosing model turn commits. */
    val rollbackOnAbort: Boolean = false,
)

fun ChatMessage.withVariableState(stateJson: String): ChatMessage {
    return copy(variableStateJson = stateJson)
}

data class ChatSession(
    val id: String,
    /** Physical Agent workspace shared by this character and this role mode. */
    val workspaceId: String = "",
    val title: String,
    val characterId: String,
    val characterName: String,
    val characterAvatar: String,
    val characterPersona: CharacterCard,
    val characterMode: String = CharacterMode.Agent.storageValue,
    val permissionMode: AgentPermissionMode = AgentPermissionMode.AskForApproval,
    val messages: List<ChatMessage>,
    val createdAt: String = "",
    val updatedAt: String,
    val modelSettings: Map<String, ChatModelSelection> = emptyMap(),
    val initialVariableStateJson: String = "",
    val variableStateJson: String = "",
)

data class ChatDraft(
    val session: ChatSession,
    val selectedModelConfig: ModelConfig,
    val selectedModel: String,
    val modelParameters: ModelParameters = ModelParameters(),
    val openingOptions: List<ChatOpeningOption> = emptyList(),
    val selectedOpeningOptionId: String = "",
    val openingSelectionEnabled: Boolean = false,
)

data class ChatOpeningOption(
    val id: String,
    val title: String,
)

data class ChatListItem(
    val id: String,
    val title: String,
    val characterId: String,
    val characterMode: String = CharacterMode.Agent.storageValue,
    val characterName: String,
    val characterAvatar: String,
    val summary: String,
    val updatedAt: String,
    val messageCount: Int,
)
