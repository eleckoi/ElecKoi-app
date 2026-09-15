package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.foundation.diagnostics.CrashDiagnostics
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTodoWriteTool
import com.eleckoi.android.engine.agent.api.AgentUpdatePlanTool
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.agentWarningNotice
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.hasRenderableContent
import com.eleckoi.android.feature.chat.roleplay.actions.GenerateImageActionName
import com.eleckoi.android.feature.chat.roleplay.actions.RoleplayImageActionController
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Projects one Agent event stream into the pending Room/UI message.
 *
 * Turn orchestration stays in [CharacterAgentGenerationService]; this class owns only event
 * reduction, visible snapshot publication, and the small protocol reactions tied to those events.
 */
internal class CharacterAgentTurnProjector(
    private val scope: CoroutineScope,
    session: ChatSession,
    pending: ChatMessage,
    prompt: String,
    private val imageActions: RoleplayImageActionController,
    private val checkpointWriter: CharacterGenerationCheckpointWriter<CharacterAgentTurnSnapshot>,
    private val streamPublisher: LatestStreamSnapshotPublisher<CharacterAgentTurnSnapshot>,
    private val variableStateProvider: (() -> String)?,
    private val declineApproval: suspend (requestId: Long) -> Unit,
    private val onTurnCompleted: (AgentSessionEvent.TurnCompleted) -> Unit,
    private val onTerminalFailure: (Throwable) -> Unit,
) {
    private val stateLock = Any()
    private val baseSession = session
    private val metricsCollector = ChatTurnMetricsCollector()
    private val generationStatsProjector = ChatSessionGenerationStatsProjector(
        session.generationStats,
    )
    private val assistantDeltas = CharacterAssistantDeltaAccumulator()
    private val phaseMarkerProjector = AssistantPhaseMarkerProjector()
    private var assistantDeltaFlushJob: Job? = null
    private var pending = pending
    private var timeline = listOf(
        CreationTimelineItem(
            id = "user-${pending.id}",
            kind = CreationTimelineKind.User,
            text = prompt,
            createdAtMillis = System.currentTimeMillis(),
        ),
    )
    private var planUpdateSequence = 0
    private var pendingRevision = 0L
    private var lastPublishedRevision = -1L
    private var actionCallCount = 0

    private var completionAtMillis: Long = 0L

    val observedCompletionAtMillis: Long
        get() = synchronized(stateLock) { completionAtMillis }

    fun pendingMessage(): ChatMessage = synchronized(stateLock) { pending }

    fun generationStats(): ChatSessionGenerationStats = synchronized(stateLock) {
        generationStatsProjector.snapshot()
    }

    fun flushPendingAssistantDelta() {
        synchronized(stateLock) { flushAssistantDeltasLocked() }
    }

    suspend fun accept(event: AgentSessionEvent) {
        if (event is AgentSessionEvent.TurnCompleted) {
            val snapshot = synchronized(stateLock) {
                flushAssistantDeltasLocked()
                completeTurn(event)
            }
            streamPublisher.stopAndFlush(snapshot)
            onTurnCompleted(event)
            return
        }

        var approvalRequestId: Long? = null
        synchronized(stateLock) {
            if (event !is AgentSessionEvent.AssistantDelta) flushAssistantDeltasLocked()
            event.agentRuntimeIdentity()?.let { identity ->
                replacePending { message ->
                    message.copy(
                        runtimeThreadId = identity.threadId,
                        runtimeTurnId = identity.turnId.ifBlank { message.runtimeTurnId },
                    )
                }
            }
            val metricsChanged = metricsCollector.accept(event)
            val generationStatsChanged = generationStatsProjector.accept(
                event = event,
                turnMetrics = metricsCollector.snapshot(),
                turnContextWindowUsage = metricsCollector.contextWindowUsage(),
            )
            if (metricsChanged || generationStatsChanged) {
                replacePending { message ->
                    message.copy(
                        generationMetrics = metricsCollector.snapshot(),
                        contextWindowUsage = metricsCollector.contextWindowUsage(),
                    )
                }
            }

            if ((metricsChanged || generationStatsChanged) && event !is AgentSessionEvent.AssistantDelta) {
                publish(force = true)
            }
            when (event) {
                is AgentSessionEvent.AssistantDelta -> if (event.visible) acceptAssistantDelta(event)
                is AgentSessionEvent.ReasoningSummaryDelta -> {
                    applyTimelineEvent(event)
                    publish()
                }
                is AgentSessionEvent.ReasoningTextDelta -> {
                    applyTimelineEvent(event)
                    publish()
                }
                is AgentSessionEvent.ModelHistoryItemCompleted -> {
                    replacePending { message ->
                        message.copy(
                            modelHistoryItems = message.modelHistoryItems + event.responseItemJson,
                        )
                    }
                }
                is AgentSessionEvent.WorkItemStarted -> {
                    applyTimelineEvent(event)
                    // A tool can finish before the conflated publisher gets a frame. Preserve the
                    // start boundary so every native work item visibly enters Running before settling.
                    publish(force = true, guaranteeDelivery = true)
                }
                is AgentSessionEvent.CommandOutput -> {
                    applyTimelineEvent(event)
                    publish()
                }
                is AgentSessionEvent.FileChangesUpdated -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.WorkItemProgress -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.WorkItemCompleted -> acceptWorkItemCompleted(event)
                is AgentSessionEvent.DelegatedSessionEvent -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.SessionFailed -> onTerminalFailure(
                    ElecKoiDataException(event.message),
                )
                is AgentSessionEvent.Warning -> acceptWarning(event)
                is AgentSessionEvent.ApprovalRequested -> {
                    acceptApprovalRequest(event)
                    approvalRequestId = event.requestId
                }
                is AgentSessionEvent.ApprovalResolved -> acceptApprovalResolution(event)
                is AgentSessionEvent.TurnStarted -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.StepStarted -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.StepCompleted -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.HostRequest,
                is AgentSessionEvent.TokenUsageUpdated,
                is AgentSessionEvent.ContextWindowUpdated,
                -> Unit
                is AgentSessionEvent.TurnDiffUpdated -> {
                    applyTimelineEvent(event)
                    publish(force = true)
                }
                is AgentSessionEvent.TurnCompleted -> error("TurnCompleted 已在锁外完成")
            }
        }
        approvalRequestId?.let { requestId -> runCatching { declineApproval(requestId) } }
    }

    private fun acceptAssistantDelta(event: AgentSessionEvent.AssistantDelta) {
        if (event.actionCalls.isNotEmpty()) {
            flushAssistantDeltasLocked()
            acceptAssistantDeltaImmediate(event)
            return
        }
        assistantDeltas.offer(event).forEach(::acceptAssistantDeltaImmediate)
        scheduleAssistantDeltaFlushLocked()
    }

    private fun acceptAssistantDeltaImmediate(event: AgentSessionEvent.AssistantDelta) {
        event.actionCalls.forEach { call ->
            actionCallCount += 1
            val now = System.currentTimeMillis()
            val timelineId = "action-${event.itemId}-$actionCallCount"
            val receipt = imageActions.accept(
                name = call.name,
                turnId = event.turnId,
                argumentsJson = call.argumentsJson,
            )
            CrashDiagnostics.memoryBreadcrumb(
                event = "roleplay_action_received",
                fields = mapOf(
                    "name" to call.name,
                    "accepted" to !receipt.failed,
                    "image_count" to imageActions.generatingAttachments().size,
                ),
            )
            appendTimelineItem(
                CreationTimelineItem(
                    id = timelineId,
                    kind = CreationTimelineKind.Tool,
                    text = receipt.label,
                    detail = receipt.failure,
                    running = !receipt.failed,
                    failed = receipt.failed,
                    workItemType = AgentWorkItemType.Action,
                    turnId = event.turnId,
                    createdAtMillis = now,
                    completedAtMillis = now.takeIf { receipt.failed },
                    toolName = call.name,
                    toolArguments = call.argumentsJson,
                ),
            )
            if (!receipt.failed && call.name == GenerateImageActionName) {
                replacePending { message ->
                    message.copy(imageAttachments = imageActions.generatingAttachments())
                }
            }
        }
        phaseMarkerProjector.recordAppend(
            itemId = "assistant-${event.itemId}",
            delta = event.delta,
        )
        applyTimelineEvent(event)
        // Marker-only phase transitions are real UI events too. Feature-level batching covers DSH
        // and every provider path, while the Responses bridge may already have coalesced fragments.
        publish(force = true)
    }

    private fun scheduleAssistantDeltaFlushLocked() {
        if (!assistantDeltas.hasPending() || assistantDeltaFlushJob?.isActive == true) return
        assistantDeltaFlushJob = scope.launch {
            delay(AssistantDeltaFrameMillis)
            synchronized(stateLock) {
                assistantDeltaFlushJob = null
                assistantDeltas.flush()?.let(::acceptAssistantDeltaImmediate)
            }
        }
    }

    private fun flushAssistantDeltasLocked() {
        assistantDeltaFlushJob?.cancel()
        assistantDeltaFlushJob = null
        assistantDeltas.flush()?.let(::acceptAssistantDeltaImmediate)
    }

    private fun acceptWorkItemCompleted(event: AgentSessionEvent.WorkItemCompleted) {
        val isPlanUpdate =
            event.toolName == AgentUpdatePlanTool ||
                event.toolName == AgentUpdateRoleplayPlanTool ||
                event.toolName == AgentTodoWriteTool
        // Completion-only Harness adapters need a synthetic chronological id. DSH emits a normal
        // start first, so keep that call id and close the existing row instead of duplicating it.
        val hasStartedPlanItem = isPlanUpdate && timeline.any { item ->
            item.workItemId == event.itemId
        }
        val timelineEvent = if (isPlanUpdate && !hasStartedPlanItem) {
            planUpdateSequence += 1
            event.copy(itemId = "update-plan-${pendingMessage().id}-$planUpdateSequence")
        } else {
            event
        }
        applyTimelineEvent(timelineEvent)
        publish(force = true)
    }

    private fun completeTurn(event: AgentSessionEvent.TurnCompleted): CharacterAgentTurnSnapshot {
        // Provider timestamps may describe an earlier upstream boundary. The running label uses
        // this device's clock, so its terminal boundary must use the same clock and never regress.
        completionAtMillis = stableChatTurnCompletionAtMillis(
            providerCompletedAtMillis = event.completedAtMillis,
            locallyObservedAtMillis = System.currentTimeMillis(),
        )
        timeline = CreationAgentTimelineReducer.finishTurn(
            timeline = timeline,
            status = event.status,
            turnId = event.turnId,
            completedAtMillis = completionAtMillis,
        )
        projectTimeline(turnRunning = false)
        val snapshot = snapshot()
        checkpointWriter.offer(snapshot)
        return snapshot
    }

    private fun acceptWarning(event: AgentSessionEvent.Warning) {
        agentWarningNotice(event.message)?.let { warning ->
            val now = System.currentTimeMillis()
            appendTimelineItem(
                CreationTimelineItem(
                    id = "warning-${timeline.size}",
                    kind = CreationTimelineKind.Tool,
                    text = "运行提示",
                    detail = warning,
                    failed = true,
                    workItemType = AgentWorkItemType.Unknown,
                    createdAtMillis = now,
                    completedAtMillis = now,
                ),
            )
            publish(force = true)
        }
    }

    private fun acceptApprovalRequest(event: AgentSessionEvent.ApprovalRequested) {
        appendTimelineItem(
            CreationTimelineItem(
                id = "approval-${event.requestId}",
                kind = CreationTimelineKind.Tool,
                text = event.title.ifBlank { "等待授权" },
                detail = event.detail,
                running = true,
                workItemType = AgentWorkItemType.Tool,
                createdAtMillis = System.currentTimeMillis(),
                rawCommand = event.rawCommand,
                commandActions = event.commandActions,
            ),
        )
        publish(force = true)
        // The suspendable decline is sent after leaving [stateLock].
    }

    private fun acceptApprovalResolution(event: AgentSessionEvent.ApprovalResolved) {
        updateTimelineItem("approval-${event.requestId}") { item ->
            item.copy(
                detail = "角色聊天暂未开放高风险操作授权，已安全拒绝",
                running = false,
                failed = true,
                completedAtMillis = System.currentTimeMillis(),
            )
        }
        publish(force = true)
    }

    private fun replacePending(transform: (ChatMessage) -> ChatMessage) {
        val next = transform(pending)
        if (next != pending) {
            pending = next
            pendingRevision += 1L
        }
    }

    private fun publish(
        force: Boolean = false,
        guaranteeDelivery: Boolean = false,
    ) {
        val message = pendingMessage()
        if (!message.hasRenderableContent()) return
        if (!force && pendingRevision == lastPublishedRevision) return
        lastPublishedRevision = pendingRevision
        val snapshot = snapshot()
        if (guaranteeDelivery) {
            streamPublisher.offerGuaranteed(snapshot)
        } else {
            streamPublisher.offer(snapshot)
        }
        checkpointWriter.offer(snapshot)
    }

    private fun snapshot(): CharacterAgentTurnSnapshot = CharacterAgentTurnSnapshot(
        baseSession = baseSession,
        pendingMessage = pending,
        generationStats = generationStatsProjector.snapshot(),
    )

    private fun projectTimeline(turnRunning: Boolean = true) {
        val currentVariableState = variableStateProvider?.invoke().orEmpty()
        replacePending { message ->
            val projected = message.withCreationAgentTimeline(
                timeline = timeline,
                turnRunning = turnRunning,
                phaseMarkerProjector = phaseMarkerProjector,
            )
            if (currentVariableState.isBlank()) {
                projected
            } else {
                projected.copy(variableStateJson = currentVariableState)
            }
        }
    }

    private fun applyTimelineEvent(event: AgentSessionEvent) {
        timeline = CreationAgentTimelineReducer.apply(timeline, event)
        projectTimeline()
    }

    private fun appendTimelineItem(item: CreationTimelineItem) {
        timeline = timeline + item
        projectTimeline()
    }

    private fun updateTimelineItem(
        id: String,
        transform: (CreationTimelineItem) -> CreationTimelineItem,
    ) {
        timeline = timeline.map { item -> if (item.id == id) transform(item) else item }
        projectTimeline()
    }

    private companion object {
        const val AssistantDeltaFrameMillis = 33L
    }
}

/** Lightweight producer snapshot; materialization keeps history as a shared immutable prefix. */
internal data class CharacterAgentTurnSnapshot(
    val baseSession: ChatSession,
    val pendingMessage: ChatMessage,
    val generationStats: ChatSessionGenerationStats,
) {
    fun materialize(): ChatSession = baseSession.copy(
        messages = ImmutableAppendedList(baseSession.messages, pendingMessage),
        generationStats = generationStats,
    )
}
