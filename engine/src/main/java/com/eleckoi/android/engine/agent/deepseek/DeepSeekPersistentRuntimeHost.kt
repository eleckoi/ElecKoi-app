package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.adapter.DshHostBridgeServer
import com.eleckoi.android.engine.agent.adapter.AdapterContextPressure
import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentToolContextBlockIds
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessJsonRpcClient
import com.eleckoi.android.engine.agent.deepseek.protocol.LocalRuntimeDeepSeekTransport
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshSessionInspection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.reasoning.DshPiAiProviderCatalog
import com.eleckoi.android.engine.generation.reasoning.DshResolvedModelCapabilities
import com.eleckoi.android.engine.generation.reasoning.DshModelCapabilities
import com.eleckoi.android.engine.generation.reasoning.usesDshDeepSeekOfficialRoute
import com.eleckoi.android.engine.generation.model.configuredAutoCompactTokenLimit
import com.eleckoi.android.engine.generation.model.configuredContextWindowTokens
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.generation.model.defaultContextWindowTokens
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Application owner of one durable DSH process.
 *
 * DSH itself multiplexes independent agents by session id. Android keeps only provider credentials,
 * tool handlers and optional request captures in per-session routes; closing a screen releases that route but
 * deliberately leaves the DSH agent and its JSONL transcript alive.
 */
