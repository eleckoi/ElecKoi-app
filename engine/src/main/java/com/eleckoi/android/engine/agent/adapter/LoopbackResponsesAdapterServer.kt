package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.readRequest
import com.eleckoi.android.engine.agent.adapter.request.ResponsesCompactionRequestProjector
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJson
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.generation.model.effectiveApiFormat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call

data class ResponsesAdapterEndpoint(
    /** Session-scoped loopback root. Production DSH uses its host-tools and provider-wire routes. */
    val baseUrl: String,
    val sessionToken: String,
)

/**
 * Session-scoped localhost bridge. The upstream API key never enters the Linux guest.
 * Only a random, short-lived path token is visible to the local app-server process.
 */
class LoopbackResponsesAdapterServer(
    private val modelConfig: ModelConfig,
    private val scope: CoroutineScope,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val requestCaptureWorkspaceId: String = "",
    private val requestCaptureConversationId: String = "",
    private val captureProviderRequests: Boolean = false,
    private val legacyResponsesProbeEnabled: Boolean = false,
    private val toolRequestFilter: (Set<String>, JsonObject) -> JsonObject = { _, request -> request },
    private val dynamicTools: List<AgentDynamicTool> = emptyList(),
    deepSeekFileUploadIndex: File? = null,
) {
    private val _defaultTurnFailures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val defaultTurnFailures: Flow<String> = _defaultTurnFailures.asSharedFlow()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val upstreams = ConcurrentHashMap.newKeySet<Call>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var endpoint: ResponsesAdapterEndpoint? = null
    private val requestSequence = AtomicInteger(0)
    private val routeRegistry = ResponsesAdapterRouteRegistry(
        modelConfig = modelConfig,
        dynamicTools = dynamicTools,
        requestCaptureWorkspaceId = requestCaptureWorkspaceId,
        requestCaptureConversationId = requestCaptureConversationId,
        captureProviderRequests = captureProviderRequests,
        onDefaultTurnFailure = { message -> _defaultTurnFailures.tryEmit(message) },
    )
    private val contextPressureEndpoint = ContextPressureEndpoint(routeRegistry)
    private val deepSeekVisionFiles = deepSeekFileUploadIndex?.let(::DeepSeekVisionFilesAdapter)
    private val streamProxy = ResponsesAdapterStreamProxy(scope, upstreams, deepSeekVisionFiles)
    private val providerBridgeEndpoint = DshProviderBridgeEndpoint(
        routeRegistry = routeRegistry,
        streamProxy = streamProxy,
        toolRequestFilter = toolRequestFilter,
    )

    suspend fun start(): ResponsesAdapterEndpoint = withContext(Dispatchers.IO) {
        endpoint?.let { return@withContext it }
        require(dynamicTools.map { it.definition.name }.distinct().size == dynamicTools.size) {
            "Android 动态工具包含重复名称"
        }
        require(modelConfig.apiKey.isNotBlank()) { "模型配置缺少 API Key" }
        require(modelConfig.model.isNotBlank()) { "模型配置缺少模型名" }
        val token = tokenFactory().also {
            require(SessionToken.matches(it)) { "Adapter session token 格式无效" }
        }
        val socket = ServerSocket().apply {
            reuseAddress = false
            bind(InetSocketAddress(InetAddress.getByName(LoopbackHost), 0), MaxPendingConnections)
        }
        serverSocket = socket
        val created = ResponsesAdapterEndpoint(
            baseUrl = "http://$LoopbackHost:${socket.localPort}/$token/v1",
            sessionToken = token,
        )
        endpoint = created
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket, created) }
        created
    }

    suspend fun stop() {
        routeRegistry.close()
        providerBridgeEndpoint.close()
        withContext(Dispatchers.IO) {
            runCatching { serverSocket?.close() }
            sockets.forEach { runCatching { it.close() } }
            upstreams.forEach(Call::cancel)
            sockets.clear()
            upstreams.clear()
        }
        acceptJob?.cancelAndJoin()
        acceptJob = null
        serverSocket = null
        endpoint = null
    }

    /** Binds the private default route to one Harness session's `prompt_cache_key`. */
    fun bindDefaultSessionRoute(routeKey: String) {
        routeRegistry.bindDefaultSessionRoute(routeKey)
    }

    /** Opens a bounded provider-request window for one user-initiated Agent turn. */
    fun beginTurn(userMessage: String = ""): String {
        return routeRegistry.beginDefaultTurn(userMessage)
    }

    fun bindActiveTurn(captureId: String, runtimeTurnId: String) {
        routeRegistry.bindDefaultTurn(captureId, runtimeTurnId)
    }

    fun endTurn() {
        routeRegistry.endDefaultTurn()
    }

    /**
     * Registers one loaded Agent session with its Android-side provider credentials.
     *
     * Supported Harness clients send the owning session id as `prompt_cache_key`,
     * so one persistent app-server can safely multiplex simultaneous turns without exposing API
     * keys to the Linux process.
     */
    internal fun registerSessionRoute(
        routeKey: String,
        routeModelConfig: ModelConfig,
        routeSubagentModelConfig: ModelConfig? = null,
        routeSystemInstructions: String = "",
        routeHistoryCompactionInstructions: String? = null,
        routeEnabledToolGroupIds: Set<String>,
        routeDynamicTools: List<AgentDynamicTool> = emptyList(),
        routeRequestCaptureWorkspaceId: String = "",
        routeRequestCaptureConversationId: String = "",
        routeCaptureProviderRequests: Boolean,
        onTurnFailure: (String) -> Unit = {},
        onContextPressure: (AdapterContextPressure) -> Unit = {},
    ): String {
        return routeRegistry.registerSessionRoute(
            routeKey = routeKey,
            routeModelConfig = routeModelConfig,
            routeSubagentModelConfig = routeSubagentModelConfig,
            routeSystemInstructions = routeSystemInstructions,
            routeHistoryCompactionInstructions = routeHistoryCompactionInstructions,
            routeEnabledToolGroupIds = routeEnabledToolGroupIds,
            routeDynamicTools = routeDynamicTools,
            routeRequestCaptureWorkspaceId = routeRequestCaptureWorkspaceId,
            routeRequestCaptureConversationId = routeRequestCaptureConversationId,
            routeCaptureProviderRequests = routeCaptureProviderRequests,
            onTurnFailure = onTurnFailure,
            onContextPressure = onContextPressure,
        )
    }

    fun unregisterSessionRoute(routeKey: String, ownerToken: String) {
        routeRegistry.unregisterSessionRoute(routeKey, ownerToken)
    }

    /**
     * Gives an in-process DSH child the parent's provider and host-tool authority.
     * Nested children are flattened to one owned root route.
     */
    fun registerChildSessionRoute(parentSessionId: String, childSessionId: String): Boolean {
        return routeRegistry.registerChildSessionRoute(parentSessionId, childSessionId)
    }

    fun unregisterChildSessionRoute(childSessionId: String) {
        routeRegistry.unregisterChildSessionRoute(childSessionId)
    }

    internal fun beginSessionTurn(
        routeKey: String,
        ownerToken: String,
        userMessage: String,
        turnContext: AgentTurnRequestContext? = null,
    ): String = routeRegistry.beginSessionTurn(routeKey, ownerToken, userMessage, turnContext)

    fun bindSessionTurn(
        routeKey: String,
        ownerToken: String,
        captureId: String,
        runtimeTurnId: String,
    ) {
        routeRegistry.bindSessionTurn(routeKey, ownerToken, captureId, runtimeTurnId)
    }

    fun endSessionTurn(routeKey: String, ownerToken: String) {
        routeRegistry.endSessionTurn(routeKey, ownerToken)
    }

    private suspend fun acceptLoop(server: ServerSocket, endpoint: ResponsesAdapterEndpoint) {
        var consecutiveFailures = 0
        while (!server.isClosed) {
            val socket = try {
                withContext(Dispatchers.IO) { server.accept() }.also { consecutiveFailures = 0 }
            } catch (error: Exception) {
                if (server.isClosed) return
                consecutiveFailures += 1
                val multiplier = 1L shl (consecutiveFailures - 1).coerceAtMost(5)
                delay((AcceptFailureInitialBackoffMillis * multiplier).coerceAtMost(AcceptFailureMaxBackoffMillis))
                continue
            }
            if (sockets.size >= MaxConcurrentRequests) {
                runCatching { socket.close() }
                continue
            }
            sockets += socket
            scope.launch(Dispatchers.IO) {
                try {
                    handle(socket, endpoint)
                } finally {
                    sockets -= socket
                    runCatching { socket.close() }
                }
            }
        }
    }

    private suspend fun handle(socket: Socket, endpoint: ResponsesAdapterEndpoint) {
        val requestId = "adapter-${requestSequence.incrementAndGet()}"
        // Unauthenticated local clients get only a short window, so a handful of slow sockets
        // cannot occupy every adapter worker for the full provider timeout.
        socket.soTimeout = PreAuthClientSocketTimeoutMillis
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = runCatching { readRequest(input) }.getOrElse { error ->
            writeJsonError(output, 400, error.message ?: "HTTP 请求无效")
            return
        }
        val responsesPath = "/${endpoint.sessionToken}/v1/responses"
        val hostToolCallPath = "/${endpoint.sessionToken}/host-tools/call"
        val contextPressurePath = "/${endpoint.sessionToken}/host-tools/context-pressure"
        val providerPreparePath = "/${endpoint.sessionToken}/host-tools/provider/prepare"
        val providerCancelPath = "/${endpoint.sessionToken}/host-tools/provider/cancel"
        val providerWirePrefix = "/${endpoint.sessionToken}/provider-wire/"
        if (request.method == "POST" && request.path == hostToolCallPath) {
            socket.soTimeout = ClientSocketTimeoutMillis
            executeHostTool(request.body, output)
            return
        }
        if (request.method == "POST" && request.path == contextPressurePath) {
            socket.soTimeout = ClientSocketTimeoutMillis
            contextPressureEndpoint.accept(request.body, output)
            return
        }
        if (request.method == "POST" && request.path == providerPreparePath) {
            socket.soTimeout = ClientSocketTimeoutMillis
            providerBridgeEndpoint.prepare(request.body, output, requestId)
            return
        }
        if (request.method == "POST" && request.path == providerCancelPath) {
            socket.soTimeout = ClientSocketTimeoutMillis
            providerBridgeEndpoint.cancel(request.body, output)
            return
        }
        if (request.method == "POST" && request.path.startsWith(providerWirePrefix)) {
            socket.soTimeout = ClientSocketTimeoutMillis
            providerBridgeEndpoint.proxyNative(request, output, providerWirePrefix)
            return
        }
        if (!legacyResponsesProbeEnabled || request.method != "POST" || request.path != responsesPath) {
            writeJsonError(output, 404, "Not found")
            return
        }
        socket.soTimeout = ClientSocketTimeoutMillis
        val responsesRequestBody = request.body.toString(Charsets.UTF_8)
        val responsesRequest = runCatching {
            ElecKoiJson.parseToJsonElement(responsesRequestBody).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "Responses JSON 无效：${error.message}")
            return
        }
        val resolvedRoute = routeRegistry.resolve(responsesRequest)
        if (resolvedRoute == null) {
            writeJsonError(output, 429, "No active Agent session route")
            return
        }
        val route = resolvedRoute.route
        val routeModelConfig = resolvedRoute.modelConfig
        if (!route.consumeRequestBudget()) {
            writeJsonError(output, 429, "No active Harness route or provider request budget exhausted")
            return
        }
        val turnRequestIndex = route.turnRequestSequence.incrementAndGet()
        val captureId = route.activeRequestCaptureId.get().orEmpty()
        if (captureId.isNotBlank()) {
            AgentRequestDiagnostics.recordHarnessRequest(
                captureId = captureId,
                requestId = requestId,
                requestBody = responsesRequestBody,
            )
        }
        val isCompactionRequest = request.headers[CompactionRequestHeader] == "1" ||
            ResponsesCompactionRequestProjector.isNativeCompactionRequest(responsesRequest)
        // Spawned children own fresh DSH transcripts. They inherit provider authority, not the
        // parent's product-dialogue history projection. Auxiliary compaction is still sanitized
        // because it must never inherit interactive tools or output protocols.
        val projectedRequest = when {
            isCompactionRequest -> route.projectLegacyProbeRequest(
                request = responsesRequest,
                isCompactionRequest = true,
            )
            resolvedRoute.aliased -> responsesRequest
            else -> route.projectLegacyProbeRequest(
                request = responsesRequest,
                isCompactionRequest = false,
            )
        }
        val routedRequest = if (isCompactionRequest) {
            projectedRequest
        } else {
            projectedRequest.withAdditionalSystemInstructions(route.systemInstructions)
        }
        val filteredRequest = runCatching {
            toolRequestFilter(route.enabledToolGroupIds, routedRequest)
        }.getOrElse { error ->
            writeJsonError(output, 400, error.message ?: "Responses 工具过滤失败")
            return
        }
        when (routeModelConfig.effectiveApiFormat()) {
            ModelApiFormat.Responses -> {
                val preparedRequest = buildJsonObject {
                    filteredRequest.forEach { (key, value) -> put(key, value) }
                    put("model", routeModelConfig.model.trim())
                    if (!isCompactionRequest) {
                        routeModelConfig.configuredMaxOutputTokens()?.let {
                            put("max_output_tokens", it)
                        }
                    }
                }
                streamProxy.proxyNativeResponsesRequest(
                    output = output,
                    request = preparedRequest,
                    requestId = requestId,
                    captureId = captureId,
                    route = route,
                    routeModelConfig = routeModelConfig,
                )
            }
            ModelApiFormat.ChatCompletions -> streamProxy.proxyConvertedChatRequest(
                output = output,
                request = filteredRequest,
                requestId = requestId,
                captureId = captureId,
                turnRequestIndex = turnRequestIndex,
                route = route,
                routeModelConfig = routeModelConfig,
                isCompactionRequest = isCompactionRequest,
            )
            ModelApiFormat.AnthropicMessages,
            ModelApiFormat.GoogleGemini,
            -> {
                val format = routeModelConfig.effectiveApiFormat().storageValue
                writeJsonError(output, 400, "接口格式尚未接入请求链：$format")
            }
        }
    }

    private suspend fun executeHostTool(body: ByteArray, output: OutputStream) {
        val request = runCatching {
            ElecKoiJson.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "动态工具请求无效：${error.message}")
            return
        }
        val name = (request["name"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val arguments = request["arguments"] as? JsonObject
        val sessionId = (request["sessionId"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
        if (name == null || arguments == null || sessionId == null) {
            writeJsonError(output, 400, "动态工具请求缺少 sessionId、name 或 arguments")
            return
        }
        val route = routeRegistry.routeForHostTool(sessionId)
        val tool = route?.dynamicTools?.singleOrNull { it.definition.name == name }
        if (tool == null) {
            writeJsonError(output, 404, "动态工具不可用")
            return
        }
        val result = runCatching { tool.handler.execute(arguments) }
            .getOrElse { error ->
                AgentDynamicToolResult(
                    content = error.message
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                        ?.take(MaxHostToolErrorChars)
                        ?.takeIf(String::isNotBlank)
                        ?: "工具执行失败",
                    success = false,
                )
            }
        writeJson(
            output,
            buildJsonObject {
                put("content", result.content)
                put("success", result.success)
            },
        )
    }

    private fun JsonObject.withAdditionalSystemInstructions(additional: String): JsonObject {
        val normalized = additional.trim()
        if (normalized.isEmpty()) return this
        val existing = (get("instructions") as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            .orEmpty()
        return buildJsonObject {
            this@withAdditionalSystemInstructions.forEach { (key, value) -> put(key, value) }
            put(
                "instructions",
                listOf(existing, normalized)
                    .filter(String::isNotBlank)
                    .joinToString("\n\n"),
            )
        }
    }

    private companion object {
        const val CompactionRequestHeader = "x-deepseek-harness-compact"
        const val AcceptFailureInitialBackoffMillis = 50L
        const val AcceptFailureMaxBackoffMillis = 1_000L
        const val LoopbackHost = "127.0.0.1"
        const val MaxPendingConnections = 16
        const val MaxConcurrentRequests = 8
        const val MaxBodyBytes = 24 * 1024 * 1024
        const val MaxErrorBytes = 64 * 1024
        const val MaxClientErrorChars = 2_000
        const val MaxHostToolErrorChars = 1_000
        const val MaxUpstreamLineChars = 4 * 1024 * 1024
        const val MaxUpstreamResponseChars = 32 * 1024 * 1024
        const val PreAuthClientSocketTimeoutMillis = 5_000
        const val ClientSocketTimeoutMillis = 180_000
        const val NativeReplayWindowMillis = 40L
        val SessionToken = Regex("^[A-Za-z0-9_-]{24,128}$")
        val NativeTerminalEvents = setOf(
            "response.completed",
            "response.failed",
            "response.incomplete",
        )
    }

    private class DownstreamConnectionClosed(cause: Throwable) : RuntimeException(cause)

}
