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
                event("""{"type":"request/header","seq":2,"time":1011,"data":{"turn":1,"step":1,"reason":"initial","header":{"system":"Be concise","tools":[],"config":{"provider":"openai","model":"gpt-test"}}}}"""),
                event("""{"type":"user/message","seq":3,"time":1020,"data":{"turn":1,"step":1,"message":{"source":{"kind":"user"},"content":[{"type":"text","text":"hello"}]}}}"""),
                event("""{"type":"tool/call","seq":4,"time":1030,"data":{"turn":1,"step":1,"callId":"call-1","name":"search","arguments":{"query":"weather"}}}"""),
                event("""{"type":"tool/result","seq":5,"time":1055,"data":{"turn":1,"step":1,"message":{"source":{"callId":"call-1"},"content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"sunny"}]}]}}}"""),
                event("""{"type":"assistant/message","seq":6,"time":1080,"data":{"turn":1,"step":1,"message":{"source":{"provider":"openai","model":"gpt-test"},"content":[{"type":"text","text":"It is sunny."}]}}}"""),
                event("""{"type":"step/end","seq":7,"time":1081,"data":{"turn":1,"step":1}}"""),
                event("""{"type":"compaction/start","seq":8,"time":1090,"data":{"turn":1,"compactionId":"compact-1"}}"""),
                event("""{"type":"compaction/summary","seq":9,"time":1100,"data":{"turn":1,"compactionId":"compact-1","summary":{"type":"text","text":"summary"}}}"""),
                event("""{"type":"compaction/end","seq":10,"time":1110,"data":{"turn":1,"compactionId":"compact-1"}}"""),
                event("""{"type":"turn/end","seq":11,"time":1120,"data":{"turn":1}}"""),
            ),
            header = event("""{"type":"session","id":"thread-1","createdAt":900}"""),
        )

        assertEquals(4, projection.records.size)
        assertEquals(
            listOf(
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
        assertTrue(tool.requests.single().rawJson.contains("request/header"))
        assertEquals(listOf(1, 2), projection.records.flatMap { it.requests }.map { it.number })
        val compaction = projection.records.last()
        assertEquals(2, compaction.requests.single().number)
        assertEquals(null, compaction.requests.single().step)
        assertEquals(DshTrajectoryRecordStatus.Complete, compaction.requests.single().status)
        assertEquals(900L, projection.startedAtMillis)
        assertEquals(1120L, projection.completedAtMillis)
    }

    @Test
    fun omitsHarnessSystemPromptAndKeepsApprovalOutcome() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"request/header","seq":0,"time":10,"data":{"header":{"system":"one","tools":[],"config":{}}}}"""),
                event("""{"type":"request/header","seq":1,"time":20,"data":{"header":{"system":"two","tools":[],"config":{}}}}"""),
                event("""{"type":"approval/asked","seq":2,"time":30,"data":{"id":"approval-1","reason":"run","toolName":"shell"}}"""),
                event("""{"type":"approval/decided","seq":3,"time":45,"data":{"id":"approval-1","outcome":"cancelled"}}"""),
            ),
        )

        assertEquals(listOf("授权请求"), projection.records.map { it.title })
        val approval = projection.records.last()
        assertEquals(DshTrajectoryRecordStatus.Cancelled, approval.status)
        assertEquals(15L, approval.durationMillis)
    }

    @Test
    fun mergesOfficialCompactionCheckpointIntoItsLifecycleRecord() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"compaction/start","seq":1,"time":100,"data":{"turn":1,"compactionId":"compact-1"}}"""),
                event("""{"type":"compaction/summary","seq":2,"time":110,"data":{"turn":1,"compactionId":"compact-1","summary":{"type":"text","text":"history summary"}}}"""),
                event("""{"type":"user/message","seq":3,"time":111,"data":{"turn":1,"message":{"source":{"kind":"plugin","plugin":"compact","compactionId":"compact-1"},"content":[{"type":"text","text":"This is an automatically generated checkpoint.\n\n<compacted-summary>history summary</compacted-summary>"}]}}}"""),
                event("""{"type":"compaction/end","seq":4,"time":120,"data":{"turn":1,"compactionId":"compact-1"}}"""),
            ),
        )

        assertEquals(1, projection.records.size)
        val compaction = projection.records.single()
        assertEquals(DshTrajectoryRecordKind.Compaction, compaction.kind)
        assertEquals("history summary", compaction.output)
        assertTrue(compaction.rawJson.contains("\"plugin\": \"compact\""))
    }

    @Test
    fun keepsUncorrelatedPluginContextVisible() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"user/message","seq":1,"time":100,"data":{"turn":1,"message":{"source":{"kind":"plugin","plugin":"foreign-plugin"},"content":[{"type":"text","text":"foreign context"}]}}}"""),
            ),
        )

        assertEquals(1, projection.records.size)
        assertEquals(DshTrajectoryRecordKind.Context, projection.records.single().kind)
    }

    @Test
    fun hidesInternalProjectionRowsInsteadOfInventingMainTrajectoryEvents() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"turn/start","seq":0,"time":1000,"data":{"turn":1}}"""),
                event("""{"type":"step/start","seq":1,"time":1001,"data":{"turn":1,"step":1}}"""),
                event("""{"type":"user/message","seq":2,"time":1010,"data":{"turn":1,"step":1,"message":{"id":"eleckoi-request-projection:v1","source":{"kind":"plugin","plugin":"eleckoi-request-projection"},"content":[{"type":"text","text":"ELECKOI_REQUEST_PROJECTION_V1\\n[]"}]}}}"""),
                event("""{"type":"user/message","seq":3,"time":1020,"data":{"turn":1,"step":1,"message":{"id":"old-copy","source":{"kind":"plugin","plugin":"eleckoi-agent-session-bridge"},"content":[{"type":"text","text":"旧设定副本"}]}}}"""),
                event("""{"type":"user/message","seq":4,"time":1030,"data":{"turn":1,"step":1,"message":{"source":{"kind":"user"},"content":[{"type":"text","text":"hello"}]}}}"""),
                event("""{"type":"tool/call","seq":5,"time":1040,"data":{"turn":1,"step":1,"callId":"call-1","name":"read","arguments":{}}}"""),
                event("""{"type":"tool/result","seq":6,"time":1050,"data":{"turn":1,"step":1,"message":{"source":{"kind":"tool","callId":"call-1"},"content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"done"}]}]}}}"""),
                event("""{"type":"assistant/message","seq":7,"time":1060,"data":{"turn":1,"step":1,"message":{"source":{"provider":"openai","model":"test"},"content":[{"type":"text","text":"answer"}]}}}"""),
            ),
        )

        assertEquals(listOf("用户消息", "read", "助手消息"), projection.records.map(DshTrajectoryRecord::title))
        assertTrue(projection.records.none { it.rawJson.contains("旧设定副本") })
        assertEquals(1, projection.records.flatMap(DshTrajectoryRecord::requests).size)
    }

    @Test
    fun keepsSessionGlobalRequestNumbersAcrossSeedBoundaryTurnsAndCompaction() {
        val events = listOf(
            event("""{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}"""),
            event("""{"type":"step/start","seq":1,"time":110,"data":{"turn":1,"step":1}}"""),
            event("""{"type":"assistant/message","seq":2,"time":120,"data":{"turn":1,"step":1,"message":{"content":[{"type":"text","text":"first"}]}}}"""),
            event("""{"type":"step/start","seq":3,"time":130,"data":{"turn":1,"step":2}}"""),
            event("""{"type":"assistant/message","seq":4,"time":140,"data":{"turn":1,"step":2,"message":{"content":[{"type":"text","text":"second"}]}}}"""),
            event("""{"type":"session/end-seed","seq":5,"time":150,"data":{}}"""),
            event("""{"type":"turn/start","seq":6,"time":200,"data":{"turn":2}}"""),
            event("""{"type":"compaction/start","seq":7,"time":210,"data":{"turn":2,"compactionId":"compact-2"}}"""),
            event("""{"type":"compaction/end","seq":8,"time":220,"data":{"turn":2,"compactionId":"compact-2"}}"""),
            event("""{"type":"step/start","seq":9,"time":230,"data":{"turn":2,"step":1}}"""),
            event("""{"type":"assistant/message","seq":10,"time":250,"data":{"turn":2,"step":1,"message":{"content":[{"type":"text","text":"fourth"}]}}}"""),
        )

        val firstRead = DshTrajectoryProjector.project(events)
        val resumedRead = DshTrajectoryProjector.project(events.shuffled(kotlin.random.Random(7)))
        val expected = listOf(
            Triple(1L, 1, 1),
            Triple(3L, 2, 2),
            Triple(7L, 3, null),
            Triple(9L, 4, 1),
        )
        fun numbered(projection: DshTrajectoryProjection) = projection.records
            .flatMap(DshTrajectoryRecord::requests)
            .map { request -> Triple(request.seq, request.number, request.step) }

        assertEquals(expected, numbered(firstRead))
        assertEquals(expected, numbered(resumedRead))
        assertEquals(listOf(3, 4), firstRead.records.takeLast(2).flatMap { it.requests }.map { it.number })
    }

    @Test
    fun countsFailedCompactionAsARequest() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"compaction/start","seq":20,"time":100,"data":{"turn":3,"compactionId":"failed"}}"""),
                event("""{"type":"compaction/end","seq":21,"time":140,"data":{"turn":3,"compactionId":"failed","error":{"message":"rejected"}}}"""),
            ),
        )

        val request = projection.records.single().requests.single()
        assertEquals(1, request.number)
        assertEquals(20L, request.seq)
        assertEquals(DshTrajectoryRecordStatus.Error, request.status)
        assertEquals(40L, request.durationMillis)
    }

    private fun event(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
