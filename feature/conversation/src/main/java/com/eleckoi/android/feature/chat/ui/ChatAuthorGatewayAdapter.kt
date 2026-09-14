package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.chat.data.stream.isAppendOnlyUpdate
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.modelconfig.model.ModelParameters
import com.eleckoi.android.feature.chat.ui.author.toAuthorSnapshot
import com.eleckoi.android.feature.chat.ui.author.toFeatureModel
import com.eleckoi.android.sdk.author.AuthorApiEvent
import com.eleckoi.android.sdk.author.AuthorChatGateway
import com.eleckoi.android.sdk.author.AuthorChatSnapshot
import com.eleckoi.android.sdk.author.AuthorCommandResult
import com.eleckoi.android.sdk.author.AuthorModelParameters
import com.eleckoi.android.sdk.author.messages.toAuthorMessageJson
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
    publisher: ChatAuthorEventPublisher,
) : AuthorChatGateway {
    override val authorEvents: SharedFlow<AuthorApiEvent> = publisher.events

    override fun snapshot(): AuthorChatSnapshot {
        val current = state()
        return AuthorChatSnapshot(
            draft = current.draft?.toAuthorSnapshot(),
            sessions = current.sessions.map { it.toAuthorSnapshot() },
            input = current.input,
            isGenerating = current.isSending,
            errorMessage = current.errorMessage,
            modelConfigs = current.modelConfigs,
        )
    }

    override fun setInput(value: String): AuthorCommandResult {
        if (state().isSending) return rejected("AI 正在生成，暂时不能修改输入框")
        updateState { it.copy(input = value) }
        return accepted()
    }

    override fun send(text: String): AuthorCommandResult {
        val content = text.trim()
        val current = state()
        if (content.isBlank()) return rejected("发送内容不能为空")
        if (current.isSending) return rejected("AI 正在生成")
        if (current.draft == null) return rejected("聊天还没有加载完成")
        actions.send(content)
        return accepted("消息已提交")
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

    override fun editMessage(messageId: String, text: String): AuthorCommandResult {
        val replacement = text.trim()
        val current = state()
        if (replacement.isBlank()) return rejected("修改后的消息不能为空")
        if (current.isSending) return rejected("AI 正在生成")
        val message = current.draft?.session?.messages?.firstOrNull { it.id == messageId }
            ?: return rejected("没有找到消息：$messageId")
        if (message.role == MessageRole.System) return rejected("系统消息不能修改")
        if (message.pending) return rejected("消息仍在生成中，暂时不能修改")
        updateState { it.copy(editingMessage = message, editInput = replacement) }
        actions.saveEditedMessage()
        return accepted("消息修改已保存")
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

    override fun createNewChat(
        characterId: String,
        characterMode: String?,
    ): AuthorCommandResult {
        val id = characterId.ifBlank { state().draft?.session?.characterId.orEmpty() }
        if (id.isBlank()) return rejected("角色 ID 不能为空")
        if (state().isSending) return rejected("AI 正在生成，暂时不能创建对话")
        actions.createChat(id, characterMode ?: CharacterMode.Agent.storageValue)
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
        parameters: AuthorModelParameters,
    ): AuthorCommandResult {
        val current = state()
        if (current.isSending) return rejected("AI 正在生成，暂时不能切换模型")
        if (current.draft == null) return rejected("当前没有聊天上下文")
        val config = current.modelConfigs.firstOrNull { it.id == configId }
            ?: return rejected("没有找到模型配置：$configId")
        val selectedModel = model.ifBlank { config.model }
        if (selectedModel.isBlank()) return rejected("模型名称不能为空")
        actions.selectModel(config.id, selectedModel, parameters.toFeatureModel())
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
    val send: (String) -> Unit,
    val stopGeneration: () -> Unit,
    val regenerate: (ChatMessage) -> Unit,
    val saveEditedMessage: () -> Unit,
    val submitEditedMessage: () -> Unit,
    val createChat: (String, String) -> Unit,
    val openChat: (String) -> Unit,
    val deleteChat: (String) -> Unit,
    val selectModel: (String, String, ModelParameters) -> Unit,
    val selectOpening: suspend (String, String) -> Result<ChatDraft>,
    val replaceVariableState: suspend (String, String) -> Result<ChatDraft>,
    val resetVariableState: suspend (String) -> Result<ChatDraft>,
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
    }

    private fun publishChanges(previous: ChatUiState, current: ChatUiState) {
        if (current.input != previous.input) {
            emit("input.changed", buildJsonObject { put("text", current.input) })
        }

        val previousSession = previous.draft?.session
        val currentSession = current.draft?.session
        if (
            currentSession?.id != previousSession?.id ||
            currentSession?.title != previousSession?.title
        ) {
            emit(
                "chat.changed",
                currentSession?.let { session ->
                    buildJsonObject {
                        put("id", session.id)
                        put("title", session.title)
                        put("characterId", session.characterId)
                        put("characterName", session.characterName)
                    }
                } ?: JsonNull,
            )
            emit(
                "context.changed",
                currentSession?.let { session ->
                    buildJsonObject {
                        put("surface", "chat")
                        put("sessionId", session.id)
                        put("characterId", session.characterId)
                        put("characterName", session.characterName)
                    }
                } ?: JsonNull,
            )
        }

        val previousMessages = previousSession?.messages.orEmpty()
        val currentMessages = currentSession?.messages.orEmpty()
        if (currentMessages != previousMessages) {
            val latest = currentMessages.lastOrNull()
            if (latest?.pending == true) {
                val previousLatest = previousMessages.lastOrNull()
                val delta = if (
                    previousLatest?.id == latest.id &&
                    isAppendOnlyUpdate(previousLatest.content, latest.content)
                ) {
                    latest.content.substring(previousLatest.content.length)
                } else {
                    latest.content
                }
                emit(
                    "message.delta",
                    messageEventPayload(currentSession?.id.orEmpty(), latest, delta),
                )
            } else {
                emit(
                    "messages.changed",
                    buildJsonObject {
                        put("sessionId", currentSession?.id.orEmpty())
                        put("count", currentMessages.size)
                        put(
                            "current",
                            latest?.let {
                                messageEventPayload(currentSession?.id.orEmpty(), it)
                            } ?: JsonNull,
                        )
                    },
                )
            }
        }

        val previousVariableState = previousSession?.variableStateJson
            ?.takeIf { it.isNotBlank() }
            ?: previousMessages.lastOrNull()?.variableStateJson.orEmpty()
        val currentVariableState = currentSession?.variableStateJson
            ?.takeIf { it.isNotBlank() }
            ?: currentMessages.lastOrNull()?.variableStateJson.orEmpty()
        if (currentVariableState != previousVariableState) {
            emit(
                "variables.changed",
                buildJsonObject {
                    put("sessionId", currentSession?.id.orEmpty())
                    put("stateJson", currentVariableState)
                    put(
                        "state",
                        runCatching { Json.parseToJsonElement(currentVariableState) }.getOrNull() ?: JsonNull,
                    )
                },
            )
        }

        val previousOpenings = previous.draft?.let { draft ->
            Triple(draft.openingOptions, draft.selectedOpeningOptionId, draft.openingSelectionEnabled)
        }
        val currentOpenings = current.draft?.let { draft ->
            Triple(draft.openingOptions, draft.selectedOpeningOptionId, draft.openingSelectionEnabled)
        }
        if (currentOpenings != previousOpenings) {
            val draft = current.draft
            emit(
                "opening.changed",
                buildJsonObject {
                    put("sessionId", draft?.session?.id.orEmpty())
                    put("selectedId", draft?.selectedOpeningOptionId.orEmpty())
                    put("selectionEnabled", draft?.openingSelectionEnabled == true)
                    put("items", buildJsonArray {
                        draft?.openingOptions.orEmpty().forEach { option ->
                            add(buildJsonObject {
                                put("id", option.id)
                                put("title", option.title)
                                put("selected", option.id == draft?.selectedOpeningOptionId)
                            })
                        }
                    })
                },
            )
        }

        if (current.isSending != previous.isSending) {
            when {
                current.isSending -> emit(
                    "generation.started",
                    buildJsonObject { put("sessionId", currentSession?.id.orEmpty()) },
                )
                stopRequested.getAndSet(false) -> emit(
                    "generation.stopped",
                    buildJsonObject { put("sessionId", currentSession?.id.orEmpty()) },
                )
                current.errorMessage.isNotBlank() -> emit(
                    "generation.failed",
                    buildJsonObject {
                        put("sessionId", currentSession?.id.orEmpty())
                        put("message", current.errorMessage)
                    },
                )
                else -> emit(
                    "generation.completed",
                    buildJsonObject { put("sessionId", currentSession?.id.orEmpty()) },
                )
            }
        }
        if (
            current.draft?.selectedModelConfig?.id != previous.draft?.selectedModelConfig?.id ||
            current.draft?.selectedModel != previous.draft?.selectedModel ||
            current.draft?.modelParameters != previous.draft?.modelParameters
        ) {
            emit(
                "model.changed",
                buildJsonObject {
                    put("configId", current.draft?.selectedModelConfig?.id.orEmpty())
                    put("provider", current.draft?.selectedModelConfig?.provider.orEmpty())
                    put("model", current.draft?.selectedModel.orEmpty())
                    put("stream", current.draft?.modelParameters?.stream ?: true)
                    put("temperature", current.draft?.modelParameters?.temperature ?: 1.0)
                    put("topP", current.draft?.modelParameters?.topP ?: 1.0)
                },
            )
        }
    }

    private fun emit(name: String, payload: kotlinx.serialization.json.JsonElement) {
        mutableEvents.tryEmit(AuthorApiEvent(name, payload))
    }

    private fun messageEventPayload(
        sessionId: String,
        message: ChatMessage,
        delta: String? = null,
    ) = buildJsonObject {
        put("sessionId", sessionId)
        message.toAuthorSnapshot().toAuthorMessageJson().forEach { (key, value) -> put(key, value) }
        if (delta != null) put("delta", delta)
    }
}
