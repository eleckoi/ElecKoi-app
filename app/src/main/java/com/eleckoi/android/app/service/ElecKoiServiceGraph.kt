package com.eleckoi.android.app.service

import android.content.Context
import android.content.pm.ApplicationInfo
import com.eleckoi.android.compatibility.mvu.display.MvuMessageDisplayAdapter
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.agent.tools.AgentToolScopes
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.generation.config.AndroidKeystoreModelSecretCodec
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.transfer.data.CharacterTransferRepository
import com.eleckoi.android.feature.characters.data.UserProfileRepository
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.presets.data.StoryPresetRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.data.ChatInputImageStore
import com.eleckoi.android.engine.agent.eleckoi.conversation.ConversationAttachmentCleanup
import com.eleckoi.android.engine.agent.eleckoi.conversation.RoomConversationLedger
import com.eleckoi.android.feature.chat.ui.blocks.markdown.MarkdownRebuildableCaches
import com.eleckoi.android.feature.chat.data.GenerationAttemptRepository
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.settings.data.appearance.AppearanceRepository
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.appfont.data.AppFontRepository
import com.eleckoi.android.app.service.backup.DataBackupService
import com.eleckoi.android.app.service.cleanup.PersistentCleanupQueue
import com.eleckoi.android.foundation.storage.JsonFileStore
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File

/**
 * Application-service composition root used by [ElecKoiRepository].
 *
 * Construction stays centralized while behavior is owned by narrow services.
 * This also makes the facade's compatibility surface explicit and temporary.
 */
