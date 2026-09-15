package com.eleckoi.android.feature.characters.presets.data.importing

import com.eleckoi.android.feature.characters.presets.data.AgentPresetImportConversion
import com.eleckoi.android.feature.characters.presets.data.AgentPresetTransferVersion
import com.eleckoi.android.feature.characters.presets.data.media.AgentPresetAuthorAvatarStore
import com.eleckoi.android.feature.characters.presets.data.policy.uniqueAgentPresetName
import com.eleckoi.android.feature.characters.presets.data.storage.toStorageRecord
import com.eleckoi.android.feature.characters.presets.data.storage.toVersionRecord
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.toTag
import com.eleckoi.android.feature.characters.presets.model.withRequiredBuiltIns
import com.eleckoi.android.feature.characters.modes.story.regex.data.normalizedRegexRules
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.HiddenToolTimelineEntryId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.HistoryCompactionEntryId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHistoryCompactionEntry
import com.eleckoi.android.foundation.storage.room.AgentPresetDao
import java.util.UUID

/** Validates, rebases, and persists a foreign preset as a new local identity. */
internal class AgentPresetImportCoordinator(
    private val dao: AgentPresetDao,
    private val authorAvatars: AgentPresetAuthorAvatarStore,
) {
    suspend fun import(conversion: AgentPresetImportConversion): AgentPreset {
        val id = "agent-preset-${UUID.randomUUID()}"
        val currentTransfer = AgentPresetTransferVersion(
            id = conversion.preset.activeVersionId,
            number = conversion.preset.activeVersionNumber,
            name = conversion.preset.name,
            createdAtEpochMs = System.currentTimeMillis(),
            preset = conversion.preset,
        )
        val sourceVersions = conversion.versions.ifEmpty { listOf(currentTransfer) }.toMutableList()
        var activeIndex = sourceVersions.indexOfFirst { it.id == conversion.preset.activeVersionId }
        if (activeIndex < 0) {
            activeIndex = sourceVersions.indexOfFirst { it.number == conversion.preset.activeVersionNumber }
        }
        if (activeIndex < 0) {
            sourceVersions += currentTransfer
            activeIndex = sourceVersions.lastIndex
        }
        val importedVersions = sourceVersions.mapIndexed { index, version ->
            val localVersionId = "$id:v${index + 1}"
            version.copy(
                id = localVersionId,
                preset = rebaseContent(
                    source = version.preset,
                    presetId = id,
                    scope = "v${index + 1}",
                    versionId = localVersionId,
                    versionNumber = version.number,
                ),
            )
        }
        val activeVersion = importedVersions[activeIndex]
        val normalizedTags = normalizedTags(conversion.preset)
        val modelFamily = AgentPresetModelFamily.entries
            .firstOrNull { family -> normalizedTags.any { it.id == family.storageValue } }
            ?: conversion.preset.modelFamily
        val importedName = uniqueAgentPresetName(
            conversion.preset.name.trim().take(60).ifBlank { "导入预设" },
            dao.presetNames(),
        )
        val authorAvatarFile = authorAvatars.storeImported(id, conversion.authorAvatar)
        val imported = activeVersion.preset.copy(
            id = id,
            name = importedName,
            modelFamily = modelFamily,
            modelTags = normalizedTags,
            libraryGroupId = "",
            activeVersionId = activeVersion.id,
            activeVersionNumber = activeVersion.number,
            profile = conversion.preset.profile.copy(
                authorAvatarPath = authorAvatarFile?.absolutePath.orEmpty(),
                usageInstructions = activeVersion.preset.profile.usageInstructions,
                timeline = activeVersion.preset.profile.timeline,
            ),
        ).withRequiredBuiltIns()
        val sortIndex = dao.nextSortIndex()
        val record = imported.toStorageRecord(sortIndex = sortIndex)
        try {
            dao.replace(record)
            importedVersions.forEach { version ->
                val versionPreset = version.preset.copy(
                    id = id,
                    name = importedName,
                    modelFamily = modelFamily,
                    modelTags = normalizedTags,
                    libraryGroupId = "",
                    profile = conversion.preset.profile.copy(
                        authorAvatarPath = authorAvatarFile?.absolutePath.orEmpty(),
                        usageInstructions = version.preset.profile.usageInstructions,
                        timeline = version.preset.profile.timeline,
                    ),
                ).withRequiredBuiltIns()
                dao.replaceVersion(
                    versionPreset.toStorageRecord(sortIndex).toVersionRecord(
                        versionId = version.id,
                        versionNumber = version.number,
                        versionName = version.name,
                        createdAtEpochMs = version.createdAtEpochMs,
                    ),
                )
            }
        } catch (error: Throwable) {
            if (dao.presetExists(id)) dao.deletePreset(id)
            authorAvatarFile?.delete()
            throw error
        }
        return imported
    }

    private fun rebaseContent(
        source: AgentPreset,
        presetId: String,
        scope: String,
        versionId: String,
        versionNumber: Int,
    ): AgentPreset {
        val normalizedSource = source.withRequiredBuiltIns()
        val sourceGroups = normalizedSource.groups
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val groupIds = sourceGroups.mapIndexed { index, group ->
            group.id to "$presetId-$scope-group-${index + 1}"
        }.toMap()
        val sourcePositions = normalizedSource.promptPositions
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val promptPositionIds = sourcePositions.mapIndexed { index, position ->
            position.id to "$presetId-$scope-position-${index + 1}"
        }.toMap()
        return normalizedSource.copy(
            id = presetId,
            activeVersionId = versionId,
            activeVersionNumber = versionNumber,
            libraryGroupId = "",
            regexRules = normalizedSource.regexRules.mapIndexed { index, rule ->
                rule.copy(id = "$presetId-$scope-regex-${index + 1}", order = index)
            }.normalizedRegexRules(),
            groups = sourceGroups.mapIndexed { index, group ->
                group.copy(
                    id = groupIds.getValue(group.id),
                    parentId = groupIds[group.parentId].orEmpty(),
                    order = index + 1,
                    createdAt = "",
                    updatedAt = "",
                )
            },
            entries = normalizedSource.entries.mapIndexed { index, entry ->
                entry.copy(
                    id = when {
                        entry.isHistoryCompactionEntry() -> HistoryCompactionEntryId
                        entry.isHiddenToolTimelineEntry() -> HiddenToolTimelineEntryId
                        else -> "$presetId-$scope-entry-${index + 1}"
                    },
                    groupId = groupIds[entry.groupId].orEmpty(),
                    promptPositionId = promptPositionIds[entry.promptPositionId].orEmpty(),
                    createdAt = "",
                    updatedAt = "",
                )
            },
            promptPositions = sourcePositions.mapIndexed { index, position ->
                position.copy(
                    id = promptPositionIds.getValue(position.id),
                    order = index + 1,
                    createdAt = "",
                    updatedAt = "",
                )
            },
            expandedGroupIds = normalizedSource.expandedGroupIds
                .mapNotNull(groupIds::get)
                .distinct(),
            toolConfiguration = normalizedSource.toolConfiguration.copy(
                subagentModelConfigId = "",
                subagentModel = "",
                toolModelConfigIds = emptyMap(),
            ).normalized(),
        ).withRequiredBuiltIns()
    }

    private fun normalizedTags(source: AgentPreset): List<AgentPresetModelTag> = source.modelTags
            .map { it.copy(id = it.id.trim().lowercase(), label = it.label.trim()) }
            .filter { it.id.isNotBlank() && it.label.isNotBlank() }
            .distinctBy(AgentPresetModelTag::id)
            .take(8)
            .ifEmpty { listOf(AgentPresetModelFamily.General.toTag()) }
}
