package com.eleckoi.android.engine.agent.api

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

data class AgentSessionOptions(
    val harness: AgentHarnessId = AgentHarnessId.DeepSeek,
    val workspaceId: String,
    /** App-private project path relative to files/creator_workspaces. Blank uses workspaces/<id>/project. */
    val workspaceProjectPath: String = "",
    /** Stable conversation identity. Several independent native threads may share one workspace. */
    val conversationId: String = workspaceId,
    /** Tool groups selected by the global preset for this concrete session request route. */
    val enabledToolGroupIds: Set<String> = emptySet(),
    /** Preset whose immutable tool configuration was captured for this session. */
    val presetId: String? = null,
    /** Exact app model configuration version used by this session. Null keeps the active config. */
    val modelConfigId: String? = null,
    /** Optional saved model configuration used only by spawned child Agents. Null follows parent. */
    val subagentModelConfigId: String? = null,
    /** Exact model inside [subagentModelConfigId]. Null uses that configuration's saved default. */
    val subagentModel: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val baseInstructions: String? = null,
    val developerInstructions: String? = null,
    /** Selects the runtime conversation; persistent product chats normally bind or resume it. */
    val threadStart: AgentThreadStart = AgentThreadStart.BoundOrNew,
    /** Native threads made unreachable by a destructive branch replacement. */
    val discardThreadIds: Set<String> = emptySet(),
    /** Transient tasks may opt out of the Harness transcript; ordinary chats keep it durable. */
    val ephemeral: Boolean = false,
    /** Authoritative product dialogue available to a Harness for native seeding or request projection. */
    val initialHistoryItems: List<AgentHistoryItem> = emptyList(),
    /** Controls whether prior native Agent events or product-owned dialogue form the next request. */
    val historyPolicy: AgentHistoryPolicy = AgentHistoryPolicy.NativeSession,
    /** Optional directive used only by a Harness-owned history compaction request. */
    val historyCompactionInstructions: String? = null,
    /** Emits each native model-history item so the host can commit it to Room. */
    val captureModelHistory: Boolean = false,
    /** A real assistant history item injected only when a fresh native thread is created. */
    val initialAssistantMessage: String? = null,
    /** Captures exact provider request bodies in the process-local diagnostics viewer. */
    val captureProviderRequests: Boolean = false,
    /** Hard wall-clock budget for one agent turn, including tool execution and approvals. */
    val maxTurnDurationMillis: Long = 45 * 60 * 1_000L,
    /** Time allowed for a timed-out turn to honour the interrupt request before fail-closed stop. */
    val turnInterruptGraceMillis: Long = 20_000L,
    val permissionMode: AgentPermissionMode = AgentPermissionMode.AskForApproval,
    /** Filesystem boundary applied independently from command/file-change approval behavior. */
    val fileAccessScope: AgentFileAccessScope = AgentFileAccessScope.FullRuntime,
    /** Turn-visible tools whose implementation remains inside the Android host process. */
    val dynamicTools: List<AgentDynamicTool> = emptyList(),
    /** Request-visible Agent context blocks controlled by the same capability switches as tools. */
    val toolContextBlocks: List<AgentToolContextBlock> = emptyList(),
)

/** Backend-neutral policy for projecting history into provider requests. */
enum class AgentHistoryPolicy {
    /** Keep the Harness's complete native history, including tool calls and results. */
    NativeSession,
    /** Use product dialogue between turns while retaining all native events in the active turn. */
    ProductDialogue,
}

/** Backend-neutral envelope around one provider-native history item. */
data class AgentHistoryItem(
    val responseItemJson: String,
)

/** A stable, independently ordered block inside the model-visible tool-context bucket. */
data class AgentToolContextBlock(
    val id: String,
    val enabled: Boolean,
    val order: Int,
)

object AgentToolContextBlockIds {
    const val Permissions = "permissions"
    const val Skills = "skills"
    const val Extensions = "extensions"
    const val Collaboration = "collaboration"
    const val Environment = "environment"
}

sealed interface AgentThreadStart {
    /** Resume the conversation's bound thread, or create one when no binding exists. */
    data object BoundOrNew : AgentThreadStart

    /** Resume this exact branch and replace the conversation binding with it. */
    data class Resume(val threadId: String) : AgentThreadStart

    /** Create a durable branch containing source history through lastTurnId, inclusive. */
    data class Fork(
        val lastTurnId: String,
        val sourceThreadId: String = "",
    ) : AgentThreadStart

