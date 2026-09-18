package com.eleckoi.android.feature.chat.ui.message

import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.conversation.timeline.CreationDetailPayload
import com.eleckoi.android.feature.conversation.timeline.initialSelectedItemPath
import com.eleckoi.android.feature.conversation.timeline.AgentPlanStepPresentation
import com.eleckoi.android.feature.conversation.timeline.AgentPlanStepStatus
import com.eleckoi.android.feature.conversation.timeline.AgentPlanUpdatePresentation
import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatToolCallRecord
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAgentProcessSheetTest {
    @Test
    fun `a lone context compaction opens its summary without an intermediate list`() {
        val compaction = timelineItem("compact-1").copy(
            workItemType = AgentWorkItemType.ContextCompaction,
        )

        assertEquals(
            listOf("compact-1"),
            CreationDetailPayload(title = "详情", items = listOf(compaction))
                .initialSelectedItemPath(),
        )
        assertEquals(
            emptyList<String>(),
            CreationDetailPayload(title = "详情", items = listOf(timelineItem("tool-1")))
                .initialSelectedItemPath(),
        )
        assertEquals(
            emptyList<String>(),
            CreationDetailPayload(
                title = "详情",
                items = listOf(compaction, timelineItem("tool-1")),
            ).initialSelectedItemPath(),
        )
    }

    @Test
    fun `back from an auto opened lone compaction returns directly to process`() {
        val payload = CreationDetailPayload(
            title = "详情",
            items = listOf(
                timelineItem("compact-1").copy(
                    workItemType = AgentWorkItemType.ContextCompaction,
                ),
            ),
        )

        assertNull(
            chatProcessDetailPathAfterBack(
                payload = payload,
                selectedPath = payload.initialSelectedItemPath(),
            ),
        )
    }

    @Test
    fun `back from a nested detail returns to its payload root before closing`() {
        val child = timelineItem("child")
        val parent = timelineItem("parent").copy(childTimeline = listOf(child))
        val payload = CreationDetailPayload(title = "详情", items = listOf(parent))

        assertEquals(
            listOf("parent"),
            chatProcessDetailPathAfterBack(payload, listOf("parent", "child")),
        )
        assertNull(chatProcessDetailPathAfterBack(payload, emptyList()))
    }

    @Test
    fun `accepted final body auto completes only the structural final plan item`() {
        val plan = AgentPlanUpdatePresentation(
            explanation = "",
            steps = listOf(
                AgentPlanStepPresentation("分析设定", AgentPlanStepStatus.Completed),
                AgentPlanStepPresentation("输出 FINAL 正文", AgentPlanStepStatus.InProgress),
            ),
        )

        val completed = plan.withAutoCompletedRoleplayFinal(enabled = true)

        assertEquals(
            listOf(AgentPlanStepStatus.Completed, AgentPlanStepStatus.Completed),
            completed.steps.map(AgentPlanStepPresentation::status),
        )
        assertEquals(plan, plan.withAutoCompletedRoleplayFinal(enabled = false))
    }

    @Test
    fun `final body is accepted only after a settled reply contains recognized final content`() {
        val boundary = ChatToolCallRecord(
            callId = "final-boundary",
            name = "协议标识",
            result = "<FINAL>",
            narrative = true,
            messagePhase = AgentMessagePhase.FinalAnswer,
            phaseHeader = AgentMessagePhase.FinalAnswer,
        )
        val settled = ChatMessage(
            id = "assistant",
            role = MessageRole.Assistant,
            content = "角色扮演正文",
            pending = false,
            toolCalls = listOf(boundary),
        )

        assertEquals(true, settled.hasAcceptedRoleplayFinalBody())
        assertEquals(false, settled.copy(pending = true).hasAcceptedRoleplayFinalBody())
        assertEquals(false, settled.copy(content = "").hasAcceptedRoleplayFinalBody())
        assertEquals(false, settled.copy(toolCalls = emptyList()).hasAcceptedRoleplayFinalBody())
    }

    @Test
    fun `opening a detail during a running chat preserves its operation group`() {
        val bound = CreationDetailPayload(
            title = "详情",
            items = listOf(requestItem("request-1"), timelineItem("first")),
            liveTurnId = "message-1",
            liveCurrentOperationGroup = true,
            liveOperationGroupAnchorId = "first",
        ).bindToLiveChatProcess(
            messageId = "message-1",
            turnRunning = true,
            activePlanUpdateId = "plan-1",
        )

        assertEquals("message-1", bound.liveTurnId)
        assertEquals("message-1", bound.sourceTurnId)
        assertEquals("first", bound.liveOperationGroupAnchorId)
        assertEquals(true, bound.liveCurrentOperationGroup)
    }

    @Test
    fun `live detail grows only inside the clicked stage and settles with the turn`() {
        val bound = CreationDetailPayload(
            title = "详情",
            items = listOf(requestItem("request-1"), timelineItem("first")),
            liveTurnId = "message-1",
            liveCurrentOperationGroup = true,
            liveOperationGroupAnchorId = "first",
        )
        val refreshed = bound.refreshFromLiveChatProcess(
            messageId = "message-1",
            timelineItems = listOf(
                requestItem("request-1"),
                timelineItem("first"),
                requestItem("request-2"),
                timelineItem("same-stage"),
                stageItem("stage-boundary"),
                requestItem("request-3"),
                timelineItem("next-stage"),
            ),
            turnRunning = false,
            activePlanUpdateId = null,
        )

        assertEquals(
            listOf("first", "same-stage"),
            refreshed.items.map { it.id },
        )
        assertNull(refreshed.liveTurnId)
    }

    private fun timelineItem(id: String): CreationTimelineItem = CreationTimelineItem(
        id = id,
        kind = CreationTimelineKind.Tool,
        text = id,
    )

    private fun requestItem(id: String): CreationTimelineItem = timelineItem(id).copy(
        workItemType = AgentWorkItemType.Request,
    )

    private fun stageItem(id: String): CreationTimelineItem = CreationTimelineItem(
        id = id,
        kind = CreationTimelineKind.Assistant,
        text = "阶段结论",
    )
}
