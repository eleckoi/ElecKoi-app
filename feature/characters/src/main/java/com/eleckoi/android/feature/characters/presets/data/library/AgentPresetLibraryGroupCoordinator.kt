package com.eleckoi.android.feature.characters.presets.data.library

import com.eleckoi.android.feature.characters.presets.data.policy.uniqueAgentPresetName
import com.eleckoi.android.feature.characters.presets.model.AgentPresetLibraryGroup
import com.eleckoi.android.foundation.storage.room.AgentPresetDao
import com.eleckoi.android.foundation.storage.room.AgentPresetLibraryGroupEntity
import java.util.UUID

/** Keeps author-library grouping operations out of the preset content repository. */
internal class AgentPresetLibraryGroupCoordinator(
    private val dao: AgentPresetDao,
) {
    suspend fun create(name: String): AgentPresetLibraryGroup {
        val group = AgentPresetLibraryGroupEntity(
            id = "agent-preset-group-${UUID.randomUUID()}",
            name = uniqueAgentPresetName(
                name.trim().ifBlank { "新分组" },
                dao.libraryGroupNames(),
            ),
            sortIndex = dao.nextLibraryGroupSortIndex(),
        )
        dao.upsertLibraryGroup(group)
        return AgentPresetLibraryGroup(group.id, group.name, group.sortIndex)
    }

    suspend fun rename(groupId: String, name: String) {
        if (groupId.isBlank()) return
        val current = dao.libraryGroups().firstOrNull { it.id == groupId } ?: return
        val normalized = uniqueAgentPresetName(
            name.trim().ifBlank { current.name },
            dao.libraryGroups().filterNot { it.id == groupId }.map { it.name },
        )
        dao.renameLibraryGroup(groupId, normalized)
    }

    suspend fun movePreset(presetId: String, groupId: String) {
        dao.movePreset(presetId, groupId)
    }

    suspend fun delete(groupId: String) {
        if (groupId.isBlank()) return
        // "全部预设" is the recovery scope: preserve presets without assigning another author.
        dao.movePresetsFromDeletedGroup(groupId, "")
        dao.deleteLibraryGroup(groupId)
    }
}
