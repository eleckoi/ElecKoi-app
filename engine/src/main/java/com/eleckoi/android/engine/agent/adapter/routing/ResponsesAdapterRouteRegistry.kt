package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Owns provider credentials and turn authority for default, root, and child Agent routes. */
internal class ResponsesAdapterRouteRegistry(
    modelConfig: ModelConfig,
    dynamicTools: List<AgentDynamicTool>,
    requestCaptureWorkspaceId: String,
    requestCaptureConversationId: String,
    captureProviderRequests: Boolean,
    onDefaultTurnFailure: (String) -> Unit,
) {
    private val defaultSessionRouteKey = AtomicReference<String?>(null)
    private val defaultRoute = AdapterProviderRoute(
        ownerToken = DefaultRouteOwner,
        modelConfig = modelConfig,
        dynamicTools = dynamicTools,
        requestCaptureWorkspaceId = requestCaptureWorkspaceId,
        requestCaptureConversationId = requestCaptureConversationId,
        captureProviderRequests = captureProviderRequests,
        onTurnFailure = onDefaultTurnFailure,
    )
    private val routes = ConcurrentHashMap<String, AdapterProviderRoute>()
    private val routeAliases = ConcurrentHashMap<String, SessionRouteAlias>()

    fun close() {
        defaultRoute.endTurn()
        defaultSessionRouteKey.set(null)
        routes.values.forEach(AdapterProviderRoute::endTurn)
        routes.clear()
        routeAliases.clear()
    }

    fun bindDefaultSessionRoute(routeKey: String) {
        require(SessionRouteKey.matches(routeKey)) { "Agent session 路由编号无效" }
        check(
            defaultSessionRouteKey.compareAndSet(null, routeKey) ||
                defaultSessionRouteKey.get() == routeKey,
        ) { "默认 Agent session 路由已经绑定" }
    }

    fun beginDefaultTurn(userMessage: String): String = defaultRoute.beginTurn(userMessage)

    fun bindDefaultTurn(captureId: String, runtimeTurnId: String) {
        defaultRoute.bindActiveTurn(captureId, runtimeTurnId)
    }

    fun endDefaultTurn() {
        defaultRoute.endTurn()
    }

    fun registerSessionRoute(
        routeKey: String,
        routeModelConfig: ModelConfig,
        routeSubagentModelConfig: ModelConfig?,
        routeSystemInstructions: String,
        routeHistoryCompactionInstructions: String?,
        routeEnabledToolGroupIds: Set<String>,
        routeDynamicTools: List<AgentDynamicTool>,
        routeRequestCaptureWorkspaceId: String,
        routeRequestCaptureConversationId: String,
        routeCaptureProviderRequests: Boolean,
        onTurnFailure: (String) -> Unit,
        onContextPressure: (AdapterContextPressure) -> Unit,
    ): String {
        require(SessionRouteKey.matches(routeKey)) { "Agent session 路由编号无效" }
        require(routeModelConfig.apiKey.isNotBlank()) { "模型配置缺少 API Key" }
        require(routeModelConfig.model.isNotBlank()) { "模型配置缺少模型名" }
        routeSubagentModelConfig?.let { childConfig ->
            require(childConfig.apiKey.isNotBlank()) { "子 Agent 模型配置缺少 API Key" }
            require(childConfig.model.isNotBlank()) { "子 Agent 模型配置缺少模型名" }
        }
        require(routeDynamicTools.map { it.definition.name }.distinct().size == routeDynamicTools.size) {
            "Android 动态工具包含重复名称"
        }
        val ownerToken = UUID.randomUUID().toString()
        routes[routeKey] = AdapterProviderRoute(
            ownerToken = ownerToken,
            modelConfig = routeModelConfig,
            subagentModelConfig = routeSubagentModelConfig,
            systemInstructions = routeSystemInstructions.trim(),
            historyCompactionInstructions = routeHistoryCompactionInstructions?.trim()?.takeIf(String::isNotBlank),
            enabledToolGroupIds = routeEnabledToolGroupIds,
            dynamicTools = routeDynamicTools,
            requestCaptureWorkspaceId = routeRequestCaptureWorkspaceId,
            requestCaptureConversationId = routeRequestCaptureConversationId,
            captureProviderRequests = routeCaptureProviderRequests,
            onTurnFailure = onTurnFailure,
            onContextPressure = onContextPressure,
        )
        return ownerToken
    }

    fun unregisterSessionRoute(routeKey: String, ownerToken: String) {
        routes.computeIfPresent(routeKey) { _, route ->
            if (route.ownerToken == ownerToken) {
                route.endTurn()
                routeAliases.entries.removeIf { (_, alias) ->
                    alias.rootRouteKey == routeKey && alias.ownerToken == ownerToken
                }
                null
            } else {
                route
            }
        }
    }

    fun registerChildSessionRoute(parentSessionId: String, childSessionId: String): Boolean {
        require(SessionRouteKey.matches(parentSessionId)) { "父 Agent session 路由编号无效" }
        require(SessionRouteKey.matches(childSessionId)) { "子 Agent session 路由编号无效" }
        if (parentSessionId == childSessionId || routes.containsKey(childSessionId)) return false
        val parent = registeredRouteForKey(parentSessionId) ?: return false
        val alias = SessionRouteAlias(
            rootRouteKey = parent.rootRouteKey,
            ownerToken = parent.route.ownerToken,
        )
        val existing = routeAliases.putIfAbsent(childSessionId, alias)
        return existing == null || existing == alias
    }

    fun unregisterChildSessionRoute(childSessionId: String) {
        routeAliases.remove(childSessionId)
    }

    fun beginSessionTurn(
        routeKey: String,
        ownerToken: String,
        userMessage: String,
        turnContext: AgentTurnRequestContext?,
    ): String = requireOwnedRoute(routeKey, ownerToken).beginTurn(userMessage, turnContext)

    fun bindSessionTurn(
        routeKey: String,
        ownerToken: String,
        captureId: String,
        runtimeTurnId: String,
    ) {
        requireOwnedRoute(routeKey, ownerToken).bindActiveTurn(captureId, runtimeTurnId)
    }

    fun endSessionTurn(routeKey: String, ownerToken: String) {
        routes[routeKey]
            ?.takeIf { it.ownerToken == ownerToken }
            ?.endTurn()
    }

    suspend fun resolve(request: JsonObject): ResolvedProviderRoute? {
        val cacheKey = (request["prompt_cache_key"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: return defaultResolvedRoute()
        if (cacheKey == defaultSessionRouteKey.get()) return defaultResolvedRoute()
        registeredRouteForKey(cacheKey)?.let { return it }
        if (cacheKey.startsWith(GuardianRoutePrefix)) {
            registeredRouteForKey(cacheKey.removePrefix(GuardianRoutePrefix))?.let { return it }
        }
        // DSH publishes subagent.started immediately before the child reaches the HTTP adapter.
        // Give the independent notification transport a short scheduling window.
        repeat(ChildRouteRegistrationAttempts) {
            delay(ChildRouteRegistrationPollMillis)
            registeredRouteForKey(cacheKey)?.let { return it }
        }
        return null
    }

    /** Resolves the session id carried by DSH before any provider wire format is chosen. */
    suspend fun resolveSession(routeKey: String): ResolvedProviderRoute? {
        if (routeKey == defaultSessionRouteKey.get()) return defaultResolvedRoute()
        registeredRouteForKey(routeKey)?.let { return it }
        if (routeKey.startsWith(GuardianRoutePrefix)) {
            registeredRouteForKey(routeKey.removePrefix(GuardianRoutePrefix))?.let { return it }
        }
        repeat(ChildRouteRegistrationAttempts) {
            delay(ChildRouteRegistrationPollMillis)
            registeredRouteForKey(routeKey)?.let { return it }
        }
        return null
    }

    fun routeForHostTool(sessionId: String): AdapterProviderRoute? =
        registeredRouteForKey(sessionId)?.route
            ?: if (sessionId == defaultSessionRouteKey.get()) defaultRoute else null

    fun publishContextPressure(sample: AdapterContextPressure): Boolean {
        val resolved = registeredRouteForKey(sample.sessionId) ?: return false
        // Child agents have independent context surfaces. The visible root meter must not inherit
        // a delegated session's pressure merely because provider authority is shared.
        if (!resolved.aliased) resolved.route.publishContextPressure(sample)
        return true
    }

    private fun requireOwnedRoute(routeKey: String, ownerToken: String): AdapterProviderRoute =
        routes[routeKey]?.takeIf { it.ownerToken == ownerToken }
            ?: error("Agent session 的模型路由已经失效")

    private fun registeredRouteForKey(routeKey: String): ResolvedProviderRoute? {
        routes[routeKey]?.let {
            return ResolvedProviderRoute(
                route = it,
                modelConfig = it.modelConfig,
                aliased = false,
                rootRouteKey = routeKey,
            )
        }
        val alias = routeAliases[routeKey] ?: return null
        val root = routes[alias.rootRouteKey]
            ?.takeIf { it.ownerToken == alias.ownerToken }
        if (root == null) {
            routeAliases.remove(routeKey, alias)
            return null
        }
        return ResolvedProviderRoute(
            route = root,
            modelConfig = root.subagentModelConfig ?: root.modelConfig,
            aliased = true,
            rootRouteKey = alias.rootRouteKey,
        )
    }

    private fun defaultResolvedRoute() = ResolvedProviderRoute(
        route = defaultRoute,
        modelConfig = defaultRoute.modelConfig,
        aliased = false,
        rootRouteKey = DefaultRouteOwner,
    )

    private data class SessionRouteAlias(
        val rootRouteKey: String,
        val ownerToken: String,
    )

    private companion object {
        const val ChildRouteRegistrationAttempts = 20
        const val ChildRouteRegistrationPollMillis = 10L
        const val GuardianRoutePrefix = "guardian:"
        const val DefaultRouteOwner = "default"
        val SessionRouteKey = Regex("^[A-Za-z0-9._:-]{1,160}$")
    }
}

internal data class ResolvedProviderRoute(
    val route: AdapterProviderRoute,
    val modelConfig: ModelConfig,
    val aliased: Boolean,
    val rootRouteKey: String,
)
