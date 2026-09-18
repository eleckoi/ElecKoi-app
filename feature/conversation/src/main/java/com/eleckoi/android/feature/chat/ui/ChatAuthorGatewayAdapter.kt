package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.data.stream.isAppendOnlyUpdate
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.author.toAuthorSnapshot
import com.eleckoi.android.sdk.author.AuthorApiEvent
import com.eleckoi.android.sdk.author.AuthorChatGateway
import com.eleckoi.android.sdk.author.AuthorChatSnapshot
import com.eleckoi.android.sdk.author.AuthorCommandResult
import com.eleckoi.android.sdk.author.AuthorDeleteMessagesResult
import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorSendImageAttachment
import com.eleckoi.android.sdk.author.messages.toAuthorMessageJson
import com.eleckoi.android.sdk.author.messages.toAuthorProcessJson
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Translates the Author API contract into ViewModel intents.
 *
 * It never owns UI state; reads and updates are callbacks into ChatViewModel's
 * single MutableStateFlow owner.
 */
internal class ChatAuthorGatewayAdapter(
    private val state: () -> ChatUiState,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val actions: ChatAuthorActions,
    private val publisher: ChatAuthorEventPublisher,
) : AuthorChatGateway {
    override val authorEvents: SharedFlow<AuthorApiEvent> = publisher.events

    override fun snapshot(): AuthorChatSnapshot {
        val current = state()
        val sourceDraft = current.draft
        val draftSnapshot = sourceDraft?.toAuthorSnapshot()?.let { draft ->
            val presentation = current.generationPresentation
            val pendingId = presentation?.assistantMessageId
                ?.takeIf { presentation.sessionId == draft.session.id }
                ?.takeIf { id -> draft.session.messages.none { it.id == id } }
            if (pendingId == null) {
                draft
            } else {
                val selected = sourceDraft.selectedModelConfig
                draft.copy(
                    session = draft.session.copy(
                        messages = draft.session.messages + AuthorMessageSnapshot(
                            id = pendingId,
                            conversationId = draft.session.id,
                            role = "assistant",
                            content = "",
                            reasoningContent = "",
                            provider = selected.provider,
                            model = sourceDraft.selectedModel,
                            createdAt = "",
                            pending = current.isSending,
                            variableStateJson = draft.session.variableStateJson,
                        ),
                    ),
                )
            }
        }
        return AuthorChatSnapshot(
            draft = draftSnapshot,
            sessions = current.sessions.map { it.toAuthorSnapshot() },
            input = current.input,
            isGenerating = current.isSending,
            errorMessage = current.errorMessage,
            modelConfigs = current.modelConfigs,
            activeRunId = current.authorRunId(current.activeAuthorAssistant()),
            activeMessageId = current.generationPresentation?.assistantMessageId.orEmpty(),
            activeOutput = current.activeAuthorAssistant()?.content.orEmpty(),
        )
    }

    override fun setInput(value: String): AuthorCommandResult {
        if (state().isSending) return rejected("AI 正在生成，暂时不能修改输入框")
        updateState { it.copy(input = value) }
        return accepted()
    }

    override suspend fun send(
        text: String,
        attachments: List<AuthorSendImageAttachment>,
    ): AuthorCommandResult {
        val content = text.trim()
        val current = state()
        if (content.isBlank() && attachments.isEmpty()) return rejected("发送内容和图片不能同时为空")
        if (current.isSending) return rejected("AI 正在生成")
        if (current.draft == null) return rejected("聊天还没有加载完成")
        return actions.send(content, attachments).fold(
            onSuccess = { accepted("消息已提交") },
            onFailure = { error -> rejected(error.message ?: "图片处理失败") },
        )
    }

    override fun stopGeneration(): AuthorCommandResult {
        if (!state().isSending) return rejected("当前没有正在生成的回复")
        actions.stopGeneration()
        return accepted("已请求停止生成")
    }

    override fun regenerate(messageId: String): AuthorCommandResult {
        val current = state()
        if (current.isSending) return rejected("AI 正在生成")
        val message = current.draft?.session?.messages?.firstOrNull { it.id == messageId }
            ?: return rejected("没有找到消息：$messageId")
        if (message.role != MessageRole.Assistant) return rejected("只能重新生成 AI 消息")
        actions.regenerate(message)
        return accepted("已开始重新生成")
    }

    override fun editAndRegenerate(messageId: String, text: String): AuthorCommandResult {
        val replacement = text.trim()
        val current = state()
        if (replacement.isBlank()) return rejected("修改后的消息不能为空")
        if (current.isSending) return rejected("AI 正在生成")
        val message = current.draft?.session?.messages?.firstOrNull { it.id == messageId }
            ?: return rejected("没有找到消息：$messageId")
        if (message.role != MessageRole.User) return rejected("只能修改用户消息")
        updateState { it.copy(editingMessage = message, editInput = replacement) }
        actions.submitEditedMessage()
        return accepted("已修改消息并开始重新生成")
    }

    override suspend fun deleteMessagesFrom(messageId: String): AuthorDeleteMessagesResult {
        val current = state()
        if (current.isSending) throw IllegalStateException("AI 正在生成，暂时不能删除消息")
        val draft = current.draft ?: throw IllegalStateException("当前没有聊天上下文")
        val result = actions.deleteMessagesFrom(draft.session.id, messageId).getOrThrow()
        updateState { latest ->
            if (latest.draft?.session?.id == draft.session.id) latest.copy(draft = result.draft) else latest
        }
        com.eleckoi.android.feature.chat.ui.roleplay.web.host.RoleplayRichHeightCache
            .discardMessages(draft.session.id, result.deletedMessageIds)
        publisher.publishMessagesChanged(draft.session.id, "deleted", result.deletedMessageIds)
        return AuthorDeleteMessagesResult(result.deletedMessageIds.size, result.remainingMessageCount)
    }

    override fun createNewChat(characterId: String): AuthorCommandResult {
        val id = characterId.ifBlank { state().draft?.session?.characterId.orEmpty() }
        if (id.isBlank()) return rejected("角色 ID 不能为空")
        if (state().isSending) return rejected("AI 正在生成，暂时不能创建对话")
        actions.createChat(id)
        return accepted("正在创建新对话")
    }

    override fun openChat(sessionId: String): AuthorCommandResult {
        if (sessionId.isBlank()) return rejected("对话 ID 不能为空")
        val current = state()
        if (current.isSending) return rejected("AI 正在生成，暂时不能切换对话")
        if (current.sessions.none { it.id == sessionId } && current.draft?.session?.id != sessionId) {
            return rejected("没有找到对话：$sessionId")
        }
        actions.openChat(sessionId)
        return accepted("正在打开对话")
    }

    override fun deleteChat(sessionId: String): AuthorCommandResult {
        if (sessionId.isBlank()) return rejected("对话 ID 不能为空")
        val current = state()
        if (current.isSending) return rejected("AI 正在生成，暂时不能删除对话")
        if (current.sessions.none { it.id == sessionId } && current.draft?.session?.id != sessionId) {
            return rejected("没有找到对话：$sessionId")
        }
        actions.deleteChat(sessionId)
        return accepted("正在删除对话")
    }

    override fun selectModel(
        configId: String,
        model: String,
    ): AuthorCommandResult {
        val current = state()
        if (current.isSending) return rejected("AI 正在生成，暂时不能切换模型")
        if (current.draft == null) return rejected("当前没有聊天上下文")
        val config = current.modelConfigs.firstOrNull { it.id == configId }
            ?: return rejected("没有找到模型配置：$configId")
        val selectedModel = model.ifBlank { config.model }
        if (selectedModel.isBlank()) return rejected("模型名称不能为空")
        actions.selectModel(config.id, selectedModel)
        return accepted("正在切换聊天模型")
    }

    override suspend fun selectOpening(openingOptionId: String): AuthorCommandResult {
        val current = state()
        val draft = current.draft ?: return rejected("当前没有聊天上下文")
        if (current.isSending) return rejected("AI 正在生成，暂时不能更换开场白")
        if (!draft.openingSelectionEnabled) return rejected("当前不能更换开场白")
        if (draft.openingOptions.none { it.id == openingOptionId }) {
            return rejected("没有找到开场白：$openingOptionId")
        }
        if (draft.selectedOpeningOptionId == openingOptionId) {
            return accepted("当前已经是这条开场白")
        }
        val sessionId = draft.session.id
        return actions.selectOpening(sessionId, openingOptionId).fold(
            onSuccess = { next ->
                updateState { latest ->
                    if (latest.draft?.session?.id == sessionId) latest.copy(draft = next) else latest
                }
                accepted("开场白已更换")
            },
            onFailure = { error -> rejected(error.message ?: "更换开场白失败") },
        )
    }

    override suspend fun replaceVariableState(stateJson: String): AuthorCommandResult {
        val current = state()
        val sessionId = current.draft?.session?.id.orEmpty()
        if (sessionId.isBlank()) return rejected("当前没有聊天上下文")
        if (current.isSending) return rejected("AI 正在生成，暂时不能修改变量")
        return actions.replaceVariableState(sessionId, stateJson).fold(
            onSuccess = { draft ->
                updateState { it.copy(draft = draft) }
                accepted("变量状态已更新")
            },
            onFailure = { error -> rejected(error.message ?: "变量状态更新失败") },
        )
    }

    override suspend fun resetVariableState(): AuthorCommandResult {
        val current = state()
        val sessionId = current.draft?.session?.id.orEmpty()
        if (sessionId.isBlank()) return rejected("当前没有聊天上下文")
        if (current.isSending) return rejected("AI 正在生成，暂时不能重置变量")
        return actions.resetVariableState(sessionId).fold(
            onSuccess = { draft ->
                updateState { it.copy(draft = draft) }
                accepted("变量状态已重置")
            },
            onFailure = { error -> rejected(error.message ?: "变量状态重置失败") },
        )
    }

    private fun accepted(message: String = "") = AuthorCommandResult(accepted = true, message = message)

    private fun rejected(message: String) = AuthorCommandResult(accepted = false, message = message)
}

