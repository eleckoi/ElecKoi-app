package com.eleckoi.android.foundation.storage.room

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Data conversion that belongs exclusively to the atomic v1 -> v2 migration. */
internal object LegacyAgentPresetMigration {
    const val RoleplayPlanEntryId: String = "fixed-roleplay-plan"
    const val DshHarnessIdentityEntryId: String = "built-in-dsh-harness-identity"

    data class VersionKey(
        val presetId: String,
        val versionId: String,
    )

    data class CapturedPlans(
        val presets: Map<String, List<String>>,
        val versions: Map<VersionKey, List<String>>,
    )

    fun capturePlans(db: SupportSQLiteDatabase): CapturedPlans = CapturedPlans(
        presets = buildMap {
            db.query(
                "SELECT `presetId`, `payloadJson` FROM `story_preset_entries` WHERE `entryId` = ?",
                arrayOf(RoleplayPlanEntryId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), roleplayPlanSteps(cursor.getString(1)))
                }
            }
        },
        versions = buildMap {
            db.query(
                "SELECT `presetId`, `versionId`, `payloadJson` " +
                    "FROM `story_preset_version_entries` WHERE `entryId` = ?",
                arrayOf(RoleplayPlanEntryId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    put(
                        VersionKey(cursor.getString(0), cursor.getString(1)),
                        roleplayPlanSteps(cursor.getString(2)),
                    )
                }
            }
        },
    )

    fun finish(db: SupportSQLiteDatabase, captured: CapturedPlans) {
        captured.presets.forEach { (legacyPresetId, steps) ->
            db.execSQL(
                "UPDATE `agent_preset_contents` SET `content` = ? " +
                    "WHERE `presetId` = ? AND `kind` = 'tool_configuration'",
                arrayOf(toolConfigurationWithRoleplayPlan(steps), legacyPresetId.toAgentPresetId()),
            )
        }
        captured.versions.forEach { (legacyKey, steps) ->
            db.execSQL(
                "UPDATE `agent_preset_version_contents` SET `content` = ? " +
                    "WHERE `presetId` = ? AND `versionId` = ? AND `kind` = 'tool_configuration'",
                arrayOf(
                    toolConfigurationWithRoleplayPlan(steps),
                    legacyKey.presetId.toAgentPresetId(),
                    legacyKey.versionId.toAgentPresetId(),
                ),
            )
        }
        removeObsoletePresetEntries(db)
        removeObsoleteSettingLibraryEntries(db)
        removeObsoleteConversationChanges(db)
    }

    internal fun roleplayPlanSteps(rawPayload: String): List<String> {
        val value = runCatching { JSONObject(rawPayload) }.getOrElse { error ->
            throw IllegalStateException("旧角色扮演计划已损坏，无法整理。", error)
        }
        val content = value.opt("content") as? String
            ?: throw IllegalStateException("旧角色扮演计划正文无效，无法整理。")
        val steps = content.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        check(steps.isNotEmpty() && steps.size <= 20 && steps.none { it.length > 2_000 }) {
            "旧角色扮演计划无法完整转换，拒绝截断。"
        }
        return steps
    }

    internal fun toolConfigurationWithRoleplayPlan(steps: List<String>): String =
        JSONObject(ElecKoiDatabaseMigrations.DefaultToolConfiguration)
            .put("roleplayPlan", JSONObject().put("steps", JSONArray(steps)))
            .toString()

    private fun removeObsoletePresetEntries(db: SupportSQLiteDatabase) {
        val currentRows = buildList {
            db.query("SELECT `presetId`, `entryId`, `payloadJson` FROM `agent_preset_entries`").use { cursor ->
                while (cursor.moveToNext()) {
                    if (isObsoleteEntry(cursor.getString(1), cursor.getString(2))) {
                        add(cursor.getString(0) to cursor.getString(1))
                    }
                }
            }
        }
        currentRows.forEach { (presetId, entryId) ->
            db.execSQL(
                "DELETE FROM `agent_preset_entries` WHERE `presetId` = ? AND `entryId` = ?",
                arrayOf(presetId, entryId),
            )
        }

        val versionRows = buildList {
            db.query(
                "SELECT `presetId`, `versionId`, `entryId`, `payloadJson` FROM `agent_preset_version_entries`",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    if (isObsoleteEntry(cursor.getString(2), cursor.getString(3))) {
                        add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
            }
        }
        versionRows.forEach { (presetId, versionId, entryId) ->
            db.execSQL(
                "DELETE FROM `agent_preset_version_entries` " +
                    "WHERE `presetId` = ? AND `versionId` = ? AND `entryId` = ?",
                arrayOf(presetId, versionId, entryId),
            )
        }
    }

    private fun removeObsoleteSettingLibraryEntries(db: SupportSQLiteDatabase) {
        val entries = buildSet {
            db.query(
                "SELECT `characterId`, `entryId`, `payloadJson` FROM `setting_entry_contents`",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    if (isObsoleteEntry(cursor.getString(1), cursor.getString(2))) {
                        add(cursor.getString(0) to cursor.getString(1))
                    }
                }
            }
        }
        entries.forEach { (characterId, entryId) ->
            val args = arrayOf(characterId, entryId)
            db.execSQL(
                "DELETE FROM `setting_library_version_entry_links` " +
                    "WHERE `characterId` = ? AND `entryId` = ?",
                args,
            )
            db.execSQL(
                "DELETE FROM `setting_library_entry_links` WHERE `characterId` = ? AND `entryId` = ?",
                args,
            )
            db.execSQL(
                "DELETE FROM `setting_entry_contents` WHERE `characterId` = ? AND `entryId` = ?",
                args,
            )
        }
    }

    private fun removeObsoleteConversationChanges(db: SupportSQLiteDatabase) {
        val rows = buildList {
            db.query(
                "SELECT `sessionId`, `targetType`, `targetId`, `payloadJson` " +
                    "FROM `conversation_setting_changes`",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val targetType = cursor.getString(1)
                    val targetId = cursor.getString(2)
                    if (targetType == "entry" && isObsoleteEntry(targetId, cursor.getString(3))) {
                        add(Triple(cursor.getString(0), targetType, targetId))
                    }
                }
            }
        }
        rows.forEach { (sessionId, targetType, targetId) ->
            db.execSQL(
                "DELETE FROM `conversation_setting_changes` " +
                    "WHERE `sessionId` = ? AND `targetType` = ? AND `targetId` = ?",
                arrayOf(sessionId, targetType, targetId),
            )
        }
    }

    internal fun isObsoleteEntry(entryId: String, payloadJson: String): Boolean =
        entryId == RoleplayPlanEntryId ||
            entryId == DshHarnessIdentityEntryId ||
            runCatching { JSONObject(payloadJson).optString("kind") == "roleplay_plan" }.getOrDefault(false)

    private fun String.toAgentPresetId(): String = when {
        startsWith("story-preset-") -> "agent-preset-${removePrefix("story-preset-")}"
        else -> this
    }
}
