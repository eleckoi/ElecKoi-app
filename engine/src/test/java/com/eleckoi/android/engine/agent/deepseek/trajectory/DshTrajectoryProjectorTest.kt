package com.eleckoi.android.engine.agent.deepseek.trajectory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DshTrajectoryProjectorTest {
    @Test
    fun projectsPcTrajectoryKindsRequestsAndDurations() {
        val projection = DshTrajectoryProjector.project(
            input = listOf(
                event("""{"type":"turn/start","seq":0,"time":1000,"data":{"turn":1}}"""),
                event("""{"type":"step/start","seq":1,"time":1010,"data":{"turn":1,"step":1}}"""),
                event(
                    """{"type":"request/header","seq":2,"time":1011,"data":{"turn":1,"step":1,"reason":"initial","header":{"system":"Be concise","tools":[],"config":{"provider":"openai","model":"gpt-test"}}}}""",
                ),
                event(
                    """{"type":"user/message","seq":3,"time":1020,"data":{"turn":1,"step":1,"message":{"source":{"kind":"user"},"content":[{"type":"text","text":"hello"}]}}}""",
                ),
                event(
                    """{"type":"tool/call","seq":4,"time":1030,"data":{"turn":1,"step":1,"callId":"call-1","name":"search","arguments":{"query":"weather"}}}""",
                ),
                event(
                    """{"type":"tool/result","seq":5,"time":1055,"data":{"turn":1,"step":1,"message":{"source":{"callId":"call-1"},"content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"sunny"}]}]}}}""",
                ),
                event(
                    """{"type":"assistant/message","seq":6,"time":1080,"data":{"turn":1,"step":1,"message":{"source":{"provider":"openai","model":"gpt-test"},"content":[{"type":"text","text":"It is sunny."}]}}}""",
                ),
                event("""{"type":"step/end","seq":7,"time":1081,"data":{"turn":1,"step":1}}"""),
                event(
                    """{"type":"compaction/start","seq":8,"time":1090,"data":{"turn":1,"compactionId":"compact-1"}}""",
                ),
                event(
                    """{"type":"compaction/summary","seq":9,"time":1100,"data":{"turn":1,"compactionId":"compact-1","summary":{"type":"text","text":"summary"}}}""",
                ),
                event(
                    """{"type":"compaction/end","seq":10,"time":1110,"data":{"turn":1,"compactionId":"compact-1"}}""",
                ),
                event("""{"type":"turn/end","seq":11,"time":1120,"data":{"turn":1}}"""),
            ),
            header = event("""{"type":"session","id":"thread-1","createdAt":900}"""),
        )

        assertEquals(5, projection.records.size)
        assertEquals(
            listOf(
                DshTrajectoryRecordKind.System,
                DshTrajectoryRecordKind.User,
                DshTrajectoryRecordKind.Tool,
                DshTrajectoryRecordKind.Assistant,
                DshTrajectoryRecordKind.Compaction,
            ),
            projection.records.map(DshTrajectoryRecord::kind),
        )
        val tool = projection.records.first { it.kind == DshTrajectoryRecordKind.Tool }
        assertEquals(DshTrajectoryRecordStatus.Complete, tool.status)
        assertEquals("sunny", tool.output)
        assertEquals(25L, tool.durationMillis)
        assertEquals(1, tool.requests.size)
        assertEquals("openai", tool.requests.single().provider)
        assertEquals("gpt-test", tool.requests.single().model)
        assertEquals(20L, tool.requests.single().durationMillis)
        assertTrue(projection.records.first().rawJson.contains("request/header"))
        assertEquals(900L, projection.startedAtMillis)
        assertEquals(1120L, projection.completedAtMillis)
    }

    @Test
    fun keepsSystemPromptUpdatesAndApprovalOutcome() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"request/header","seq":0,"time":10,"data":{"header":{"system":"one","tools":[],"config":{}}}}"""),
                event("""{"type":"request/header","seq":1,"time":20,"data":{"header":{"system":"two","tools":[],"config":{}}}}"""),
                event("""{"type":"approval/asked","seq":2,"time":30,"data":{"id":"approval-1","reason":"run","toolName":"shell"}}"""),
                event("""{"type":"approval/decided","seq":3,"time":45,"data":{"id":"approval-1","outcome":"cancelled"}}"""),
            ),
        )

        assertEquals(listOf("初始系统提示词", "系统提示词更新", "授权请求"), projection.records.map { it.title })
        val approval = projection.records.last()
        assertEquals(DshTrajectoryRecordStatus.Cancelled, approval.status)
        assertEquals(15L, approval.durationMillis)
    }

    private fun event(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