internal class ChatAuthorActions(
    val send: suspend (String, List<AuthorSendImageAttachment>) -> Result<Unit>,
    val stopGeneration: () -> Unit,
    val regenerate: (ChatMessage) -> Unit,
    val submitEditedMessage: () -> Unit,
    val createChat: (String) -> Unit,
    val openChat: (String) -> Unit,
    val deleteChat: (String) -> Unit,
    val selectModel: (String, String) -> Unit,
    val selectOpening: suspend (String, String) -> Result<ChatDraft>,
    val replaceVariableState: suspend (String, String) -> Result<ChatDraft>,
    val resetVariableState: suspend (String) -> Result<ChatDraft>,
    val deleteMessagesFrom: suspend (String, String) -> Result<com.eleckoi.android.feature.chat.data.ChatDeleteMessagesResult>,
)

/**
 * Projects UI-state transitions into Author API events without mutating state.
 */
internal class ChatAuthorEventPublisher(
    private val scope: CoroutineScope,
    private val states: StateFlow<ChatUiState>,
) {
    private val mutableEvents = MutableSharedFlow<AuthorApiEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val stopRequested = AtomicBoolean(false)
    private val outputSequences = mutableMapOf<String, Int>()
    val events: SharedFlow<AuthorApiEvent> = mutableEvents.asSharedFlow()

    fun start() {
        scope.launch {
            var previous = states.value
            states.drop(1).collect { current ->
                if (mutableEvents.subscriptionCount.value == 0) {
                    previous = current
                    return@collect
                }
                publishChanges(previous, current)
                previous = current
            }
        }
    }

    fun markStopRequested() {
        stopRequested.set(true)
        val current = states.value
        val conversationId = current.draft?.session?.id.orEmpty()
        if (conversationId.isNotBlank()) emitAgentState(conversationId, "stopping")
    }

    fun publishMessagesChanged(conversationId: String, reason: String, messageIds: List<String>) {
        require(conversationId.isNotBlank())
        require(reason in setOf("sent", "edited", "deleted", "regenerated"))
        require(messageIds.isNotEmpty() && messageIds.none(String::isBlank))
        emit("messages.changed", buildJsonObject {
            put("conversationId", conversationId)
            put("reason", reason)
            put("messageIds", buildJsonArray { messageIds.forEach { add(JsonPrimitive(it)) } })
        })
    }

    private fun publishChanges(previous: ChatUiState, current: ChatUiState) {
        val currentSession = current.draft?.session
        val conversationId = currentSession?.id.orEmpty()
        if (conversationId.isBlank()) return
        val currentMessages = currentSession?.messages.orEmpty()
        val previousAssistant = previous.activeAuthorAssistant()
        val currentAssistant = current.activeAuthorAssistant()
        val runId = current.authorRunId(currentAssistant)

        if (currentAssistant != null) {
            val previousSameMessage = previousAssistant?.takeIf { it.id == currentAssistant.id }
            val previousContent = previousSameMessage?.content.orEmpty()
            if (currentAssistant.pending && isAppendOnlyUpdate(previousContent, currentAssistant.content)) {
                val delta = currentAssistant.content.substring(previousContent.length)
                if (delta.isNotEmpty()) {
                    val sequence = outputSequences.merge(runId, 1, Int::plus) ?: 1
                    if (sequence == 1) emitAgentState(conversationId, "streaming")
                    emit("agent.output.delta", buildJsonObject {
                        put("conversationId", conversationId)
                        put("runId", runId)
                        put("messageId", currentAssistant.id)
                        put("sequence", sequence)
                        put("delta", delta)
                    })
                }
            }
            publishProcessChanges(conversationId, runId, previousSameMessage, currentAssistant)
            if (currentAssistant.generationMetrics != previousSameMessage?.generationMetrics) {
                emit("agent.generation.stats", buildJsonObject {
                    put("conversationId", conversationId)
                    put("runId", runId)
                    put("stats", currentAssistant.generationMetrics.toAuthorStatsJson(currentAssistant))
                })
            }
        }

        if (current.isSending != previous.isSending) {
            when {
                current.isSending -> emitAgentState(conversationId, "starting")
                current.errorMessage.isNotBlank() -> {
                    emit(
                        "agent.run.failed",
                        buildJsonObject {
                            put("conversationId", conversationId)
                            put("runId", runId)
                            put("messageId", currentAssistant?.id.orEmpty())
                            put("code", "GENERATION_FAILED")
                            put("message", current.errorMessage)
                        },
                    )
                    emitAgentState(conversationId, "error", current.errorMessage)
                    outputSequences.remove(runId)
                }
                stopRequested.getAndSet(false) -> {
                    emitAgentState(conversationId, "idle", "stopped")
                    outputSequences.remove(runId)
                }
                currentAssistant != null -> {
                    val sequence = currentMessages.indexOfFirst { it.id == currentAssistant.id }
                        .takeIf { it >= 0 }
                    emit("agent.run.finished", buildJsonObject {
                        put("conversationId", conversationId)
                        put("runId", runId)
                        put(
                            "message",
                            currentAssistant.toAuthorSnapshot(conversationId, sequence).toAuthorMessageJson(),
                        )
                    })
                    emitAgentState(conversationId, "idle")
                    outputSequences.remove(runId)
                }
                else -> emitAgentState(conversationId, "idle")
            }
        }
    }

    private fun publishProcessChanges(
        conversationId: String,
        runId: String,
        previous: ChatMessage?,
        current: ChatMessage,
    ) {
        val previousItems = previous
            ?.toAuthorSnapshot(conversationId)
            ?.process
            .orEmpty()
            .associateBy { it.id }
        current.toAuthorSnapshot(conversationId).process.forEach { item ->
            if (previousItems[item.id] != item) {
                emit("agent.process.updated", buildJsonObject {
                    put("conversationId", conversationId)
                    put("runId", runId)
                    put("messageId", current.id)
                    put("item", item.toAuthorProcessJson())
                })
            }
        }
    }

    private fun emitAgentState(conversationId: String, state: String, detail: String = "") {
        emit("agent.state.changed", buildJsonObject {
            put("conversationId", conversationId)
            put("state", state)
            if (detail.isNotBlank()) put("detail", detail)
        })
    }

    private fun emit(name: String, payload: kotlinx.serialization.json.JsonElement) {
        mutableEvents.tryEmit(AuthorApiEvent(name, payload))
    }

}

