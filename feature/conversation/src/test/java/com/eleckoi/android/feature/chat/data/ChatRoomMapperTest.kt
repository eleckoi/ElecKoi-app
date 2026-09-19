package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.foundation.storage.room.ChatSessionEntity
import com.eleckoi.android.foundation.storage.room.ChatSessionCharacterSnapshotEntity
import com.eleckoi.android.foundation.storage.room.ChatSessionRecord
import com.eleckoi.android.foundation.storage.room.ChatSessionVariableStateEntity
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatToolCallRecord
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.content.ToolCallState
import com.eleckoi.android.feature.chat.roleplay.actions.GenerateImageActionName
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRoomMapperTest {
    @Test
    fun `room restores user image dimensions used to reserve transcript height`() {
        val user = ChatMessage(
            id = "user-image",
            role = MessageRole.User,
            content = "看图",
            inputImageAttachments = listOf(
                ChatUserImageAttachment(
                    id = "input-1",
                    localPath = "D:/images/input-1.png",
                    mediaType = "image/png",
                    imageWidth = 832,
                    imageHeight = 1216,
                ),
            ),
        )

        val restored = user.toLedgerMessage().toChatMessage().inputImageAttachments.single()

        assertEquals(832, restored.imageWidth)
        assertEquals(1216, restored.imageHeight)
    }

    @Test
    fun `room session metadata accepts one indexed ledger page`() {
        val first = ChatMessage(
            id = "assistant-corrupt",
            role = MessageRole.Assistant,
            content = "keep current text",
            runtimeThreadId = "thread-1",
            runtimeTurnId = "turn-1",
        )
        val second = ChatMessage(
            id = "assistant-next",
            role = MessageRole.Assistant,
            content = "next text",
            runtimeThreadId = "thread-2",
            runtimeTurnId = "turn-2",
        )
        val restored = chatSessionFromRoom(
            record = sessionEntity(),
            messages = listOf(first, second),
        )

        assertEquals(listOf("assistant-corrupt", "assistant-next"), restored.messages.map { it.id })
        assertEquals("workspace-1", restored.workspaceId)
        assertEquals("keep current text", restored.messages.first().content)
        assertEquals("thread-1", restored.messages.first().runtimeThreadId)
        assertEquals("turn-1", restored.messages.first().runtimeTurnId)
        assertEquals(AgentPermissionMode.AskForApproval, restored.permissionMode)
    }

    @Test
    fun `room tail restores whole Agent turn timing directly`() {
        val assistant = ChatMessage(
            id = "assistant-timed",
            role = MessageRole.Assistant,
            content = "完成",
            turnStartedAtMillis = 1_000L,
            turnCompletedAtMillis = 16_000L,
            runtimeThreadId = "thread-timed",
            runtimeTurnId = "turn-timed",
            imageAttachments = listOf(
                ChatImageAttachment(
                    id = "reply-image-assistant-timed",
                    generationAttemptId = "image-attempt-2",
                    localPath = "D:/images/assistant-timed.png",
                    status = ChatImageStatus.Ready,
                    prompt = "1girl, rainy classroom",
                    negativePrompt = "text, watermark",
                    afterParagraph = 3,
                    frameIndex = 2,
                    frameCount = 4,
                    imageWidth = 1024,
                    imageHeight = 576,
                ),
            ),
        )
        val restored = assistant.toLedgerMessage().toChatMessage()

        assertEquals(1_000L, restored.turnStartedAtMillis)
        assertEquals(16_000L, restored.turnCompletedAtMillis)
        assertEquals("thread-timed", restored.runtimeThreadId)
        assertEquals("turn-timed", restored.runtimeTurnId)
        assertEquals(ChatImageStatus.Ready, restored.imageAttachments.single().status)
        assertEquals("image-attempt-2", restored.imageAttachments.single().generationAttemptId)
        assertEquals("D:/images/assistant-timed.png", restored.imageAttachments.single().localPath)
        assertEquals("1girl, rainy classroom", restored.imageAttachments.single().prompt)
        assertEquals("text, watermark", restored.imageAttachments.single().negativePrompt)
        assertEquals(3, restored.imageAttachments.single().afterParagraph)
        assertEquals(2, restored.imageAttachments.single().frameIndex)
        assertEquals(4, restored.imageAttachments.single().frameCount)
        assertEquals(1024, restored.imageAttachments.single().imageWidth)
        assertEquals(576, restored.imageAttachments.single().imageHeight)
    }

    @Test
    fun `room load settles a historical image action whose attachment already failed`() {
        val assistant = ChatMessage(
            id = "assistant-stuck",
            role = MessageRole.Assistant,
            content = "回复正文",
            turnCompletedAtMillis = 42L,
            toolCalls = listOf(
                ChatToolCallRecord(
                    callId = "action-1",
                    name = "生成配图",
                    state = ToolCallState.Running,
                    workItemType = AgentWorkItemType.Action,
                    toolName = GenerateImageActionName,
                ),
            ),
            imageAttachments = listOf(
                ChatImageAttachment(
                    id = "image-1",
                    status = ChatImageStatus.Failed,
                    errorMessage = "旧版生图流程未能启动",
                ),
            ),
        )

        val restored = assistant.toLedgerMessage().toChatMessage()
        val action = restored.toolCalls.single()

        assertEquals(ToolCallState.Failed, action.state)
        assertEquals("旧版生图流程未能启动", action.result)
        assertEquals(42L, action.completedAtMillis)
    }

    @Test
    fun `role reply stores card speaker separately from its model`() {
        val stored = ChatMessage(
            id = "assistant-speaker",
            role = MessageRole.Assistant,
            content = "你好",
            provider = "provider-a",
            model = "model-a",
        ).toLedgerMessage(
            characterId = "card-a",
            characterName = "甲",
            characterAvatar = "images/card-a.png",
        )

        assertEquals("card-a", stored.speakerId)
        assertEquals("card_character", stored.speakerKind)
        assertEquals("甲", stored.speakerName)
        assertEquals("provider-a", stored.provider)
        assertEquals("model-a", stored.model)
        assertEquals("card-a", stored.toChatMessage().speakerId)
    }

    private fun sessionEntity(): ChatSessionRecord = ChatSessionRecord(
        session = ChatSessionEntity(
            id = "session-1",
            workspaceId = "workspace-1",
            title = "test",
            characterId = "character-1",
            characterName = "character",
            characterAvatar = "",
            historySummary = "next text",
            historyMessageCount = 2,
            historyUserMessageCount = 0,
            createdAt = "2026-07-15T00:00:00Z",
            updatedAt = "2026-07-15T00:00:00Z",
        ),
        characterSnapshot = ChatSessionCharacterSnapshotEntity("session-1", "{}"),
        variableStates = listOf(
            ChatSessionVariableStateEntity("session-1", ChatVariableStateInitial, "{}"),
            ChatSessionVariableStateEntity("session-1", ChatVariableStateCurrent, "{}"),
        ),
    )

}
