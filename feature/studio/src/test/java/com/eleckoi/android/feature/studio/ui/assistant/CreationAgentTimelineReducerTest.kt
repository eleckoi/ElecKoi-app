package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.feature.conversation.markdown.*
import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.model.*
import com.eleckoi.android.feature.conversation.timeline.ui.*

import com.eleckoi.android.engine.agent.api.AgentCommandAction
import com.eleckoi.android.engine.agent.api.AgentCommandActionType
import com.eleckoi.android.engine.agent.api.AgentFileChange
import com.eleckoi.android.engine.agent.api.AgentFileChangeKind
import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toStoredTimeline
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toUiTimeline
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationAgentTimelineReducerTest {
    @Test
    fun `creator user images survive timeline persistence without embedding bytes`() {
        val image = ChatUserImageAttachment(
            id = "image-1",
            localPath = "C:/private/input/image-1.png",
            mediaType = "image/png",
            displayName = "reference.png",
            bytes = 4_096L,
            imageWidth = 512,
            imageHeight = 768,
        )
        val restored = listOf(
            CreationTimelineItem(
                id = "user-image",
                kind = CreationTimelineKind.User,
                text = "按这张参考图修改角色",
                inputImages = listOf(image),
            ),
        ).toStoredTimeline().toUiTimeline().single()

        assertEquals(listOf(image), restored.inputImages)
    }

    @Test
    fun `dsh step start creates one durable request boundary`() {
        val event = AgentSessionEvent.StepStarted(
            threadId = "thread",
            turnId = "turn",
            step = 2,
            startedAtMillis = 100L,
        )

        val started = CreationAgentTimelineReducer.apply(emptyList(), event)
        val repeated = CreationAgentTimelineReducer.apply(started, event)
        val completed = CreationAgentTimelineReducer.apply(
            timeline = repeated,
            event = AgentSessionEvent.StepCompleted(
                threadId = "thread",
                turnId = "turn",
                step = 2,
                completedAtMillis = 200L,
            ),
        )
        val restored = completed.toStoredTimeline().toUiTimeline().single()

        assertEquals(1, repeated.size)
        assertEquals("request-turn-2", restored.id)
        assertEquals("请求 2", restored.text)
        assertEquals(AgentWorkItemType.Request, restored.workItemType)
        assertFalse(restored.running)
        assertEquals(100L, restored.createdAtMillis)
        assertEquals(200L, restored.completedAtMillis)
    }

    @Test
    fun `dsh step end settles streamed work but not one way host actions`() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "reasoning",
                kind = CreationTimelineKind.Tool,
                text = "",
                detail = "分析设定",
                running = true,
                workItemId = "reasoning-1",
                workItemType = AgentWorkItemType.Reasoning,
                turnId = "turn",
            ),
            CreationTimelineItem(
                id = "action",
                kind = CreationTimelineKind.Tool,
                text = "生成配图",
                running = true,
                workItemId = "action-1",
                workItemType = AgentWorkItemType.Action,
                turnId = "turn",
            ),
        )

        val completed = CreationAgentTimelineReducer.apply(
            timeline = timeline,
            event = AgentSessionEvent.StepCompleted(
                threadId = "thread",
                turnId = "turn",
                step = 1,
                completedAtMillis = 200L,
            ),
        )

        assertFalse(completed.first().running)
        assertEquals(200L, completed.first().completedAtMillis)
        assertTrue(completed.last().running)
    }

    @Test
    fun `turn start binds runtime id without resetting the already visible attempt timer`() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "user",
                kind = CreationTimelineKind.User,
                text = "生成页面",
                createdAtMillis = 10L,
            ),
        )

        val started = CreationAgentTimelineReducer.apply(
            timeline = timeline,
            event = AgentSessionEvent.TurnStarted(
                threadId = "thread",
                turnId = "turn",
                startedAtMillis = 100L,
            ),
        )

        assertEquals("turn", started.single().turnId)
        assertEquals(10L, started.single().createdAtMillis)
    }

    @Test
    fun `committed prompt binds existing row and same turn steer appends once`() {
        val submitted = listOf(
            CreationTimelineItem(
                id = "submitted",
                kind = CreationTimelineKind.User,
                text = "生成页面",
                turnId = "turn",
            ),
        )
        val promptStarted = CreationAgentTimelineReducer.apply(
            timeline = submitted,
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "prompt",
                type = AgentWorkItemType.UserMessage,
                label = "生成页面",
            ),
        )
        val steerStarted = CreationAgentTimelineReducer.apply(
            timeline = promptStarted,
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "steer",
                clientUserMessageId = "client-steer",
                type = AgentWorkItemType.UserMessage,
                label = "算了不要了",
            ),
        )
        val steerCompleted = CreationAgentTimelineReducer.apply(
            timeline = steerStarted,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "steer",
                clientUserMessageId = "client-steer",
                type = AgentWorkItemType.UserMessage,
                status = AgentWorkStatus.Completed,
                summary = "算了不要了",
            ),
        )

        assertEquals(listOf("生成页面", "算了不要了"), steerCompleted.map { it.text })
        assertEquals(listOf("prompt", "steer"), steerCompleted.map { it.workItemId })
        assertTrue(steerCompleted.all { it.turnId == "turn" })
    }

    @Test
    fun `official steer is appended only when its UserMessage event arrives`() {
        val beforeCommit = listOf(
            CreationTimelineItem(
                id = "submitted",
                kind = CreationTimelineKind.User,
                text = "生成页面",
                turnId = "turn",
                workItemId = "prompt",
            ),
            CreationTimelineItem(
                id = "reasoning-before-steer",
                kind = CreationTimelineKind.Tool,
                text = "思考过程",
                workItemId = "reasoning",
                workItemType = AgentWorkItemType.Reasoning,
                turnId = "turn",
            ),
        )

        val committed = CreationAgentTimelineReducer.apply(
            timeline = beforeCommit,
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "server-steer",
                clientUserMessageId = "client-steer",
                type = AgentWorkItemType.UserMessage,
                label = "不要了",
                startedAtMillis = 100L,
            ),
        )

        assertEquals(3, committed.size)
        assertEquals(
            listOf("submitted", "reasoning-before-steer", "user-client-steer"),
            committed.map { it.id },
        )
        assertEquals("不要了", committed.last().text)
        assertEquals("server-steer", committed.last().workItemId)
        assertEquals(100L, committed.last().createdAtMillis)
    }

    @Test
    fun `empty official turn diff clears an earlier net change`() {
        val initial = listOf(
            CreationTimelineItem(
                kind = CreationTimelineKind.User,
                text = "先创建再删除",
                turnId = "turn",
            ),
        )
        val added = CreationAgentTimelineReducer.apply(
            timeline = initial,
            event = AgentSessionEvent.TurnDiffUpdated(
                threadId = "thread",
                turnId = "turn",
                diff = "diff --git a/index.html b/index.html\n+new",
            ),
        )
        val reverted = CreationAgentTimelineReducer.apply(
            timeline = added,
            event = AgentSessionEvent.TurnDiffUpdated(
                threadId = "thread",
                turnId = "turn",
                diff = "",
            ),
        )

        assertEquals("", reverted.single().diff)
        assertTrue(reverted.single().turnDiffObserved)
        val restored = reverted.toStoredTimeline().toUiTimeline().single()
        assertEquals("", restored.diff)
        assertTrue(restored.turnDiffObserved)
    }

    @Test
    fun `turn completion can commit a reconciled empty authoritative diff`() {
        val stale = listOf(
            CreationTimelineItem(
                kind = CreationTimelineKind.User,
                text = "先创建再用命令删除",
                turnId = "turn",
                diff = "diff --git a/index.html b/index.html\n--- /dev/null\n+++ b/index.html\n+new",
                turnDiffObserved = true,
            ),
        )

        val completed = CreationAgentTimelineReducer.finishTurn(
            timeline = stale,
            status = AgentWorkStatus.Completed,
            turnId = "turn",
            diff = "",
            turnDiffObserved = true,
        )

        assertEquals("", completed.single().diff)
        assertTrue(completed.single().turnDiffObserved)
    }

    @Test
    fun `file change lifecycle uses only canonical item paths and diff`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "file-item",
                type = AgentWorkItemType.FileChange,
                label = "修改 1 个文件",
                fileChanges = listOf(
                    AgentFileChange(
                        path = "/workspace/index.html",
                        kind = AgentFileChangeKind.Add,
                        diff = "<html>\n",
                    ),
                ),
                paths = listOf("/workspace/index.html"),
                diff = "*** Begin Patch",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "file-item",
                type = AgentWorkItemType.FileChange,
                status = AgentWorkStatus.Completed,
                fileChanges = listOf(
                    AgentFileChange(
                        path = "/workspace/index.html",
                        kind = AgentFileChangeKind.Add,
                        diff = "<html>\n<body></body>\n",
                    ),
                ),
                paths = listOf("/workspace/index.html"),
                diff = "*** Begin Patch\n*** End Patch",
            ),
        )

        assertEquals(listOf("index.html"), completed.single().paths)
        assertEquals(AgentFileChangeKind.Add, completed.single().fileChanges.single().kind)
        assertEquals("index.html", completed.single().fileChanges.single().path)
        assertEquals("*** Begin Patch\n*** End Patch", completed.single().diff)
        assertFalse(completed.single().running)

        val restored = completed.toStoredTimeline().toUiTimeline().single()
        assertEquals(completed.single().fileChanges, restored.fileChanges)
    }

    @Test
    fun `command text that writes a file remains a command item`() {
        val command = "node -e \"require('fs').writeFileSync('index.html','ok')\""
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "command-item",
                type = AgentWorkItemType.Command,
                label = "运行 $command",
                rawCommand = command,
                commandActions = listOf(
                    AgentCommandAction(AgentCommandActionType.Unknown, command = command),
                ),
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "command-item",
                type = AgentWorkItemType.Command,
                status = AgentWorkStatus.Failed,
                summary = "node: not found",
                exitCode = 127,
                rawCommand = command,
                commandActions = listOf(
                    AgentCommandAction(AgentCommandActionType.Unknown, command = command),
                ),
            ),
        )

        assertEquals(1, completed.size)
        assertEquals(AgentWorkItemType.Command, completed.single().workItemType)
        assertTrue(completed.single().failed)
        assertTrue(completed.single().detail.contains("node: not found"))
        assertTrue(completed.none { it.workItemType == AgentWorkItemType.FileChange })
    }

    @Test
    fun `canonical tool name and result survive persistence`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "tool-item",
                type = AgentWorkItemType.Tool,
                label = "files · read_file",
                toolName = "files · read_file",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "tool-item",
                type = AgentWorkItemType.Tool,
                status = AgentWorkStatus.Completed,
                summary = "index.html contents",
                detail = "files · read_file",
                toolName = "files · read_file",
            ),
        )
        val restored = completed.toStoredTimeline().toUiTimeline().single()

        assertEquals("files · read_file", restored.toolName)
        assertEquals("index.html contents", restored.detail)
        assertFalse(restored.running)
    }

    @Test
    fun `starting canonical operation closes preceding reasoning phase`() {
        val reasoning = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.ReasoningSummaryDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning-item",
                summaryIndex = 0,
                delta = "先检查项目",
            ),
        )
        val withFile = CreationAgentTimelineReducer.apply(
            timeline = reasoning,
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "file-item",
                type = AgentWorkItemType.FileChange,
                label = "修改 1 个文件",
            ),
        )

        assertFalse(withFile.first().running)
        assertTrue(withFile.last().running)
        assertEquals(AgentWorkItemType.FileChange, withFile.last().workItemType)
    }

    @Test
    fun `assistant lifecycle preserves official message phase`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "message",
                type = AgentWorkItemType.AssistantMessage,
                label = "生成回复",
                messagePhase = AgentMessagePhase.Commentary,
            ),
        )
        val streamed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.AssistantDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "message",
                delta = "正在检查",
                phase = AgentMessagePhase.Commentary,
                phaseHeader = AgentMessagePhase.Commentary,
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = streamed,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "message",
                type = AgentWorkItemType.AssistantMessage,
                status = AgentWorkStatus.Completed,
                summary = "正在检查",
                messagePhase = AgentMessagePhase.Commentary,
            ),
        )

        assertEquals("正在检查", completed.single().text)
        assertEquals(AgentMessagePhase.Commentary, completed.single().messagePhase)
        assertEquals(AgentMessagePhase.Commentary, completed.single().phaseHeader)
        assertFalse(completed.single().running)
    }

    @Test
    fun `command output and completion stay on the same canonical item`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "command",
                type = AgentWorkItemType.Command,
                label = "运行 pnpm test",
                rawCommand = "pnpm test",
            ),
        )
        val output = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.CommandOutput(
                threadId = "thread",
                turnId = "turn",
                itemId = "command",
                delta = "PASS\n",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = output,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "command",
                type = AgentWorkItemType.Command,
                status = AgentWorkStatus.Completed,
                summary = "PASS\n",
                exitCode = 0,
                rawCommand = "pnpm test",
            ),
        )

        assertEquals(1, completed.size)
        assertEquals("PASS\n\nexit 0", completed.single().detail)
        assertFalse(completed.single().running)
    }

    @Test
    fun `file patch arriving before started is retained by item id`() {
        val patched = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.FileChangesUpdated(
                threadId = "thread",
                turnId = "turn",
                itemId = "file-item",
                paths = listOf("/workspace/a.txt"),
                diff = "diff-a",
            ),
        )
        val started = CreationAgentTimelineReducer.apply(
            timeline = patched,
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "file-item",
                type = AgentWorkItemType.FileChange,
                label = "修改 1 个文件",
            ),
        )

        assertEquals(1, started.size)
        assertEquals(listOf("a.txt"), started.single().paths)
        assertEquals("diff-a", started.single().diff)
    }

    @Test
    fun `streamed patch updates create and refresh the live file item`() {
        val firstUpdate = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.FileChangesUpdated(
                threadId = "thread",
                turnId = "turn",
                itemId = "patch",
                paths = listOf("/workspace/index.html"),
                diff = "+first line",
            ),
        )
        val secondUpdate = CreationAgentTimelineReducer.apply(
            timeline = firstUpdate,
            event = AgentSessionEvent.FileChangesUpdated(
                threadId = "thread",
                turnId = "turn",
                itemId = "patch",
                paths = listOf("/workspace/index.html"),
                diff = "+first line\n+second line",
            ),
        )

        assertEquals(1, secondUpdate.size)
        assertEquals(AgentWorkItemType.FileChange, secondUpdate.single().workItemType)
        assertTrue(secondUpdate.single().running)
        assertEquals(listOf("index.html"), secondUpdate.single().paths)
        assertEquals("+first line\n+second line", secondUpdate.single().diff)
    }

    @Test
    fun `reasoning summary and detail remain separate official streams`() {
        val summary = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.ReasoningSummaryDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning",
                summaryIndex = 0,
                delta = "检查结构",
            ),
        )
        val detail = CreationAgentTimelineReducer.apply(
            timeline = summary,
            event = AgentSessionEvent.ReasoningTextDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning",
                contentIndex = 0,
                delta = "详细推理",
            ),
        )

        assertEquals("检查结构", detail.single().text)
        assertEquals("详细推理", detail.single().detail)
    }

    @Test
    fun `completed reasoning snapshot replaces streamed fragments without duplication`() {
        val streamed = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.ReasoningTextDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning",
                contentIndex = 0,
                delta = "partial ",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = streamed,
            event = AgentSessionEvent.ReasoningTextDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning",
                contentIndex = 0,
                delta = "partial complete",
                completeSnapshot = true,
            ),
        )

        assertEquals(1, completed.size)
        assertEquals("partial complete", completed.single().detail)
    }

    @Test
    fun `mcp progress stays on tool item and completed result is retained`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "mcp",
                type = AgentWorkItemType.Tool,
                label = "files · search",
                toolName = "files · search",
            ),
        )
        val progressed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.WorkItemProgress(
                threadId = "thread",
                turnId = "turn",
                itemId = "mcp",
                message = "正在扫描目录",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = progressed,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "mcp",
                type = AgentWorkItemType.Tool,
                status = AgentWorkStatus.Completed,
                summary = "找到 3 个文件",
                toolName = "files · search",
            ),
        )

        assertEquals(1, completed.size)
        assertEquals("正在扫描目录\n找到 3 个文件", completed.single().detail)
        assertFalse(completed.single().running)
    }

    @Test
    fun `completed plan replaces non authoritative streamed plan text`() {
        val streamed = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.ReasoningSummaryDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "plan",
                summaryIndex = 0,
                delta = "临时计划",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = streamed,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "plan",
                type = AgentWorkItemType.Reasoning,
                status = AgentWorkStatus.Completed,
                summary = "最终计划",
                completedAtMillis = 500L,
                completionTextIsAuthoritative = true,
            ),
        )

        assertEquals("最终计划", completed.single().text)
        assertEquals(500L, completed.single().completedAtMillis)
    }

    @Test
    fun `distinct plan updates remain at their chronological positions`() {
        val firstPlan = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "plan-1",
                type = AgentWorkItemType.Tool,
                status = AgentWorkStatus.Completed,
                toolName = "update_plan",
                toolArguments =
                    """{"plan":[{"step":"读取上下文","status":"completed"},{"step":"生成正文","status":"inProgress"}]}""",
            ),
        )
        val reasoning = CreationAgentTimelineReducer.apply(
            timeline = firstPlan,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning",
                type = AgentWorkItemType.Reasoning,
                status = AgentWorkStatus.Completed,
                summary = "继续生成正文",
            ),
        )
        val secondPlan = CreationAgentTimelineReducer.apply(
            timeline = reasoning,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "plan-2",
                type = AgentWorkItemType.Tool,
                status = AgentWorkStatus.Completed,
                toolName = "update_plan",
                toolArguments =
                    """{"plan":[{"step":"读取上下文","status":"completed"},{"step":"生成正文","status":"completed"}]}""",
            ),
        )

        assertEquals(listOf("plan-1", "reasoning", "plan-2"), secondPlan.map { it.workItemId })
        assertEquals(2, secondPlan.count { it.toolName == "update_plan" })
        assertEquals(
            listOf("inProgress", "completed"),
            secondPlan.filter { it.toolName == "update_plan" }.map { item ->
                if ("inProgress" in item.toolArguments) "inProgress" else "completed"
            },
        )
    }

    @Test
    fun `turn completion only closes existing canonical items`() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "user",
                kind = CreationTimelineKind.User,
                text = "生成页面",
                turnId = "turn",
                running = true,
            ),
            CreationTimelineItem(
                id = "command",
                kind = CreationTimelineKind.Tool,
                text = "运行 node build.js",
                workItemId = "command",
                workItemType = AgentWorkItemType.Command,
                turnId = "turn",
                running = true,
            ),
        )

        val completed = CreationAgentTimelineReducer.finishTurn(
            timeline = timeline,
            status = AgentWorkStatus.Completed,
            turnId = "turn",
            diff = "turn-diff",
            completedAtMillis = 100L,
        )

        assertEquals(2, completed.size)
        assertTrue(completed.none(CreationTimelineItem::running))
        assertEquals("turn-diff", completed.first().diff)
        assertTrue(completed.none { it.workItemType == AgentWorkItemType.FileChange })
    }

    @Test
    fun `turn completion closes items before and after a same turn steer`() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "user",
                kind = CreationTimelineKind.User,
                text = "生成页面",
                turnId = "turn",
            ),
            CreationTimelineItem(
                id = "command",
                kind = CreationTimelineKind.Tool,
                text = "运行测试",
                turnId = "turn",
                running = true,
            ),
            CreationTimelineItem(
                id = "steer",
                kind = CreationTimelineKind.User,
                text = "先修失败的测试",
                turnId = "turn",
            ),
            CreationTimelineItem(
                id = "assistant",
                kind = CreationTimelineKind.Assistant,
                text = "正在处理",
                turnId = "turn",
                running = true,
            ),
        )

        val completed = CreationAgentTimelineReducer.finishTurn(
            timeline = timeline,
            status = AgentWorkStatus.Completed,
            turnId = "turn",
            diff = "turn-diff",
            completedAtMillis = 100L,
        )

        assertTrue(completed.none(CreationTimelineItem::running))
        assertEquals("turn-diff", completed.first().diff)
        assertEquals("", completed[2].diff)
    }

    @Test
    fun `context compaction keeps its official type through completion and persistence`() {
        val started = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "compact-turn",
                itemId = "compact-item",
                type = AgentWorkItemType.ContextCompaction,
                label = "自动压缩上下文",
            ),
        )
        val completed = CreationAgentTimelineReducer.apply(
            timeline = started,
            event = AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "compact-turn",
                itemId = "compact-item",
                type = AgentWorkItemType.ContextCompaction,
                status = AgentWorkStatus.Completed,
                summary = "上下文已自动压缩",
                detail = "用户与角色已经在车站会合，约定一起回家。\n\n已替换约 26800 Token 的历史上下文",
            ),
        )
        val restored = completed.toStoredTimeline().toUiTimeline().single()

        assertEquals(AgentWorkItemType.ContextCompaction, restored.workItemType)
        assertFalse(restored.running)
        assertEquals("上下文已自动压缩", restored.text)
        assertTrue(restored.detail.contains("用户与角色已经在车站会合"))
        assertFalse(restored.detail.contains("正在自动压缩"))
    }

    @Test
    fun `successful model turn does not pretend an asynchronous action already finished`() {
        val timeline = listOf(
            CreationTimelineItem(
                id = "user",
                kind = CreationTimelineKind.User,
                text = "过来",
                turnId = "turn",
            ),
            CreationTimelineItem(
                id = "action",
                kind = CreationTimelineKind.Tool,
                text = "生成配图",
                workItemType = AgentWorkItemType.Action,
                toolName = "generate_image",
                turnId = "turn",
                running = true,
            ),
        )

        val completed = CreationAgentTimelineReducer.finishTurn(
            timeline = timeline,
            status = AgentWorkStatus.Completed,
            turnId = "turn",
            diff = "",
            completedAtMillis = 100L,
        )

        val action = completed.single { it.workItemType == AgentWorkItemType.Action }
        assertTrue(action.running)
        assertEquals(null, action.completedAtMillis)
        assertEquals(
            AgentWorkItemType.Action,
            listOf(action).toStoredTimeline().toUiTimeline().single().workItemType,
        )
    }

    @Test
    fun `delegated session keeps its live process nested under the subagent call`() {
        val parent = CreationAgentTimelineReducer.apply(
            timeline = emptyList(),
            event = AgentSessionEvent.WorkItemStarted(
                threadId = "parent",
                turnId = "parent-turn",
                itemId = "delegate-call",
                type = AgentWorkItemType.Tool,
                label = "subagent",
                toolName = "subagent",
            ),
        )
        val withRequest = CreationAgentTimelineReducer.apply(
            timeline = parent,
            event = AgentSessionEvent.DelegatedSessionEvent(
                lineage = listOf("delegate-call"),
                childSessionId = "child-session",
                event = AgentSessionEvent.StepStarted(
                    threadId = "child-session",
                    turnId = "child-turn",
                    step = 1,
                    startedAtMillis = 20L,
                ),
            ),
        )
        val withTool = CreationAgentTimelineReducer.apply(
            timeline = withRequest,
            event = AgentSessionEvent.DelegatedSessionEvent(
                lineage = listOf("delegate-call"),
                childSessionId = "child-session",
                event = AgentSessionEvent.WorkItemStarted(
                    threadId = "child-session",
                    turnId = "child-turn",
                    itemId = "read-call",
                    type = AgentWorkItemType.Tool,
                    label = "read",
                    toolName = "read",
                    startedAtMillis = 25L,
                ),
            ),
        )

        val delegation = withTool.single()
        assertEquals("child-session", delegation.delegatedSessionId)
        assertEquals(listOf(AgentWorkItemType.Request, AgentWorkItemType.Tool), delegation.childTimeline.map { it.workItemType })
        assertTrue(delegation.childTimeline.last().running)

        val restored = withTool.toStoredTimeline().toUiTimeline().single()
        assertEquals("child-session", restored.delegatedSessionId)
        assertEquals("read", restored.childTimeline.last().toolName)
    }
}
