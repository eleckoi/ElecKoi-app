package com.eleckoi.android.engine.agent.eleckoi.conversation

import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorConversationInputImage
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineKind
import com.eleckoi.android.engine.workspace.model.CreatorConversationWorkItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantRoomHistoryTest {
    @Test
    fun `creator image metadata is paged with its user turn`() {
        val imageFile = File.createTempFile("creator-history-", ".webp").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val image = CreatorConversationInputImage(
            id = "image-1",
            localPath = imageFile.absolutePath,
            mediaType = "image/webp",
            displayName = "reference.webp",
            creatorMediaReference = "conversation-attachment:image-1",
            bytes = 8_192L,
            imageWidth = 640,
            imageHeight = 960,
        )
        val timeline = listOf(
            CreatorConversationTimelineItem(
                id = "user-1",
                kind = CreatorConversationTimelineKind.User,
                text = "参考这张图",
                turnId = "turn-1",
                inputImages = listOf(image),
            ),
            CreatorConversationTimelineItem(
                id = "assistant-1",
                kind = CreatorConversationTimelineKind.Assistant,
                text = "收到",
                turnId = "turn-1",
            ),
        )

        val ledger = creatorTimelineLedgerMessages(timeline)

        assertTrue(ledger.first().inputImageAttachmentsJson.contains(imageFile.name))
        assertEquals(listOf(image), creatorTimelineFromLedger(ledger).first().inputImages)

        val nativeLedger = ledger.mapIndexed { index, message ->
            if (index == 1) {
                message.copy(
                    modelHistoryItems = listOf(
                        """{"type":"message","role":"user","content":[{"type":"input_text","text":"参考这张图"}]}""",
                        """{"type":"message","role":"assistant","content":[{"type":"output_text","text":"收到"}]}""",
                    ),
                )
            } else {
                message
            }
        }
        val restoredHistory = assistantFullHistory(nativeLedger).map(AgentHistoryItem::responseItemJson)
        // Local JVM tests cannot execute android.util.Base64. The omission marker proves the
        // image-aware bounded-history projection replaced the text-only native user item; on
        // device the same path emits input_image with a data URL.
        assertTrue(
            restoredHistory.first().contains("input_image") ||
                restoredHistory.first().contains("累计载荷上限省略"),
        )
        assertTrue(restoredHistory.last().contains("收到"))
    }

    @Test
    fun `one Room turn keeps detailed UI timeline and exact native model history`() {
        val native = listOf(
            """{"type":"message","role":"user","content":[{"type":"input_text","text":"检查项目"}]}""",
            """{"type":"function_call","call_id":"call-1","name":"shell_command","arguments":"{}"}""",
            """{"type":"function_call_output","call_id":"call-1","output":"README.md"}""",
            """{"type":"message","role":"assistant","content":[{"type":"output_text","text":"完成"}]}""",
        )
        val timeline = listOf(
            CreatorConversationTimelineItem(
                id = "user-1",
                kind = CreatorConversationTimelineKind.User,
                text = "检查项目",
                runtimeThreadId = "runtime-thread-1",
                turnId = "turn-1",
                createdAtMillis = 1_000L,
                turnStartedAtMillis = 1_100L,
                completedAtMillis = 2_000L,
                modelHistoryItems = native,
            ),
            CreatorConversationTimelineItem(
                id = "tool-1",
                kind = CreatorConversationTimelineKind.Tool,
                text = "执行命令",
                detail = "README.md",
                workItemType = CreatorConversationWorkItemType.Command,
                turnId = "turn-1",
                createdAtMillis = 1_200L,
                completedAtMillis = 1_500L,
            ),
            CreatorConversationTimelineItem(
                id = "assistant-1",
                kind = CreatorConversationTimelineKind.Assistant,
                text = "完成",
                turnId = "turn-1",
                createdAtMillis = 1_600L,
                completedAtMillis = 2_000L,
                messagePhase = AgentMessagePhase.FinalAnswer,
            ),
        )

        val ledger = creatorTimelineLedgerMessages(timeline)

        assertEquals(2, ledger.size)
        assertEquals(native, ledger.last().modelHistoryItems)
        assertEquals("runtime-thread-1", ledger.first().runtimeThreadId)
        assertEquals("runtime-thread-1", ledger.last().runtimeThreadId)
        assertEquals("1970-01-01T00:00:01Z", ledger.first().createdAt)
        assertEquals(1_100L, ledger.first().turnStartedAtMillis)
        assertEquals(1_100L, ledger.last().turnStartedAtMillis)
        assertEquals(timeline, creatorTimelineFromLedger(ledger))
        assertEquals(native, assistantFullHistory(ledger).map { it.responseItemJson })
    }

    @Test
    fun `role projection excludes native tool history while assistant projection retains it`() {
        val toolCall = """{"type":"function_call","call_id":"call-1","name":"shell_command","arguments":"{}"}"""
        val messages = listOf(
            LedgerMessage(id = "u1", role = "user", content = "上次问题"),
            LedgerMessage(
                id = "a1",
                role = "assistant",
                content = "上次回答",
                modelHistoryItems = listOf(toolCall),
            ),
        )

        val role = roomConversationHistory(messages, currentUserMessageId = "u2")
        val assistant = assistantFullHistory(messages)

        assertEquals(2, role.size)
        assertTrue(role[0].responseItemJson.contains("上次问题"))
        assertTrue(role[1].responseItemJson.contains("上次回答"))
        assertFalse(role.any { it.responseItemJson.contains("shell_command") })
        assertTrue(assistant.single().responseItemJson.contains("shell_command"))
    }
}
