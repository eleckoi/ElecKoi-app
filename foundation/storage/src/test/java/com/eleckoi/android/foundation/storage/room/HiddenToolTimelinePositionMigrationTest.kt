package com.eleckoi.android.foundation.storage.room

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenToolTimelinePositionMigrationTest {
    @Test
    fun `legacy after-latest-user timeline receives its editable position`() {
        val migrated = requireNotNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(),
                promptPositionsJson = "[]",
            ),
        )

        val entry = JSONObject(migrated.entryPayloadJson)
        assertEquals("insert_point_4", entry.getString("position"))
        assertEquals("hidden-tool-timeline", entry.getString("prompt_position_id"))
        val positions = JSONArray(migrated.promptPositionsJson)
        assertEquals(1, positions.length())
        assertEquals("hidden-tool-timeline", positions.getJSONObject(0).getString("id"))
        assertEquals("隐藏工具时间线", positions.getJSONObject(0).getString("name"))
        assertEquals("insert_point_4", positions.getJSONObject(0).getString("anchor"))
        assertEquals("before_setting_position", positions.getJSONObject(0).getString("side"))
    }

    @Test
    fun `partially migrated timeline preserves its existing custom position`() {
        val positions = JSONArray().put(
            JSONObject()
                .put("id", "hidden-tool-timeline")
                .put("name", "模型工具记录")
                .put("anchor", "insert_point_2")
                .put("side", "after_setting_position")
                .put("order", 3),
        )

        val migrated = requireNotNull(
            HiddenToolTimelinePositionMigration.migratePayloads(legacyEntry(), positions.toString()),
        )

        val entry = JSONObject(migrated.entryPayloadJson)
        assertEquals("insert_point_2", entry.getString("position"))
        assertEquals("hidden-tool-timeline", entry.getString("prompt_position_id"))
        val migratedPositions = JSONArray(migrated.promptPositionsJson)
        assertEquals(1, migratedPositions.length())
        assertEquals("模型工具记录", migratedPositions.getJSONObject(0).getString("name"))
        assertEquals("after_setting_position", migratedPositions.getJSONObject(0).getString("side"))
    }

    @Test
    fun `deleted customized and current timelines are not recreated`() {
        assertNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(enabled = false),
                promptPositionsJson = "[]",
            ),
        )
        assertNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(position = "insert_point_1"),
                promptPositionsJson = "[]",
            ),
        )
        assertNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(position = "insert_point_5"),
                promptPositionsJson = "[]",
            ),
        )
        assertNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(promptPositionId = "hidden-tool-timeline"),
                promptPositionsJson = "[]",
            ),
        )
    }

    @Test
    fun `existing unrelated positions are preserved after adding the timeline`() {
        val original = JSONObject()
            .put("id", "author-position")
            .put("name", "作者位置")
            .put("anchor", "insert_point_1")
            .put("side", "before_setting_position")
            .put("order", 1)
        val migrated = requireNotNull(
            HiddenToolTimelinePositionMigration.migratePayloads(
                entryPayloadJson = legacyEntry(),
                promptPositionsJson = JSONArray().put(original).toString(),
            ),
        )

        val positions = JSONArray(migrated.promptPositionsJson)
        assertEquals(2, positions.length())
        assertTrue((0 until positions.length()).any { positions.getJSONObject(it).getString("id") == "author-position" })
    }

    private fun legacyEntry(
        position: String = "after_latest_user_input",
        promptPositionId: String = "",
        enabled: Boolean = true,
    ): String = JSONObject()
        .put("id", "built-in-hidden-tool-timeline")
        .put("kind", "hidden_tool_timeline")
        .put("enabled", enabled)
        .put("position", position)
        .put("prompt_position_id", promptPositionId)
        .put("insert_role", "user")
        .toString()
}
