package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJson
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.agent.adapter.request.ProviderNativeWebSearchProjector
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.generation.model.configuredTemperature
import com.eleckoi.android.engine.generation.model.configuredTopP
import com.eleckoi.android.engine.generation.model.effectiveApiFormat
import com.eleckoi.android.engine.generation.model.usesChatThinkingToggleContract
import com.eleckoi.android.engine.generation.reasoning.DshModelCapabilities
import com.eleckoi.android.engine.generation.reasoning.DshReasoningEfforts
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.OutputStream
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Production DSH provider boundary.
 *
 * Android owns product-level history/insertion projection. DSH's pi-ai adapter owns the final
 * provider serialization. A one-time route tool reconnects that serialized request to the Android
 * credential/model route and is removed before the request reaches the provider.
 */
internal class DshProviderBridgeEndpoint(
    private val routeRegistry: ResponsesAdapterRouteRegistry,
    private val streamProxy: ResponsesAdapterStreamProxy,
    private val toolRequestFilter: (Set<String>, JsonObject) -> JsonObject,
    tokenFactory: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preparedRequests = PreparedProviderRequestRegistry(
        tokenFactory = tokenFactory,
        clock = clock,
    )

    fun close() {
        preparedRequests.clear()
    }

    suspend fun prepare(
        body: ByteArray,
        output: OutputStream,
        requestId: String,
    ) {
        val requestBody = body.toString(Charsets.UTF_8)
        val request = runCatching {
            ElecKoiJson.parseToJsonElement(requestBody).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "DSH 通用请求 JSON 无效：${error.message}")
            return
        }
        val sessionId = (request["sessionId"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
        if (sessionId == null || request["messages"] !is JsonArray) {
            writeJsonError(output, 400, "DSH 通用请求缺少 sessionId 或 messages")
            return
        }
        val resolved = routeRegistry.resolveSession(sessionId)
        if (resolved == null) {
            writeJsonError(output, 429, "No active Agent session route")
            return
        }
        val route = resolved.route
        if (!route.consumeRequestBudget()) {
            writeJsonError(output, 429, "No active Harness route or provider request budget exhausted")
            return
        }
        val requestIndex = route.turnRequestSequence.incrementAndGet()
        val captureId = route.activeRequestCaptureId.get().orEmpty()
        if (captureId.isNotBlank()) {
            AgentRequestDiagnostics.recordHarnessRequest(captureId, requestId, requestBody)
        }
        val isCompaction = (request["purpose"] as? JsonPrimitive)?.contentOrNull == "compaction"
        val projected = when {
            isCompaction -> route.projectDshRequest(request, requestIndex, isCompactionRequest = true)
            resolved.aliased -> request
            else -> route.projectDshRequest(request, requestIndex, isCompactionRequest = false)
        }
        val routed = if (isCompaction) projected else projected.withAdditionalSystem(route.systemInstructions)
        val filtered = runCatching { toolRequestFilter(route.enabledToolGroupIds, routed) }.getOrElse { error ->
            writeJsonError(output, 400, error.message ?: "DSH 工具过滤失败")
            return
        }
        val routeConfig = resolved.modelConfig
        val wireFormat = when (routeConfig.effectiveApiFormat()) {
            ModelApiFormat.Responses -> ProviderWireFormat.Responses
            ModelApiFormat.ChatCompletions -> ProviderWireFormat.ChatCompletions
            ModelApiFormat.AnthropicMessages -> ProviderWireFormat.AnthropicMessages
            ModelApiFormat.GoogleGemini -> ProviderWireFormat.GoogleGemini
        }
        val requestWithLimits = buildJsonObject {
            filtered.forEach { (key, value) -> put(key, value) }
            routeConfig.configuredTemperature()?.let { put("temperature", it) }
            routeConfig.configuredTopP()?.let { put("topP", it) }
            if (!isCompaction) {
                routeConfig.configuredMaxOutputTokens()?.let { put("maxTokens", it) }
            }
        }
        val requestToken = runCatching {
            preparedRequests.issue(
                PreparedProviderRequest(
                    route = route,
                    modelConfig = routeConfig,
                    format = wireFormat,
                    requestId = requestId,
                    captureId = captureId,
                    isCompactionRequest = isCompaction,
                    createdAtMillis = clock(),
                ),
            )
        }.getOrElse { error ->
            writeJsonError(output, 429, error.message ?: "无法准备 Provider 请求")
            return
        }
        writeJson(
            output,
            buildJsonObject {
                put("requestToken", requestToken)
                put("api", routeConfig.piApiFor(wireFormat))
                put("model", routeConfig.model.trim())
                put("wireProfile", DshModelCapabilities.active(routeConfig).wireProfile)
                val reasoningEffort = DshReasoningEfforts.selected(routeConfig)
                    .takeUnless { isCompaction }
                reasoningEffort?.let { put("reasoningEffort", it) }
                put("request", requestWithLimits)
            },
        )
    }

    fun cancel(body: ByteArray, output: OutputStream) {
        val request = runCatching {
            ElecKoiJson.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
        }.getOrNull()
        val token = (request?.get("requestToken") as? JsonPrimitive)?.contentOrNull
        if (token == null) {
            writeJsonError(output, 400, "取消请求缺少 requestToken")
            return
        }
        writeJson(output, buildJsonObject { put("cancelled", preparedRequests.cancel(token)) })
    }

    suspend fun proxyNative(
        request: AdapterHttpRequest,
        output: OutputStream,
        providerWirePrefix: String,
    ) {
        val wireFormat = wireFormatFor(request.path.removePrefix(providerWirePrefix))
        if (wireFormat == null) {
            writeJsonError(output, 404, "Provider request path 与协议不匹配")
            return
        }
        val nativeRequest = runCatching {
            ElecKoiJson.parseToJsonElement(request.body.toString(Charsets.UTF_8)).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "Provider JSON 无效：${error.message}")
            return
        }
        val routed = runCatching { nativeRequest.withoutInternalRouteTool(wireFormat) }.getOrNull()
        if (routed == null) {
            writeJsonError(output, 404, "Provider request route 已失效")
            return
        }
        val prepared = preparedRequests.consume(routed.requestToken)
        if (prepared == null || prepared.format != wireFormat) {
            writeJsonError(output, 404, "Provider request route 已失效")
            return
        }
        streamProxy.proxyNativeProviderRequest(
            output = output,
            request = prepareNativeWireRequest(routed.request, prepared),
            format = prepared.format,
            requestId = prepared.requestId,
            captureId = prepared.captureId,
            route = prepared.route,
            routeModelConfig = prepared.modelConfig,
            protocolHeaders = request.headers.filterKeys(ForwardedProtocolHeaders::contains),
        )
    }

    private fun wireFormatFor(relativePath: String): ProviderWireFormat? = when (relativePath) {
        "responses/v1/responses" -> ProviderWireFormat.Responses
        "chat/v1/chat/completions" -> ProviderWireFormat.ChatCompletions
        "chat-thinking/v1/chat/completions" -> ProviderWireFormat.ChatCompletions
        "anthropic/v1/messages" -> ProviderWireFormat.AnthropicMessages
        "google/models/$WireModel:streamGenerateContent" -> ProviderWireFormat.GoogleGemini
        else -> null
    }

    private fun prepareNativeWireRequest(
        request: JsonObject,
        prepared: PreparedProviderRequest,
    ): JsonObject {
        val config = prepared.modelConfig
        val projected = ProviderNativeWebSearchProjector.project(
            request = request,
            format = prepared.format,
            modelConfig = config,
        )
        return buildJsonObject {
            projected.forEach { (key, value) -> put(key, value) }
            if (prepared.format != ProviderWireFormat.GoogleGemini) {
                put("model", config.model.trim())
            }
        }
    }

    private fun com.eleckoi.android.engine.generation.model.ModelConfig.piApiFor(
        wireFormat: ProviderWireFormat,
    ): String = if (
        wireFormat == ProviderWireFormat.ChatCompletions && usesChatThinkingToggleContract()
    ) {
        "openai-completions-thinking"
    } else {
        wireFormat.piApi
    }

    private fun JsonObject.withoutInternalRouteTool(format: ProviderWireFormat): RoutedNativeRequest? {
        val tools = get("tools") as? JsonArray ?: return null
        val tokens = mutableListOf<String>()
        val retained = when (format) {
            ProviderWireFormat.Responses,
            ProviderWireFormat.AnthropicMessages,
            -> JsonArray(tools.filterNot { tool ->
                tool.jsonObject.text("name").routeToken()?.also(tokens::add) != null
            })
            ProviderWireFormat.ChatCompletions -> JsonArray(tools.filterNot { tool ->
                (tool.jsonObject["function"] as? JsonObject)
                    ?.text("name")
                    .routeToken()
                    ?.also(tokens::add) != null
            })
            ProviderWireFormat.GoogleGemini -> JsonArray(tools.mapNotNull { tool ->
                val declaration = tool.jsonObject
                val functions = declaration["functionDeclarations"] as? JsonArray
                    ?: return@mapNotNull declaration
                val retainedFunctions = JsonArray(functions.filterNot { function ->
                    function.jsonObject.text("name").routeToken()?.also(tokens::add) != null
                })
                when {
                    retainedFunctions.isNotEmpty() -> buildJsonObject {
                        declaration.forEach { (key, value) -> put(key, value) }
                        put("functionDeclarations", retainedFunctions)
                    }
                    declaration.size > 1 -> JsonObject(declaration - "functionDeclarations")
                    else -> null
                }
            })
        }
        val token = tokens.singleOrNull() ?: return null
        val routeOnlyFields = setOf("tool_choice", "toolChoice", "toolConfig")
        val sanitized = buildJsonObject {
            this@withoutInternalRouteTool.forEach { (key, value) ->
                if (key != "tools" && (retained.isNotEmpty() || key !in routeOnlyFields)) {
                    put(key, value)
                }
            }
            if (retained.isNotEmpty()) put("tools", retained)
        }
        return RoutedNativeRequest(token, sanitized)
    }

    private fun JsonObject.withAdditionalSystem(additional: String): JsonObject {
        val normalized = additional.trim()
        if (normalized.isEmpty()) return this
        val existing = (get("system") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        return buildJsonObject {
            this@withAdditionalSystem.forEach { (key, value) -> put(key, value) }
            put("system", listOf(existing, normalized).filter(String::isNotBlank).joinToString("\n\n"))
        }
    }

    private fun String?.routeToken(): String? = this
        ?.takeIf { it.startsWith(RouteToolPrefix) }
        ?.removePrefix(RouteToolPrefix)
        ?.takeIf(ProviderRequestToken::matches)

    private fun JsonObject.text(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private data class RoutedNativeRequest(
        val requestToken: String,
        val request: JsonObject,
    )

    private companion object {
        const val WireModel = "eleckoi-wire"
        const val RouteToolPrefix = "eleckoi_internal_route_"
        val ForwardedProtocolHeaders = setOf("anthropic-version", "anthropic-beta")
        val ProviderRequestToken = Regex("^[A-Za-z0-9_-]{24,128}$")
    }
}
