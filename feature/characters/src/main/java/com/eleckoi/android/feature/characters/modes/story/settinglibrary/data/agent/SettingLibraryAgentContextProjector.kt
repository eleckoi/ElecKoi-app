package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry

internal object SettingLibraryAgentContextProjector {
    fun project(
        characterId: String,
        library: SettingLibrary,
    ): SettingLibraryAgentTurnContext {
        val groupsById = library.groups.associateBy(SettingLibraryGroup::id)
        val automaticEntries = library.entries.filter { entry ->
            !entry.isFixedEntry() &&
                entry.enabled &&
                entry.triggerMode == SettingLibraryTriggerMode.Always
        }
        fun SettingLibraryEntry.toAgentEntry(): SettingLibraryAgentEntry {
            val groupPath = settingLibraryGroupPath(groupsById[groupId], groupsById)
            return SettingLibraryAgentEntry(
                id = id,
                title = title,
                groupId = groupId,
                groupPath = groupPath,
                path = listOf(
                    groupPath,
                    settingLibrarySafePathSegment(title.ifBlank { "未命名设定" }),
                ).filter(String::isNotBlank).joinToString("/"),
                content = content,
                selectionHint = agentSelectionHint,
                readStrategy = agentReadStrategy,
                dynamicMode = dynamicMode,
                treeOrderPath = settingLibraryTreeOrderPath(this, groupsById),
            )
        }
        val readableEntries = library.entries.asSequence()
            .filterNot(SettingLibraryEntry::isFixedEntry)
            .filter { entry ->
                    entry.enabled &&
                    entry.triggerMode == SettingLibraryTriggerMode.AgentTool &&
                    (entry.content.isNotBlank() || entry.dynamicMode == SettingLibraryDynamicMode.EjsReference)
            }
            .map(SettingLibraryEntry::toAgentEntry)
            .distinctBy(SettingLibraryAgentEntry::id)
            .sortedWith(settingLibraryTreeOrderComparator(SettingLibraryAgentEntry::treeOrderPath))
            .toList()
        val agentGroups = library.groups.map { group ->
            SettingLibraryAgentGroup(
                id = group.id,
                name = group.name,
                parentId = group.parentId,
                path = settingLibraryGroupPath(group, groupsById),
            )
        }
        return SettingLibraryAgentTurnContext(
            automaticLibrary = SettingLibrary(
                characterId = characterId,
                name = library.name,
                entries = automaticEntries,
                groups = library.groups,
                promptPositions = library.promptPositions,
                activeVersionId = library.activeVersionId,
            ),
            readableEntries = readableEntries,
            keywordStrategyEntries = library.entries.filter { entry ->
                !entry.isFixedEntry() &&
                    entry.enabled &&
                    entry.triggerMode == SettingLibraryTriggerMode.AgentTool &&
                    entry.agentReadStrategy == SettingLibraryAgentReadStrategy.Keyword &&
                    entry.content.isNotBlank()
            },
            groups = agentGroups,
        )
    }
}
