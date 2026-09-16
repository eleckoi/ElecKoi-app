package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntryKind
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.room.SettingLibraryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryEntryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryGroupEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryRecord
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionEntryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionGroupEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class SettingLibrarySnapshot(
    val activeVersionId: String,
    val versions: List<SettingLibraryVersion>,
)

/** Owns every JSON representation used by the setting-library repository and session change log. */
internal object SettingLibraryJsonCodec {
    private const val Format = "eleckoi.workspace-setting-library"
    private const val FormatVersion = 3
    private const val SnapshotFormat = "eleckoi.setting-library-snapshot"

    fun exportLibrary(library: SettingLibrary): String = JSONObject()
        .put("format", Format)
        .put("version", FormatVersion)
        .put("character_id", library.characterId)
        .put("name", library.name)
        .put("list_all_expanded", library.listAllExpanded)
        .put("expanded_group_ids", JSONArray(library.expandedGroupIds))
        .put("entries", JSONArray(library.entries.map(::entryToJson)))
        .put("groups", JSONArray(library.groups.map(::groupToJson)))
        .put("prompt_positions", JSONArray(library.promptPositions.map(::promptPositionToJson)))
        .toString(2)

    fun exportSnapshot(library: SettingLibrary): String = JSONObject()
        .put("format", SnapshotFormat)
        .put("version", 1)
        .put("active_version_id", library.activeVersionId)
        .put("versions", JSONArray(library.versions.map(::versionToJson)))
        .toString()

