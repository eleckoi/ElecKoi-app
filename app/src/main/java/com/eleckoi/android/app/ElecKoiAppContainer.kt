package com.eleckoi.android.app

import android.content.Context
import com.eleckoi.android.engine.agent.deepseek.DeepSeekAgentSessionFactory
import com.eleckoi.android.engine.agent.deepseek.DeepSeekPersistentRuntimeHost
import com.eleckoi.android.app.service.ElecKoiRepository
import com.eleckoi.android.engine.workspace.runtime.service.LocalRuntimeServiceClient
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeState
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentHarnessId
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWebSearchTool
import com.eleckoi.android.engine.agent.api.AgentNativeWebSearchBridgeTool
import com.eleckoi.android.engine.agent.api.AgentRemoteDshTaskTool
import com.eleckoi.android.engine.agent.search.RipgrepAgentVirtualFileSearch
import com.eleckoi.android.engine.agent.websearch.TavilyApiClient
import com.eleckoi.android.engine.agent.websearch.tavilyWebSearchTool
import com.eleckoi.android.engine.agent.websearch.nativeWebSearchBridgeTool
import com.eleckoi.android.engine.agent.tools.AgentToolCatalogStore
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.agent.tools.AgentToolScopes
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.agent.harness.AgentHarnessBackend
import com.eleckoi.android.app.background.AndroidAgentForegroundController
import com.eleckoi.android.app.background.AndroidAgentRunCompletionNotifier
import com.eleckoi.android.app.background.AgentNotificationCenter
import com.eleckoi.android.app.background.AgentBackgroundProtection
import com.eleckoi.android.feature.agenttools.data.AgentToolsRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.withRoleplayPlanEnabled
import com.eleckoi.android.feature.settings.data.websearch.WebSearchSettingsRepository
import com.eleckoi.android.feature.settings.data.websearch.WebSearchMode
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshPlugin
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshConnectionState
import com.eleckoi.android.engine.agent.remotedsh.remoteDshTaskTool
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshTurnImageRegistry
import com.eleckoi.android.feature.settings.data.remotedsh.RemoteDshSettingsRepository
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import kotlinx.coroutines.flow.first