internal class ElecKoiServiceGraph(
    context: Context,
    isCreatorCapabilityEnabled: () -> Boolean,
    toolModelConfigId: (scopeId: String, groupId: String) -> String,
    initializeCharacterTools: (characterId: String) -> Unit,
    deleteCharacterTools: (Collection<String>) -> Unit,
    exportToolConfig: () -> String,
    restoreToolConfig: (String) -> Unit,
) {
    private var agentRuns: AgentRunManager? = null
    private val captureProviderRequestsByDefault = (
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
    ) != 0

    init {
        AgentRequestDiagnostics.configureCaptureDefault(captureProviderRequestsByDefault)
    }

    private val store = JsonFileStore(context)
    private val database = ElecKoiDatabase.get(context)
    private val cleanupQueue = PersistentCleanupQueue(database)
    private val characters = CharacterRepository(store, database)
    private val creatorWorkspaces = CreatorWorkspaceRepository(context.applicationContext, database)
    private val settingLibrary = SettingLibraryRepository(
        database = database,
        characters = characters,
    )
    private val regexRules = RegexRuleRepository(database, characters)
    internal val storyPresets = StoryPresetRepository(
        dao = database.storyPresetDao(),
        store = store,
        onActivePresetChanged = regexRules::notifyActivePresetChanged,
    )
    private val variableConfig = VariableConfigRepository(database) { characterId ->
        characters.characterById(characterId) != null
    }
    private val frontendProjects = FrontendProjectRepository(
        context.applicationContext,
        database,
        cleanupQueue,
    )
    private val variableRuntime = VariableRuntimeService(context.applicationContext)
    private val settings = ModelConfigRepository(
        database = database,
        secretCodec = AndroidKeystoreModelSecretCodec(),
    )
    internal val uiPreferences = UiPreferencesRepository(context)
    private val profile = UserProfileRepository(context, store, database)
    private val appFont = AppFontRepository(context)
    private val appearance = AppearanceRepository(store, uiPreferences)
    private val replyImageGenerator = ReplyImageGenerator(
        rootDirectory = File(context.filesDir, "generated/chat-images"),
    )
    private val generationAttempts = GenerationAttemptRepository(database)
    private val chatInputImages = ChatInputImageStore(context.applicationContext)
    private val sessions = ChatSessionStore(
        database = database,
        characters = characters,
        generationAttempts = generationAttempts,
        historySaveModeProvider = { uiPreferences.read().historySaveMode },
        replyImageGenerator = replyImageGenerator,
        inputImageStore = chatInputImages,
        cleanupRunner = cleanupQueue,
        onSessionsDeleted = { ids ->
            requireConversationsIdle(ids)
            uiPreferences.removeChatSessionIds(ids)
            MarkdownRebuildableCaches.clearAfterConversationDeletion(ids)
            clearChatProjectionCaches()
        },
    )
    private val creatorLedger = CreatorLedgerCoordinator(
        ledger = RoomConversationLedger(database),
        database = database,
        creatorWorkspaces = creatorWorkspaces,
        attachmentCleanup = ConversationAttachmentCleanup(database, chatInputImages::deletePath, replyImageGenerator),
        onWorkspacesDeleted = { uiPreferences.removeCreatorWorkspaceIds(it) },
        beforeDeletion = ::requireConversationsIdle,
        cleanupRunner = cleanupQueue,
    )
    val dataBackupService = DataBackupService(
        context = context.applicationContext,
        characters = characters,
        profile = profile,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        regexRules = regexRules,
        storyPresets = storyPresets,
        sessions = sessions,
        uiPreferences = uiPreferences,
        appFont = appFont,
        modelConfigs = settings,
        frontendProjects = frontendProjects,
        exportToolConfig = exportToolConfig,
        restoreToolConfig = restoreToolConfig,
        database = database,
        creatorWorkspaces = creatorWorkspaces,
    )
    private val characterTransfers = CharacterTransferRepository(
        context = context.applicationContext,
        characters = characters,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        regexRules = regexRules,
        frontendProjects = frontendProjects,
        initializeImportedCharacterTools = initializeCharacterTools,
        deleteImportedCharacterTools = deleteCharacterTools,
    )
    private val modelSelections = ChatModelSelectionResolver(
        settings = settings,
        uiPreferences = uiPreferences,
    )
    val modelService = ModelServiceImpl(settings, uiPreferences, modelSelections)
    val profileService = ProfileServiceImpl(profile)
    val appearanceService = AppearanceServiceImpl(appearance, uiPreferences)
    val shellService = ShellServiceImpl(sessions, uiPreferences)
    val settingLibraryService = SettingLibraryServiceImpl(settingLibrary, sessions, characters)
    val variableConfigService = VariableConfigServiceImpl(variableConfig)
    val regexRuleService = RegexRuleServiceImpl(regexRules)
    val frontendProjectService = FrontendProjectServiceImpl(frontendProjects)
    val characterService = CharacterServiceImpl(
        deleteCharacterTools = deleteCharacterTools,
        beforeDeleteCharacters = { ids ->
            val active = agentRuns?.activeRun?.value?.descriptor
            if (active != null) {
                val activeCharacter = database.chatDao().sessionById(active.conversationId)
                    ?.session
                    ?.characterId
                val workspace = creatorWorkspaces.get(active.workspaceId)
                check(activeCharacter !in ids &&
                    !(workspace?.linkedCharacterMode != null && workspace.linkedCharacterId in ids)
                ) { "请先停止正在运行的任务，再删除它的角色" }
            }
        },
        regexRules = regexRules,
        deleteWorkspace = creatorLedger::deleteWorkspace,
        characters = characters,
        sessions = sessions,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        frontendProjects = frontendProjects,
        creatorWorkspaces = creatorWorkspaces,
        initializeCharacterTools = initializeCharacterTools,
        cleanupRunner = cleanupQueue,
    )
    val characterTransferService = CharacterTransferServiceImpl(
        transfers = characterTransfers,
        creatorWorkspaces = creatorWorkspaces,
    )
    val creatorAssistantService = CreatorAssistantServiceImpl(
        rollbackCharacter = { characterService.deleteCharacters(listOf(it)) },
        creatorLedger = creatorLedger,
        creatorWorkspaces = creatorWorkspaces,
        database = database,
        uiPreferences = uiPreferences,
        modelSelections = modelSelections,
        modelConfigs = settings,
        replyImageGenerator = replyImageGenerator,
        inputImages = chatInputImages,
        characters = characters,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        regexRules = regexRules,
        mediaCacheDirectory = File(context.cacheDir, "creator-media-bindings"),
        isCreatorCapabilityEnabled = isCreatorCapabilityEnabled,
        imageModelConfigId = {
            toolModelConfigId(
                AgentToolScopes.Shared,
                AgentToolRequestPolicy.BuiltInCreator,
            )
        },
        initializeCharacterTools = initializeCharacterTools,
    )
    val chatService: ChatServiceImpl = ChatServiceImpl(
        characters = characters,
        sessions = sessions,
        settings = settings,
        uiPreferences = uiPreferences,
        appearance = appearance,
        settingLibrary = settingLibrary,
        regexRules = regexRules,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        creatorWorkspaces = creatorWorkspaces,
        modelService = modelService,
        modelSelections = modelSelections,
        replyImageGenerator = replyImageGenerator,
        generationAttempts = generationAttempts,
        inputImages = chatInputImages,
        displayCompatibility = MvuMessageDisplayAdapter,
        toolModelConfigId = toolModelConfigId,
        captureProviderRequests = captureProviderRequestsByDefault,
    )

    fun attachCharacterAgentRuntime(
        agentSessions: AgentSessionFactory,
        runtime: LocalRuntimeGateway,
        virtualFileSearch: AgentVirtualFileSearch,
        toolContextSnapshot: (String) -> AgentToolContextSnapshot,
        agentRuns: AgentRunManager,
        publishRemoteDshTurnImages: (String, List<AgentInputImage>) -> Unit,
    ) {
        this.agentRuns = agentRuns
        chatService.attachCharacterAgentRuntime(
            agentSessions,
            runtime,
            virtualFileSearch,
            toolContextSnapshot,
            agentRuns,
            publishRemoteDshTurnImages,
            storyPresets::activePreset,
        )
    }

    private fun requireConversationsIdle(ids: Collection<String>) {
        check(agentRuns?.activeRun?.value?.descriptor?.conversationId !in ids) {
            "请先停止正在运行的任务，再删除它的对话或角色"
        }
    }

    private fun clearChatProjectionCaches() = chatService.clearDeletionProjectionCaches()

    fun recoverAbandonedRoleGenerations() {
        sessions.settleAllOrphanedGenerations()
    }

    suspend fun resumePendingCleanup() {
        cleanupQueue.resume { operation ->
            when (operation.kind) {
                "character" -> characterService.deleteCharacterNow(operation.targetId)
                "chat_session" -> sessions.resumeDelete(operation.targetId)
                "creator_workspace" -> creatorLedger.deleteWorkspaceNow(operation.targetId)
                "creator_conversation" -> creatorLedger.resumeConversationDeletion(operation.targetId)
                "frontend_project" -> frontendProjects.resumeProjectDeletion(operation.targetId)
                else -> error("未知清理任务：${operation.kind}")
            }
        }
    }

    fun userProfile(): UserProfile = profile.load()

    fun characterCollection(): CharactersPayload = characters.loadCharacters()

    fun loadSettingLibrary(characterId: String): SettingLibrary = settingLibrary.load(characterId)

    fun loadVariableConfig(characterId: String): VariableConfig = variableConfig.load(characterId)

    suspend fun creatorModelConfig(configId: String?): ModelConfig {
        return modelSelections.creatorModelConfig(configId)
    }

    suspend fun checkVariableRuntime(): VariableRuntimeCheckResult {
        return variableRuntime.checkJavaScriptEngine()
    }

    suspend fun validateVariableSchema(schemaCode: String): VariableRuntimeCheckResult {
        return variableRuntime.validateSchemaCode(schemaCode)
    }

    suspend fun validateVariableState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult = variableRuntime.validateState(schemaCode, stateJson)

    fun modelCollection() = settings.loadModelConfigCollection()

    fun chatList(): List<ChatListItem> = chatService.chatList()

    suspend fun refreshChatDraft(sessionId: String): ChatDraft = chatService.refreshChatDraft(sessionId)
}
