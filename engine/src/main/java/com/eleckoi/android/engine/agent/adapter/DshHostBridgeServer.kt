package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.readRequest
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJson
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.generation.model.ModelConfig
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call

data class DshHostBridgeEndpoint(
    /** Session-scoped loopback root. Production DSH uses its host-tools and provider-wire routes. */
    val baseUrl: String,
    val sessionToken: String,
)

/**
 * Session-scoped localhost bridge. The upstream API key never enters the Linux guest.
 * Only a random, short-lived path token is visible to the local app-server process.
 */
class DshHostBridgeServer(
    private val scope: CoroutineScope,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    deepSeekFileUploadIndex: File? = null,
) {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val upstreams = ConcurrentHashMap.newKeySet<Call>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var endpoint: DshHostBridgeEndpoint? = null
    private val requestSequence = AtomicInteger(0)
    private val routeRegistry = DshProviderRouteRegistry()
    private val contextPressureEndpoint = ContextPressureEndpoint(routeRegistry)
    private val deepSeekVisionFiles = deepSeekFileUploadIndex?.let(::DeepSeekVisionFilesAdapter)
    private val streamProxy = DshProviderStreamProxy(upstreams, deepSeekVisionFiles)
    private val providerWireEndpoint = DshProviderWireEndpoint(
        routeRegistry = routeRegistry,
        streamProxy = streamProxy,
    )

    suspend fun start(): DshHostBridgeEndpoint = withContext(Dispatchers.IO) {
        endpoint?.let { return@withContext it }
        val token = tokenFactory().also {
            require(SessionToken.matches(it)) { "Adapter session token 格式无效" }
        }
        val socket = ServerSocket().apply {
            reuseAddress = false
            bind(InetSocketAddress(InetAddress.getByName(LoopbackHost), 0), MaxPendingConnections)
        }
        serverSocket = socket
        val created = DshHostBridgeEndpoint(
            baseUrl = "http://$LoopbackHost:${socket.localPort}/$token/v1",
            sessionToken = token,
        )
        endpoint = created
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket, created) }
        created
    }

    suspend fun stop() {
        routeRegistry.close()
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

    /**
     * Registers one loaded Agent session with its Android-side provider credentials.
     *
     * The patched DSH adapters send the owning session id in their deployment-only HTTP header,
     * so one persistent app-server can safely multiplex simultaneous turns without exposing API
     * keys to the Linux process.
     */
    internal fun registerSessionRoute(
        routeKey: String,
        routeModelConfig: ModelConfig,
        routeSubagentModelConfig: ModelConfig? = null,
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
    ): String {
        return routeRegistry.beginSessionTurn(routeKey, ownerToken, userMessage, turnContext)
    }

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

    private suspend fun acceptLoop(server: ServerSocket, endpoint: DshHostBridgeEndpoint) {
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

    private suspend fun handle(socket: Socket, endpoint: DshHostBridgeEndpoint) {
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
        val hostToolCallPath = "/${endpoint.sessionToken}/host-tools/call"
        val contextPressurePath = "/${endpoint.sessionToken}/host-tools/context-pressure"
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
        if (request.method == "POST" && request.path.startsWith(providerWirePrefix)) {
            socket.soTimeout = ClientSocketTimeoutMillis
            providerWireEndpoint.proxyNative(request, output, providerWirePrefix, requestId)
            return
        }
        writeJsonError(output, 404, "Not found")
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

    private companion object {
        const val AcceptFailureInitialBackoffMillis = 50L
        const val AcceptFailureMaxBackoffMillis = 1_000L
        const val LoopbackHost = "127.0.0.1"
        const val MaxPendingConnections = 16
        const val MaxConcurrentRequests = 8
        const val MaxHostToolErrorChars = 1_000
        const val PreAuthClientSocketTimeoutMillis = 5_000
        const val ClientSocketTimeoutMillis = 180_000
        val SessionToken = Regex("^[A-Za-z0-9_-]{24,128}$")
    }
}
