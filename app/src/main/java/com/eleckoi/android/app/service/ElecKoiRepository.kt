package com.eleckoi.android.app.service

import android.content.Context
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.immersive.api.FrontendProjectService
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.feature.characters.api.CharacterService
import com.eleckoi.android.feature.characters.modes.story.regex.api.RegexRuleService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.api.SettingLibraryService
import com.eleckoi.android.feature.characters.modes.story.variables.api.VariableConfigService
import com.eleckoi.android.feature.characters.transfer.api.CharacterTransferService
import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.settings.api.AppearanceService
import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.feature.settings.api.ProfileService
import com.eleckoi.android.feature.studio.api.CreatorAssistantService

/**
 * Compatibility facade for the application's feature services.
 *
 * Existing consumers can keep using [ElecKoiRepository], while each service
 * contract is implemented by a focused owner assembled in [ElecKoiServiceGraph].
 */
class ElecKoiRepository private constructor(
    private val graph: ElecKoiServiceGraph,
) :
    ChatService by graph.chatService,
    CharacterService by graph.characterService,
    CharacterTransferService by graph.characterTransferService,
    ModelService by graph.modelService,
    ProfileService by graph.profileService,
    AppearanceService by graph.appearanceService,
    ShellService by graph.shellService,
    SettingLibraryService by graph.settingLibraryService,
    VariableConfigService by graph.variableConfigService,
    RegexRuleService by graph.regexRuleService,
    FrontendProjectService by graph.frontendProjectService,
    CreatorAssistantService by graph.creatorAssistantService {

    // Resolve members shared by multiple feature contracts explicitly. This
    // preserves the single stream instances exposed by the original facade.
    override val uiPreferencesFlow = graph.chatService.uiPreferencesFlow
    override val chatListFlow = graph.chatService.chatListFlow
    override val modelCollectionFlow = graph.chatService.modelCollectionFlow
    override val characterCollectionFlow = graph.chatService.characterCollectionFlow
    internal val uiPreferencesRepository = graph.uiPreferences
    internal val storyPresetRepository = graph.storyPresets
    internal val dataBackupService = graph.dataBackupService

    internal constructor(
        context: Context,
        isCreatorCapabilityEnabled: () -> Boolean,
        toolModelConfigId: (scopeId: String, groupId: String) -> String,
        initializeCharacterTools: (characterId: String) -> Unit,
        deleteCharacterTools: (Collection<String>) -> Unit,
        exportToolConfig: () -> String,
        restoreToolConfig: (String) -> Unit,
    ) : this(
        ElecKoiServiceGraph(
            context = context,
            isCreatorCapabilityEnabled = isCreatorCapabilityEnabled,
            toolModelConfigId = toolModelConfigId,
            initializeCharacterTools = initializeCharacterTools,
            deleteCharacterTools = deleteCharacterTools,
            exportToolConfig = exportToolConfig,
            restoreToolConfig = restoreToolConfig,
        ),
    )

    internal fun attachCharacterAgentRuntime(
        agentSessions: AgentSessionFactory,
        runtime: LocalRuntimeGateway,
        virtualFileSearch: AgentVirtualFileSearch,
        toolContextSnapshot: (String) -> AgentToolContextSnapshot,
        agentRuns: AgentRunManager,
        publishRemoteDshTurnImages: (String, List<AgentInputImage>) -> Unit,
    ) {
        graph.attachCharacterAgentRuntime(
            agentSessions,
            runtime,
            virtualFileSearch,
            toolContextSnapshot,
            agentRuns,
            publishRemoteDshTurnImages,
        )
    }

    internal fun recoverAbandonedRoleGenerations() {
        graph.recoverAbandonedRoleGenerations()
    }

    internal suspend fun resumePendingCleanup() {
        graph.resumePendingCleanup()
    }

    fun userProfile() = graph.userProfile()

    fun characterCollection() = graph.characterCollection()

    fun loadSettingLibrary(characterId: String) = graph.loadSettingLibrary(characterId)

    fun loadVariableConfig(characterId: String) = graph.loadVariableConfig(characterId)

    suspend fun creatorModelConfig(configId: String?): ModelConfig {
        return graph.creatorModelConfig(configId)
    }

    suspend fun checkVariableRuntime(): VariableRuntimeCheckResult {
        return graph.checkVariableRuntime()
    }

    suspend fun validateVariableSchema(schemaCode: String): VariableRuntimeCheckResult {
        return graph.validateVariableSchema(schemaCode)
    }

    suspend fun validateVariableState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult = graph.validateVariableState(schemaCode, stateJson)

    fun modelCollection() = graph.modelCollection()

    fun chatList() = graph.chatList()

    suspend fun refreshChatDraft(sessionId: String) = graph.refreshChatDraft(sessionId)

    override fun saveModelConfig(config: ModelConfig): ModelConfig {
        return graph.modelService.saveModelConfig(config)
    }
}
