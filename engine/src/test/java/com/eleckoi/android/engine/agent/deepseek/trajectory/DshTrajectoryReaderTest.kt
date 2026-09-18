package com.eleckoi.android.engine.agent.deepseek.trajectory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DshTrajectoryReaderTest {
    @Test
    fun `accepts the DSH logical session header without a physical row type`() {
        val header = buildJsonObject {
            put("version", 0)
            put("id", "eleckoi-session")
            put("createdAt", 1_000)
        }

        requireMatchingDshSessionHeader(header, "eleckoi-session")
    }

    @Test
    fun `rejects a logical session header owned by another thread`() {
        val header = buildJsonObject {
            put("version", 0)
            put("id", "another-session")
            put("createdAt", 1_000)
        }

        assertThrows(IllegalStateException::class.java) {
            requireMatchingDshSessionHeader(header, "eleckoi-session")
        }
    }

    @Test
    fun `pagination keeps request numbers derived from the complete persisted session`() {
        val projection = DshTrajectoryProjector.project(
            listOf(
                event("""{"type":"turn/start","seq":0,"data":{"turn":1}}"""),
                event("""{"type":"step/start","seq":1,"data":{"turn":1,"step":1}}"""),
                event("""{"type":"assistant/message","seq":2,"data":{"turn":1,"step":1,"message":{"content":[{"type":"text","text":"first"}]}}}"""),
                event("""{"type":"step/start","seq":3,"data":{"turn":1,"step":2}}"""),
                event("""{"type":"assistant/message","seq":4,"data":{"turn":1,"step":2,"message":{"content":[{"type":"text","text":"second"}]}}}"""),
                event("""{"type":"session/end-seed","seq":5,"data":{}}"""),
                event("""{"type":"turn/start","seq":6,"data":{"turn":2}}"""),
                event("""{"type":"compaction/start","seq":7,"data":{"turn":2,"compactionId":"compact"}}"""),
                event("""{"type":"compaction/end","seq":8,"data":{"turn":2,"compactionId":"compact"}}"""),
                event("""{"type":"step/start","seq":9,"data":{"turn":2,"step":1}}"""),
                event("""{"type":"assistant/message","seq":10,"data":{"turn":2,"step":1,"message":{"content":[{"type":"text","text":"fourth"}]}}}"""),
            ),
        )

        val latest = paginateDshTrajectory(
            runtimeThreadId = "eleckoi-session",
            projectedRecords = projection.records,
            projection = projection,
            options = DshTrajectoryReadOptions(limit = 2),
        )
        val older = paginateDshTrajectory(
            runtimeThreadId = "eleckoi-session",
            projectedRecords = projection.records,
            projection = projection,
            options = DshTrajectoryReadOptions(beforeIndex = latest.beforeIndex, limit = 2),
        )

        assertEquals(listOf(3, 4), latest.records.flatMap { it.requests }.map { it.number })
        assertEquals(listOf(1, 2), older.records.flatMap { it.requests }.map { it.number })
    }

    private fun event(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
