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
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.agent.harness.AgentHarnessBackend
import com.eleckoi.android.app.background.AndroidAgentForegroundController
import com.eleckoi.android.app.background.AndroidAgentRunCompletionNotifier
import com.eleckoi.android.app.background.AgentNotificationCenter
import com.eleckoi.android.app.background.AgentBackgroundProtection
import com.eleckoi.android.feature.settings.data.websearch.WebSearchSettingsRepository
import com.eleckoi.android.feature.settings.data.websearch.WebSearchMode
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshPlugin
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshConnectionState
import com.eleckoi.android.engine.agent.remotedsh.remoteDshTaskTool
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshTurnImageRegistry
import com.eleckoi.android.feature.settings.data.remotedsh.RemoteDshSettingsRepository
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Application-scoped composition root. Data repositories and executable runtimes stay separate. */
class ElecKoiAppContainer(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext

    private val runtimePaths = RuntimePaths(applicationContext)
    private val database = ElecKoiDatabase.get(applicationContext)
    private val agentToolCatalogStore = AgentToolCatalogStore()
    val repository = ElecKoiRepository(
        context = applicationContext,
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
        val activeToolContext = agentToolCatalogStore.toolContextSnapshot(options.enabledToolGroupIds)
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
                roleBinding = {
                    options.presetId?.let(remoteDshSettingsRepository::roleBinding)
                },
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
                dynamicTools = existingTools + listOfNotNull(
                    webSearchTool,
                    nativeWebSearchBridge,
                    remoteDshTool,
                ),
            ),
        )
    }

    init {
        repository.setRuntimeSessionCleanup(runtimePaths::deletePersistentDeepSeekSessions)
        // Provision both user-configurable channels before the Settings row can open Android's
        // notification page. Channel creation is idempotent and does not post a notification.
        AgentNotificationCenter.ensureChannels(applicationContext)
        repository.attachCharacterAgentRuntime(
            agentSessions,
            localRuntime,
            agentVirtualFileSearch,
            { enabledGroupIds -> agentToolCatalogStore.toolContextSnapshot(enabledGroupIds) },
            agentRuns,
            remoteDshTurnImages::publish,
        )
    }

    internal fun agentToolContextSnapshot(enabledGroupIds: Set<String>): AgentToolContextSnapshot =
        agentToolCatalogStore.toolContextSnapshot(enabledGroupIds)

    internal fun agentToolGroups(enabledGroupIds: Set<String>): List<AgentToolGroupSnapshot> =
        agentToolCatalogStore.groups(enabledGroupIds)

    internal suspend fun creatorAssistantEnabledToolGroupIds(): Set<String>? =
        repository.uiPreferencesRepository.read().creatorAssistantEnabledToolGroupIds

    internal suspend fun setCreatorAssistantEnabledToolGroupIds(groupIds: Set<String>) {
        repository.uiPreferencesRepository.setCreatorAssistantEnabledToolGroupIds(groupIds)
    }

    internal suspend fun creatorAssistantImageModelConfigId(): String =
        repository.uiPreferencesRepository.read().creatorAssistantImageModelConfigId

    internal suspend fun setCreatorAssistantImageModelConfigId(configId: String) {
        repository.uiPreferencesRepository.setCreatorAssistantImageModelConfigId(configId)
    }

    internal fun isCharacterSettingLibraryToolEnabled(characterId: String): Boolean =
        runBlocking {
            AgentToolRequestPolicy.BuiltInSettingLibrary in
                repository.agentPresetRepository.activePreset().toolConfiguration.enabledGroupIds
        }

    internal suspend fun enableCharacterSettingLibraryTool(characterId: String) {
        val preset = repository.agentPresetRepository.activePreset()
        repository.agentPresetRepository.update(
            preset.copy(toolConfiguration = preset.toolConfiguration.copy(
                includedGroupIds = (preset.toolConfiguration.includedGroupIds +
                    AgentToolRequestPolicy.BuiltInSettingLibrary).distinct(),
                enabledGroupIds = preset.toolConfiguration.enabledGroupIds +
                    AgentToolRequestPolicy.BuiltInSettingLibrary,
            )),
        )
    }

    suspend fun prewarmAgentRuntime() {
        // This is deliberately process-scoped: it connects the local runtime service only and
        // never creates a DSH conversation/session for an arbitrary workspace or model.
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
