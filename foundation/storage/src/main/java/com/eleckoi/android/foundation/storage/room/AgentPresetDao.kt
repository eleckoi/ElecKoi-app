package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentPresetDao {
    @Query("SELECT * FROM agent_preset_state WHERE singletonId = 0 LIMIT 1")
    fun stateFlow(): Flow<AgentPresetStateEntity?>

    @Query("SELECT * FROM agent_preset_library_groups ORDER BY sortIndex, name COLLATE NOCASE")
    fun libraryGroupsFlow(): Flow<List<AgentPresetLibraryGroupEntity>>

    @Query(
        """
        SELECT
            preset.id,
            preset.name,
            preset.modelFamily,
            preset.modelTagsJson,
            preset.libraryGroupId,
            preset.activeVersionId,
            preset.authorName,
            preset.authorAvatarPath,
            COALESCE((SELECT content.content FROM agent_preset_contents AS content
                WHERE content.presetId = preset.id AND content.kind = 'usage_instructions' LIMIT 1), '') AS usageInstructions,
            COALESCE((SELECT content.content FROM agent_preset_contents AS content
                WHERE content.presetId = preset.id AND content.kind = 'timeline' LIMIT 1), '[]') AS timelineJson,
            COALESCE((
                SELECT version.versionNumber
                FROM agent_preset_versions AS version
                WHERE version.presetId = preset.id AND version.versionId = preset.activeVersionId
                LIMIT 1
            ), 1) AS activeVersionNumber,
            preset.sortIndex,
            (SELECT COUNT(*) FROM agent_preset_entries AS entry WHERE entry.presetId = preset.id) AS entryCount
        FROM agent_presets AS preset
        ORDER BY preset.sortIndex, preset.name COLLATE NOCASE
        """,
    )
    fun summariesFlow(): Flow<List<AgentPresetSummaryRecord>>

    @Transaction
    @Query("SELECT * FROM agent_presets WHERE id = :presetId LIMIT 1")
    suspend fun preset(presetId: String): AgentPresetRecord?

    @Query(
        """
        SELECT
            version.versionId AS id,
            version.versionNumber AS number,
            version.name,
            version.createdAtEpochMs,
            (SELECT COUNT(*) FROM agent_preset_version_entries AS entry
                WHERE entry.presetId = version.presetId AND entry.versionId = version.versionId) AS entryCount
        FROM agent_preset_versions AS version
        WHERE version.presetId = :presetId
        ORDER BY version.versionNumber DESC
        """,
    )
    suspend fun versionSummaries(presetId: String): List<AgentPresetVersionSummaryRecord>

    @Transaction
    @Query("SELECT * FROM agent_preset_versions WHERE presetId = :presetId AND versionId = :versionId LIMIT 1")
    suspend fun version(presetId: String, versionId: String): AgentPresetVersionRecord?

    @Transaction
    @Query("SELECT * FROM agent_preset_versions WHERE presetId = :presetId ORDER BY versionNumber")
    suspend fun versions(presetId: String): List<AgentPresetVersionRecord>

    @Query("SELECT * FROM agent_preset_state WHERE singletonId = 0 LIMIT 1")
    suspend fun state(): AgentPresetStateEntity?

    @Query(
        """
        SELECT content.content
        FROM agent_preset_contents AS content
        INNER JOIN agent_preset_state AS state ON state.singletonId = 0
        WHERE content.presetId = state.activePresetId AND content.kind = 'regex_rules'
        LIMIT 1
        """,
    )
    fun activePresetRegexRulesJson(): String?

    @Query("SELECT COUNT(*) FROM agent_presets")
    suspend fun presetCount(): Int

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM agent_presets")
    suspend fun nextSortIndex(): Int

    @Query("SELECT sortIndex FROM agent_presets WHERE id = :presetId LIMIT 1")
    suspend fun presetSortIndex(presetId: String): Int?

    @Query("SELECT activeVersionId FROM agent_presets WHERE id = :presetId LIMIT 1")
    suspend fun presetActiveVersionId(presetId: String): String?

    @Query("SELECT name FROM agent_presets")
    suspend fun presetNames(): List<String>

    @Query("SELECT name FROM agent_presets WHERE id != :presetId")
    suspend fun otherPresetNames(presetId: String): List<String>

    @Query("SELECT name FROM agent_preset_library_groups")
    suspend fun libraryGroupNames(): List<String>

    @Query("SELECT * FROM agent_preset_library_groups ORDER BY sortIndex, name COLLATE NOCASE")
    suspend fun libraryGroups(): List<AgentPresetLibraryGroupEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM agent_presets WHERE id = :presetId)")
    suspend fun presetExists(presetId: String): Boolean

    @Query("SELECT id FROM agent_presets ORDER BY sortIndex, name COLLATE NOCASE LIMIT 1")
    suspend fun firstPresetId(): String?

    @Query("SELECT id FROM agent_presets ORDER BY sortIndex, name COLLATE NOCASE")
    suspend fun presetIds(): List<String>

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM agent_preset_library_groups")
    suspend fun nextLibraryGroupSortIndex(): Int

    @Query("SELECT COALESCE(MAX(versionNumber), 0) + 1 FROM agent_preset_versions WHERE presetId = :presetId")
    suspend fun nextVersionNumber(presetId: String): Int

    @Query("SELECT COUNT(*) FROM agent_preset_versions WHERE presetId = :presetId")
    suspend fun versionCount(presetId: String): Int

    @Query("SELECT versionNumber FROM agent_preset_versions WHERE presetId = :presetId AND versionId = :versionId LIMIT 1")
    suspend fun versionNumber(presetId: String, versionId: String): Int?

    @Transaction
    suspend fun replace(record: AgentPresetRecord) {
        replaceKnown(record, preset(record.preset.id))
    }

    @Transaction
    suspend fun replaceKnown(record: AgentPresetRecord, current: AgentPresetRecord?) {
        val plan = agentPresetWritePlan(current, record)
        plan.preset?.let { upsertPreset(it) }
        plan.deleteContentKinds.forEachPresetDeleteBatch { deletePresetContents(record.preset.id, it) }
        plan.deleteEntryIds.forEachPresetDeleteBatch { deleteEntryRows(record.preset.id, it) }
        plan.deleteGroupIds.forEachPresetDeleteBatch { deleteGroupRows(record.preset.id, it) }
        if (plan.upsertEntries.isNotEmpty()) upsertEntryRows(plan.upsertEntries)
        if (plan.upsertContents.isNotEmpty()) upsertPresetContents(plan.upsertContents)
        if (plan.upsertGroups.isNotEmpty()) upsertGroupRows(plan.upsertGroups)
    }

    @Transaction
    suspend fun replaceVersion(record: AgentPresetVersionRecord) {
        replaceVersionKnown(record, version(record.version.presetId, record.version.versionId))
    }

    @Transaction
    suspend fun replaceVersionKnown(
        record: AgentPresetVersionRecord,
        current: AgentPresetVersionRecord?,
    ) {
        val plan = agentPresetVersionWritePlan(current, record)
        plan.version?.let { upsertVersion(it) }
        plan.deleteContentKinds.forEachPresetDeleteBatch {
            deleteVersionContents(record.version.presetId, record.version.versionId, it)
        }
        plan.deleteEntryIds.forEachPresetDeleteBatch {
            deleteVersionEntryRows(record.version.presetId, record.version.versionId, it)
        }
        plan.deleteGroupIds.forEachPresetDeleteBatch {
            deleteVersionGroupRows(record.version.presetId, record.version.versionId, it)
        }
        if (plan.upsertEntries.isNotEmpty()) upsertVersionEntryRows(plan.upsertEntries)
        if (plan.upsertContents.isNotEmpty()) upsertVersionContents(plan.upsertContents)
        if (plan.upsertGroups.isNotEmpty()) upsertVersionGroupRows(plan.upsertGroups)
    }

    @Upsert
    suspend fun upsertState(state: AgentPresetStateEntity)

    @Upsert
    suspend fun upsertPreset(preset: AgentPresetEntity)

    @Upsert
    suspend fun upsertPresetContents(contents: List<AgentPresetContentEntity>)

    @Upsert
    suspend fun upsertLibraryGroup(group: AgentPresetLibraryGroupEntity)

    @Upsert
    suspend fun upsertVersion(version: AgentPresetVersionEntity)

    @Upsert
    suspend fun upsertVersionContents(contents: List<AgentPresetVersionContentEntity>)

    @Upsert
    suspend fun upsertEntryRows(entries: List<AgentPresetEntryEntity>)

    @Upsert
    suspend fun upsertGroupRows(groups: List<AgentPresetGroupEntity>)

    @Upsert
    suspend fun upsertVersionEntryRows(entries: List<AgentPresetVersionEntryEntity>)

    @Upsert
    suspend fun upsertVersionGroupRows(groups: List<AgentPresetVersionGroupEntity>)

    @Query("DELETE FROM agent_preset_entries WHERE presetId = :presetId AND entryId IN (:entryIds)")
    suspend fun deleteEntryRows(presetId: String, entryIds: List<String>)

    @Query("DELETE FROM agent_preset_groups WHERE presetId = :presetId AND groupId IN (:groupIds)")
    suspend fun deleteGroupRows(presetId: String, groupIds: List<String>)

    @Query("DELETE FROM agent_preset_contents WHERE presetId = :presetId AND kind IN (:kinds)")
    suspend fun deletePresetContents(presetId: String, kinds: List<String>)

    @Query("DELETE FROM agent_preset_version_entries WHERE presetId = :presetId AND versionId = :versionId AND entryId IN (:entryIds)")
    suspend fun deleteVersionEntryRows(presetId: String, versionId: String, entryIds: List<String>)

    @Query("DELETE FROM agent_preset_version_groups WHERE presetId = :presetId AND versionId = :versionId AND groupId IN (:groupIds)")
    suspend fun deleteVersionGroupRows(presetId: String, versionId: String, groupIds: List<String>)

    @Query("DELETE FROM agent_preset_version_contents WHERE presetId = :presetId AND versionId = :versionId AND kind IN (:kinds)")
    suspend fun deleteVersionContents(presetId: String, versionId: String, kinds: List<String>)

    @Query("DELETE FROM agent_preset_versions WHERE presetId = :presetId AND versionId = :versionId")
    suspend fun deleteVersion(presetId: String, versionId: String)

    @Query("UPDATE agent_presets SET libraryGroupId = :libraryGroupId WHERE id = :presetId")
    suspend fun movePreset(presetId: String, libraryGroupId: String)

    @Query("UPDATE agent_preset_library_groups SET name = :name WHERE id = :groupId")
    suspend fun renameLibraryGroup(groupId: String, name: String)

    @Query(
        """
        UPDATE agent_presets
        SET authorName = :authorName
        WHERE id = :presetId
        """,
    )
    suspend fun updatePresetProfileRow(
        presetId: String,
        authorName: String,
    )

    @Transaction
    suspend fun updatePresetProfile(
        presetId: String,
        authorName: String,
        usageInstructions: String,
        timelineJson: String,
    ) {
        updatePresetProfileRow(presetId, authorName)
        upsertPresetContents(
            listOf(
                AgentPresetContentEntity(presetId, "usage_instructions", usageInstructions),
                AgentPresetContentEntity(presetId, "timeline", timelineJson),
            ),
        )
    }

    @Query("UPDATE agent_presets SET authorAvatarPath = :path WHERE id = :presetId")
    suspend fun updatePresetAuthorAvatar(presetId: String, path: String)

    @Query(
        "UPDATE agent_presets SET modelFamily = :modelFamily, modelTagsJson = :modelTagsJson WHERE id = :presetId",
    )
    suspend fun updatePresetModelTags(
        presetId: String,
        modelFamily: String,
        modelTagsJson: String,
    )

    @Query("UPDATE agent_presets SET name = :name WHERE id = :presetId")
    suspend fun renamePresetRow(presetId: String, name: String)

    @Query(
        """
        UPDATE agent_preset_versions
        SET name = :name
        WHERE presetId = :presetId
            AND versionId = (SELECT activeVersionId FROM agent_presets WHERE id = :presetId LIMIT 1)
        """,
    )
    suspend fun renameActiveVersionRow(presetId: String, name: String)

    @Transaction
    suspend fun renamePreset(presetId: String, name: String) {
        renamePresetRow(presetId, name)
        renameActiveVersionRow(presetId, name)
    }

    @Query("UPDATE agent_presets SET activeVersionId = :versionId WHERE id = :presetId")
    suspend fun setActiveVersion(presetId: String, versionId: String)

    @Query(
        """
        UPDATE agent_preset_contents
        SET content = :regexRulesJson
        WHERE kind = 'regex_rules'
          AND presetId = (SELECT activePresetId FROM agent_preset_state WHERE singletonId = 0 LIMIT 1)
        """,
    )
    fun updateActivePresetRegexRules(regexRulesJson: String)

    @Query(
        """
        UPDATE agent_preset_version_contents
        SET content = :regexRulesJson
        WHERE kind = 'regex_rules'
          AND presetId = (SELECT activePresetId FROM agent_preset_state WHERE singletonId = 0 LIMIT 1)
          AND versionId = (
              SELECT activeVersionId
              FROM agent_presets
              WHERE id = (SELECT activePresetId FROM agent_preset_state WHERE singletonId = 0 LIMIT 1)
              LIMIT 1
          )
        """,
    )
    fun updateActivePresetVersionRegexRules(regexRulesJson: String)

    @Query("UPDATE agent_presets SET libraryGroupId = :fallbackGroupId WHERE libraryGroupId = :deletedGroupId")
    suspend fun movePresetsFromDeletedGroup(deletedGroupId: String, fallbackGroupId: String)

    @Query("DELETE FROM agent_preset_library_groups WHERE id = :groupId")
    suspend fun deleteLibraryGroup(groupId: String)

    @Query("DELETE FROM agent_presets WHERE id = :presetId")
    suspend fun deletePreset(presetId: String)

}

private const val AGENT_PRESET_DELETE_BATCH_SIZE = 900

private suspend inline fun List<String>.forEachPresetDeleteBatch(
    crossinline delete: suspend (List<String>) -> Unit,
) {
    chunked(AGENT_PRESET_DELETE_BATCH_SIZE).forEach { delete(it) }
}
