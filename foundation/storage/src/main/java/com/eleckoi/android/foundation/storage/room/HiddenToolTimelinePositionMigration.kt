package com.eleckoi.android.foundation.storage.room

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** One-time v2 -> v3 repair for hidden-tool timeline positions stored before prompt positions existed. */
internal object HiddenToolTimelinePositionMigration {
    private const val HiddenTimelineEntryId = "built-in-hidden-tool-timeline"
    private const val HiddenTimelinePositionId = "hidden-tool-timeline"
    private const val HiddenTimelinePositionName = "隐藏工具时间线"
    private const val LegacyAnchor = "after_latest_user_input"
    private const val DefaultAnchor = "insert_point_4"
    private const val DefaultSide = "before_setting_position"
    private const val PromptPositionsKind = "prompt_positions"

    internal data class MigratedPayloads(
        val entryPayloadJson: String,
        val promptPositionsJson: String,
    )

    private data class PresetEntryRow(
        val presetId: String,
        val payloadJson: String,
    )

    private data class VersionEntryRow(
        val presetId: String,
        val versionId: String,
        val payloadJson: String,
    )

    fun migrate(db: SupportSQLiteDatabase) {
        migrateCurrentPresets(db)
        migrateVersionSnapshots(db)
    }

    internal fun migratePayloads(
        entryPayloadJson: String,
        promptPositionsJson: String?,
    ): MigratedPayloads? {
        val entry = parseObject(entryPayloadJson, "隐藏工具时间线条目")
        if (
            !entry.optBoolean("enabled", true) ||
            entry.optString("prompt_position_id").isNotBlank() ||
            entry.optString("position") != LegacyAnchor
        ) {
            return null
        }

        val positions = parseArray(promptPositionsJson.orEmpty().ifBlank { "[]" }, "预设插入位置")
        val existingPosition = (0 until positions.length())
            .mapNotNull(positions::optJSONObject)
            .firstOrNull { it.optString("id") == HiddenTimelinePositionId }
        val anchor = existingPosition?.optString("anchor")?.takeIf(String::isNotBlank) ?: DefaultAnchor
        val migratedPositions = if (existingPosition == null) {
            JSONArray().apply {
                put(defaultPromptPosition())
                for (index in 0 until positions.length()) put(positions.get(index))
            }
        } else {
            positions
        }
        entry.put("position", anchor)
        entry.put("prompt_position_id", HiddenTimelinePositionId)
        return MigratedPayloads(
            entryPayloadJson = entry.toString(),
            promptPositionsJson = migratedPositions.toString(),
        )
    }

    private fun migrateCurrentPresets(db: SupportSQLiteDatabase) {
        val rows = buildList {
            db.query(
                "SELECT `presetId`, `payloadJson` FROM `agent_preset_entries` WHERE `entryId` = ?",
                arrayOf(HiddenTimelineEntryId),
            ).use { cursor ->
                while (cursor.moveToNext()) add(PresetEntryRow(cursor.getString(0), cursor.getString(1)))
            }
        }
        rows.forEach { row ->
            val currentPositions = db.promptPositions(
                table = "agent_preset_contents",
                whereClause = "`presetId` = ?",
                whereArgs = listOf(row.presetId),
            )
            val migrated = migratePayloads(row.payloadJson, currentPositions) ?: return@forEach
            db.upsertPromptPositions(
                table = "agent_preset_contents",
                keyColumns = "`presetId`, `kind`",
                keyPlaceholders = "?, ?",
                keyArgs = listOf(row.presetId, PromptPositionsKind),
                content = migrated.promptPositionsJson,
            )
            db.execSQL(
                "UPDATE `agent_preset_entries` SET `payloadJson` = ? " +
                    "WHERE `presetId` = ? AND `entryId` = ?",
                arrayOf(migrated.entryPayloadJson, row.presetId, HiddenTimelineEntryId),
            )
        }
    }

    private fun migrateVersionSnapshots(db: SupportSQLiteDatabase) {
        val rows = buildList {
            db.query(
                "SELECT `presetId`, `versionId`, `payloadJson` " +
                    "FROM `agent_preset_version_entries` WHERE `entryId` = ?",
                arrayOf(HiddenTimelineEntryId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(VersionEntryRow(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        }
        rows.forEach { row ->
            val currentPositions = db.promptPositions(
                table = "agent_preset_version_contents",
                whereClause = "`presetId` = ? AND `versionId` = ?",
                whereArgs = listOf(row.presetId, row.versionId),
            )
            val migrated = migratePayloads(row.payloadJson, currentPositions) ?: return@forEach
            db.upsertPromptPositions(
                table = "agent_preset_version_contents",
                keyColumns = "`presetId`, `versionId`, `kind`",
                keyPlaceholders = "?, ?, ?",
                keyArgs = listOf(row.presetId, row.versionId, PromptPositionsKind),
                content = migrated.promptPositionsJson,
            )
            db.execSQL(
                "UPDATE `agent_preset_version_entries` SET `payloadJson` = ? " +
                    "WHERE `presetId` = ? AND `versionId` = ? AND `entryId` = ?",
                arrayOf(migrated.entryPayloadJson, row.presetId, row.versionId, HiddenTimelineEntryId),
            )
        }
    }

    private fun SupportSQLiteDatabase.promptPositions(
        table: String,
        whereClause: String,
        whereArgs: List<Any>,
    ): String? = query(
        "SELECT `content` FROM `$table` WHERE $whereClause AND `kind` = ?",
        (whereArgs + PromptPositionsKind).toTypedArray(),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun SupportSQLiteDatabase.upsertPromptPositions(
        table: String,
        keyColumns: String,
        keyPlaceholders: String,
        keyArgs: List<Any>,
        content: String,
    ) {
        execSQL(
            "INSERT OR REPLACE INTO `$table` ($keyColumns, `content`) VALUES ($keyPlaceholders, ?)",
            (keyArgs + content).toTypedArray(),
        )
    }

    private fun defaultPromptPosition(): JSONObject = JSONObject()
        .put("id", HiddenTimelinePositionId)
        .put("name", HiddenTimelinePositionName)
        .put("anchor", DefaultAnchor)
        .put("side", DefaultSide)
        .put("order", 1)
        .put("created_at", "")
        .put("updated_at", "")

    private fun parseObject(raw: String, label: String): JSONObject = runCatching { JSONObject(raw) }
        .getOrElse { error -> throw IllegalStateException("$label JSON 已损坏，无法完成数据库迁移。", error) }

    private fun parseArray(raw: String, label: String): JSONArray = runCatching { JSONArray(raw) }
        .getOrElse { error -> throw IllegalStateException("$label JSON 已损坏，无法完成数据库迁移。", error) }
}
