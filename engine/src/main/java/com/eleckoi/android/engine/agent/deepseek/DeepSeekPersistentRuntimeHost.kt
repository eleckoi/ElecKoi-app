package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.adapter.LoopbackResponsesAdapterServer
import com.eleckoi.android.engine.agent.adapter.AdapterContextPressure
import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentHistoryPolicy
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentToolContextBlockIds
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessJsonRpcClient
import com.eleckoi.android.engine.agent.deepseek.protocol.LocalRuntimeDeepSeekTransport
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryContextStore
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.configuredAutoCompactTokenLimit
import com.eleckoi.android.engine.generation.model.configuredContextWindowTokens
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    private val toolRequestFilter: (Set<String>, JsonObject) -> JsonObject,
) : DeepSeekSessionBackendFactory, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val trajectoryContextStore = DshTrajectoryContextStore(runtimePaths)
    private val toolDefinitions = linkedMapOf<String, AgentToolDefinition>()
    private var running: RunningHost? = null

    override suspend fun prepare(
        options: AgentSessionOptions,
        scope: CoroutineScope,
    ): PreparedDeepSeekBackend {
        val storedConfig = modelConfigProvider(options.modelConfigId)
        val selectedModel = options.model?.trim().orEmpty().ifBlank { storedConfig.model.trim() }
        require(selectedModel.isNotBlank()) { "模型配置缺少模型名" }
        val routeConfig = storedConfig.copy(model = selectedModel)
        val host = ensureStarted(
            requiredTools = options.dynamicTools,
            contextWindow = routeConfig.configuredContextWindowTokens(),
            autoCompactTokenLimit = routeConfig.configuredAutoCompactTokenLimit(),
        )
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
        val runtimeWorkspacePath = runtimePaths.persistentGuestWorkspacePath(
            options.workspaceId,
            options.workspaceProjectPath,
        )
        val route = SessionProviderRoute(
            adapter = host.adapter,
            modelConfig = routeConfig,
            subagentModelConfig = subagentConfig,
            systemInstructions = buildDeepSeekSessionSystemInstructions(options, runtimeWorkspacePath),
            enabledToolGroupIds = options.enabledToolGroupIds,
            dynamicTools = options.dynamicTools,
            requestCaptureWorkspaceId = options.workspaceId,
            requestCaptureConversationId = options.conversationId,
            captureProviderRequests = options.captureProviderRequests,
            sessionExists = runtimePaths::persistentDeepSeekSessionExists,
            historyPolicy = options.historyPolicy,
            historyCompactionInstructions = options.historyCompactionInstructions,
        )
        return PreparedDeepSeekBackend(
            model = PersistentRoutingModel,
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
        requiredTools: List<AgentDynamicTool>,
        contextWindow: Int,
        autoCompactTokenLimit: Int?,
    ): RunningHost =
        lifecycleMutex.withLock {
            val catalogChanged = mergeToolDefinitions(requiredTools)
            running?.takeIf {
                it.alive.get() &&
                    !catalogChanged &&
                    it.contextWindow == contextWindow &&
                    it.autoCompactTokenLimit == autoCompactTokenLimit
            }?.let { return@withLock it }
            running?.let { stale -> stopHost(stale) }
            running = null

            val adapter = LoopbackResponsesAdapterServer(
                modelConfig = ModelConfig(
                    apiKey = UnusedRouteCredential,
                    model = PersistentRoutingModel,
                ),
                scope = scope,
                toolRequestFilter = toolRequestFilter,
                deepSeekFileUploadIndex = runtimePaths.deepSeekFileUploadIndex,
                recordTrajectoryContext = trajectoryContextStore::recordTurn,
            )
            val endpoint = adapter.start()
            val transport = LocalRuntimeDeepSeekTransport(
                runtime = runtime,
                launchSpec = DeepSeekRuntimeLaunchSpec(
                    workspaceId = runtimePaths.persistentDeepSeekWorkspaceId,
                    providerBaseUrl = endpoint.baseUrl,
                    model = PersistentRoutingModel,
                    modelContextWindow = contextWindow,
                    autoCompactTokenLimit = autoCompactTokenLimit,
                    systemPrompt = SharedSystemPrompt,
                    ephemeral = false,
                    hostToolCatalogJson = hostToolCatalogJson(),
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
                    provider = ProviderRoute,
                    model = PersistentRoutingModel,
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
                    contextWindow = contextWindow,
                    autoCompactTokenLimit = autoCompactTokenLimit,
                ).also { running = it }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    runCatching { client.shutdown() }
                    runCatching { adapter.stop() }
                }
                throw error
            }
        }

    private fun mergeToolDefinitions(requiredTools: List<AgentDynamicTool>): Boolean {
        var changed = false
        requiredTools.forEach { tool ->
            val definition = tool.definition
            val existing = toolDefinitions[definition.name]
            require(existing == null || existing == definition) {
                "Android 动态工具 ${definition.name} 在不同会话中使用了不一致的协议"
            }
            if (existing == null) {
                toolDefinitions[definition.name] = definition
                changed = true
            }
        }
        return changed
    }

    private fun hostToolCatalogJson(): String = buildJsonObject {
        put("tools", buildJsonArray {
            toolDefinitions.values.forEach { definition ->
                add(buildJsonObject {
                    put("name", definition.name)
                    put("description", definition.description)
                    put("parameters", definition.parameters)
                })
            }
        })
    }.toString()

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
        val adapter: LoopbackResponsesAdapterServer,
        val client: DeepSeekHarnessJsonRpcClient,
        val alive: AtomicBoolean,
        val notificationJob: Job,
        val failureJob: Job,
        val contextWindow: Int,
        val autoCompactTokenLimit: Int?,
    )

    private class SessionProviderRoute(
        private val adapter: LoopbackResponsesAdapterServer,
        private val modelConfig: ModelConfig,
        private val subagentModelConfig: ModelConfig?,
        private val systemInstructions: String,
        private val enabledToolGroupIds: Set<String>,
        private val dynamicTools: List<AgentDynamicTool>,
        private val requestCaptureWorkspaceId: String,
        private val requestCaptureConversationId: String,
        private val captureProviderRequests: Boolean,
        private val sessionExists: (String) -> Boolean,
        private val historyPolicy: AgentHistoryPolicy,
        private val historyCompactionInstructions: String?,
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
                routeSystemInstructions = systemInstructions,
                routeHistoryCompactionInstructions = historyCompactionInstructions,
                routeEnabledToolGroupIds = enabledToolGroupIds,
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
                    historyProjection = when {
                        historyPolicy == AgentHistoryPolicy.ProductDialogue ->
                            AgentHistoryProjection.ReplacePreviousTurns
                        !sessionExists(sessionId) -> AgentHistoryProjection.SeedProductHistory
                        else -> AgentHistoryProjection.Native
                    },
                ),
            )
        }

        fun beginTurn(
            userMessage: String,
            history: List<AgentHistoryItem>,
            contextInjections: List<AgentContextInjection>,
        ): String {
            val current = requireNotNull(binding.get()) { "DSH session 尚未绑定模型路由" }
            return adapter.beginSessionTurn(
                routeKey = current.sessionId,
                ownerToken = current.ownerToken,
                userMessage = userMessage,
                turnContext = AgentTurnRequestContext(
                    userMessage = userMessage,
                    history = history,
                    injections = contextInjections,
                    historyProjection = current.historyProjection,
                ),
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
        )
    }

    private companion object {
        const val PersistentRoutingModel = "eleckoi-dsh-route"
        const val ProviderRoute = "eleckoi-bridge"
        const val RuntimeWorkspace = "/workspace"
        const val UnusedRouteCredential = "unused-local-route"
        const val SharedSystemPrompt = ""
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
