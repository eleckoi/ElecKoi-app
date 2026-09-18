package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.api.AgentApprovalDecision
import com.eleckoi.android.engine.agent.api.AgentApprovalKind
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentHarnessId
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentSession
import com.eleckoi.android.engine.agent.api.AgentPrompt
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentSessionState
import com.eleckoi.android.engine.agent.api.AgentSubagentTool
import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.engine.agent.api.AgentTurnHandle
import com.eleckoi.android.engine.agent.api.AgentTurnSteerUnavailableException
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekApprovalOutcome
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessEventMapper
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHistoryEncoding
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekNotification
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekPromptMode
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekPermissionPreset
import com.eleckoi.android.engine.agent.deepseek.protocol.withDeepSeekProtocolTimeout
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal class DeepSeekAgentSession(
    private val options: AgentSessionOptions,
    private val scope: CoroutineScope,
    private val backendFactory: DeepSeekSessionBackendFactory,
) : AgentSession {
    private val actionMutex = Mutex()
    private val eventMapper = DeepSeekHarnessEventMapper()
    private val _state = MutableStateFlow<AgentSessionState>(AgentSessionState.Stopped)
    private val _events = MutableSharedFlow<AgentSessionEvent>(extraBufferCapacity = EventBufferCapacity)
    private var backend: PreparedDeepSeekBackend? = null
    private var notificationJob: Job? = null
    private var failureJob: Job? = null
    private var adapterFailureJob: Job? = null
    private var contextPressureJob: Job? = null
    private var turnWatchdogJob: Job? = null
    private var threadId: String? = null
    private var pendingTurnStart: CompletableDeferred<AgentSessionEvent.TurnStarted>? = null
    private var requestCaptureId: String? = null
    private var pendingUserHistoryText: String? = null
    private val pendingSubagentCalls = mutableMapOf<String, ArrayDeque<String>>()
    private val subagentLineageBySession = mutableMapOf<String, List<String>>()
    private val pendingApprovalIds = ConcurrentHashMap.newKeySet<Long>()

    override val state: StateFlow<AgentSessionState> = _state.asStateFlow()
    override val events: SharedFlow<AgentSessionEvent> = _events.asSharedFlow()

    override suspend fun start() = actionMutex.withLock {
        check(_state.value == AgentSessionState.Stopped) { "Agent 会话已经启动" }
        require(options.workspaceId.isNotBlank() && options.conversationId.isNotBlank()) { "Agent 会话标识无效" }
        require(options.maxTurnDurationMillis > 0L) { "Agent 单回合时限必须大于 0" }
        val selectedThread = resolveThreadId(options.threadStart)
        _state.value = AgentSessionState.Starting
        try {
            val prepared = backendFactory.prepare(options, scope)
            backend = prepared
            notificationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                prepared.client.notifications.collect(::handleNotification)
            }
            failureJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                prepared.client.failures.collect(::failSession)
            }
            adapterFailureJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                prepared.turnFailures.collect(::failSession)
            }
            contextPressureJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                prepared.contextPressures.collect(::publishContextPressure)
            }
            if (options.discardThreadIds.isNotEmpty()) {
                prepared.discardSessions(options.discardThreadIds - selectedThread)
            }
            threadId = selectedThread
            prepared.bindSession(selectedThread)
            if (!prepared.clientAlreadyStarted) {
                prepared.client.start(
                    cwd = RuntimeWorkspace,
                    provider = prepared.provider,
                    model = prepared.model,
                    maxTokens = prepared.maxTokens,
                )
            }
            val requestedPreset = options.permissionMode.toDeepSeekPermissionPreset()
            val effectivePreset = prepared.client.setPermission(
                sessionId = selectedThread,
                cwd = prepared.sessionCwd,
                preset = requestedPreset,
            )
            check(effectivePreset == requestedPreset) { "DSH 未应用请求的权限模式" }
            _state.value = AgentSessionState.Ready(AgentHarnessId.DeepSeek, selectedThread)
        } catch (error: Throwable) {
            _state.value = AgentSessionState.Failed(error.message ?: "DeepSeek Harness 会话启动失败")
            closeBackend()
            throw error
        }
    }

    override suspend fun send(text: String, contextInjections: List<AgentContextInjection>): AgentTurnHandle =
        send(AgentPrompt(text), contextInjections)

    override suspend fun send(prompt: AgentPrompt, contextInjections: List<AgentContextInjection>): AgentTurnHandle =
        actionMutex.withLock {
            val ready = _state.value as? AgentSessionState.Ready
                ?: error("Agent 会话尚未就绪或正在执行任务")
            require(prompt.text.isNotBlank() || prompt.images.isNotEmpty()) { "Agent 消息不能为空" }
            val prepared = requireNotNull(backend)
            requestCaptureId = prepared.beginTurn(
                userMessage = prompt.text,
                history = options.initialHistoryItems,
                contextInjections = contextInjections,
            )
            pendingUserHistoryText = prompt.text
            val started = CompletableDeferred<AgentSessionEvent.TurnStarted>()
            check(pendingTurnStart == null) { "DeepSeek Harness 已有等待启动的回合" }
            pendingTurnStart = started
            val messageId = try {
                prepared.client.prompt(
                    sessionId = ready.threadId,
                    text = prompt.text,
                    images = prompt.images,
                    cwd = prepared.sessionCwd,
                )
            } catch (error: Throwable) {
                abandonTurnStart(error)
                throw error
            }
            val turn = try {
                withDeepSeekProtocolTimeout(
                    timeoutMillis = TurnStartTimeoutMillis,
                    operation = "等待 DeepSeek Harness 建立回合",
                ) { started.await() }
            } catch (error: Throwable) {
                abandonTurnStart(error)
                throw error
            }
            AgentTurnHandle(turn.threadId, turn.turnId, messageId)
        }

    override suspend fun steer(text: String): AgentTurnHandle = actionMutex.withLock {
        val running = _state.value as? AgentSessionState.Running
            ?: throw AgentTurnSteerUnavailableException("当前没有可追加指令的 DSH 回合")
        require(text.isNotBlank()) { "追加指令不能为空" }
        val messageId = requireNotNull(backend).client.prompt(
            sessionId = running.threadId,
            text = text,
            mode = DeepSeekPromptMode.Steer,
            cwd = requireNotNull(backend).sessionCwd,
        )
        AgentTurnHandle(running.threadId, running.turnId, messageId)
    }

    override suspend fun updatePermissionMode(permissionMode: AgentPermissionMode) = actionMutex.withLock {
        check(_state.value != AgentSessionState.Stopped && _state.value !is AgentSessionState.Failed) {
            "DeepSeek Harness 会话尚未启动"
        }
        val prepared = requireNotNull(backend)
        val activeThreadId = requireNotNull(threadId)
        val requestedPreset = permissionMode.toDeepSeekPermissionPreset()
        val effectivePreset = prepared.client.setPermission(
            sessionId = activeThreadId,
            cwd = prepared.sessionCwd,
            preset = requestedPreset,
        )
        check(effectivePreset == requestedPreset) { "DSH 未应用请求的权限模式" }
    }

    override suspend fun interrupt() = actionMutex.withLock {
        val running = _state.value as? AgentSessionState.Running ?: return@withLock
        _state.value = AgentSessionState.Stopping(AgentHarnessId.DeepSeek, running.threadId, running.turnId)
        val accepted = requireNotNull(backend).client.cancel(running.threadId)
        if (!accepted) {
            _state.value = AgentSessionState.Running(
                AgentHarnessId.DeepSeek,
                running.threadId,
                running.turnId,
            )
            error("DSH 找不到当前会话，无法取消活动回合")
        }
    }

    override suspend fun resolveApproval(requestId: Long, decision: AgentApprovalDecision) = actionMutex.withLock {
        check(requestId in pendingApprovalIds) { "DSH 审批请求已处理或不存在" }
        val outcome = when (decision) {
            AgentApprovalDecision.Accept -> DeepSeekApprovalOutcome.AllowedOnce
            AgentApprovalDecision.Decline -> DeepSeekApprovalOutcome.Rejected
            AgentApprovalDecision.Cancel -> DeepSeekApprovalOutcome.Cancelled
            AgentApprovalDecision.AcceptForSession -> error("DSH 沙箱只支持单次放行")
        }
        check(requireNotNull(backend).client.resolveApproval(requestId, outcome)) {
            "DSH 审批请求已失效"
        }
    }

    override suspend fun shutdown() = actionMutex.withLock {
        closeBackend()
        _state.value = AgentSessionState.Stopped
        scope.cancel()
    }

    private suspend fun handleNotification(notification: DeepSeekNotification) {
        if (notification.method == "session.approval_requested") {
            handleApprovalRequested(notification)
            return
        }
        if (notification.method == "session.approval_resolved") {
            handleApprovalResolved(notification)
            return
        }
        if (notification.method == "subagent.started") {
            bindStartedSubagent(notification)
            return
        }
        if (notification.method == "subagent.finished") return
        if (notification.method == "session.status") {
            val notificationSessionId = notification.params.stringValue("sessionId")
            if (notificationSessionId != threadId) return
        }
        if (notification.method == "session.event" || notification.method == "agent.assistant-stream") {
            val notificationSessionId = notification.params.stringValue("sessionId") ?: return
            if (notificationSessionId != threadId) {
                val lineage = subagentLineageBySession[notificationSessionId] ?: return
                eventMapper.map(notification).forEach { childEvent ->
                    registerPendingSubagentCall(childEvent)
                    _events.emit(
                        AgentSessionEvent.DelegatedSessionEvent(
                            lineage = lineage,
                            childSessionId = notificationSessionId,
                            event = childEvent.withDelegatedModel(backend?.subagentModel.orEmpty()),
                        ),
                    )
                }
                return
            }
        }
        eventMapper.map(notification).forEach { mappedEvent ->
            val event = mappedEvent.withDelegatedModel(backend?.subagentModel.orEmpty())
            registerPendingSubagentCall(event)
            when (event) {
                is AgentSessionEvent.TurnStarted -> {
                    _state.value = AgentSessionState.Running(
                        AgentHarnessId.DeepSeek,
                        event.threadId,
                        event.turnId,
                    )
                    requestCaptureId?.let { captureId -> backend?.bindTurn(captureId, event.turnId) }
                    pendingTurnStart?.complete(event)
                    pendingTurnStart = null
                    armTurnWatchdog(event)
                }
                is AgentSessionEvent.TurnCompleted -> {
                    turnWatchdogJob?.cancel()
                    turnWatchdogJob = null
                    backend?.endTurn()
                    requestCaptureId = null
                    if (_state.value !is AgentSessionState.Failed) {
                        _state.value = AgentSessionState.Ready(AgentHarnessId.DeepSeek, event.threadId)
                    }
                }
                else -> Unit
            }
            if (event !is AgentSessionEvent.ModelHistoryItemCompleted || options.captureModelHistory) {
                _events.emit(event)
            }
            if (event is AgentSessionEvent.TurnStarted) {
                val historyText = pendingUserHistoryText
                pendingUserHistoryText = null
                if (options.captureModelHistory && historyText != null) {
                    _events.emit(
                        AgentSessionEvent.ModelHistoryItemCompleted(
                            event.threadId,
                            event.turnId,
                            DeepSeekHistoryEncoding.responseMessage("user", historyText),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleApprovalRequested(notification: DeepSeekNotification) {
        if (notification.params.stringValue("ownerSessionId") != threadId) return
        val running = _state.value as? AgentSessionState.Running ?: return
        val requestId = notification.params.longValue("requestId")?.takeIf { it > 0L } ?: return
        if (!pendingApprovalIds.add(requestId)) return
        val toolName = notification.params.stringValue("toolName").orEmpty()
        val callId = notification.params.stringValue("callId")
        val reason = notification.params.stringValue("reason")
        val kind = when (toolName.lowercase()) {
            "bash" -> AgentApprovalKind.Command
            "write", "edit" -> AgentApprovalKind.FileChange
            else -> AgentApprovalKind.Other
        }
        val title = when (kind) {
            AgentApprovalKind.Command -> "允许执行受限命令？"
            AgentApprovalKind.FileChange -> "允许访问工作区外文件？"
            AgentApprovalKind.Other -> "允许扩大文件访问范围？"
        }
        _events.emit(
            AgentSessionEvent.ApprovalRequested(
                requestId = requestId,
                kind = kind,
                threadId = running.threadId,
                turnId = running.turnId,
                itemId = callId ?: "approval-$requestId",
                title = title,
                detail = reason ?: "DSH 请求临时扩大文件访问范围",
                availableDecisions = listOf(
                    AgentApprovalDecision.Accept,
                    AgentApprovalDecision.Decline,
                    AgentApprovalDecision.Cancel,
                ),
            ),
        )
    }

    private suspend fun handleApprovalResolved(notification: DeepSeekNotification) {
        if (notification.params.stringValue("ownerSessionId") != threadId) return
        val requestId = notification.params.longValue("requestId")?.takeIf { it > 0L } ?: return
        if (!pendingApprovalIds.remove(requestId)) return
        _events.emit(AgentSessionEvent.ApprovalResolved(requestId, requireNotNull(threadId)))
    }

    private fun bindStartedSubagent(notification: DeepSeekNotification) {
        val parentSessionId = notification.params.stringValue("parentSessionId") ?: return
        val childSessionId = notification.params.stringValue("childSessionId") ?: return
        val parentLineage = if (parentSessionId == threadId) {
            emptyList()
        } else {
            subagentLineageBySession[parentSessionId] ?: return
        }
        val parentCallId = pendingSubagentCalls[parentSessionId]
            ?.pollFirst()
            ?: return
        subagentLineageBySession[childSessionId] = parentLineage + parentCallId
    }

    private fun registerPendingSubagentCall(event: AgentSessionEvent) {
        val started = event as? AgentSessionEvent.WorkItemStarted ?: return
        if (started.toolName != AgentSubagentTool) return
        pendingSubagentCalls
            .getOrPut(started.threadId, ::ArrayDeque)
            .addLast(started.itemId)
    }

    private fun kotlinx.serialization.json.JsonObject.stringValue(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.longValue(name: String): Long? =
        (get(name) as? JsonPrimitive)?.longOrNull

    private fun AgentPermissionMode.toDeepSeekPermissionPreset(): DeepSeekPermissionPreset = when (this) {
        AgentPermissionMode.AskForApproval -> DeepSeekPermissionPreset.AskForApproval
        AgentPermissionMode.ApproveForMe -> DeepSeekPermissionPreset.ApproveForMe
        AgentPermissionMode.FullAccess -> DeepSeekPermissionPreset.FullAccess
    }

    private fun AgentSessionEvent.withDelegatedModel(model: String): AgentSessionEvent = when (this) {
        is AgentSessionEvent.WorkItemStarted -> if (toolName == AgentSubagentTool) {
            copy(delegatedModel = model)
        } else {
            this
        }
        is AgentSessionEvent.WorkItemCompleted -> if (toolName == AgentSubagentTool) {
            copy(delegatedModel = model)
        } else {
            this
        }
        else -> this
    }

    private fun resolveThreadId(start: AgentThreadStart): String = when (start) {
        AgentThreadStart.BoundOrNew -> stableSessionId(options.workspaceId, options.conversationId)
        is AgentThreadStart.Resume -> start.threadId.also(::requireSafeSessionId)
        AgentThreadStart.Fresh -> "eleckoi-${UUID.randomUUID()}"
        is AgentThreadStart.Fork,
        is AgentThreadStart.ReplaceFrom,
        -> error("DeepSeek Harness SDK 当前不支持会话分叉")
    }

    private fun stableSessionId(workspaceId: String, conversationId: String): String =
        "eleckoi-${UUID.nameUUIDFromBytes("$workspaceId\u0000$conversationId".toByteArray(StandardCharsets.UTF_8))}"

    private fun requireSafeSessionId(value: String) {
        require(SessionId.matches(value)) { "DeepSeek Harness sessionId 无效" }
    }

    private suspend fun failSession(message: String) {
        if (_state.value == AgentSessionState.Stopped || _state.value is AgentSessionState.Failed) return
        pendingTurnStart?.completeExceptionally(IllegalStateException(message))
        pendingTurnStart = null
        _state.value = AgentSessionState.Failed(message)
        _events.emit(AgentSessionEvent.SessionFailed(message))
        actionMutex.withLock {
            withContext(NonCancellable) {
                closeBackend()
            }
        }
    }

    private suspend fun publishContextPressure(sample: DeepSeekContextPressure) {
        if (sample.sessionId != threadId) return
        val runningTurnId = (_state.value as? AgentSessionState.Running)?.turnId
        _events.emit(
            AgentSessionEvent.ContextWindowUpdated(
                threadId = sample.sessionId,
                turnId = runningTurnId,
                pressureTokens = sample.pressureTokens,
                projectedTokens = sample.projectedTokens,
                modelContextWindow = sample.contextWindow,
                systemTokens = sample.systemTokens,
                toolsTokens = sample.toolsTokens,
                messageTokens = sample.messageTokens,
            ),
        )
    }

    private suspend fun abandonTurnStart(error: Throwable) {
        pendingTurnStart = null
        requestCaptureId = null
        pendingUserHistoryText = null
        backend?.endTurn()
        if (_state.value !is AgentSessionState.Failed) {
            val message = error.message ?: "DeepSeek Harness 回合启动失败"
            _state.value = AgentSessionState.Failed(message)
            _events.emit(AgentSessionEvent.SessionFailed(message))
        }
        withContext(NonCancellable) {
            closeBackend()
        }
    }

    private fun armTurnWatchdog(turn: AgentSessionEvent.TurnStarted) {
        turnWatchdogJob?.cancel()
        turnWatchdogJob = scope.launch {
            delay(options.maxTurnDurationMillis)
            actionMutex.withLock {
                val running = _state.value as? AgentSessionState.Running
                if (running?.turnId != turn.turnId) return@withLock
                turnWatchdogJob = null
                _state.value = AgentSessionState.Stopping(AgentHarnessId.DeepSeek, turn.threadId, turn.turnId)
                val accepted = runCatching { backend?.client?.cancel(turn.threadId) == true }.getOrDefault(false)
                if (!accepted) {
                    backend?.abort()
                    closeBackend()
                    val message = "DSH 回合超过 ${options.maxTurnDurationMillis} ms，且无法取消，已终止运行时"
                    _events.emit(
                        AgentSessionEvent.TurnCompleted(
                            threadId = turn.threadId,
                            turnId = turn.turnId,
                            status = com.eleckoi.android.engine.agent.api.AgentWorkStatus.Failed,
                            errorMessage = message,
                            completedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    _events.emit(AgentSessionEvent.SessionFailed(message))
                    _state.value = AgentSessionState.Failed(message)
                }
            }
        }
    }

    private suspend fun closeBackend() = withContext(NonCancellable) {
        turnWatchdogJob?.cancel()
        turnWatchdogJob = null
        notificationJob?.cancel()
        notificationJob = null
        failureJob?.cancel()
        failureJob = null
        adapterFailureJob?.cancel()
        adapterFailureJob = null
        contextPressureJob?.cancel()
        contextPressureJob = null
        requestCaptureId = null
        pendingUserHistoryText = null
        pendingTurnStart?.cancel()
        pendingTurnStart = null
        pendingApprovalIds.clear()
        runCatching { backend?.close() }
        backend = null
    }

    private companion object {
        const val EventBufferCapacity = 256
        const val TurnStartTimeoutMillis = 20_000L
        const val RuntimeWorkspace = "/workspace"
        val SessionId = Regex("^[A-Za-z0-9._:-]{1,160}$")
    }
}
