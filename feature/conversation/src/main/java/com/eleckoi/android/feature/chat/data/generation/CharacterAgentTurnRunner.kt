package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentApprovalDecision
import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.api.AgentPrompt
import com.eleckoi.android.engine.agent.api.AgentSession
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentTurnHandle
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.runtime.model.connectAndAwaitRuntimeReady
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.content.ToolCallState
import com.eleckoi.android.feature.chat.roleplay.actions.RoleplayImageActionController
import com.eleckoi.android.feature.chat.roleplay.actions.reconcileGenerateImageActionState
import com.eleckoi.android.foundation.diagnostics.CrashDiagnostics
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the transport-facing lifecycle of one Agent turn. */
internal class CharacterAgentTurnRunner(
    private val sessions: ChatSessionStore,
    private val regexRules: com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository,
    private val runtime: LocalRuntimeGateway,
    private val agentSessions: AgentSessionFactory,
    private val prepareDraftProjection: (ChatSession, ModelConfig) -> (ChatSession) -> ChatDraft,
    private val replyImageGenerator: ReplyImageGenerator,
    private val generationAttempts: GenerationAttemptRepository,
    private val publishRemoteDshTurnImages: (String, List<AgentInputImage>) -> Unit,
    private val captureProviderRequests: Boolean,
    private val generations: GenerationLeaseRegistry,
    private val cancellationScope: CoroutineScope,
    private val turnPreparer: CharacterAgentTurnPreparer,
    private val turnCommitter: CharacterAgentTurnCommitter,
    private val activeSession: AtomicReference<AgentSession?>,
) {
    suspend fun run(
        session: ChatSession,
        prompt: AgentPrompt,
        config: ModelConfig,
        replacementMessageId: String?,
        obsoleteRuntimeThreadIds: Set<String> = emptySet(),
        onDelta: (ChatDraft) -> Unit,
    ): ChatSendResult = coroutineScope {
        val turnScope = this
        val lease = generations.begin(session.id)
        val draftProjection = CharacterAgentTurnDraftProjection(
            initialSession = session,
            config = config,
            prepare = prepareDraftProjection,
        )
        val checkpointWriter = CharacterGenerationCheckpointWriter<CharacterAgentTurnSnapshot>(
            scope = this,
            persist = { snapshot ->
                sessions.checkpointAssistantResponse(snapshot.materialize())
            },
        )
        val pending = pendingAssistantMessage(
            id = replacementMessageId ?: com.eleckoi.android.foundation.storage.newId(10),
            session = session,
            config = config,
        )
        val streamPublisher = LatestStreamSnapshotPublisher<CharacterAgentTurnSnapshot>(
            scope = turnScope,
            emit = { snapshot ->
                emitDelta(lease, onDelta, draftProjection.project(snapshot.materialize()))
            },
        )
        // Sending publishes the real user turn immediately. Regeneration's truncated branch is
        // already published by the coordinator; this transport-only pending row stays internal.
        if (replacementMessageId == null) {
            emitDelta(lease, onDelta, draftProjection.project(session))
        }
        val preparedTurn = turnPreparer.prepare(
            session = session,
            config = config,
            replacementMessageId = replacementMessageId,
            obsoleteRuntimeThreadIds = obsoleteRuntimeThreadIds,
        )
        val imageConfig = preparedTurn.imageConfig
        val variableTurnState = preparedTurn.variableTurnState
        val remoteDshConversationId = preparedTurn.options.conversationId
        val agent = agentSessions.create(preparedTurn.options)
        activeSession.set(agent)
        lease.invokeOnCancel {
            cancellationScope.launch { runCatching { agent.interrupt() } }
        }

        val turnResults = Channel<AgentSessionEvent.TurnCompleted>(Channel.UNLIMITED)
        val terminalFailure = CompletableDeferred<Throwable>()
        val replyOwnerMessageId = session.messages.asReversed()
            .firstOrNull { it.role == MessageRole.User }
            ?.id
            ?: throw ElecKoiDataException("AI 回复没有对应的用户回合")
        val replyAttempt = generationAttempts.beginReply(
            conversationId = session.id,
            userMessageId = replyOwnerMessageId,
            outputMessageId = pending.id,
        )
        val imageActions = RoleplayImageActionController(
            imageConfig = imageConfig,
            scope = turnScope,
            generator = replyImageGenerator,
            sessionId = session.id,
            messageId = pending.id,
            parentAttemptId = replyAttempt.id,
            generationAttempts = generationAttempts,
            characterImagePrompt = session.characterPersona.imagePrompt,
            onRequestCapture = if (
                captureProviderRequests || AgentRequestDiagnostics.captureEnabled.value
            ) {
                { turnId, capture ->
                    AgentRequestDiagnostics.recordAuxiliaryRequest(
                        workspaceId = session.workspaceId,
                        conversationId = roleConversationId(session.id),
                        runtimeTurnId = turnId,
                        label = capture.label,
                        logicalRequestBody = capture.logicalRequestBody,
                        providerRequestBody = capture.providerRequestBody,
                    )
                }
            } else {
                null
            },
        )
        lease.invokeOnCancel {
            imageActions.cancel()
            cancellationScope.launch { runCatching { agent.interrupt() } }
        }
        CrashDiagnostics.memoryBreadcrumb(
            event = "roleplay_generation_started",
            fields = mapOf(
                "image_enabled" to (imageConfig != null),
                "provider" to config.provider,
            ),
        )
        val projector = CharacterAgentTurnProjector(
            scope = turnScope,
            session = session,
            pending = pending,
            prompt = prompt.text,
            imageActions = imageActions,
            checkpointWriter = checkpointWriter,
            streamPublisher = streamPublisher,
            variableStateProvider = variableTurnState?.let { turnState ->
                { turnState.stateJson }
            },
            declineApproval = { requestId ->
                agent.resolveApproval(requestId, AgentApprovalDecision.Decline)
            },
            onTurnCompleted = { event -> turnResults.trySend(event) },
            onTerminalFailure = { error -> terminalFailure.complete(error) },
        )
        val collector: Job = launch(start = CoroutineStart.UNDISPATCHED) {
            agent.events.collect { event -> projector.accept(event) }
        }

        var activeTurn: AgentTurnHandle? = null
        publishRemoteDshTurnImages(remoteDshConversationId, prompt.images)
        try {
            runtime.connectAndAwaitRuntimeReady()
            ensureActive(lease)
            agent.start()
            ensureActive(lease)
            activeTurn = agent.send(prompt, preparedTurn.contextInjections)
            suspend fun awaitTurnCompletion(): AgentSessionEvent.TurnCompleted =
                kotlinx.coroutines.selects.select {
                    turnResults.onReceive { it }
                    terminalFailure.onAwait { throw it }
                }
            val completed = awaitTurnCompletion()
            CrashDiagnostics.memoryBreadcrumb(
                event = "roleplay_model_turn_completed",
                fields = mapOf("status" to completed.status.name),
            )
            ensureActive(lease)
            val projectedMessage = projector.pendingMessage()
            val finishedMessage = projectedMessage.copy(
                pending = false,
                createdAt = nowIso(),
                turnCompletedAtMillis = projector.observedCompletionAtMillis
                    .takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                toolCalls = projectedMessage.toolCalls.map { call ->
                    if (call.state == ToolCallState.Running || call.state == ToolCallState.Pending) {
                        if (
                            call.workItemType == AgentWorkItemType.Action &&
                            completed.status == AgentWorkStatus.Completed
                        ) {
                            call
                        } else call.copy(
                            result = call.result.ifBlank { completed.errorMessage.orEmpty() },
                            state = if (completed.status == AgentWorkStatus.Completed) {
                                ToolCallState.Succeeded
                            } else {
                                ToolCallState.Failed
                            },
                        )
                    } else {
                        call
                    }
                },
            )
            if (completed.status != AgentWorkStatus.Completed) {
                throw ElecKoiDataException(
                    completed.errorMessage?.takeIf(String::isNotBlank) ?: "Agent 回合未完成",
                )
            }
            val committedVariableState = variableTurnState?.stateJson ?: session.variableStateJson
            val committedImageAttachments = imageActions
                .attachmentsForCompletedReply(finishedMessage.content.isNotBlank())
                .ifEmpty { finishedMessage.imageAttachments }
            val imageTerminalAt = System.currentTimeMillis()
            val regexConfig = regexRules.load(session.characterId)
            var committedAssistant = finishedMessage.copy(
                content = com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor.transform(
                    text = finishedMessage.content,
                    rules = regexRules.rulesFor(
                        regexConfig,
                        com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget.AiOutput,
                        com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleSurface.Stored,
                    ),
                    target = com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget.AiOutput,
                ),
                reasoningContent = com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor.transform(
                    text = finishedMessage.reasoningContent,
                    rules = regexRules.rulesFor(
                        regexConfig,
                        com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget.Reasoning,
                        com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleSurface.Stored,
                    ),
                    target = com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget.Reasoning,
                ),
                runtimeThreadId = completed.threadId,
                runtimeTurnId = completed.turnId,
                variableStateJson = committedVariableState,
                imageAttachments = committedImageAttachments,
                toolCalls = reconcileGenerateImageActionState(
                    toolCalls = finishedMessage.toolCalls,
                    imageAttachments = committedImageAttachments,
                    completedAtMillis = imageTerminalAt,
                ),
            )
            var finished = session.copy(
                messages = session.messages + committedAssistant,
                variableStateJson = committedVariableState,
                updatedAt = nowIso(),
                generationStats = projector.generationStats(),
            )
            checkpointWriter.stop()
            turnCommitter.commitActive(
                lease = lease,
                session = finished,
                terminalAttemptId = replyAttempt.id,
                terminalAttemptState = GenerationAttemptState.Succeeded,
            )
            var finalDraft = draftProjection.project(finished)
            emitDelta(lease, onDelta, finalDraft)

            imageActions.collectCompletionUpdates { attachments ->
                ensureActive(lease)
                val actionFinishedAt = System.currentTimeMillis()
                committedAssistant = committedAssistant.copy(
                    imageAttachments = attachments,
                    toolCalls = reconcileGenerateImageActionState(
                        toolCalls = committedAssistant.toolCalls,
                        imageAttachments = attachments,
                        completedAtMillis = actionFinishedAt,
                    ),
                )
                finished = finished.copy(
                    messages = finished.messages.dropLast(1) + committedAssistant,
                    updatedAt = nowIso(),
                )
                turnCommitter.commitActive(lease, finished)
                finalDraft = draftProjection.project(finished)
                emitDelta(lease, onDelta, finalDraft)
            }

            generations.finish(lease)
            ChatSendResult(finalDraft)
        } catch (error: Throwable) {
            CrashDiagnostics.memoryBreadcrumb(
                event = "roleplay_generation_failed",
                fields = mapOf(
                    "type" to error.javaClass.name,
                    "cancelled" to (error is kotlinx.coroutines.CancellationException),
                ),
            )
            projector.flushPendingAssistantDelta()
            streamPublisher.stop()
            checkpointWriter.stop()
            val cancelled = generations.isCancelled(lease) || error is kotlinx.coroutines.CancellationException
            val failureReason = if (cancelled) {
                "生成已停止"
            } else {
                error.message?.takeIf(String::isNotBlank) ?: "生成未完成"
            }
            val stopped = settleStoppedSession(
                session = session.copy(generationStats = projector.generationStats()),
                pending = projector.pendingMessage(),
                activeTurn = activeTurn,
                failureReason = failureReason,
            )
            withContext(NonCancellable) {
                val committed = generations.commitIfOwned(lease) {
                    turnCommitter.persistCompletedTail(
                        session = stopped,
                        terminalAttemptId = replyAttempt.id,
                        terminalAttemptState = if (cancelled) {
                            GenerationAttemptState.Cancelled
                        } else {
                            GenerationAttemptState.Failed
                        },
                        terminalAttemptError = failureReason,
                    )
                }
                if (committed) sessions.applyHistorySavePolicy(stopped.characterId)
            }
            generations.finish(lease)
            if (cancelled) {
                throw ElecKoiDataException(GenerationCancelled, error)
            }
            throw error
        } finally {
            // Terminal commits normally settle this first. This no-op fallback closes the narrow
            // race where a cancelled/superseded lease loses persistence ownership before its
            // catch block can write a terminal response.
            runCatching { generationAttempts.cancel(replyAttempt.id, "生成已停止") }
            checkpointWriter.stop()
            collector.cancelAndJoin()
            activeSession.compareAndSet(agent, null)
            withContext(NonCancellable) { runCatching { agent.shutdown() } }
            publishRemoteDshTurnImages(remoteDshConversationId, emptyList())
        }
    }

    private fun emitDelta(
        lease: GenerationLeaseRegistry.Lease,
        onDelta: (ChatDraft) -> Unit,
        draft: ChatDraft,
    ) {
        ensureActive(lease)
        onDelta(draft)
    }

    private fun ensureActive(lease: GenerationLeaseRegistry.Lease) {
        if (generations.isCancelled(lease)) throw ElecKoiDataException(GenerationCancelled)
    }
}
