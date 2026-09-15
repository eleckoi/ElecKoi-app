package com.eleckoi.android.feature.characters.presets.data.storage

import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.withRequiredBuiltIns
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleJsonCodec
import com.eleckoi.android.feature.characters.modes.story.regex.data.normalizedRegexRules
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryJsonCodec
import com.eleckoi.android.foundation.storage.room.AgentPresetEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetContentEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetEntryEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetGroupEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetRecord
import com.eleckoi.android.foundation.storage.room.AgentPresetVersionEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetVersionContentEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetVersionEntryEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetVersionGroupEntity
import com.eleckoi.android.foundation.storage.room.AgentPresetVersionRecord
import org.json.JSONArray
import org.json.JSONObject

internal fun AgentPreset.toStorageRecord(sortIndex: Int): AgentPresetRecord = AgentPresetRecord(
    preset = AgentPresetEntity(
        id = id,
        name = name,
        modelFamily = modelFamily.storageValue,
        modelTagsJson = AgentPresetMetadataCodec.encodeModelTags(modelTags),
        libraryGroupId = libraryGroupId,
        activeVersionId = activeVersionId,
        authorName = profile.authorName,
        authorAvatarPath = profile.authorAvatarPath,
        sortIndex = sortIndex,
        expandedGroupIdsJson = JSONArray(expandedGroupIds.distinct()).toString(),
    ),
    contents = listOf(
        AgentPresetContentEntity(id, UsageInstructionsContentKind, profile.usageInstructions),
        AgentPresetContentEntity(
            id,
            ToolConfigurationContentKind,
            AgentPresetMetadataCodec.encodeToolConfiguration(toolConfiguration, roleplayPlan),
        ),
        AgentPresetContentEntity(id, TimelineContentKind, AgentPresetMetadataCodec.encodeTimeline(profile.timeline)),
        AgentPresetContentEntity(id, RegexRulesContentKind, RegexRuleJsonCodec.encodeRules(regexRules)),
        AgentPresetContentEntity(
            id,
            PromptPositionsContentKind,
            JSONArray(promptPositions.map(SettingLibraryJsonCodec::promptPositionToJson)).toString(),
        ),
    ),
    entries = entries.mapIndexed { index, entry ->
        AgentPresetEntryEntity(
            presetId = id,
            entryId = entry.id,
            sortIndex = index,
            payloadJson = SettingLibraryJsonCodec.entryToJson(entry).toString(),
        )
    },
    groups = groups.mapIndexed { index, group ->
        AgentPresetGroupEntity(
            presetId = id,
            groupId = group.id,
            sortIndex = index,
            payloadJson = SettingLibraryJsonCodec.groupToJson(group).toString(),
        )
    },
)

internal fun AgentPresetRecord.toAgentPreset(): AgentPreset {
    val contentByKind = contents.associate { it.kind to it.content }
    return AgentPreset(
        id = preset.id,
        name = preset.name,
        modelFamily = AgentPresetModelFamily.fromStorage(preset.modelFamily),
        modelTags = AgentPresetMetadataCodec.decodeModelTags(preset.modelTagsJson, preset.modelFamily),
        libraryGroupId = preset.libraryGroupId,
        activeVersionId = preset.activeVersionId,
        activeVersionNumber = runCatching {
            preset.activeVersionId.substringAfterLast(":v").toInt()
        }.getOrDefault(1),
        profile = AgentPresetMetadataCodec.decodeProfile(
            authorName = preset.authorName,
            authorAvatarPath = preset.authorAvatarPath,
            usageInstructions = contentByKind[UsageInstructionsContentKind].orEmpty(),
            timelineJson = contentByKind[TimelineContentKind].orEmpty(),
        ),
        entries = entries.sortedBy { it.sortIndex }.mapNotNull { row ->
            runCatching {
                SettingLibraryJsonCodec.entryFromJson(JSONObject(row.payloadJson))
            }.getOrNull()
        },
        groups = groups.sortedBy { it.sortIndex }.mapIndexedNotNull { index, row ->
            runCatching {
                SettingLibraryJsonCodec.groupFromJson(index, JSONObject(row.payloadJson))
            }.getOrNull()
        },
        promptPositions = runCatching { JSONArray(contentByKind[PromptPositionsContentKind].orEmpty()) }
            .getOrDefault(JSONArray())
            .let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.let { value ->
                            add(SettingLibraryJsonCodec.promptPositionFromJson(index, value))
                        }
                    }
                }
            },
        toolConfiguration = AgentPresetMetadataCodec.decodeToolConfiguration(
            contentByKind[ToolConfigurationContentKind].orEmpty(),
        ),
        roleplayPlan = AgentPresetMetadataCodec.decodeRoleplayPlan(
            contentByKind[ToolConfigurationContentKind].orEmpty(),
        ),
        regexRules = RegexRuleJsonCodec.decodeRules(
            contentByKind[RegexRulesContentKind].orEmpty(),
        ).normalizedRegexRules(),
        expandedGroupIds = runCatching { JSONArray(preset.expandedGroupIdsJson) }
            .getOrDefault(JSONArray())
            .let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            },
    ).withRequiredBuiltIns()
}