    fun parseSnapshot(json: String): SettingLibrarySnapshot {
        val source = runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("设定库快照格式不正确", it) }
        if (source.optString("format") != SnapshotFormat) {
            throw ElecKoiDataException("这不是 ElecKoi 设定库快照")
        }
        val versions = source.optJSONArray("versions")
            ?.jsonObjects()
            ?.mapIndexed(::versionFromJson)
            .orEmpty()
        val activeVersionId = source.optString("active_version_id")
            .takeIf { id -> versions.any { it.id == id } }
            ?: versions.firstOrNull()?.id.orEmpty()
        return SettingLibrarySnapshot(activeVersionId, versions)
    }

    fun parseExport(json: String, versionId: String): SettingLibraryVersion {
        val source = runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("设定库文件格式不正确", it) }
        requireCurrentFormat(source)
        return SettingLibraryVersion(
            id = versionId,
            name = source.optString("name"),
            entries = source.optJSONArray("entries")?.jsonObjects()?.map(::entryFromJson).orEmpty(),
            groups = source.optJSONArray("groups")?.jsonObjects()?.mapIndexed(::groupFromJson).orEmpty(),
            promptPositions = source.optJSONArray("prompt_positions")?.jsonObjects()?.mapIndexed(::promptPositionFromJson).orEmpty(),
            listAllExpanded = source.optBoolean("list_all_expanded", true),
            expandedGroupIds = source.optJSONArray("expanded_group_ids")?.strings().orEmpty(),
        )
    }

    fun fromEntity(record: SettingLibraryRecord): SettingLibrary {
        return runCatching {
            val entity = record.library
            SettingLibrary(
                characterId = entity.characterId,
                name = entity.name,
                entries = record.entries
                    .sortedBy(SettingLibraryEntryEntity::sortIndex)
                    .map { row -> entryFromJson(JSONObject(row.payloadJson)) },
                groups = record.groups
                    .sortedBy(SettingLibraryGroupEntity::sortIndex)
                    .mapIndexed { index, row -> groupFromJson(index, JSONObject(row.payloadJson)) },
                promptPositions = JSONArray(entity.promptPositionsJson).jsonObjects().mapIndexed(::promptPositionFromJson),
                activeVersionId = entity.activeVersionId,
                versions = record.versions
                    .sortedBy(SettingLibraryVersionEntity::sortIndex)
                    .map { version ->
                        SettingLibraryVersion(
                            id = version.versionId,
                            name = version.name,
                            entries = record.versionEntries
                                .asSequence()
                                .filter { row -> row.versionId == version.versionId }
                                .sortedBy(SettingLibraryVersionEntryEntity::sortIndex)
                                .map { row -> entryFromJson(JSONObject(row.payloadJson)) }
                                .toList(),
                            groups = record.versionGroups
                                .asSequence()
                                .filter { row -> row.versionId == version.versionId }
                                .sortedBy(SettingLibraryVersionGroupEntity::sortIndex)
                                .mapIndexed { index, row -> groupFromJson(index, JSONObject(row.payloadJson)) }
                                .toList(),
                            promptPositions = JSONArray(version.promptPositionsJson).jsonObjects().mapIndexed(::promptPositionFromJson),
                            listAllExpanded = version.listAllExpanded,
                            expandedGroupIds = JSONArray(version.expandedGroupIdsJson).strings(),
                            createdAt = version.createdAt,
                            updatedAt = version.updatedAt,
                        )
                    },
                listAllExpanded = entity.listAllExpanded,
                expandedGroupIds = JSONArray(entity.expandedGroupIdsJson).strings(),
            )
        }.getOrElse { error ->
            throw ElecKoiDataException("Room 设定库数据损坏：${error.message}", error)
        }
    }

    fun toEntity(library: SettingLibrary, updatedAt: String): SettingLibraryRecord = SettingLibraryRecord(
        library = SettingLibraryEntity(
            characterId = library.characterId,
            name = library.name,
            activeVersionId = library.activeVersionId,
            listAllExpanded = library.listAllExpanded,
            expandedGroupIdsJson = JSONArray(library.expandedGroupIds).toString(),
            promptPositionsJson = JSONArray(library.promptPositions.map(::promptPositionToJson)).toString(),
            updatedAt = updatedAt,
        ),
        entries = library.entries.mapIndexed { index, entry ->
            SettingLibraryEntryEntity(
                characterId = library.characterId,
                entryId = entry.id,
                sortIndex = index,
                payloadJson = entryToJson(entry).toString(),
            )
        },
        groups = library.groups.mapIndexed { index, group ->
            SettingLibraryGroupEntity(
                characterId = library.characterId,
                groupId = group.id,
                sortIndex = index,
                payloadJson = groupToJson(group).toString(),
            )
        },
        versions = library.versions.mapIndexed { index, version ->
            SettingLibraryVersionEntity(
                characterId = library.characterId,
                versionId = version.id,
                sortIndex = index,
                name = version.name,
                listAllExpanded = version.listAllExpanded,
                expandedGroupIdsJson = JSONArray(version.expandedGroupIds).toString(),
                promptPositionsJson = JSONArray(version.promptPositions.map(::promptPositionToJson)).toString(),
                createdAt = version.createdAt,
                updatedAt = version.updatedAt,
            )
        },
        versionEntries = library.versions.flatMap { version ->
            version.entries.mapIndexed { index, entry ->
                SettingLibraryVersionEntryEntity(
                    characterId = library.characterId,
                    versionId = version.id,
                    entryId = entry.id,
                    sortIndex = index,
                    payloadJson = entryToJson(entry).toString(),
                )
            }
        },
        versionGroups = library.versions.flatMap { version ->
            version.groups.mapIndexed { index, group ->
                SettingLibraryVersionGroupEntity(
                    characterId = library.characterId,
                    versionId = version.id,
                    groupId = group.id,
                    sortIndex = index,
                    payloadJson = groupToJson(group).toString(),
                )
            }
        },
    )

    fun versionToJson(version: SettingLibraryVersion): JSONObject = JSONObject()
        .put("id", version.id)
        .put("name", version.name)
        .put("entries", JSONArray(version.entries.map(::entryToJson)))
        .put("groups", JSONArray(version.groups.map(::groupToJson)))
        .put("prompt_positions", JSONArray(version.promptPositions.map(::promptPositionToJson)))
        .put("list_all_expanded", version.listAllExpanded)
        .put("expanded_group_ids", JSONArray(version.expandedGroupIds))
        .put("created_at", version.createdAt)
        .put("updated_at", version.updatedAt)

    fun entryToJson(entry: SettingLibraryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("title", entry.title)
        .put("icon_id", entry.iconId)
        .put("kind", entry.kind.storageValue)
        .put("group_id", entry.groupId)
        .put("content", entry.content)
        .put("opening_messages", JSONArray(entry.openingMessages.map(::openingMessageToJson)))
        .put("default_opening_message_id", entry.defaultOpeningMessageId)
        .put("agent_selection_hint", entry.agentSelectionHint)
        .put("agent_read_strategy", entry.agentReadStrategy.storageValue)
        .put("agent_read_condition", entry.agentReadCondition)
        .put("dynamic_mode", entry.dynamicMode.storageValue)
        .put("keywords", JSONArray(entry.keywords))
        .put("keyword_scan_depth", entry.keywordScanDepth)
        .put("condition_keywords", JSONArray(entry.conditionKeywords))
        .put("keyword_condition", entry.keywordCondition.storageValue)
        .put("keyword_use_regex", entry.keywordUseRegex)
        .put("keyword_ignore_case", entry.keywordIgnoreCase)
        .put("keyword_whole_word", entry.keywordWholeWord)
        .put("keyword_recursion_depth", entry.keywordRecursionDepth)
        .put("trigger_mode", entry.triggerMode?.storageValue.orEmpty())
        .put("enabled", entry.enabled)
        .put("position", entry.position?.storageValue.orEmpty())
        .put("prompt_position_id", entry.promptPositionId)
        .put("insert_role", entry.insertRole.storageValue)
        .put("order", entry.order)
        .put("view_order", entry.viewOrder)
        .put("group_view_order", entry.groupViewOrder)
        .put("tree_view_order", entry.treeViewOrder)
        .put("created_at", entry.createdAt)
        .put("updated_at", entry.updatedAt)

    fun entryFromJson(value: JSONObject): SettingLibraryEntry {
        return SettingLibraryEntry(
        id = value.optString("id"),
        title = value.optString("title"),
        iconId = value.optString("icon_id"),
        kind = SettingLibraryEntryKind.entries.firstOrNull { it.storageValue == value.optString("kind") }
            ?: SettingLibraryEntryKind.Normal,
        groupId = value.optString("group_id"),
        content = value.optString("content"),
        openingMessages = value.optJSONArray("opening_messages")
            ?.jsonObjects()
            ?.map(::openingMessageFromJson)
            .orEmpty(),
        defaultOpeningMessageId = value.optString("default_opening_message_id"),
        agentSelectionHint = value.optString("agent_selection_hint"),
        agentReadStrategy = SettingLibraryAgentReadStrategy.entries.firstOrNull {
            it.storageValue == value.optString("agent_read_strategy")
        } ?: SettingLibraryAgentReadStrategy.Normal,
        agentReadCondition = value.optString("agent_read_condition"),
        dynamicMode = SettingLibraryDynamicMode.entries.firstOrNull {
            it.storageValue == value.optString("dynamic_mode")
        } ?: SettingLibraryDynamicMode.SingleCondition,
        keywords = value.optJSONArray("keywords")?.strings().orEmpty(),
        keywordScanDepth = value.optInt("keyword_scan_depth", 1),
        conditionKeywords = value.optJSONArray("condition_keywords")?.strings().orEmpty(),
        keywordCondition = SettingLibraryKeywordCondition.entries.firstOrNull {
            it.storageValue == value.optString("keyword_condition")
        } ?: SettingLibraryKeywordCondition.None,
        keywordUseRegex = value.optBoolean("keyword_use_regex", false),
        keywordIgnoreCase = value.optBoolean("keyword_ignore_case", true),
        keywordWholeWord = value.optBoolean("keyword_whole_word", false),
        keywordRecursionDepth = value.optInt("keyword_recursion_depth", 0).coerceAtLeast(0),
        triggerMode = SettingLibraryTriggerMode.entries.firstOrNull {
            it.storageValue == value.optString("trigger_mode")
        },
        enabled = value.optBoolean("enabled", true),
        position = SettingLibraryPosition.entries.firstOrNull {
            it.storageValue == value.optString("position")
        },
        promptPositionId = value.optString("prompt_position_id"),
        insertRole = SettingLibraryInsertRole.entries.firstOrNull {
            it.storageValue == value.optString("insert_role")
        } ?: SettingLibraryInsertRole.User,
        order = value.optInt("order", 1),
        viewOrder = value.optInt("view_order", 0),
        groupViewOrder = value.optInt("group_view_order", 0),
        treeViewOrder = value.optInt("tree_view_order", 0),
        createdAt = value.optString("created_at"),
        updatedAt = value.optString("updated_at"),
    )
    }

    private fun openingMessageToJson(message: SettingLibraryOpeningMessage): JSONObject = JSONObject()
        .put("id", message.id)
        .put("title", message.title)
        .put("content", message.content)
        .put("initial_variable_state", message.initialVariableStateJson)

    private fun openingMessageFromJson(value: JSONObject): SettingLibraryOpeningMessage =
        SettingLibraryOpeningMessage(
            id = value.optString("id"),
            title = value.optString("title"),
            content = value.optString("content"),
            initialVariableStateJson = value.optString("initial_variable_state"),
        )

    fun groupToJson(group: SettingLibraryGroup): JSONObject = JSONObject()
        .put("id", group.id)
        .put("name", group.name)
        .put("parent_id", group.parentId)
        .put("order", group.order)
        .put("tree_view_order", group.treeViewOrder)
        .put("created_at", group.createdAt)
        .put("updated_at", group.updatedAt)

    fun groupFromJson(index: Int, value: JSONObject): SettingLibraryGroup = SettingLibraryGroup(
        id = value.optString("id"),
        name = value.optString("name"),
        parentId = value.optString("parent_id"),
        order = value.optInt("order", index + 1),
        treeViewOrder = value.optInt("tree_view_order", index + 1),
        createdAt = value.optString("created_at"),
        updatedAt = value.optString("updated_at"),
    )

    fun promptPositionToJson(position: SettingLibraryPromptPosition): JSONObject = JSONObject()
        .put("id", position.id)
        .put("name", position.name)
        .put("anchor", position.anchor.storageValue)
        .put("side", position.side.storageValue)
        .put("order", position.order)
        .put("created_at", position.createdAt)
        .put("updated_at", position.updatedAt)

    fun promptPositionFromJson(index: Int, value: JSONObject): SettingLibraryPromptPosition =
        SettingLibraryPromptPosition(
            id = value.optString("id"),
            name = value.optString("name"),
            anchor = SettingLibraryPosition.entries.firstOrNull {
                it.storageValue == value.optString("anchor")
            } ?: SettingLibraryPosition.InsertPoint1,
            side = SettingLibraryPromptPositionSide.entries.firstOrNull {
                it.storageValue == value.optString("side")
            } ?: SettingLibraryPromptPositionSide.BeforeSettingPosition,
            order = value.optInt("order", index + 1).coerceAtLeast(1),
            createdAt = value.optString("created_at"),
            updatedAt = value.optString("updated_at"),
        )

    private fun versionFromJson(index: Int, value: JSONObject): SettingLibraryVersion = SettingLibraryVersion(
        id = value.optString("id").ifBlank { "library-snapshot-${index + 1}" },
        name = value.optString("name"),
        entries = value.optJSONArray("entries")?.jsonObjects()?.map(::entryFromJson).orEmpty(),
        groups = value.optJSONArray("groups")?.jsonObjects()?.mapIndexed(::groupFromJson).orEmpty(),
        promptPositions = value.optJSONArray("prompt_positions")?.jsonObjects()?.mapIndexed(::promptPositionFromJson).orEmpty(),
        listAllExpanded = value.optBoolean("list_all_expanded", true),
        expandedGroupIds = value.optJSONArray("expanded_group_ids")?.strings().orEmpty(),
        createdAt = value.optString("created_at"),
        updatedAt = value.optString("updated_at"),
    )

    private fun JSONArray.jsonObjects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }


    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun requireCurrentFormat(value: JSONObject) {
        if (value.optString("format") != Format || value.optInt("version") != FormatVersion) {
            throw ElecKoiDataException("设定库格式版本不匹配，请删除旧设定库后重新创建")
        }
    }
}