/** Application-scoped composition root. Data repositories and executable runtimes stay separate. */
class ElecKoiAppContainer(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext

    private val runtimePaths = RuntimePaths(applicationContext)
    private val database = ElecKoiDatabase.get(applicationContext)
    private val agentToolCatalogStore = AgentToolCatalogStore(database)
    val repository = ElecKoiRepository(
        context = applicationContext,
        isCreatorCapabilityEnabled = {
            agentToolCatalogStore.isEnabled(
                AgentToolScopes.Shared,
                AgentToolRequestPolicy.BuiltInCreator,
            )
        },
        toolModelConfigId = agentToolCatalogStore::toolModelConfigId,
        deleteCharacterTools = agentToolCatalogStore::deleteForCharacters,
        exportToolConfig = agentToolCatalogStore::exportBackupJson,
        restoreToolConfig = agentToolCatalogStore::restoreBackupJson,
        initializeCharacterTools = { characterId ->
            initializeCharacterToolDefaults(
                characterId = characterId,
                setGroupEnabled = agentToolCatalogStore::setEnabled,
            )
        },
    )
    internal val dataBackupService = repository.dataBackupService
    val localRuntime = LocalRuntimeServiceClient(applicationContext)
    val agentBackgroundProtection = AgentBackgroundProtection(applicationContext)
    internal val webSearchSettingsRepository = WebSearchSettingsRepository(applicationContext)
    internal val remoteDshSettingsRepository = RemoteDshSettingsRepository(applicationContext)
    internal val remoteDshPlugin = RemoteDshPlugin()
    private val remoteDshTurnImages = RemoteDshTurnImageRegistry()
    internal val tavilyApiClient = TavilyApiClient()
    val agentRuns = AgentRunManager(
        AndroidAgentForegroundController(applicationContext, agentBackgroundProtection),
        AndroidAgentRunCompletionNotifier(applicationContext),
    )
    private val agentVirtualFileSearch = RipgrepAgentVirtualFileSearch(
        applicationContext,
        runtimePaths,
    )
    internal val agentToolsRepository = AgentToolsRepository(
        toolCatalogStore = agentToolCatalogStore,
        modelConfigs = { repository.modelCollection().configs },
        saveModelConfig = repository::saveModelConfig,
        refreshModels = repository::refreshModelsForChat,
        loadCharacterImagePrompt = { scopeId ->
            AgentToolScopes.characterId(scopeId)
                ?.let { characterId ->
                    repository.characterCollection().items
                        .firstOrNull { it.id == characterId }
                        ?.persona
                        ?.imagePrompt
                }
                .orEmpty()
        },
        persistCharacterImagePrompt = { scopeId, prompt ->
            val characterId = AgentToolScopes.characterId(scopeId)
                ?: error("当前插件没有角色上下文")
            val character = repository.characterCollection().items
                .firstOrNull { it.id == characterId }
                ?: error("角色不存在")
            repository.saveCharacterPersona(
                characterId,
                character.persona.copy(imagePrompt = prompt),
            ).persona.imagePrompt
        },
        syncRoleplayPlanEntryEnabled = { scopeId, enabled ->
            AgentToolScopes.characterId(scopeId)?.let { characterId ->
                val current = repository.loadSettingLibrary(characterId)
                val updated = current.withRoleplayPlanEnabled(enabled)
                if (updated !== current) {
                    repository.saveSettingLibrary(characterId, updated)
                }
            }
        },
    )
    private val deepSeekHost = DeepSeekPersistentRuntimeHost(
        runtime = localRuntime,
        runtimePaths = runtimePaths,
        modelConfigProvider = repository::creatorModelConfig,
        toolRequestFilter = agentToolCatalogStore::filterRequest,
    )
    private val deepSeekHarness: AgentHarnessBackend = DeepSeekAgentSessionFactory(
        backendFactory = deepSeekHost,
    )
    val agentSessions: AgentSessionFactory = AgentSessionFactory { options ->
        val activeToolContext = agentToolCatalogStore.toolContextSnapshot(options.toolScopeId)
        val existingTools = options.dynamicTools
        val webSearchSettings = webSearchSettingsRepository.settings.value
        // Native mode advertises a function-shaped marker to DSH/pi-ai. The final provider
        // boundary turns it into a provider-native declaration only when supported.
        val nativeWebSearchBridge = nativeWebSearchBridgeTool().takeIf {
            webSearchSettings.mode == WebSearchMode.ProviderNative &&
                existingTools.none { tool ->
                tool.definition.name == AgentNativeWebSearchBridgeTool
                }
        }
        val webSearchTool = if (
            webSearchSettings.apiKeyConfigured &&
            webSearchSettings.mode == WebSearchMode.Tavily &&
            activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInWeb) &&
            existingTools.none { it.definition.name == AgentWebSearchTool }
        ) {
            tavilyWebSearchTool(
                apiClient = tavilyApiClient,
                apiKey = webSearchSettingsRepository::apiKey,
                maxResults = { webSearchSettingsRepository.settings.value.maxResults },
            )
        } else {
            null
        }
        val remoteDshTool = if (
            activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInRemoteDsh) &&
            existingTools.none { it.definition.name == AgentRemoteDshTaskTool }
        ) {
            remoteDshTaskTool(
                plugin = remoteDshPlugin,
                roleBinding = { remoteDshSettingsRepository.roleBinding(options.toolScopeId) },
                ensureConnected = {
                    check(remoteDshSettingsRepository.settings.value.enabled) {
                        "远端 DSH 电脑连接尚未开启；请从当前角色的工具页进入远端 DSH 配置"
                    }
                    if (remoteDshPlugin.state.value !is RemoteDshConnectionState.Connected) {
                        remoteDshPlugin.connect(remoteDshSettingsRepository.connectionConfig())
                    }
                },
                currentTurnImages = { remoteDshTurnImages.current(options.conversationId) },
            )
        } else {
            null
        }
        deepSeekHarness.create(
            options.copy(
                harness = AgentHarnessId.DeepSeek,
                subagentModelConfigId = agentToolCatalogStore
                    .subagentModelConfigId(options.toolScopeId)
                    .takeIf(String::isNotBlank),
                subagentModel = agentToolCatalogStore
                    .subagentModel(options.toolScopeId)
                    .takeIf(String::isNotBlank),
                dynamicTools = existingTools + listOfNotNull(
                    webSearchTool,
                    nativeWebSearchBridge,
                    remoteDshTool,
                ),
            ),
        )
    }

    init {
        // Provision both user-configurable channels before the Settings row can open Android's
        // notification page. Channel creation is idempotent and does not post a notification.
        AgentNotificationCenter.ensureChannels(applicationContext)
        repository.attachCharacterAgentRuntime(
            agentSessions,
            localRuntime,
            agentVirtualFileSearch,
            agentToolCatalogStore::toolContextSnapshot,
            agentRuns,
            remoteDshTurnImages::publish,
        )
    }

    internal fun agentToolContextSnapshot(scopeId: String): AgentToolContextSnapshot =
        agentToolCatalogStore.toolContextSnapshot(scopeId)

    internal fun isCharacterSettingLibraryToolEnabled(characterId: String): Boolean =
        agentToolCatalogStore.isEnabled(
            AgentToolScopes.character(characterId),
            AgentToolRequestPolicy.BuiltInSettingLibrary,
        )

    internal suspend fun enableCharacterSettingLibraryTool(characterId: String) {
        agentToolsRepository.setGroupEnabled(
            scopeId = AgentToolScopes.character(characterId),
            groupId = AgentToolRequestPolicy.BuiltInSettingLibrary,
            enabled = true,
        )
    }

    suspend fun prewarmAgentRuntime() {
        // This is deliberately process-scoped: it connects the local runtime service only and
        // never creates a DSH conversation/session for an arbitrary workspace or model.
        repository.characterCollection().items.forEach { character ->
            val scopeId = AgentToolScopes.character(character.id)
            if (!agentToolCatalogStore.hasExplicitConfiguration(scopeId)) {
                initializeCharacterToolDefaults(character.id, agentToolCatalogStore::setEnabled)
            }
        }
        repository.resumePendingCleanup()
        repository.recoverAbandonedRoleGenerations()
        localRuntime.connect()
        localRuntime.state.first { state ->
            when (state) {
                is LocalRuntimeState.Ready -> state.capabilities.isUsable
                is LocalRuntimeState.Running -> state.capabilities.isUsable
                else -> false
            }
        }
    }

    override fun close() {
        remoteDshPlugin.close()
        agentRuns.close()
        agentBackgroundProtection.close()
        deepSeekHost.close()
        localRuntime.close()
    }
}

/** Every new character can read its own settings and variables without granting unrelated tools. */
internal fun initializeCharacterToolDefaults(
    characterId: String,
    setGroupEnabled: (scopeId: String, groupId: String, enabled: Boolean) -> Unit,
) {
    val scopeId = AgentToolScopes.character(characterId)
    listOf(
        AgentToolRequestPolicy.BuiltInSettingLibrary,
        AgentToolRequestPolicy.BuiltInVariables,
    ).forEach { groupId ->
        setGroupEnabled(scopeId, groupId, true)
    }
}

/** Imported characters have no switch section in older user-visible backups. */
internal fun initializeMissingCharacterToolDefaults(
    existingCharacterIds: Collection<String>,
    savedCharacterIds: Collection<String>,
    initializeCharacterTools: (characterId: String) -> Unit,
) {
    val existing = existingCharacterIds.toSet()
    savedCharacterIds.distinct().filterNot { it in existing }.forEach(initializeCharacterTools)
}
