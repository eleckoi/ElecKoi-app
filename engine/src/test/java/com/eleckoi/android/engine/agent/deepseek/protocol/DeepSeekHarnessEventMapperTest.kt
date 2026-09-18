package com.eleckoi.android.engine.agent.deepseek.protocol

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekHarnessEventMapperTest {
    private val mapper = DeepSeekHarnessEventMapper()

    @Test
    fun `maps turn text reasoning usage and completion`() {
        val started = map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":2}}""").single()
            as AgentSessionEvent.TurnStarted
        assertEquals("session:2", started.turnId)

        val stepStarted = map(
            """{"type":"step/start","seq":1,"time":101,"data":{"turn":2,"step":1}}""",
        ).single() as AgentSessionEvent.StepStarted
        assertEquals(1, stepStarted.step)
        assertEquals("session:2", stepStarted.turnId)

        val text = map(
            """{"type":"assistant/chunk","seq":2,"time":102,"data":{"turn":2,"step":1,"chunk":{"type":"text-delta","index":0,"text":"完成"}}}""",
        ).single() as AgentSessionEvent.AssistantDelta
        assertEquals("完成", text.delta)
        assertEquals(102L, text.observedAtMillis)

        val reasoning = map(
            """{"type":"assistant/chunk","seq":3,"time":103,"data":{"turn":2,"step":1,"chunk":{"type":"reasoning-delta","index":1,"text":"检查"}}}""",
        ).single() as AgentSessionEvent.ReasoningTextDelta
        assertEquals("检查", reasoning.delta)
        assertEquals(1, reasoning.step)
        assertEquals(103L, reasoning.observedAtMillis)

        val usage = map(
            """{"type":"assistant/chunk","seq":4,"time":104,"data":{"turn":2,"step":1,"chunk":{"type":"usage","usage":{"inputTokens":10,"cacheReadTokens":3,"outputTokens":4,"reasoningTokens":2}}}}""",
        ).single() as AgentSessionEvent.TokenUsageUpdated
        assertEquals(17, usage.total.totalTokens)
        assertEquals(3, usage.last.cacheReadTokens)
        assertTrue(usage.last.cacheUsageReported)
        assertEquals(2, usage.last.reasoningOutputTokens)

        val nativeToolToken = map(
            """{"type":"assistant/chunk","seq":5,"time":105,"data":{"turn":2,"step":1,"chunk":{"type":"tool-call-delta","index":2,"id":"call-1","name":"read","argumentsDelta":""}}}""",
        ).single() as AgentSessionEvent.AssistantDelta
        assertTrue(!nativeToolToken.visible)
        assertTrue(nativeToolToken.tokenObserved)
        assertEquals(105L, nativeToolToken.observedAtMillis)

        val stepCompleted = map(
            """{"type":"step/end","seq":6,"time":106,"data":{"turn":2,"step":1}}""",
        ).single() as AgentSessionEvent.StepCompleted
        assertEquals(1, stepCompleted.step)
        assertEquals(106L, stepCompleted.completedAtMillis)

        val completed = map(
            """{"type":"turn/end","seq":7,"time":107,"data":{"turn":2,"reason":{"kind":"completed"}}}""",
        ).single() as AgentSessionEvent.TurnCompleted
        assertEquals(AgentWorkStatus.Completed, completed.status)
    }

    @Test
    fun `maps tool lifecycle without guessing command semantics`() {
        map("""{"type":"turn/start","seq":0,"time":99,"data":{"turn":1}}""")
        val started = map(
            """{"type":"tool/call","seq":1,"time":100,"data":{"turn":1,"step":1,"callId":"call-1","name":"bash","arguments":"{\"command\":\"pwd\"}"}}""",
        ).filterIsInstance<AgentSessionEvent.WorkItemStarted>().single()
        assertEquals("bash", started.toolName)
        assertTrue(started.toolArguments.contains("pwd"))

        val completed = map(
            """{"type":"tool/result","seq":2,"time":101,"data":{"turn":1,"step":1,"message":{"id":"msg-1","role":"user","content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"/workspace"}]}],"source":{"kind":"tool","callId":"call-1"}}}}""",
        ).filterIsInstance<AgentSessionEvent.WorkItemCompleted>().single()
        assertEquals("call-1", completed.itemId)
        assertEquals("bash", completed.toolName)
        assertTrue(completed.toolArguments.contains("pwd"))
        assertEquals("/workspace", completed.summary)
        assertEquals("bash", completed.detail)
        assertEquals(AgentWorkStatus.Completed, completed.status)
    }

    @Test
    fun `maps native automatic compaction into one visible work item`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":3}}""")

        val started = map(
            """{"type":"compaction/start","seq":1,"time":101,"data":{"compactionId":"compact-1","turn":3}}""",
        ).single() as AgentSessionEvent.WorkItemStarted
        assertEquals(AgentWorkItemType.ContextCompaction, started.type)
        assertEquals("session:3", started.turnId)
        assertEquals("正在自动压缩", started.label)

        assertTrue(
            map(
                """{"type":"compaction/summary","seq":2,"time":102,"data":{"compactionId":"compact-1","shadowedTokenCount":26800,"summary":[{"type":"text","text":"用户与角色已经在车站会合，约定一起回家。"}]}}""",
            ).isEmpty(),
        )
        val completed = map(
            """{"type":"compaction/end","seq":3,"time":103,"data":{"compactionId":"compact-1","turn":3}}""",
        ).single() as AgentSessionEvent.WorkItemCompleted
        assertEquals(AgentWorkItemType.ContextCompaction, completed.type)
        assertEquals(AgentWorkStatus.Completed, completed.status)
        assertEquals("上下文已自动压缩", completed.summary)
        assertTrue(completed.detail.contains("用户与角色已经在车站会合"))
        assertTrue(completed.detail.contains("26800"))
        assertEquals(started.itemId, completed.itemId)
    }

    @Test
    fun `maps failed automatic compaction with a user readable error`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":4}}""")
        map(
            """{"type":"compaction/start","seq":1,"time":101,"data":{"compactionId":"compact-2","turn":4}}""",
        )
        val completed = map(
            """{"type":"compaction/end","seq":2,"time":102,"data":{"compactionId":"compact-2","turn":4,"error":"summarization produced no text summary content"}}""",
        ).single() as AgentSessionEvent.WorkItemCompleted
        assertEquals(AgentWorkStatus.Failed, completed.status)
        assertEquals("摘要模型没有返回可用的文本内容", completed.summary)
        assertEquals(completed.summary, completed.detail)
    }

    @Test
    fun `explains native compaction shrink guard failure`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":5}}""")
        map(
            """{"type":"compaction/start","seq":1,"time":101,"data":{"compactionId":"compact-3","turn":5}}""",
        )
        val completed = map(
            """{"type":"compaction/end","seq":2,"time":102,"data":{"compactionId":"compact-3","turn":5,"error":"summary is not smaller than the shadowed content (274 estimated framed tokens >= 274)"}}""",
        ).single() as AgentSessionEvent.WorkItemCompleted

        assertEquals(AgentWorkStatus.Failed, completed.status)
        assertEquals(
            "摘要没有比被替换的历史更短（摘要约 274 Token，原历史约 274 Token）",
            completed.summary,
        )
    }

    @Test
    fun `completed assistant snapshot projects reasoning separately from visible text`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")

        val reasoningOnly = map(
            """{"type":"assistant/message","seq":1,"time":101,"data":{"turn":1,"step":1,"message":{"id":"message-1","role":"assistant","content":[{"type":"reasoning","text":"reasoning detail"},{"type":"tool-call","id":"call-1","name":"bash","arguments":"{}"}]}}}""",
        )
        val reasoningSnapshot = reasoningOnly
            .filterIsInstance<AgentSessionEvent.ReasoningTextDelta>()
            .single()
        assertEquals("reasoning detail", reasoningSnapshot.delta)
        assertEquals(0, reasoningSnapshot.contentIndex)
        assertEquals(1, reasoningSnapshot.step)
        assertEquals(101L, reasoningSnapshot.observedAtMillis)
        assertTrue(reasoningSnapshot.completeSnapshot)
        val reasoningBoundary = reasoningOnly
            .filterIsInstance<AgentSessionEvent.WorkItemCompleted>()
            .single { it.type == AgentWorkItemType.AssistantMessage }
        assertEquals(AgentWorkItemType.AssistantMessage, reasoningBoundary.type)
        assertTrue(reasoningBoundary.summary.isBlank())
        assertEquals(
            AgentWorkStatus.Completed,
            reasoningOnly.filterIsInstance<AgentSessionEvent.WorkItemCompleted>()
                .single { it.type == AgentWorkItemType.Reasoning }
                .status,
        )
        assertTrue(reasoningOnly.none { it is AgentSessionEvent.ModelHistoryItemCompleted })

        val mixed = map(
            """{"type":"assistant/message","seq":2,"time":102,"data":{"turn":1,"step":2,"message":{"id":"message-2","role":"assistant","content":[{"type":"reasoning","text":"more reasoning detail"},{"type":"text","text":"<FINAL>visible answer</FINAL>"}]}}}""",
        )
        val mixedReasoning = mixed.filterIsInstance<AgentSessionEvent.ReasoningTextDelta>().single()
        assertEquals("more reasoning detail", mixedReasoning.delta)
        assertTrue(mixedReasoning.completeSnapshot)
        val completed = mixed.filterIsInstance<AgentSessionEvent.WorkItemCompleted>()
            .single { it.type == AgentWorkItemType.AssistantMessage }
        assertEquals(AgentWorkItemType.AssistantMessage, completed.type)
        assertEquals("<FINAL>visible answer</FINAL>", completed.summary)
        val history = mixed.filterIsInstance<AgentSessionEvent.ModelHistoryItemCompleted>().single()
        assertTrue(history.responseItemJson.contains("visible answer"))
        assertTrue(!history.responseItemJson.contains("reasoning detail"))
    }

    @Test
    fun `completed assistant reasoning remains an authoritative snapshot after streamed deltas`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")
        val streamed = map(
            """{"type":"assistant/chunk","seq":1,"time":101,"data":{"turn":1,"step":1,"chunk":{"type":"reasoning-delta","index":0,"text":"partial "}}}""",
        ).single() as AgentSessionEvent.ReasoningTextDelta
        assertTrue(!streamed.completeSnapshot)

        val completed = map(
            """{"type":"assistant/message","seq":2,"time":102,"data":{"turn":1,"step":1,"message":{"id":"message-1","role":"assistant","content":[{"type":"reasoning","text":"partial complete"},{"type":"text","text":"answer"}]}}}""",
        ).filterIsInstance<AgentSessionEvent.ReasoningTextDelta>().single()

        assertEquals(streamed.itemId, completed.itemId)
        assertEquals("partial complete", completed.delta)
        assertTrue(completed.completeSnapshot)
    }

    @Test
    fun `completed reasoning keeps the DSH stream block index`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")
        assertTrue(
            map(
                """{"type":"assistant/chunk","seq":1,"time":101,"data":{"turn":1,"step":1,"chunk":{"type":"block-end","index":7,"block":{"type":"reasoning","text":"final only"}}}}""",
            ).isEmpty(),
        )

        val completed = map(
            """{"type":"assistant/message","seq":2,"time":102,"data":{"turn":1,"step":1,"message":{"id":"message-1","role":"assistant","content":[{"type":"reasoning","text":"final only"},{"type":"text","text":"answer"}]}}}""",
        ).filterIsInstance<AgentSessionEvent.ReasoningTextDelta>().single()

        assertEquals(7, completed.contentIndex)
        assertTrue(completed.itemId.endsWith("-7"))
    }

    @Test
    fun `extracts fragmented action call while preserving assistant process text`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")

        val prefix = map(
            """{"type":"assistant/chunk","seq":1,"time":101,"data":{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"<ACTION_"}}}""",
        )
        val prefixEvent = prefix.single() as AgentSessionEvent.AssistantDelta
        assertEquals("<ACTION_", prefixEvent.delta)
        assertTrue(prefixEvent.actionCalls.isEmpty())

        val decoded = map(
            """{"type":"assistant/chunk","seq":2,"time":102,"data":{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"CALL name=\"generate_image\">\n{\"frames\":[]}\n</ACTION_CALL>\n<FINAL>正文</FINAL>"}}}""",
        ).single() as AgentSessionEvent.AssistantDelta
        assertEquals(
            "CALL name=\"generate_image\">\n{\"frames\":[]}\n</ACTION_CALL>\n<FINAL>正文</FINAL>",
            decoded.delta,
        )
        assertEquals(1, decoded.actionCalls.size)
        assertEquals("generate_image", decoded.actionCalls.single().name)
        assertEquals("{\"frames\":[]}", decoded.actionCalls.single().argumentsJson)
        assertTrue(decoded.delta.contains("ACTION_CALL"))

        val snapshot = map(
            """{"type":"assistant/message","seq":3,"time":103,"data":{"turn":1,"step":1,"message":{"id":"message-1","role":"assistant","content":[{"type":"text","text":"<ACTION_CALL name=\"generate_image\">\n{\"frames\":[]}\n</ACTION_CALL>\n<FINAL>正文</FINAL>"}]}}}""",
        )
        assertTrue(
            snapshot.filterIsInstance<AgentSessionEvent.AssistantDelta>()
                .all { it.actionCalls.isEmpty() && it.delta.isEmpty() },
        )
        val completed = snapshot.filterIsInstance<AgentSessionEvent.WorkItemCompleted>().single()
        assertTrue(completed.summary.contains("ACTION_CALL"))
        assertTrue(completed.summary.contains("<FINAL>正文</FINAL>"))
        val history = snapshot.filterIsInstance<AgentSessionEvent.ModelHistoryItemCompleted>().single()
        assertTrue(history.responseItemJson.contains("ACTION_CALL"))
    }

    @Test
    fun `does not reinterpret provider reasoning as an assistant action`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")
        val marker = "<ACTION_CALL name=\"generate_image\">{\"frames\":[]}</ACTION_CALL>"
        val jsonMarker = marker.replace("\"", "\\\"")

        val event = map(
            """{"type":"assistant/chunk","seq":1,"time":101,"data":{"turn":1,"step":1,"chunk":{"type":"reasoning-delta","index":0,"text":"$jsonMarker"}}}""",
        ).single() as AgentSessionEvent.ReasoningTextDelta

        assertEquals(marker, event.delta)
    }

    @Test
    fun `live assistant stream emits text once and ignores revision changes`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""")
        val start = mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"start","attemptId":"attempt-1","revision":1,"turn":1,"step":1,"time":101}}""",
        )
        assertTrue(start.isEmpty())

        val first = mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"chunk","attemptId":"attempt-1","revision":2,"time":102,"chunk":{"type":"text-delta","index":0,"text":"<FI"}}}""",
        ).single() as AgentSessionEvent.AssistantDelta
        val second = mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"chunk","attemptId":"attempt-1","revision":9,"time":103,"chunk":{"type":"text-delta","index":0,"text":"NAL>正文"}}}""",
        ).single() as AgentSessionEvent.AssistantDelta
        assertEquals("<FI", first.delta)
        assertEquals("NAL>正文", second.delta)
        assertEquals(first.itemId, second.itemId)

        assertTrue(
            map(
                """{"type":"assistant/chunk","seq":1,"time":104,"data":{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"<FINAL>正文"}}}""",
            ).isEmpty(),
        )
    }

    @Test
    fun `abandoned live attempt interrupts only its running reasoning`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":2}}""")
        mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"start","attemptId":"attempt-2","turn":2,"step":3,"time":101}}""",
        )
        val reasoning = mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"chunk","attemptId":"attempt-2","revision":4,"time":102,"chunk":{"type":"reasoning-delta","index":7,"text":"检查"}}}""",
        ).single() as AgentSessionEvent.ReasoningTextDelta

        val cancelled = mapNotification(
            AssistantStreamMethod,
            """{"sessionId":"session","frame":{"type":"end","attemptId":"attempt-2","revision":5,"time":103,"outcome":{"kind":"abandoned"}}}""",
        ).single() as AgentSessionEvent.WorkItemCompleted
        assertEquals(reasoning.itemId, cancelled.itemId)
        assertEquals(AgentWorkItemType.Reasoning, cancelled.type)
        assertEquals(AgentWorkStatus.Interrupted, cancelled.status)
    }

    @Test
    fun `final assistant message completes final-only reasoning`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":4}}""")

        val events = map(
            """{"type":"assistant/message","seq":1,"time":104,"data":{"turn":4,"step":1,"message":{"role":"assistant","content":[{"type":"reasoning","text":"先分析"},{"type":"text","text":"最终答案"}]}}}""",
        )

        val reasoning = events.filterIsInstance<AgentSessionEvent.ReasoningTextDelta>().single()
        val completed = events.filterIsInstance<AgentSessionEvent.WorkItemCompleted>()
            .single { it.type == AgentWorkItemType.Reasoning }
        assertEquals(reasoning.itemId, completed.itemId)
        assertEquals(AgentWorkStatus.Completed, completed.status)
    }

    @Test
    fun `replayed and orphan tool results do not manufacture generic work items`() {
        map("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":3}}""")
        map(
            """{"type":"tool/call","seq":1,"time":101,"data":{"turn":3,"step":1,"callId":"call-1","name":"read","arguments":"{}"}}""",
        )
        val result =
            """{"type":"tool/result","seq":2,"time":102,"data":{"turn":3,"step":1,"message":{"role":"user","content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"ok"}]}],"source":{"kind":"tool","callId":"call-1"}}}}"""
        assertFalse(map(result).isEmpty())
        assertTrue(map(result).isEmpty())

        val orphan =
            """{"type":"tool/result","seq":3,"time":103,"data":{"turn":3,"step":1,"message":{"role":"user","content":[{"type":"tool-result","toolCallId":"missing","content":[{"type":"text","text":"late"}]}],"source":{"kind":"tool","callId":"missing"}}}}"""
        assertTrue(map(orphan).isEmpty())
    }

    private fun map(eventJson: String): List<AgentSessionEvent> = mapper.map(
        DeepSeekNotification(
            method = "session.event",
            params = Json.parseToJsonElement(
                """{"sessionId":"session","event":$eventJson}""",
            ).jsonObject,
        ),
    )

    private fun mapNotification(method: String, paramsJson: String): List<AgentSessionEvent> =
        mapper.map(
            DeepSeekNotification(
                method = method,
                params = Json.parseToJsonElement(paramsJson).jsonObject,
            ),
        )

    private companion object {
        const val AssistantStreamMethod = "agent.assistant-stream"
    }
}
