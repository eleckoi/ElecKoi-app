package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso
import java.util.Locale

internal object SettingLibraryNormalizer {
    fun normalize(characterId: String, source: SettingLibrary): SettingLibrary {
        val now = nowIso()
        val requestedVersionId = source.activeVersionId.trim().ifBlank {
            source.versions.firstOrNull { it.id.isNotBlank() }?.id ?: "library-${newId(8)}"
        }
        val previousActive = source.versions.firstOrNull { it.id == requestedVersionId }
        val activeSource = SettingLibraryVersion(
            id = requestedVersionId,
            name = source.name,
            entries = source.entries,
            groups = source.groups,
            promptPositions = source.promptPositions,
            listAllExpanded = source.listAllExpanded,
            expandedGroupIds = source.expandedGroupIds,
            createdAt = previousActive?.createdAt.orEmpty(),
            updatedAt = previousActive?.updatedAt.orEmpty(),
        )
        var activeInserted = false
        val versionSources = buildList {
            source.versions.forEach { version ->
                if (version.id == requestedVersionId) {
                    if (!activeInserted) {
                        add(activeSource)
                        activeInserted = true
                    }
                } else {
                    add(version)
                }
            }
            if (!activeInserted) add(activeSource)
        }
        val seenVersionIds = mutableSetOf<String>()
        val versions = versionSources.mapNotNull { version ->
            val versionId = version.id.trim().ifBlank { "library-${newId(8)}" }
            if (!seenVersionIds.add(versionId)) {
                null
            } else {
                normalizeVersion(
                    source = version.copy(id = versionId),
                    now = now,
                    touchUpdatedAt = versionId == requestedVersionId,
                )
            }
        }
        val activeVersion = versions.first { it.id == requestedVersionId }
        return SettingLibrary(
            characterId = characterId,
            name = activeVersion.name,
            entries = activeVersion.entries,
            groups = activeVersion.groups,
            promptPositions = activeVersion.promptPositions,
            activeVersionId = activeVersion.id,
            versions = versions,
            listAllExpanded = activeVersion.listAllExpanded,
            expandedGroupIds = activeVersion.expandedGroupIds,
        )
    }

    private fun normalizeVersion(
        source: SettingLibraryVersion,
        now: String,
        touchUpdatedAt: Boolean,
    ): SettingLibraryVersion {
        val groups = source.groups.mapIndexed { index, group ->
            group.copy(
                id = group.id.ifBlank { "group-${newId(8)}" },
                name = group.name.trim().take(80),
                parentId = group.parentId.takeIf { it != group.id }.orEmpty(),
                order = group.order.coerceAtLeast(index + 1),
                treeViewOrder = group.treeViewOrder.takeIf { it > 0 } ?: (index + 1),
                createdAt = group.createdAt.ifBlank { now },
                updatedAt = if (touchUpdatedAt) now else group.updatedAt.ifBlank { now },
            )
        }
        val sourceOpening = source.entries.firstOrNull { it.isOpeningEntry() }
        val opening = settingLibraryOpeningEntry(
            sourceOpening?.copy(
                createdAt = sourceOpening.createdAt.ifBlank { now },
                updatedAt = if (touchUpdatedAt) now else sourceOpening.updatedAt.ifBlank { now },
            ),
        )
        val promptPositions = normalizePromptPositions(source.promptPositions, now, touchUpdatedAt)
        val promptPositionIds = promptPositions.mapTo(hashSetOf()) { it.id }
        val normalizedUserEntries = source.entries
            .filterNot { it.isFixedEntry() }
            .mapIndexed { index, entry ->
                normalizeEntry(entry, index + 1, now, touchUpdatedAt).let { normalized ->
                    if (normalized.promptPositionId.isBlank() || normalized.promptPositionId in promptPositionIds) {
                        normalized
                    } else {
                        normalized.copy(promptPositionId = "")
                    }
                }
            }
        requireUniqueLogicalNames(normalizedUserEntries, groups)
        val duplicateOrderKeys = normalizedUserEntries
            .filter { it.triggerMode == SettingLibraryTriggerMode.Always && it.position != null }
            .groupingBy { entry ->
                Triple(
                    entry.triggerMode,
                    entry.promptPositionId.ifBlank { entry.position?.storageValue.orEmpty() },
                    entry.order,
                )
            }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        val entries = listOf(opening) + normalizedUserEntries.map { entry ->
            val key = Triple(
                entry.triggerMode,
                entry.promptPositionId.ifBlank { entry.position?.storageValue.orEmpty() },
                entry.order,
            )
            if (
                entry.triggerMode == SettingLibraryTriggerMode.Always &&
                key in duplicateOrderKeys
            ) {
                entry.copy(enabled = false)
            } else {
                entry
            }
        }
        return SettingLibraryVersion(
            id = source.id,
            name = source.name.trim(),
            entries = entries,
            groups = groups,
            promptPositions = promptPositions,
            listAllExpanded = source.listAllExpanded,
            expandedGroupIds = source.expandedGroupIds.distinct(),
            createdAt = source.createdAt.ifBlank { now },
            updatedAt = if (touchUpdatedAt) now else source.updatedAt.ifBlank { now },
        )
    }

