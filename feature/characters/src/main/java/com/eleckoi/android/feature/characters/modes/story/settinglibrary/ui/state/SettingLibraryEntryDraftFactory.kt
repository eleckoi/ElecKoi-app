package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode

internal enum class SettingLibraryEntryDraftKind {
    Standard,
    EjsReference,
}

internal fun createSettingLibraryEntryDraft(
    id: String,
    groupId: String,
    triggerMode: SettingLibraryTriggerMode,
    existingEntries: List<SettingLibraryEntry>,
    treeViewOrder: Int,
    kind: SettingLibraryEntryDraftKind = SettingLibraryEntryDraftKind.Standard,
): SettingLibraryEntry {
    val base = SettingLibraryEntry(
        id = id,
        title = nextSettingLibraryEntryDraftTitle(groupId, existingEntries),
        enabled = false,
        viewOrder = (existingEntries.maxOfOrNull { it.viewOrder } ?: 0) + 1,
        groupId = groupId,
        triggerMode = triggerMode,
        position = if (triggerMode == SettingLibraryTriggerMode.Cache) {
            SettingLibraryPosition.InsertPoint1
        } else {
            null
        },
        insertRole = SettingLibraryInsertRole.User,
        order = if (triggerMode == SettingLibraryTriggerMode.Cache) {
            existingEntries
                .filter { it.triggerMode == SettingLibraryTriggerMode.Cache }
                .maxOfOrNull(SettingLibraryEntry::order)
                ?.plus(1)
                ?: 1
        } else {
            1
        },
        groupViewOrder = if (groupId.isBlank()) {
            0
        } else {
            (existingEntries.filter { it.groupId == groupId }.maxOfOrNull { it.groupViewOrder } ?: 0) + 1
        },
        treeViewOrder = treeViewOrder,
    )
    return if (kind == SettingLibraryEntryDraftKind.EjsReference) {
        base.copy(
            enabled = true,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            agentReadStrategy = SettingLibraryAgentReadStrategy.VariableCondition,
            dynamicMode = SettingLibraryDynamicMode.EjsReference,
        )
    } else {
        base
    }
}

internal fun nextSettingLibraryEntryDraftTitle(
    groupId: String,
    existingEntries: List<SettingLibraryEntry>,
): String {
    val siblingTitles = existingEntries
        .asSequence()
        .filter { it.groupId == groupId }
        .map { it.title.trim() }
        .filter(String::isNotBlank)
        .toHashSet()
    if (NewSettingTitle !in siblingTitles) return NewSettingTitle
    var suffix = 2
    while ("$NewSettingTitle $suffix" in siblingTitles) suffix++
    return "$NewSettingTitle $suffix"
}

private const val NewSettingTitle = "新建设定"