internal fun AgentPresetRecord.toVersionRecord(
    versionId: String,
    versionNumber: Int,
    versionName: String,
    createdAtEpochMs: Long,
): AgentPresetVersionRecord = AgentPresetVersionRecord(
    version = AgentPresetVersionEntity(
        presetId = preset.id,
        versionId = versionId,
        versionNumber = versionNumber,
        name = versionName,
        createdAtEpochMs = createdAtEpochMs,
        expandedGroupIdsJson = preset.expandedGroupIdsJson,
    ),
    contents = contents.map { row ->
        AgentPresetVersionContentEntity(
            presetId = row.presetId,
            versionId = versionId,
            kind = row.kind,
            content = row.content,
        )
    },
    entries = entries.map { row ->
        AgentPresetVersionEntryEntity(
            presetId = row.presetId,
            versionId = versionId,
            entryId = row.entryId,
            sortIndex = row.sortIndex,
            payloadJson = row.payloadJson,
        )
    },
    groups = groups.map { row ->
        AgentPresetVersionGroupEntity(
            presetId = row.presetId,
            versionId = versionId,
            groupId = row.groupId,
            sortIndex = row.sortIndex,
            payloadJson = row.payloadJson,
        )
    },
)

internal fun AgentPresetVersionRecord.toWorkingRecord(metadata: AgentPresetEntity): AgentPresetRecord =
    AgentPresetRecord(
        preset = metadata.copy(
            activeVersionId = version.versionId,
            expandedGroupIdsJson = version.expandedGroupIdsJson,
        ),
        contents = contents.map { row ->
            AgentPresetContentEntity(metadata.id, row.kind, row.content)
        },
        entries = entries.map { row ->
            AgentPresetEntryEntity(row.presetId, row.entryId, row.sortIndex, row.payloadJson)
        },
        groups = groups.map { row ->
            AgentPresetGroupEntity(row.presetId, row.groupId, row.sortIndex, row.payloadJson)
        },
    )

internal fun AgentPresetVersionRecord.toStandaloneRecord(metadata: AgentPresetEntity): AgentPresetRecord =
    AgentPresetRecord(
        preset = metadata.copy(
            expandedGroupIdsJson = version.expandedGroupIdsJson,
        ),
        contents = contents.map { row ->
            AgentPresetContentEntity(metadata.id, row.kind, row.content)
        },
        entries = entries.map { row ->
            AgentPresetEntryEntity(metadata.id, row.entryId, row.sortIndex, row.payloadJson)
        },
        groups = groups.map { row ->
            AgentPresetGroupEntity(metadata.id, row.groupId, row.sortIndex, row.payloadJson)
        },
    )

internal const val UsageInstructionsContentKind = "usage_instructions"
internal const val ToolConfigurationContentKind = "tool_configuration"
internal const val TimelineContentKind = "timeline"
internal const val RegexRulesContentKind = "regex_rules"
internal const val PromptPositionsContentKind = "prompt_positions"
