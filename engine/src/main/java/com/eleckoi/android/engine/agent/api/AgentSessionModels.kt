package com.eleckoi.android.engine.agent.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AgentHarnessId {
    DeepSeek,
}

enum class AgentWorkItemType {
    UserMessage,
    AssistantMessage,
    /** Structural boundary for one Harness model request (DSH step). */
    Request,
    Reasoning,
    Command,
    FileChange,
    Tool,
    /** Host-side one-way action with no result returned to the model. */
    Action,
    ContextCompaction,
    Unknown,
}

enum class AgentWorkStatus {
    InProgress,
    Completed,
    Failed,
    Declined,
    Interrupted,
    Unknown,
}

/** Stable presentation phases understood by ElecKoi. A null phase means the harness omitted it. */
@Serializable
enum class AgentMessagePhase {
    @SerialName("commentary")
    Commentary,

    @SerialName("final_answer")
    FinalAnswer,
}

enum class AgentApprovalKind {
    Command,
    FileChange,
    Other,
}

enum class AgentApprovalDecision {
    Accept,
    AcceptForSession,
    Decline,
    Cancel,
}

enum class AgentFailureReason {
    ContextWindowExceeded,
    Other,
}

@Serializable
enum class AgentCommandActionType {
    Read,
    ListFiles,
    Search,
    Unknown,
}

/** Harness-neutral description of a command-side action. */
@Serializable
data class AgentCommandAction(
    val type: AgentCommandActionType,
    val command: String = "",
    val name: String? = null,
    val path: String? = null,
    val query: String? = null,
)

/** Harness-neutral file mutation kind. */
@Serializable
enum class AgentFileChangeKind {
    @SerialName("add")
    Add,

    @SerialName("delete")
    Delete,

    @SerialName("update")
    Update,
}

/** Harness-neutral file mutation. */
@Serializable
data class AgentFileChange(
    val path: String,
    val kind: AgentFileChangeKind,
    val diff: String,
    val movePath: String? = null,
)

/** Stable token accounting shared across Harness implementations. */
data class AgentTokenUsage(
    val totalTokens: Long,
    /** Input tokens that the provider had to process without cache reuse. */
    val inputTokens: Long,
    /** Input tokens read from an existing provider prompt cache. */
    val cacheReadTokens: Long,
    /** Input tokens written to a provider prompt cache for a later request. */
    val cacheWriteTokens: Long,
    /** True only when the provider explicitly returned cache accounting. */
    val cacheUsageReported: Boolean,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
)

/** A host-side one-way action parsed from ordinary assistant text, not a native Tool Call. */
data class AgentActionCall(
    val name: String,
    val argumentsJson: String,
)

/** Rich event vocabulary shared by the creation assistant and ordinary chat. */
sealed interface AgentSessionEvent {
    /**
     * A native event emitted by a delegated child session. [lineage] contains the subagent tool
     * call id at each level from the visible parent turn to the session that emitted [event].
     */
    data class DelegatedSessionEvent(
        val lineage: List<String>,
        val childSessionId: String,
        val event: AgentSessionEvent,
    ) : AgentSessionEvent

