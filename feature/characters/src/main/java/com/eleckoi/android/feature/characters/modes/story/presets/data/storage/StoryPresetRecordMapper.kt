package com.eleckoi.android.feature.characters.modes.story.presets.data.storage

import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetModelFamily
import com.eleckoi.android.feature.characters.modes.story.presets.model.withRequiredBuiltIns
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleJsonCodec
import com.eleckoi.android.feature.characters.modes.story.regex.data.normalizedRegexRules
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryJsonCodec
import com.eleckoi.android.foundation.storage.room.StoryPresetEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetContentEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetEntryEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetGroupEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetRecord
import com.eleckoi.android.foundation.storage.room.StoryPresetRuntimeEntryEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionContentEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionEntryEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionGroupEntity
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionRecord
import com.eleckoi.android.foundation.storage.room.StoryPresetVersionRuntimeEntryEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun StoryPreset.toStorageRecord(sortIndex: Int): StoryPresetRecord = StoryPresetRecord(
    preset = StoryPresetEntity(
        id = id,
        name = name,
        modelFamily = modelFamily.storageValue,
        modelTagsJson = StoryPresetMetadataCodec.encodeModelTags(modelTags),
        libraryGroupId = libraryGroupId,
        activeVersionId = activeVersionId,
        authorName = profile.authorName,
        authorAvatarPath = profile.authorAvatarPath,
        authorTagsJson = StoryPresetMetadataCodec.encodeStringList(profile.tags),
        description = profile.description,
        sortIndex = sortIndex,
        expandedGroupIdsJson = JSONArray(expandedGroupIds.distinct()).toString(),
    ),
    contents = listOf(
        StoryPresetContentEntity(id, TimelineContentKind, StoryPresetMetadataCodec.encodeTimeline(profile.timeline)),
        StoryPresetContentEntity(id, RegexRulesContentKind, RegexRuleJsonCodec.encodeRules(regexRules)),
        StoryPresetContentEntity(
            id,
            PromptPositionsContentKind,
            JSONArray(promptPositions.map(SettingLibraryJsonCodec::promptPositionToJson)).toString(),
        ),
    ),
    entries = entries.mapIndexed { index, entry ->
        StoryPresetEntryEntity(
            presetId = id,
            entryId = entry.id,
            sortIndex = index,
            payloadJson = SettingLibraryJsonCodec.entryToJson(entry).toString(),
        )
    },
    groups = groups.mapIndexed { index, group ->
        StoryPresetGroupEntity(
            presetId = id,
            groupId = group.id,
            sortIndex = index,
            payloadJson = SettingLibraryJsonCodec.groupToJson(group).toString(),
        )
    },
    runtimeEntries = emptyList(),
)

internal fun StoryPresetRecord.toStoryPreset(): StoryPreset {
    val contentByKind = contents.associate { it.kind to it.content }
    return StoryPreset(
        id = preset.id,
        name = preset.name,
        modelFamily = StoryPresetModelFamily.fromStorage(preset.modelFamily),
        modelTags = StoryPresetMetadataCodec.decodeModelTags(preset.modelTagsJson, preset.modelFamily),
        libraryGroupId = preset.libraryGroupId,
        activeVersionId = preset.activeVersionId,
        activeVersionNumber = runCatching {
            preset.activeVersionId.substringAfterLast(":v").toInt()
        }.getOrDefault(1),
        profile = StoryPresetMetadataCodec.decodeProfile(
            authorName = preset.authorName,
            authorAvatarPath = preset.authorAvatarPath,
            authorTagsJson = preset.authorTagsJson,
            description = preset.description,
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

internal fun StoryPresetRecord.toVersionRecord(
    versionId: String,
    versionNumber: Int,
    versionName: String,
    createdAtEpochMs: Long,
): StoryPresetVersionRecord = StoryPresetVersionRecord(
    version = StoryPresetVersionEntity(
        presetId = preset.id,
        versionId = versionId,
        versionNumber = versionNumber,
        name = versionName,
        createdAtEpochMs = createdAtEpochMs,
        expandedGroupIdsJson = preset.expandedGroupIdsJson,
    ),
    contents = contents.map { row ->
        StoryPresetVersionContentEntity(
            presetId = row.presetId,
            versionId = versionId,
            kind = row.kind,
            content = row.content,
        )
    },
    entries = entries.map { row ->
        StoryPresetVersionEntryEntity(
            presetId = row.presetId,
            versionId = versionId,
            entryId = row.entryId,
            sortIndex = row.sortIndex,
            payloadJson = row.payloadJson,
        )
    },
    groups = groups.map { row ->
        StoryPresetVersionGroupEntity(
            presetId = row.presetId,
            versionId = versionId,
            groupId = row.groupId,
            sortIndex = row.sortIndex,
            payloadJson = row.payloadJson,
        )
    },
    runtimeEntries = runtimeEntries.map { row ->
        StoryPresetVersionRuntimeEntryEntity(
            presetId = row.presetId,
            versionId = versionId,
            slot = row.slot,
            contentOverride = row.contentOverride,
            enabled = row.enabled,
        )
    },
)

internal fun StoryPresetVersionRecord.toWorkingRecord(metadata: StoryPresetEntity): StoryPresetRecord =
    StoryPresetRecord(
        preset = metadata.copy(
            activeVersionId = version.versionId,
            expandedGroupIdsJson = version.expandedGroupIdsJson,
        ),
        contents = contents.map { row ->
            StoryPresetContentEntity(metadata.id, row.kind, row.content)
        },
        entries = entries.map { row ->
            StoryPresetEntryEntity(row.presetId, row.entryId, row.sortIndex, row.payloadJson)
        },
        groups = groups.map { row ->
            StoryPresetGroupEntity(row.presetId, row.groupId, row.sortIndex, row.payloadJson)
        },
        runtimeEntries = runtimeEntries.map { row ->
            StoryPresetRuntimeEntryEntity(
                row.presetId,
                row.slot,
                row.contentOverride,
                row.enabled,
            )
        },
    )

internal fun StoryPresetVersionRecord.toStandaloneRecord(metadata: StoryPresetEntity): StoryPresetRecord =
    StoryPresetRecord(
        preset = metadata.copy(
            expandedGroupIdsJson = version.expandedGroupIdsJson,
        ),
        contents = contents.map { row ->
            StoryPresetContentEntity(metadata.id, row.kind, row.content)
        },
        entries = entries.map { row ->
            StoryPresetEntryEntity(metadata.id, row.entryId, row.sortIndex, row.payloadJson)
        },
        groups = groups.map { row ->
            StoryPresetGroupEntity(metadata.id, row.groupId, row.sortIndex, row.payloadJson)
        },
        runtimeEntries = runtimeEntries.map { row ->
            StoryPresetRuntimeEntryEntity(
                metadata.id,
                row.slot,
                row.contentOverride,
                row.enabled,
            )
        },
    )

internal const val TimelineContentKind = "timeline"
internal const val RegexRulesContentKind = "regex_rules"
internal const val PromptPositionsContentKind = "prompt_positions"