private fun ChatUiState.activeAuthorAssistant(): ChatMessage? {
    val session = draft?.session ?: return null
    val ownedId = generationPresentation
        ?.takeIf { it.sessionId == session.id }
        ?.assistantMessageId
    return ownedId?.let { id -> session.messages.firstOrNull { it.id == id } }
        ?: session.messages.lastOrNull { it.role == MessageRole.Assistant }
}

private fun ChatUiState.authorRunId(message: ChatMessage?): String {
    val presentation = generationPresentation
    if (presentation != null) {
        return "${presentation.sessionId}:${presentation.generation}"
    }
    return message?.runtimeTurnId?.takeIf(String::isNotBlank)
        ?: message?.id.orEmpty()
}

private fun ChatGenerationMetrics.toAuthorStatsJson(message: ChatMessage) = buildJsonObject {
    put("turns", turns)
    put("steps", steps)
    put("llmMs", llmDurationMillis)
    put("toolMs", toolDurationMillis)
    put("ttftMs", firstTokenDelayMillis)
    put("ttftSteps", firstTokenSamples)
    put("decodeMs", decodeDurationMillis)
    put("decodeTokens", decodeOutputTokens)
    put("tokenUsage", buildJsonObject {
        put("uncachedInputTokens", inputTokens)
        put("outputTokens", outputTokens)
        put("cacheReadTokens", cacheReadTokens)
        put("cacheWriteTokens", cacheWriteTokens)
    })
    put("contextPressure", buildJsonObject {
        message.contextWindowUsage?.let { usage ->
            put("projectedTokens", usage.latestTokens)
            usage.modelContextWindow?.let { put("contextWindow", it) }
        }
    })
    put("contextBreakdown", buildJsonObject {
        message.contextWindowUsage?.let { usage ->
            usage.systemTokens?.let { put("systemTokens", it) }
            usage.toolsTokens?.let { put("toolsTokens", it) }
            usage.messageTokens?.let { put("messageTokens", it) }
        }
    })
}