    /**
     * Rebuild the current route through [lastTurnId]. A Harness may use a native fork primitive,
     * but the product replaces the current route and exposes no alternate branch.
     */
    data class ReplaceFrom(
        val lastTurnId: String,
        val sourceThreadId: String = "",
    ) : AgentThreadStart

    /** Start a new empty thread and replace the conversation binding with it. */
    data object Fresh : AgentThreadStart
}

/** Product-facing permission presets; a Harness may report a reduced capability set. */
@Serializable
enum class AgentPermissionMode {
    AskForApproval,
    ApproveForMe,
    FullAccess,
}

enum class AgentFileAccessScope {
    FullRuntime,
    CurrentWorkspace,
}

data class AgentTurnHandle(
    val threadId: String,
    val turnId: String,
    /** Native Harness identity used to correlate a committed user-message notification. */
    val clientUserMessageId: String? = null,
)

/** One app-private image submitted with a user turn. Bytes are admitted by the Harness at send time. */
data class AgentInputImage(
    val localPath: String,
    val mediaType: String,
    val name: String = "",
)

/** Backend-neutral user prompt. Image bytes never become part of the product database. */
data class AgentPrompt(
    val text: String,
    val images: List<AgentInputImage> = emptyList(),
)

sealed interface AgentSessionState {
    data object Stopped : AgentSessionState
    data object Starting : AgentSessionState
    data class Ready(
        val harness: AgentHarnessId,
        val threadId: String,
    ) : AgentSessionState
    data class Running(
        val harness: AgentHarnessId,
        val threadId: String,
        val turnId: String,
    ) : AgentSessionState
    /** A stop was sent; new approvals are rejected while waiting for terminal confirmation. */
    data class Stopping(
        val harness: AgentHarnessId,
        val threadId: String,
        val turnId: String,
    ) : AgentSessionState
    data class Failed(val message: String) : AgentSessionState
}

interface AgentSession {
    val state: StateFlow<AgentSessionState>
    val events: SharedFlow<AgentSessionEvent>

    suspend fun start()
    suspend fun send(
        text: String,
        contextInjections: List<AgentContextInjection> = emptyList(),
    ): AgentTurnHandle
    suspend fun send(
        prompt: AgentPrompt,
        contextInjections: List<AgentContextInjection> = emptyList(),
    ): AgentTurnHandle {
        require(prompt.images.isEmpty()) { "当前 Agent 后端不支持图片输入" }
        return send(prompt.text, contextInjections)
    }
    /** Adds input to the currently active regular turn without starting another turn. */
    suspend fun steer(text: String): AgentTurnHandle
    /** Updates the loaded thread's settings for subsequent turns. */
    suspend fun updatePermissionMode(permissionMode: AgentPermissionMode)
    suspend fun interrupt()
    suspend fun resolveApproval(requestId: Long, decision: AgentApprovalDecision)
    suspend fun shutdown()
}

/** Request-scoped prompt material. It is sent with turn/start and is never persisted as chat. */
data class AgentContextInjection(
    val id: String,
    val anchor: AgentContextAnchor,
    val role: AgentContextRole,
    val activation: AgentContextActivation,
    val content: String,
    val order: Int = 1,
)

sealed interface AgentContextActivation {
    /** Applies only to the first provider request made inside this turn. */
    data object FirstModelRequest : AgentContextActivation

    data object Immediate : AgentContextActivation

    data class AfterToolCall(
        val toolName: String,
    ) : AgentContextActivation

    data class AfterToolCallArgumentContains(
        val toolName: String,
        val argumentName: String,
        val value: String,
    ) : AgentContextActivation
}

enum class AgentContextAnchor(val wireValue: String) {
    /** Top-level Responses API `instructions`, outside the ordered `input` item stream. */
    Instructions("instructions"),
    BeforeToolContext("beforeToolContext"),
    ToolContext("toolContext"),
    AfterToolContext("afterToolContext"),
    BeforeHistory("beforeHistory"),
    /** After previous dialogue but before the current turn's user input. */
    AfterHistory("afterHistory"),
    BeforeLatestUserInput("beforeLatestUserInput"),
    AfterLatestUserInput("afterLatestUserInput"),
    BeforeToolFlow("beforeToolFlow"),
    /** Appended after this turn's latest completed tool result, before the model continues. */
    AfterToolFlow("afterToolFlow"),
}

enum class AgentContextRole(val wireValue: String) {
    System("system"),
    User("user"),
    Assistant("assistant"),
}

/** The input could not be attached to the active turn and should be queued for a later turn. */
class AgentTurnSteerUnavailableException(message: String) : IllegalStateException(message)

fun interface AgentSessionFactory {
    fun create(options: AgentSessionOptions): AgentSession
}
