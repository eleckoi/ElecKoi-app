package com.eleckoi.android.app.service.chat

import com.eleckoi.android.engine.agent.background.AgentRunDescriptor
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.agent.background.AgentRunSurface
import com.eleckoi.android.feature.chat.data.CharacterAgentGenerationService
import com.eleckoi.android.feature.chat.data.ChatSendResult
import com.eleckoi.android.feature.chat.data.PreparedChatRegeneration
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole

internal class ChatGenerationRunCoordinator(
    private val characterAgent: () -> CharacterAgentGenerationService,
    private val agentRuns: () -> AgentRunManager,
) {
    suspend fun sendMessage(
        runId: String,
        draft: ChatDraft,
        message: String,
        inputImages: List<ChatUserImageAttachment>,
        onDelta: (ChatDraft) -> Unit,
        onUserTurnPersisted: (ChatDraft, String) -> Unit,
    ): ChatSendResult {
        val generation = characterAgent()
        return agentRuns().run(
            descriptor = draft.session.runDescriptor(runId, detail = "正在生成角色回复"),
            onStop = { generation.cancelStream(runId) },
        ) {
            running("正在生成角色回复")
            val result = generation.sendMessage(
                runId,
                draft,
                message,
                inputImages,
                onDelta,
                onUserTurnPersisted,
            )
            completed(result.notificationSummary())
            result
        }
    }

    suspend fun runPreparedRegeneration(
        runId: String,
        prepared: PreparedChatRegeneration,
        onDelta: (ChatDraft) -> Unit,
    ): ChatSendResult {
        val generation = characterAgent()
        return agentRuns().run(
            descriptor = prepared.session.runDescriptor(runId, detail = "正在重新生成角色回复"),
            onStop = { generation.cancelStream(runId) },
        ) {
            running("正在重新生成角色回复")
            val result = generation.runPreparedRegeneration(runId, prepared, onDelta)
            completed(result.notificationSummary())
            result
        }
    }

    fun cancelStream(runId: String): Boolean {
        val manager = agentRuns()
        val active = manager.activeRun.value ?: return false
        if (active.descriptor.surface != AgentRunSurface.CharacterChat) return false
        return manager.requestStop(runId)
    }

    private fun ChatSession.runDescriptor(runId: String, detail: String): AgentRunDescriptor = AgentRunDescriptor(
        runId = runId,
        surface = AgentRunSurface.CharacterChat,
        workspaceId = workspaceId,
        conversationId = id,
        title = characterName.ifBlank { "角色回复生成中" },
        detail = detail,
        avatarPath = characterPersona.assistantAvatar.ifBlank { characterAvatar },
    )
}

private fun ChatSendResult.notificationSummary(): String {
    val reply = draft.session.messages.asReversed().firstOrNull { message ->
        message.role == MessageRole.Assistant && !message.pending
    }
    return when {
        reply == null -> "角色回复已生成"
        reply.content.isNotBlank() -> reply.content
        reply.imageAttachments.isNotEmpty() -> "图片已生成"
        else -> "角色回复已生成"
    }
}
