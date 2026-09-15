package com.eleckoi.android.app.service

import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.api.CreatorCharacterPage
import com.eleckoi.android.feature.studio.api.CreatorCharacterMediaState
import com.eleckoi.android.feature.studio.api.CreatorMediaAsset
import com.eleckoi.android.feature.studio.api.CreatorMediaAssetPage
import com.eleckoi.android.feature.studio.api.CreatorMediaAssetSource
import com.eleckoi.android.feature.studio.api.CreatorSettingEntryPage
import com.eleckoi.android.feature.studio.api.CreatorSettingGroupPage
import com.eleckoi.android.feature.studio.api.CreatorSettingLibraryMetadata
import com.eleckoi.android.feature.studio.api.creatorConversationAttachmentId

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.eleckoi.conversation.RoomConversationLedger
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.imageGenerationProvider
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpoint
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.chat.data.ChatInputImageStore
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CreatorAssistantServiceImpl(
    private val creatorLedger: CreatorLedgerCoordinator,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    database: ElecKoiDatabase,
    private val uiPreferences: UiPreferencesRepository,
    private val modelSelections: ChatModelSelectionResolver,
    private val modelConfigs: ModelConfigRepository,
    private val replyImageGenerator: ReplyImageGenerator,
    private val inputImages: ChatInputImageStore,
    private val characters: CharacterRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val regexRules: RegexRuleRepository,
    private val mediaCacheDirectory: File,
    private val imageModelConfigId: suspend () -> String,
    private val rollbackCharacter: suspend (String) -> Unit,
) : CreatorAssistantService {
    private val ledger = RoomConversationLedger(database)
    private val rootResolver = CreatorCharacterRootResolver(creatorWorkspaces, characters)
    private val creatorMedia = CreatorMediaCoordinator(
        creatorWorkspaces = creatorWorkspaces,
        modelConfigs = modelConfigs,
        replyImageGenerator = replyImageGenerator,
        characters = characters,
        rootResolver = rootResolver,
        mediaCacheDirectory = mediaCacheDirectory,
        imageModelConfigId = imageModelConfigId,
    )
    private val characterContent = CreatorCharacterContentCoordinator(
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        regexRules = regexRules,
        rootResolver = rootResolver,
    )

    override suspend fun creatorCharacter(characterId: String) = withContext(Dispatchers.IO) {
        characters.characterById(characterId)
    }

    override suspend fun searchCreatorCharacters(
        query: String,
        cursor: String,
        limit: Int,
    ): CreatorCharacterPage = withContext(Dispatchers.IO) {
        val bounded = limit.coerceIn(1, MaxCreatorPageSize)
        val (afterOrder, afterId) = decodeCreatorCursor(cursor)
        val rows = characters.characterPage(query, afterOrder, afterId, bounded + 1)
        val page = rows.take(bounded)
        CreatorCharacterPage(
            items = page,
            nextCursor = if (rows.size > bounded) {
                page.lastOrNull()?.let { encodeCreatorCursor(it.order, it.id) }.orEmpty()
            } else {
                ""
            },
        )
    }

    override suspend fun prepareCreatorInputImages(
        uriValues: List<String>,
    ): List<ChatUserImageAttachment> = withContext(Dispatchers.IO) {
        inputImages.prepare(uriValues)
    }

    override fun discardCreatorInputImage(image: ChatUserImageAttachment) {
        inputImages.delete(image)
    }

    override suspend fun listCreatorWorkspaces(): List<CreatorWorkspace> =
        creatorWorkspaces.list()
            // Character chat workspaces share the same physical repository, but they are not
            // AI creator projects and must never appear in the creator assistant sidebar.
            .filterNot { workspace -> workspace.characterOwned }
            .map { creatorLedger.withTimelines(it) }

    override suspend fun createCreatorWorkspace(
        name: String,
        linkedCharacterId: String?,
    ): CreatorWorkspace = creatorWorkspaces.create(name, linkedCharacterId)

    override suspend fun renameCreatorWorkspace(workspaceId: String, name: String): CreatorWorkspace {
        return creatorWorkspaces.rename(workspaceId, name)
    }

    override suspend fun creatorWorkspace(workspaceId: String): CreatorWorkspace? =
        creatorWorkspaces.get(workspaceId)

    override suspend fun attachCreatorCharacter(
        workspaceId: String,
        characterId: String,
        access: CreatorWorkspaceRootAccess,
        makePrimary: Boolean,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        val character = characters.characterById(characterId) ?: error("角色不存在")
        creatorWorkspaces.attachCharacterRoot(
            workspaceId = workspaceId,
            characterId = character.id,
            alias = character.name,
            access = access,
            makePrimary = makePrimary,
        )
    }

    override suspend fun detachCreatorCharacter(
        workspaceId: String,
        rootId: String,
    ): CreatorWorkspace = creatorWorkspaces.detachCharacterRoot(workspaceId, rootId)

    override suspend fun setPrimaryCreatorCharacter(
        workspaceId: String,
        rootId: String,
    ): CreatorWorkspace = creatorWorkspaces.setPrimaryCharacterRoot(workspaceId, rootId)

    override suspend fun setCreatorCharacterAccess(
        workspaceId: String,
        rootId: String,
        access: CreatorWorkspaceRootAccess,
    ): CreatorWorkspace = creatorWorkspaces.setCharacterRootAccess(workspaceId, rootId, access)

    override suspend fun createAndAttachCreatorCharacter(
        workspaceId: String,
        name: String,
        group: String,
    ): Pair<CreatorWorkspace, CharacterSlot> = withContext(Dispatchers.IO) {
        val normalizedName = name.trim().take(80).ifBlank { error("角色名称不能为空") }
        var created: CharacterSlot? = null
        try {
            val initial = characters.createCharacter(group)
            created = initial
            val named = characters.saveCharacterPersona(
                initial.id,
                initial.persona.copy(
                    characterName = normalizedName,
                    assistantName = normalizedName,
                ),
            )
            creatorWorkspaces.ensureCharacterContainer(named.id)
            settingLibrary.save(named.id, settingLibrary.load(named.id))
            val workspace = creatorWorkspaces.attachCharacterRoot(
                workspaceId = workspaceId,
                characterId = named.id,
                alias = normalizedName,
                access = CreatorWorkspaceRootAccess.ReadWrite,
                makePrimary = true,
            )
            workspace to requireNotNull(characters.characterById(named.id))
        } catch (error: Throwable) {
            created?.id?.let { characterId ->
                runCatching { rollbackCharacter(characterId) }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    override suspend fun creatorCharacterMedia(
        workspaceId: String,
        rootId: String,
    ): CreatorCharacterMediaState = creatorMedia.creatorCharacterMedia(workspaceId, rootId)

    override suspend fun registerCreatorMediaAsset(
        workspaceId: String,
        sourceFile: File,
        displayName: String,
        source: CreatorMediaAssetSource,
    ): CreatorMediaAsset = creatorMedia.registerCreatorMediaAsset(workspaceId, sourceFile, displayName, source)

    override suspend fun generateCreatorMediaAsset(
        workspaceId: String,
        prompt: String,
        negativePrompt: String,
        displayName: String,
    ): CreatorMediaAsset {
        return creatorMedia.generateCreatorMediaAsset(
            workspaceId,
            prompt,
            negativePrompt,
            displayName,
        )
    }

    override suspend fun searchCreatorMediaAssets(
        workspaceId: String,
        cursor: String,
        limit: Int,
    ): CreatorMediaAssetPage = creatorMedia.searchCreatorMediaAssets(workspaceId, cursor, limit)

    override suspend fun creatorMediaAsset(
        workspaceId: String,
        assetId: String,
    ): CreatorMediaAsset? {
        val attachmentId = creatorConversationAttachmentId(assetId)
        if (attachmentId != null) {
            val sourceFile = inputImages.findById(attachmentId) ?: return null
            return creatorMedia.creatorInputAttachment(workspaceId, assetId, sourceFile)
        }
        return creatorMedia.creatorMediaAsset(workspaceId, assetId)
    }

    override suspend fun applyCreatorMediaAsset(
        workspaceId: String,
        rootId: String,
        assetId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot {
        val attachmentId = creatorConversationAttachmentId(assetId)
        if (attachmentId != null) {
            val sourceFile = inputImages.findById(attachmentId)
                ?: error("对话中的原始图片已经被删除")
            return creatorMedia.applyCreatorInputAttachment(workspaceId, rootId, sourceFile, slots)
        }
        return creatorMedia.applyCreatorMediaAsset(workspaceId, rootId, assetId, slots)
    }

    override suspend fun clearCreatorCharacterMedia(
        workspaceId: String,
        rootId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = creatorMedia.clearCreatorCharacterMedia(workspaceId, rootId, slots)

    override suspend fun loadCreatorSettingLibrary(
        workspaceId: String,
        rootId: String,
    ): SettingLibrary = characterContent.loadCreatorSettingLibrary(workspaceId, rootId)

    override suspend fun creatorSettingLibraryMetadata(
        workspaceId: String,
        rootId: String,
    ): CreatorSettingLibraryMetadata = characterContent.creatorSettingLibraryMetadata(workspaceId, rootId)

    override suspend fun searchCreatorSettingEntries(
        workspaceId: String,
        rootId: String,
        query: String,
        cursor: String,
        limit: Int,
    ): CreatorSettingEntryPage =
        characterContent.searchCreatorSettingEntries(workspaceId, rootId, query, cursor, limit)

    override suspend fun searchCreatorSettingGroups(
        workspaceId: String,
        rootId: String,
        query: String,
        cursor: String,
        limit: Int,
    ): CreatorSettingGroupPage =
        characterContent.searchCreatorSettingGroups(workspaceId, rootId, query, cursor, limit)

    override suspend fun creatorSettingEntry(
        workspaceId: String,
        rootId: String,
        entryId: String,
    ) = characterContent.creatorSettingEntry(workspaceId, rootId, entryId)

    override suspend fun saveCreatorSettingLibrary(
        workspaceId: String,
        rootId: String,
        library: SettingLibrary,
    ): SettingLibrary = characterContent.saveCreatorSettingLibrary(workspaceId, rootId, library)

    override suspend fun loadCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
    ): VariableConfig = characterContent.loadCreatorVariableConfig(workspaceId, rootId)

    override suspend fun loadVersionedCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
    ) = characterContent.loadVersionedCreatorVariableConfig(workspaceId, rootId)

    override suspend fun saveCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
    ): VariableConfig = characterContent.saveCreatorVariableConfig(workspaceId, rootId, config)

    override suspend fun saveCreatorVariableConfigIfRevision(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
        expectedRevision: Long,
    ) = characterContent.saveCreatorVariableConfigIfRevision(
        workspaceId,
        rootId,
        config,
        expectedRevision,
    )

    override suspend fun validateCreatorVariableSchema(
        schemaCode: String,
    ): VariableRuntimeCheckResult = characterContent.validateCreatorVariableSchema(schemaCode)

    override suspend fun validateCreatorVariableState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult = characterContent.validateCreatorVariableState(schemaCode, stateJson)

    override suspend fun loadCreatorRegexRules(
        workspaceId: String,
        rootId: String,
    ): RegexRuleCollection = characterContent.loadCreatorRegexRules(workspaceId, rootId)

    override suspend fun loadVersionedCreatorRegexRules(
        workspaceId: String,
        rootId: String,
    ) = characterContent.loadVersionedCreatorRegexRules(workspaceId, rootId)

    override suspend fun saveCreatorRegexRules(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
    ): RegexRuleCollection = characterContent.saveCreatorRegexRules(workspaceId, rootId, collection)

    override suspend fun saveCreatorRegexRulesIfRevision(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
        expectedRevision: Long,
    ) = characterContent.saveCreatorRegexRulesIfRevision(
        workspaceId,
        rootId,
        collection,
        expectedRevision,
    )

    override suspend fun deleteCreatorWorkspace(workspaceId: String) {
        creatorLedger.deleteWorkspace(workspaceId)
    }

    override suspend fun ensureCreatorConversation(workspaceId: String): CreatorWorkspace {
        return creatorLedger.withTimelines(creatorWorkspaces.ensureConversation(workspaceId))
    }

    override suspend fun createCreatorConversation(workspaceId: String, title: String): CreatorWorkspace {
        return creatorLedger.withTimelines(creatorWorkspaces.createConversation(workspaceId, title))
    }

    override suspend fun selectCreatorConversation(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace {
        return creatorLedger.withTimelines(creatorWorkspaces.selectConversation(workspaceId, conversationId))
    }

    override suspend fun renameCreatorConversation(
        workspaceId: String,
        conversationId: String,
        title: String,
    ): CreatorWorkspace {
        return creatorLedger.withTimelines(
            creatorWorkspaces.renameConversation(workspaceId, conversationId, title),
        )
    }

    override suspend fun saveCreatorWorkspacePermissionMode(
        workspaceId: String,
        permissionMode: AgentPermissionMode,
    ): CreatorWorkspace {
        return creatorLedger.withTimelines(
            creatorWorkspaces.saveWorkspacePermissionMode(
                workspaceId,
                permissionMode,
            ),
        )
    }

    override suspend fun saveCreatorConversationTimeline(
        workspaceId: String,
        conversationId: String,
        timeline: List<CreatorConversationTimelineItem>,
    ): CreatorWorkspace = creatorLedger.saveTimeline(workspaceId, conversationId, timeline)

    override suspend fun checkpointCreatorConversationTurn(
        workspaceId: String,
        conversationId: String,
        turnTimeline: List<CreatorConversationTimelineItem>,
    ): Unit = creatorLedger.checkpointTurn(workspaceId, conversationId, turnTimeline)

    override suspend fun truncateCreatorConversationForRegeneration(
        workspaceId: String,
        conversationId: String,
        retainedUser: CreatorConversationTimelineItem,
    ): CreatorWorkspace {
        val truncation = creatorLedger.truncateForRegeneration(
            workspaceId,
            conversationId,
            retainedUser,
        )
        creatorMedia.deleteGeneratedMediaAssets(workspaceId, truncation.removedAssetIds)
        return truncation.workspace
    }

    override fun creatorConversationPaging(conversationId: String) =
        ledger.pagingTurns(conversationId)

    override suspend fun loadCreatorConversationAgentHistory(
        workspaceId: String,
        conversationId: String,
        excludeTrailingUser: Boolean,
    ): List<AgentHistoryItem> =
        creatorLedger.loadAgentHistory(workspaceId, conversationId, excludeTrailingUser)

    override suspend fun deleteCreatorConversation(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace = creatorLedger.deleteConversation(workspaceId, conversationId)

    override suspend fun pinnedCreatorWorkspaceIds(): List<String> {
        return uiPreferences.read().pinnedCreatorWorkspaceIds
    }

    override suspend fun setPinnedCreatorWorkspaceIds(workspaceIds: List<String>) {
        uiPreferences.setPinnedCreatorWorkspaceIds(workspaceIds)
    }

    override suspend fun creatorWorkspaceExpansionOverrides(): Map<String, Boolean> {
        return uiPreferences.read().creatorWorkspaceExpansionOverrides
    }

    override suspend fun setCreatorWorkspaceExpansionOverrides(overrides: Map<String, Boolean>) {
        uiPreferences.setCreatorWorkspaceExpansionOverrides(overrides)
    }

    override suspend fun lastCreatorWorkspaceId(): String {
        return uiPreferences.read().lastCreatorWorkspaceId
    }

    override suspend fun setLastCreatorWorkspaceId(workspaceId: String) {
        uiPreferences.setLastCreatorWorkspaceId(workspaceId)
    }

    override suspend fun listCreatorWorkspaceFiles(workspaceId: String): List<CreatorWorkspaceFile> {
        return creatorWorkspaces.listFiles(workspaceId)
    }

    override suspend fun readCreatorWorkspaceText(workspaceId: String, path: String): String {
        return creatorWorkspaces.readText(workspaceId, path)
    }

    override suspend fun writeCreatorWorkspaceText(
        workspaceId: String,
        path: String,
        content: String,
    ): CreatorWorkspace = creatorWorkspaces.writeText(workspaceId, path, content)

    override suspend fun checkpointCreatorWorkspace(
        workspaceId: String,
        label: String?,
    ): CreatorWorkspaceCheckpoint = creatorWorkspaces.checkpoint(workspaceId, label)

    override suspend fun listCreatorWorkspaceCheckpoints(
        workspaceId: String,
    ): List<CreatorWorkspaceCheckpoint> = creatorWorkspaces.listCheckpoints(workspaceId)

    override suspend fun restoreCreatorWorkspaceCheckpoint(
        workspaceId: String,
        checkpointId: String,
    ): CreatorWorkspace = creatorWorkspaces.restoreCheckpoint(workspaceId, checkpointId)

    override fun creatorWorkspaceProjectDirectory(workspaceId: String): File? {
        return creatorWorkspaces.projectDirectoryOrNull(workspaceId)
    }

    override suspend fun creatorImageGenerationProvider() = withContext(Dispatchers.IO) {
        modelConfigs.loadModelConfigCollection().configs
            .firstOrNull { it.id == imageModelConfigId() }
            ?.imageGenerationProvider()
    }

    override suspend fun defaultCreatorModelConfig(): ModelConfig {
        return modelSelections.defaultCreatorModelConfig()
    }

}