    private fun normalizeEntry(
        entry: SettingLibraryEntry,
        index: Int,
        now: String,
        touchUpdatedAt: Boolean,
    ): SettingLibraryEntry {
        return entry.copy(
            id = entry.id.ifBlank { "setting-${newId(12)}" },
            title = entry.title.trim().take(120),
            groupId = entry.groupId.trim(),
            position = entry.position,
            promptPositionId = entry.promptPositionId.trim(),
            insertRole = if (entry.position == SettingLibraryPosition.Instructions) {
                SettingLibraryInsertRole.System
            } else {
                entry.insertRole.takeUnless { it == SettingLibraryInsertRole.System }
                    ?: SettingLibraryInsertRole.User
            },
            content = entry.content,
            agentSelectionHint = entry.agentSelectionHint
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MaxSettingLibrarySelectionHintCharacters),
            enabled = entry.enabled && when (entry.triggerMode) {
                SettingLibraryTriggerMode.AgentTool -> true
                SettingLibraryTriggerMode.Always -> entry.position != null
                null -> false
            },
            keywords = entry.keywords.map(String::trim).filter(String::isNotBlank).distinct(),
            conditionKeywords = entry.conditionKeywords.map(String::trim).filter(String::isNotBlank).distinct(),
            order = entry.order.coerceAtLeast(1),
            treeViewOrder = entry.treeViewOrder.takeIf { it > 0 } ?: index,
            createdAt = entry.createdAt.ifBlank { now },
            updatedAt = if (touchUpdatedAt) now else entry.updatedAt.ifBlank { now },
        )
    }

    private fun normalizePromptPositions(
        source: List<SettingLibraryPromptPosition>,
        now: String,
        touchUpdatedAt: Boolean,
    ): List<SettingLibraryPromptPosition> {
        val seenIds = mutableSetOf<String>()
        return source.mapIndexedNotNull { _, position ->
            val id = position.id.trim().ifBlank { "prompt-position-${newId(10)}" }
            if (!seenIds.add(id)) return@mapIndexedNotNull null
            position.copy(
                id = id,
                name = position.name.trim().take(60).ifBlank { "未命名提示词位置" },
                anchor = position.anchor.takeUnless { it == SettingLibraryPosition.Instructions }
                    ?: SettingLibraryPosition.InsertPoint1,
                order = position.order.coerceAtLeast(1),
                createdAt = position.createdAt.ifBlank { now },
                updatedAt = if (touchUpdatedAt) now else position.updatedAt.ifBlank { now },
            )
        }.sortedWith(
            compareBy<SettingLibraryPromptPosition> { it.anchor.ordinal }
                .thenBy { it.side.ordinal }
                .thenBy(SettingLibraryPromptPosition::order)
                .thenBy(SettingLibraryPromptPosition::id),
        ).groupBy { it.anchor to it.side }
            .flatMap { (_, positions) ->
                positions.mapIndexed { index, position -> position.copy(order = index + 1) }
            }
    }

    fun requireUniqueLogicalNames(
        entries: List<SettingLibraryEntry>,
        groups: List<SettingLibraryGroup>,
    ) {
        groups.groupBy { group ->
            group.parentId to settingLibrarySafePathSegment(group.name).lowercase(Locale.ROOT)
        }.values.firstOrNull { siblings -> siblings.size > 1 }?.let { duplicates ->
            throw ElecKoiDataException(
                "同一文件夹下已存在同名文件夹：${duplicates.first().name.ifBlank { "未命名文件夹" }}",
            )
        }
        entries.groupBy { entry ->
            entry.groupId to settingLibrarySafePathSegment(entry.title).lowercase(Locale.ROOT)
        }.values.firstOrNull { siblings -> siblings.size > 1 }?.let { duplicates ->
            throw ElecKoiDataException(
                "同一文件夹下已存在同名设定：${duplicates.first().title.ifBlank { "未命名设定" }}",
            )
        }
    }
}
