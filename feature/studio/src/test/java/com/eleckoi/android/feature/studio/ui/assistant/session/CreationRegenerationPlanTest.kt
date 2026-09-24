package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.feature.conversation.markdown.*
import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.model.*
import com.eleckoi.android.feature.conversation.timeline.ui.*

import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationRegenerationPlanTest {
    @Test
    fun imageOnlyPromptCanBeRegeneratedWithItsAttachment() {
        val image = ChatUserImageAttachment(
            id = "image-1",
            localPath = "/private/reference.png",
            mediaType = "image/png",
        )
        val plan = requireNotNull(
            planCreationRegeneration(
                timeline = listOf(
                    CreationTimelineItem(
                        id = "user-image",
                        kind = CreationTimelineKind.User,
                        text = "",
                        inputImages = listOf(image),
                    ),
                    CreationTimelineItem(
                        id = "answer",
                        kind = CreationTimelineKind.Assistant,
                        text = "分析完成",
                    ),
                ),
                restartedAtMillis = 1_000L,
            ),
        )

        assertEquals("", plan.prompt)
        assertEquals(listOf(image), plan.retainedUser.inputImages)
    }

    @Test
    fun regenerationKeepsTheLatestPromptAndDeletesItsEntireGeneratedSuffix() {
        val timeline = listOf(
            CreationTimelineItem("u1", CreationTimelineKind.User, "first"),
            CreationTimelineItem("a1", CreationTimelineKind.Assistant, "first answer"),
            CreationTimelineItem(
                id = "u2",
                kind = CreationTimelineKind.User,
                text = "second",
                turnId = "deleted-turn",
                createdAtMillis = 500L,
                completedAtMillis = 20L,
                modelHistoryItems = listOf("stale-native-user"),
            ),
            CreationTimelineItem(
                id = "tool2",
                kind = CreationTimelineKind.Tool,
                text = "continued old work",
                workItemType = AgentWorkItemType.Tool,
                turnId = "deleted-turn",
                modelHistoryItems = listOf("stale-tool-result"),
            ),
            CreationTimelineItem(
                id = "a2",
                kind = CreationTimelineKind.Assistant,
                text = "answer that must disappear",
                turnId = "deleted-turn",
                modelHistoryItems = listOf("stale-answer"),
            ),
        )

        val plan = requireNotNull(
            planCreationRegeneration(timeline, restartedAtMillis = 1_000L),
        )

        assertEquals("second", plan.prompt)
        assertEquals(listOf("u1", "a1"), plan.stableHistory.map { it.id })
        assertEquals(2, plan.retainedTurns)
        assertEquals("u2", plan.retainedUser.id)
        assertEquals("second", plan.retainedUser.text)
        assertEquals(500L, plan.retainedUser.createdAtMillis)
        assertEquals(1_000L, plan.retainedUser.turnStartedAtMillis)
        assertNull(plan.retainedUser.turnId)
        assertNull(plan.retainedUser.completedAtMillis)
        assertTrue(plan.retainedUser.modelHistoryItems.isEmpty())
    }

    @Test
    fun repeatedRegenerationOnALongConversationNeverRetainsADeletedReply() {
        val completedHistory = buildList {
            repeat(5_000) { index ->
                add(CreationTimelineItem("u$index", CreationTimelineKind.User, "prompt $index"))
                add(CreationTimelineItem("a$index", CreationTimelineKind.Assistant, "answer $index"))
            }
        }
        val target = CreationTimelineItem("target", CreationTimelineKind.User, "rewrite this")
        val staleReply = CreationTimelineItem("stale", CreationTimelineKind.Assistant, "old continuation")

        val first = requireNotNull(
            planCreationRegeneration(
                completedHistory + target + staleReply,
                restartedAtMillis = 1_000L,
            ),
        )
        val second = requireNotNull(
            planCreationRegeneration(
                first.stableHistory + first.retainedUser + staleReply,
                restartedAtMillis = 2_000L,
            ),
        )

        assertEquals(10_000, second.stableHistory.size)
        assertEquals(5_001, first.retainedTurns)
        assertEquals(5_001, second.retainedTurns)
        assertEquals("target", second.retainedUser.id)
        assertTrue(second.stableHistory.none { it.id == "stale" })
        assertTrue(second.retainedUser.modelHistoryItems.isEmpty())
    }

    @Test
    fun supplementalSteerInsideATurnDoesNotReplaceTheRoomBranchUser() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "source-user",
                kind = CreationTimelineKind.User,
                text = "create a character",
                turnId = "turn-1",
            ),
            CreationTimelineItem(
                id = "steer-user",
                kind = CreationTimelineKind.User,
                text = "make it orange",
                turnId = "turn-1",
            ),
            CreationTimelineItem(
                id = "old-answer",
                kind = CreationTimelineKind.Assistant,
                text = "continued answer",
                turnId = "turn-1",
            ),
        )

        val plan = requireNotNull(
            planCreationRegeneration(timeline, restartedAtMillis = 1_000L),
        )

        assertEquals("source-user", plan.retainedUser.id)
        assertEquals("create a character", plan.prompt)
        assertTrue(plan.stableHistory.isEmpty())
    }

    @Test
    fun editingAnEarlierPromptKeepsEarlierTurnsAndDropsTheSelectedBranchSuffix() {
        val timeline = listOf(
            CreationTimelineItem("u1", CreationTimelineKind.User, "first"),
            CreationTimelineItem("a1", CreationTimelineKind.Assistant, "first answer"),
            CreationTimelineItem(
                id = "u2",
                kind = CreationTimelineKind.User,
                text = "old second",
                createdAtMillis = 500L,
                turnId = "old-turn",
            ),
            CreationTimelineItem("a2", CreationTimelineKind.Assistant, "old second answer"),
            CreationTimelineItem("u3", CreationTimelineKind.User, "third"),
            CreationTimelineItem("a3", CreationTimelineKind.Assistant, "third answer"),
        )

        val plan = requireNotNull(
            planCreationRegeneration(
                timeline = timeline,
                restartedAtMillis = 1_000L,
                targetUserId = "u2",
                replacementText = "  edited second  ",
            ),
        )

        assertEquals("edited second", plan.prompt)
        assertEquals(listOf("u1", "a1"), plan.stableHistory.map { it.id })
        assertEquals("u2", plan.retainedUser.id)
        assertEquals("edited second", plan.retainedUser.text)
        assertEquals(500L, plan.retainedUser.createdAtMillis)
        assertEquals(1_000L, plan.retainedUser.turnStartedAtMillis)
        assertNull(plan.retainedUser.turnId)
    }

    @Test
    fun editingASupplementalSteerIsRejectedBecauseItIsNotARoomBranchTurn() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "source-user",
                kind = CreationTimelineKind.User,
                text = "create a character",
                turnId = "turn-1",
            ),
            CreationTimelineItem(
                id = "steer-user",
                kind = CreationTimelineKind.User,
                text = "make it orange",
                turnId = "turn-1",
            ),
        )

        assertNull(
            planCreationRegeneration(
                timeline = timeline,
                restartedAtMillis = 1_000L,
                targetUserId = "steer-user",
                replacementText = "make it blue",
            ),
        )
    }

    @Test
    fun regenerationStartsAFreshHarnessThreadInsteadOfResumingDeletedNativeHistory() {
        assertSame(AgentThreadStart.Fresh, creationAgentThreadStart(regenerating = true))
        assertSame(AgentThreadStart.BoundOrNew, creationAgentThreadStart(regenerating = false))
        assertEquals(
            AgentThreadStart.Resume("runtime-thread-1"),
            creationAgentThreadStart(regenerating = false, runtimeThreadId = "runtime-thread-1"),
        )
    }

    @Test
    fun creationPromptPreservesAdmittedImageMetadata() {
        val prompt = creationAgentPrompt(
            text = "use this reference",
            inputImages = listOf(
                ChatUserImageAttachment(
                    id = "image-1",
                    localPath = "/private/reference.webp",
                    mediaType = "image/webp",
                    displayName = "reference.webp",
                ),
            ),
        )

        assertEquals("use this reference", prompt.text)
        assertEquals("/private/reference.webp", prompt.images.single().localPath)
        assertEquals("image/webp", prompt.images.single().mediaType)
        assertEquals("reference.webp", prompt.images.single().name)
    }

    @Test
    fun creationPromptExposesConversationOwnedImageReferenceToCharacterMediaTools() {
        val prompt = creationAgentPrompt(
            text = "把这张图设成头像和立绘",
            inputImages = listOf(
                ChatUserImageAttachment(
                    id = "image-1",
                    localPath = "/private/reference.webp",
                    mediaType = "image/webp",
                    displayName = "四宫辉夜.webp",
                    creatorMediaReference = "conversation-attachment:input-1",
                ),
            ),
        )

        assertTrue(prompt.text.contains("四宫辉夜.webp -> asset_id=conversation-attachment:input-1"))
        assertTrue(prompt.text.contains("不是工作区永久资产"))
        assertTrue(prompt.text.contains("不要登记或复制未选图片"))
        assertFalse(prompt.text.contains("/private/reference.webp"))
    }
}