    data class TurnStarted(
        val threadId: String,
        val turnId: String,
        val startedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    /** One Harness model request and the tool executions requested by that response. */
    data class StepStarted(
        val threadId: String,
        val turnId: String,
        val step: Int,
        val startedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    /** Durable end boundary for the matching Harness step. */
    data class StepCompleted(
        val threadId: String,
        val turnId: String,
        val step: Int,
        val completedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    data class AssistantDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String,
        val step: Int = 0,
        /** Harness event time for first-token latency accounting. */
        val observedAtMillis: Long = 0L,
        val phase: AgentMessagePhase? = null,
        /** ElecKoi phase marker actually observed in the assistant text, not a provider inference. */
        val phaseHeader: AgentMessagePhase? = null,
        val actionCalls: List<AgentActionCall> = emptyList(),
        /** Whether this native token delta should enter the visible assistant transcript. */
        val visible: Boolean = true,
        /** DSH counts native tool-call fragments as first-token boundaries without displaying them. */
        val tokenObserved: Boolean = delta.isNotBlank(),
    ) : AgentSessionEvent

    /** A user-readable phase summary emitted by a harness while it is reasoning. */
    data class ReasoningSummaryDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val summaryIndex: Int,
        val delta: String,
    ) : AgentSessionEvent

    /** Detailed reasoning text, when the selected model/provider exposes it. */
    data class ReasoningTextDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val contentIndex: Int,
        val delta: String,
        val step: Int? = null,
        val observedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    /** One complete native history item, in the exact order recorded by the Agent runtime. */
    data class ModelHistoryItemCompleted(
        val threadId: String,
        val turnId: String,
        val responseItemJson: String,
    ) : AgentSessionEvent

    data class WorkItemStarted(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val clientUserMessageId: String? = null,
        val type: AgentWorkItemType,
        val label: String,
        val fileChanges: List<AgentFileChange> = emptyList(),
        val paths: List<String> = emptyList(),
        val diff: String = "",
        val messagePhase: AgentMessagePhase? = null,
        val toolName: String = "",
        val toolArguments: String = "",
        /** Effective model used by a delegated Agent, when this work item represents delegation. */
        val delegatedModel: String = "",
        val rawCommand: String = "",
        val commandActions: List<AgentCommandAction> = emptyList(),
        val startedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    data class CommandOutput(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String,
    ) : AgentSessionEvent

    data class FileChangesUpdated(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val fileChanges: List<AgentFileChange> = emptyList(),
        val paths: List<String>,
        val diff: String = "",
    ) : AgentSessionEvent

    /** Progress text emitted for a running MCP tool call. */
    data class WorkItemProgress(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val message: String,
    ) : AgentSessionEvent

    data class WorkItemCompleted(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val clientUserMessageId: String? = null,
        val type: AgentWorkItemType,
        val status: AgentWorkStatus,
        val summary: String = "",
        val detail: String = "",
        val exitCode: Int? = null,
        val fileChanges: List<AgentFileChange> = emptyList(),
        val paths: List<String> = emptyList(),
        val diff: String = "",
        val messagePhase: AgentMessagePhase? = null,
        val toolName: String = "",
        val toolArguments: String = "",
        /** Effective model used by a delegated Agent, when this work item represents delegation. */
        val delegatedModel: String = "",
        val rawCommand: String = "",
        val commandActions: List<AgentCommandAction> = emptyList(),
        val completedAtMillis: Long = 0L,
        /** Owning model step when this is an assembled assistant message. */
        val step: Int? = null,
        /** True for Plan items whose completed text supersedes streamed plan deltas. */
        val completionTextIsAuthoritative: Boolean = false,
    ) : AgentSessionEvent

    data class ApprovalRequested(
        val requestId: Long,
        val kind: AgentApprovalKind,
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val title: String,
        val detail: String,
        val availableDecisions: List<AgentApprovalDecision>,
        val rawCommand: String = "",
        val commandActions: List<AgentCommandAction> = emptyList(),
    ) : AgentSessionEvent

    /** The Harness has completed or cancelled a previously pending host request. */
    data class ApprovalResolved(
        val requestId: Long,
        val threadId: String,
    ) : AgentSessionEvent

    /** An unsupported backend-to-host request observed after a fail-closed error response. */
    data class HostRequest(
        val requestId: Long,
        val method: String,
        val paramsJson: String,
    ) : AgentSessionEvent

    data class TurnDiffUpdated(
        val threadId: String,
        val turnId: String,
        val diff: String,
    ) : AgentSessionEvent

    /** Latest token accounting reported by the active harness. */
    data class TokenUsageUpdated(
        val threadId: String,
        val turnId: String,
        /** Model step whose usage sample was replaced. */
        val step: Int,
        val total: AgentTokenUsage,
        val last: AgentTokenUsage,
        val modelContextWindow: Long?,
    ) : AgentSessionEvent

    /** DSH's native context-pressure projection for the next model request. */
    data class ContextWindowUpdated(
        val threadId: String,
        val turnId: String?,
        val pressureTokens: Long?,
        val projectedTokens: Long?,
        val modelContextWindow: Long?,
    ) : AgentSessionEvent

    data class TurnCompleted(
        val threadId: String,
        val turnId: String,
        val status: AgentWorkStatus,
        val errorMessage: String? = null,
        val failureReason: AgentFailureReason = AgentFailureReason.Other,
        val completedAtMillis: Long = 0L,
    ) : AgentSessionEvent

    /** The backend transport or protocol failed; the current session must not be reused. */
    data class SessionFailed(
        val message: String,
        val reason: AgentFailureReason = AgentFailureReason.Other,
    ) : AgentSessionEvent

    data class Warning(val message: String) : AgentSessionEvent
}
