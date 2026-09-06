package com.eleckoi.android.app.service

import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.config.VersionedVariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.VersionedRegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.studio.api.CreatorSettingEntryPage
import com.eleckoi.android.feature.studio.api.CreatorSettingGroupPage
import com.eleckoi.android.feature.studio.api.CreatorSettingLibraryMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CreatorCharacterContentCoordinator(
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val regexRules: RegexRuleRepository,
    private val rootResolver: CreatorCharacterRootResolver,
) {

    suspend fun loadCreatorSettingLibrary(
        workspaceId: String,
        rootId: String,
    ): SettingLibrary = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        settingLibrary.load(character.id)
    }

    suspend fun creatorSettingLibraryMetadata(
        workspaceId: String,
        rootId: String,
    ): CreatorSettingLibraryMetadata = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        val metadata = settingLibrary.rowMetadata(character.id)
        CreatorSettingLibraryMetadata(
            rootId = root.id,
            characterId = character.id,
            name = metadata.name,
            updatedAt = metadata.updatedAt,
            entryCount = metadata.entryCount,
            groupCount = metadata.groupCount,
            promptPositions = metadata.promptPositions,
        )
    }

    suspend fun searchCreatorSettingEntries(
        workspaceId: String,
        rootId: String,
        query: String,
        cursor: String,
        limit: Int,
    ): CreatorSettingEntryPage = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        val bounded = limit.coerceIn(1, MaxCreatorPageSize)
        val (afterIndex, afterId) = decodeCreatorCursor(cursor)
        val rows = settingLibrary.entryRows(character.id, query, afterIndex, afterId, bounded + 1)
        val page = rows.take(bounded)
        CreatorSettingEntryPage(
            items = page.map { it.entry },
            nextCursor = if (rows.size > bounded) {
                page.lastOrNull()?.let { encodeCreatorCursor(it.sortIndex, it.entry.id) }.orEmpty()
            } else {
                ""
            },
        )
    }

    suspend fun searchCreatorSettingGroups(
        workspaceId: String,
        rootId: String,
        query: String,
        cursor: String,
        limit: Int,
    ): CreatorSettingGroupPage = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        val bounded = limit.coerceIn(1, MaxCreatorPageSize)
        val (afterIndex, afterId) = decodeCreatorCursor(cursor)
        val rows = settingLibrary.groupRows(character.id, query, afterIndex, afterId, bounded + 1)
        val page = rows.take(bounded)
        CreatorSettingGroupPage(
            items = page.map { it.group },
            nextCursor = if (rows.size > bounded) {
                page.lastOrNull()?.let { encodeCreatorCursor(it.sortIndex, it.group.id) }.orEmpty()
            } else {
                ""
            },
        )
    }

    suspend fun creatorSettingEntry(
        workspaceId: String,
        rootId: String,
        entryId: String,
    ) = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        settingLibrary.entryRow(character.id, entryId)
    }

    suspend fun saveCreatorSettingLibrary(
        workspaceId: String,
        rootId: String,
        library: SettingLibrary,
    ): SettingLibrary = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        settingLibrary.save(character.id, library)
    }

    suspend fun loadCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
    ): VariableConfig = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        variableConfig.load(character.id)
    }

    suspend fun loadVersionedCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
    ): VersionedVariableConfig = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        variableConfig.loadVersioned(character.id)
    }

    suspend fun saveCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
    ): VariableConfig = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        variableConfig.save(character.id, config.copy(characterId = character.id))
    }

    suspend fun saveCreatorVariableConfigIfRevision(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
        expectedRevision: Long,
    ): VersionedVariableConfig? = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        variableConfig.saveIfRevision(
            character.id,
            config.copy(characterId = character.id),
            expectedRevision,
        )
    }

    suspend fun validateCreatorVariableSchema(
        schemaCode: String,
    ): VariableRuntimeCheckResult = withContext(Dispatchers.IO) {
        variableRuntime.validateSchemaCode(schemaCode)
    }

    suspend fun validateCreatorVariableState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult = withContext(Dispatchers.IO) {
        variableRuntime.validateState(schemaCode, stateJson)
    }

    suspend fun loadCreatorRegexRules(
        workspaceId: String,
        rootId: String,
    ): RegexRuleCollection = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        regexRules.load(character.id)
    }

    suspend fun loadVersionedCreatorRegexRules(
        workspaceId: String,
        rootId: String,
    ): VersionedRegexRuleCollection = withContext(Dispatchers.IO) {
        val (_, character) = rootResolver.requireRoot(workspaceId, rootId)
        regexRules.loadVersioned(character.id)
    }

    suspend fun saveCreatorRegexRules(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
    ): RegexRuleCollection = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        regexRules.save(character.id, collection)
    }

    suspend fun saveCreatorRegexRulesIfRevision(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
        expectedRevision: Long,
    ): VersionedRegexRuleCollection? = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        regexRules.saveIfRevision(character.id, collection, expectedRevision)
    }

}