class DeepSeekPersistentRuntimeHost(
    private val runtime: LocalRuntimeGateway,
    private val runtimePaths: RuntimePaths,
    private val modelConfigProvider: suspend (String?) -> ModelConfig,
    private val modelCatalogProvider: suspend () -> List<ModelConfig>,
) : DeepSeekSessionBackendFactory, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val agentPresetMaterializer = DshAgentPresetMaterializer(runtimePaths)
    private val sessionSnapshots = DshSessionSnapshotMaterializer(runtimePaths)
    private val deepSeekModels = linkedMapOf<String, DeepSeekCatalogModel>()
    private var running: RunningHost? = null

    override suspend fun prepare(
        options: AgentSessionOptions,
        scope: CoroutineScope,
    ): PreparedDeepSeekBackend {
        val storedConfig = modelConfigProvider(options.modelConfigId)
        val selectedModel = options.model?.trim().orEmpty().ifBlank { storedConfig.model.trim() }
        require(selectedModel.isNotBlank()) { "模型配置缺少模型名" }
        val routeConfig = storedConfig.copy(model = selectedModel)
        val subagentConfig = options.subagentModelConfigId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { modelConfigProvider(it) }
            ?.let { config ->
                config.copy(
                    model = options.subagentModel
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: config.model,
                )
            }
        val effectiveSubagentConfig = subagentConfig ?: routeConfig
        require(effectiveSubagentConfig.model.isNotBlank()) { "子 Agent 模型配置缺少模型名" }
        val contextWindow = routeConfig.configuredContextWindowTokens()
        val runtimeWorkspacePath = runtimePaths.persistentGuestWorkspacePath(
            options.workspaceId,
            options.workspaceProjectPath,
        )
        val systemInstructions = buildDeepSeekSessionSystemInstructions(options, runtimeWorkspacePath)
        val agentPreset = agentPresetMaterializer.materialize(
            options = options,
            systemInstructions = systemInstructions,
            contextWindow = contextWindow,
            autoCompactTokenLimit = routeConfig.configuredAutoCompactTokenLimit(),
        )
        val runtimeModelConfigs = runtimeModelCatalog(routeConfig, subagentConfig)
        val modelProvider = if (routeConfig.usesDshDeepSeekOfficialRoute()) {
            DshModelCapabilities.DeepSeekOfficialWireProfile
        } else {
            DshPiAiProviderCatalog.providerRoute(routeConfig, runtimeModelConfigs)
        }
        val subagentProvider = if (effectiveSubagentConfig.usesDshDeepSeekOfficialRoute()) {
            DshModelCapabilities.DeepSeekOfficialWireProfile
        } else {
            DshPiAiProviderCatalog.providerRoute(effectiveSubagentConfig, runtimeModelConfigs)
        }
        val host = ensureStarted(
            contextWindow = contextWindow,
            modelConfigs = runtimeModelConfigs,
        )
        val route = SessionProviderRoute(
            adapter = host.adapter,
            modelConfig = routeConfig,
            subagentModelConfig = subagentConfig,
            dynamicTools = options.dynamicTools,
            requestCaptureWorkspaceId = options.workspaceId,
            requestCaptureConversationId = options.conversationId,
            captureProviderRequests = options.captureProviderRequests,
            sessionExists = runtimePaths::persistentDeepSeekSessionExists,
            initialHistory = options.initialHistoryItems,
            mountedPresetId = agentPreset,
            modelProvider = modelProvider,
            subagentProvider = subagentProvider,
            snapshotMaterializer = sessionSnapshots,
        )
        return PreparedDeepSeekBackend(
            model = routeConfig.model,
            provider = modelProvider,
            subagentModel = effectiveSubagentConfig.model,
            maxTokens = routeConfig.configuredMaxOutputTokens(),
            sessionCwd = runtimeWorkspacePath,
            client = host.client,
            clientAlreadyStarted = true,
            turnFailures = route.turnFailures,
            contextPressures = route.contextPressures,
            release = route::close,
            abortHost = ::shutdown,
            discardSessionFiles = runtimePaths::deletePersistentDeepSeekSessions,
            bindSessionRoute = route::bindSession,
            beginTurnWindow = route::beginTurn,
            bindTurnWindow = route::bindTurn,
            endTurnWindow = route::endTurn,
        )
    }

    private suspend fun ensureStarted(
        contextWindow: Int,
        modelConfigs: List<ModelConfig>,
    ): RunningHost =
        lifecycleMutex.withLock {
            val deepSeekCatalogChanged = refreshDeepSeekModels(modelConfigs)
            val piAiCatalogKey = DshPiAiProviderCatalog.providersJson(
                modelConfigs,
                CatalogFingerprintBaseUrl,
            )
            running?.takeIf {
                it.alive.get() &&
                    !deepSeekCatalogChanged &&
                    it.piAiCatalogKey == piAiCatalogKey
            }?.let { return@withLock it }
            running?.let { stale -> stopHost(stale) }
            running = null

            val adapter = DshHostBridgeServer(
                scope = scope,
                deepSeekFileUploadIndex = runtimePaths.deepSeekFileUploadIndex,
            )
            val endpoint = adapter.start()
            val providerRoot = endpoint.baseUrl.removeSuffix("/").removeSuffix("/v1")
            val bootstrapConfig = modelConfigs.first()
            val bootstrapProvider = if (bootstrapConfig.usesDshDeepSeekOfficialRoute()) {
                DshModelCapabilities.DeepSeekOfficialWireProfile
            } else {
                DshPiAiProviderCatalog.providerRoute(bootstrapConfig, modelConfigs)
            }
            val bootstrapModel = if (bootstrapConfig.usesDshDeepSeekOfficialRoute()) {
                bootstrapConfig.model.trim()
            } else {
                DshPiAiProviderCatalog.runtimeModelId(bootstrapConfig)
            }
            val transport = LocalRuntimeDeepSeekTransport(
                runtime = runtime,
                launchSpec = DeepSeekRuntimeLaunchSpec(
                    workspaceId = runtimePaths.persistentDeepSeekWorkspaceId,
                    providerBaseUrl = endpoint.baseUrl,
                    model = bootstrapModel,
                    modelContextWindow = contextWindow,
                    autoCompactTokenLimit = null,
                    ephemeral = false,
                    deepSeekModelsJson = deepSeekModelsJson(),
                    piAiProvidersJson = DshPiAiProviderCatalog.providersJson(
                        modelConfigs,
                        providerRoot,
                    ),
                    // Registration is process-wide. Request visibility remains route-scoped by
                    // AgentToolCatalogStore, and each DSH agent has an independent session id.
                    workspaceToolsEnabled = true,
                    workflowToolsEnabled = true,
                    collaborationToolsEnabled = true,
                ),
                scope = scope,
            )
            val client = DeepSeekHarnessJsonRpcClient(transport, scope)
            try {
                client.start(
                    cwd = RuntimeWorkspace,
                    provider = bootstrapProvider,
                    model = bootstrapModel,
                    maxTokens = null,
                )
                val alive = AtomicBoolean(true)
                val notificationJob = this@DeepSeekPersistentRuntimeHost.scope.launch(
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    client.notifications.collect { notification ->
                        when (notification.method) {
                            "subagent.started" -> {
                                val parentSessionId = notification.params.string("parentSessionId")
                                val childSessionId = notification.params.string("childSessionId")
                                if (parentSessionId != null && childSessionId != null) {
                                    adapter.registerChildSessionRoute(parentSessionId, childSessionId)
                                }
                            }
                            "subagent.finished" -> {
                                notification.params.string("childSessionId")
                                    ?.let(adapter::unregisterChildSessionRoute)
                            }
                        }
                    }
                }
                val failureJob = this@DeepSeekPersistentRuntimeHost.scope.launch {
                    client.failures.collect { alive.set(false) }
                }
                RunningHost(
                    adapter = adapter,
                    client = client,
                    alive = alive,
                    notificationJob = notificationJob,
                    failureJob = failureJob,
                    piAiCatalogKey = piAiCatalogKey,
                ).also { running = it }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    runCatching { client.shutdown() }
                    runCatching { adapter.stop() }
                }
                throw error
            }
        }

    private suspend fun runtimeModelCatalog(vararg selected: ModelConfig?): List<ModelConfig> {
        return DshRuntimeModelCatalog.merge(modelCatalogProvider(), selected.filterNotNull())
    }

    private fun refreshDeepSeekModels(configs: List<ModelConfig>): Boolean {
        val nextModels = DefaultDeepSeekModels.associateByTo(linkedMapOf()) { it.id }
        configs
            .filter(ModelConfig::usesDshDeepSeekOfficialRoute)
            .forEach { config ->
                val optionsById = config.modelOptions.associateBy { it.id.trim() }
                (config.modelOptions.map { it.id } + config.model)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .forEach modelLoop@{ id ->
                        if (!config.copy(model = id).usesDshDeepSeekOfficialRoute()) return@modelLoop
                        val option = optionsById[id]
                        val existing = nextModels[id]
                        val next = DeepSeekCatalogModel(
                            id = id,
                            name = option?.name?.trim()?.takeIf { it.isNotBlank() && it != id }
                                ?: existing?.name,
                            contextWindow = option?.contextWindowTokens ?: existing?.contextWindow
                                ?: config.defaultContextWindowTokens(),
                            maxTokens = option?.maxOutputTokens ?: existing?.maxTokens,
                            supportsImage = existing?.supportsImage == true || config.supportsImageInput(id),
                            systemPromptInHistory = existing?.systemPromptInHistory == true,
                        )
                        nextModels[id] = next
                    }
            }
        if (deepSeekModels == nextModels) return false
        deepSeekModels.clear()
        deepSeekModels.putAll(nextModels)
        return true
    }

    private fun deepSeekModelsJson(): String = buildJsonArray {
        deepSeekModels.values.forEach { model ->
            add(buildJsonObject {
                put("id", model.id)
                model.name?.let { put("name", it) }
                put("contextWindow", model.contextWindow)
                model.maxTokens?.let { put("maxTokens", it) }
                put("inputModalities", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("text"))
                    if (model.supportsImage) add(kotlinx.serialization.json.JsonPrimitive("image"))
                })
                if (model.systemPromptInHistory) put("systemPromptUpdate", "in-history")
            })
        }
    }.toString()

    /** Reads the backend-decoded current session artifact; Kotlin never interprets physical JSONL rows. */
    suspend fun inspectSession(sessionId: String): DshSessionInspection? {
        val active = lifecycleMutex.withLock { running?.takeIf { it.alive.get() } }
        val host = active ?: run {
            val storedConfig = modelConfigProvider(null)
            require(storedConfig.model.isNotBlank()) { "模型配置缺少模型名" }
            ensureStarted(
                contextWindow = storedConfig.configuredContextWindowTokens(),
                modelConfigs = runtimeModelCatalog(storedConfig),
            )
        }
        return host.client.inspectSession(sessionId)
    }

    /** Resolves selectable capabilities through the same registered DSH adapter used for requests. */
    suspend fun resolveModelCapabilities(
        config: ModelConfig,
        modelIds: List<String>,
    ): Map<String, DshResolvedModelCapabilities> {
        val ids = modelIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return emptyMap()
        val routeConfig = config.copy(model = config.model.trim().ifBlank { ids.first() })
        val runtimeModelConfigs = runtimeModelCatalog(routeConfig)
        val host = ensureStarted(
            contextWindow = routeConfig.configuredContextWindowTokens(),
            modelConfigs = runtimeModelConfigs,
        )
        val provider = if (routeConfig.usesDshDeepSeekOfficialRoute()) {
            DshModelCapabilities.DeepSeekOfficialWireProfile
        } else {
            DshPiAiProviderCatalog.providerRoute(routeConfig, runtimeModelConfigs)
        }
        return ids.associateWith { modelId ->
            val runtimeModelId = if (routeConfig.usesDshDeepSeekOfficialRoute()) {
                modelId
            } else {
                DshPiAiProviderCatalog.runtimeModelId(routeConfig.copy(model = modelId))
            }
            host.client.resolveModel(provider, runtimeModelId)
        }
    }

    suspend fun shutdown() = lifecycleMutex.withLock {
        val active = running ?: return@withLock
        running = null
        stopHost(active)
    }

    private suspend fun stopHost(host: RunningHost) = withContext(NonCancellable) {
        host.alive.set(false)
        host.notificationJob.cancel()
        host.failureJob.cancel()
        runCatching { host.client.shutdown() }
        runCatching { host.adapter.stop() }
    }

    override fun close() {
        runBlocking(Dispatchers.IO) { shutdown() }
        scope.cancel()
    }

    private data class RunningHost(
        val adapter: DshHostBridgeServer,
        val client: DeepSeekHarnessJsonRpcClient,
        val alive: AtomicBoolean,
        val notificationJob: Job,
        val failureJob: Job,
        val piAiCatalogKey: String,
    )

    private data class DeepSeekCatalogModel(
        val id: String,
        val name: String? = null,
        val contextWindow: Int = 1_000_000,
        val maxTokens: Int? = null,
        val supportsImage: Boolean = false,
        val systemPromptInHistory: Boolean = false,
    )

    private class SessionProviderRoute(
        private val adapter: DshHostBridgeServer,
        private val modelConfig: ModelConfig,
        private val subagentModelConfig: ModelConfig?,
        private val dynamicTools: List<AgentDynamicTool>,
        private val requestCaptureWorkspaceId: String,
        private val requestCaptureConversationId: String,
        private val captureProviderRequests: Boolean,
        private val sessionExists: (String) -> Boolean,
        private val initialHistory: List<AgentHistoryItem>,
        private val mountedPresetId: String,
        private val modelProvider: String,
        private val subagentProvider: String,
        private val snapshotMaterializer: DshSessionSnapshotMaterializer,
    ) {
        private val binding = AtomicReference<Binding?>(null)
        private val _turnFailures = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val turnFailures: Flow<String> = _turnFailures.asSharedFlow()
        private val contextPressure = MutableStateFlow<DeepSeekContextPressure?>(null)
        val contextPressures: Flow<DeepSeekContextPressure> = contextPressure.filterNotNull()

        fun bindSession(sessionId: String) {
            val previous = binding.getAndSet(null)
            if (previous?.sessionId == sessionId) {
                binding.set(previous)
                return
            }
            previous?.let { adapter.unregisterSessionRoute(it.sessionId, it.ownerToken) }
            val ownerToken = adapter.registerSessionRoute(
                routeKey = sessionId,
                routeModelConfig = modelConfig,
                routeSubagentModelConfig = subagentModelConfig,
                routeDynamicTools = dynamicTools,
                routeRequestCaptureWorkspaceId = requestCaptureWorkspaceId,
                routeRequestCaptureConversationId = requestCaptureConversationId,
                routeCaptureProviderRequests = captureProviderRequests,
                onTurnFailure = { message -> _turnFailures.tryEmit(message) },
                onContextPressure = { sample -> contextPressure.value = sample.toDeepSeekSample() },
            )
            binding.set(
                Binding(
                    sessionId = sessionId,
                    ownerToken = ownerToken,
                    historyProjection = if (sessionExists(sessionId)) {
                        AgentHistoryProjection.Native
                    } else {
                        AgentHistoryProjection.SeedProductHistory
                    },
                ),
            )
            val projection = binding.get()?.historyProjection ?: AgentHistoryProjection.Native
            snapshotMaterializer.write(
                sessionId = sessionId,
                turnToken = "bound-${UUID.randomUUID()}",
                mountedPresetId = mountedPresetId,
                model = modelConfig,
                modelProvider = modelProvider,
                subagentModel = subagentModelConfig ?: modelConfig,
                subagentProvider = subagentProvider,
                turnContext = snapshotMaterializer.emptyContext(initialHistory, projection),
            )
        }

        fun beginTurn(
            userMessage: String,
            history: List<AgentHistoryItem>,
            contextInjections: List<AgentContextInjection>,
        ): String {
            val current = requireNotNull(binding.get()) { "DSH session 尚未绑定模型路由" }
            val turnContext = AgentTurnRequestContext(
                userMessage = userMessage,
                history = history,
                injections = contextInjections,
                historyProjection = current.historyProjection,
            )
            snapshotMaterializer.write(
                sessionId = current.sessionId,
                turnToken = UUID.randomUUID().toString(),
                mountedPresetId = mountedPresetId,
                model = modelConfig,
                modelProvider = modelProvider,
                subagentModel = subagentModelConfig ?: modelConfig,
                subagentProvider = subagentProvider,
                turnContext = turnContext,
            )
            return adapter.beginSessionTurn(
                routeKey = current.sessionId,
                ownerToken = current.ownerToken,
                userMessage = userMessage,
                turnContext = turnContext,
            )
        }

        fun bindTurn(captureId: String, turnId: String) {
            val current = binding.get() ?: return
            adapter.bindSessionTurn(current.sessionId, current.ownerToken, captureId, turnId)
        }

        fun endTurn() {
            val current = binding.get() ?: return
            adapter.endSessionTurn(current.sessionId, current.ownerToken)
        }

        suspend fun close() {
            val current = binding.getAndSet(null) ?: return
            adapter.unregisterSessionRoute(current.sessionId, current.ownerToken)
        }

        private data class Binding(
            val sessionId: String,
            val ownerToken: String,
            val historyProjection: AgentHistoryProjection,
        )

        private fun AdapterContextPressure.toDeepSeekSample() = DeepSeekContextPressure(
            sessionId = sessionId,
            pressureTokens = pressureTokens,
            projectedTokens = projectedTokens,
            contextWindow = contextWindow,
            systemTokens = systemTokens,
            toolsTokens = toolsTokens,
            messageTokens = messageTokens,
        )
    }

    private companion object {
        const val RuntimeWorkspace = "/workspace"
        const val CatalogFingerprintBaseUrl = "http://127.0.0.1:1/catalog00"
        val DefaultDeepSeekModels = listOf(
            DeepSeekCatalogModel(
                id = "deepseek-flash",
                name = "DeepSeek-V41-Flash",
                supportsImage = true,
                systemPromptInHistory = true,
            ),
            DeepSeekCatalogModel(id = "deepseek-v4-flash", name = "DeepSeek-V4-Flash"),
            DeepSeekCatalogModel(id = "deepseek-v4-pro", name = "DeepSeek-V4-Pro"),
            DeepSeekCatalogModel(
                id = "deepseek-v4-flash-vision-exp",
                name = "DeepSeek-V4-Flash-Vision-Exp",
                supportsImage = true,
            ),
        )
    }
}

/**
 * A workspace path is capability context, not generic conversation metadata. Keeping it out when
 * the workspace group is disabled prevents a role from learning an app-private guest path merely
 * because every DSH session still needs an internal cwd.
 */
internal fun buildDeepSeekSessionSystemInstructions(
    options: AgentSessionOptions,
    runtimeWorkspacePath: String,
): String = buildList {
    options.baseInstructions?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    options.developerInstructions?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    val workspaceVisible = options.toolContextBlocks.any { block ->
        block.id == AgentToolContextBlockIds.Permissions && block.enabled
    }
    if (workspaceVisible) {
        add(
            "This conversation's authorized workspace is $runtimeWorkspacePath. " +
                "When using Bash or file tools, stay inside that directory.",
        )
    }
}.joinToString("\n\n")

private fun JsonObject.string(name: String): String? =
    get(name)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
