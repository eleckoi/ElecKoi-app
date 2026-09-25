package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.foundation.storage.newId

data class SettingLibraryAgentEntry(
    val id: String,
    val title: String,
    val groupId: String = "",
    val groupPath: String,
    /** Human-readable logical path exposed to Agent tools; never a Room/editor ID. */
    val path: String,
    val content: String,
    val selectionHint: String = "",
    val readStrategy: SettingLibraryAgentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
    val dynamicMode: SettingLibraryDynamicMode = SettingLibraryDynamicMode.Standard,
    /** True when a keyword or EJS controller promoted this entry for the current turn. */
    val promotedToRequiredThisTurn: Boolean = false,
    /** Concrete getwi() references resolved while rendering this entry for the current turn. */
    val resolvedReferences: List<SettingLibraryResolvedReference> = emptyList(),
    /** Author-defined order from the root group down to this entry. */
    val treeOrderPath: List<Int> = emptyList(),
)

data class SettingLibraryResolvedReference(
    val id: String,
    val title: String,
    val path: String,
)

data class SettingLibraryAgentGroup(
    val id: String,
    val name: String,
    val parentId: String,
    val path: String,
)

data class SettingLibraryAgentTurnContext(
    /** Entries still resolved by the app, namely prompt-resident entries. */
    val automaticLibrary: SettingLibrary,
    /** Structured entries the model may read through request-scoped setting-library tools. */
    val readableEntries: List<SettingLibraryAgentEntry>,
    /** Keyword rules used to promote matching entries for the current turn. */
    val keywordStrategyEntries: List<SettingLibraryEntry> = emptyList(),
    /** Stable logical groups available to the session-scoped mutation tool. */
    val groups: List<SettingLibraryAgentGroup>,
)

sealed interface SettingLibrarySessionMutation {
    data class CreateEntry(
        val groupId: String,
        val title: String,
        val content: String,
        val selectionHint: String,
        val entryId: String = "session-setting-${newId(12)}",
    ) : SettingLibrarySessionMutation

    data class UpdateEntry(
        val entryId: String,
        val groupId: String?,
        val title: String?,
        val content: String?,
        val selectionHint: String?,
    ) : SettingLibrarySessionMutation

    data class DeleteEntry(val entryId: String) : SettingLibrarySessionMutation

    data class CreateGroup(
        val parentId: String,
        val name: String,
        val groupId: String = "session-group-${newId(12)}",
    ) : SettingLibrarySessionMutation

    data class UpdateGroup(
        val groupId: String,
        val parentId: String?,
        val name: String?,
    ) : SettingLibrarySessionMutation

    data class DeleteGroup(val groupId: String) : SettingLibrarySessionMutation
}

data class SettingLibraryAppliedMutation(
    val operation: String,
    val targetType: String,
    val targetId: String,
    val title: String,
)

data class SettingLibrarySessionMutationResult(
    val applied: List<SettingLibraryAppliedMutation>,
    val effectiveLibrary: SettingLibrary,
)

data class SettingLibraryRowMetadata(
    val characterId: String,
    val name: String,
    val updatedAt: String,
    val entryCount: Int,
    val groupCount: Int,
    val promptPositions: List<SettingLibraryPromptPosition>,
)

data class SettingLibraryEntryRow(
    val sortIndex: Int,
    val entry: SettingLibraryEntry,
)

data class SettingLibraryGroupRow(
    val sortIndex: Int,
    val group: SettingLibraryGroup,
)
