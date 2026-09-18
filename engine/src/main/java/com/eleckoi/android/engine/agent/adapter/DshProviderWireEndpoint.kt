package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.engine.agent.adapter.request.ProviderNativeWebSearchProjector
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.reasoning.DshProviderApi
import com.eleckoi.android.engine.generation.reasoning.effectiveDshApi
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.OutputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Credential boundary for requests already serialized by an official DSH provider adapter.
 *
 * DSH carries its owning session in a deployment-only HTTP header. Android resolves that session
 * to credentials and forwards the serialized body exactly once; it never registers a second LLM
 * adapter, re-enters `ctx.llm.stream()`, or hides routing tokens in the model's tool list.
 */
internal class DshProviderWireEndpoint(
    private val routeRegistry: DshProviderRouteRegistry,
    private val streamProxy: DshProviderStreamProxy,
) {
    suspend fun proxyNative(
        request: AdapterHttpRequest,
        output: OutputStream,
        providerWirePrefix: String,
        requestId: String,
    ) {
        val wireFormat = wireFormatFor(request.path.removePrefix(providerWirePrefix))
        if (wireFormat == null) {
            writeJsonError(output, 404, "Provider request path 与协议不匹配")
            return
        }
        val sessionId = request.headers[SessionIdHeader]
            ?.trim()
            ?.takeIf(SessionId::matches)
        if (sessionId == null) {
            writeJsonError(output, 400, "DSH Provider 请求缺少有效 session 标识")
            return
        }
        val resolved = routeRegistry.resolveSession(sessionId)
        if (resolved == null) {
            writeJsonError(output, 429, "No active Agent session route")
            return
        }
        val expectedFormat = wireFormatFor(resolved.modelConfig.effectiveDshApi())
        if (wireFormat != expectedFormat) {
            writeJsonError(output, 400, "DSH Provider 请求协议与当前模型配置不一致")
            return
        }
        val route = resolved.route
        if (!route.consumeRequestBudget()) {
            writeJsonError(output, 429, "No active Harness route or provider request budget exhausted")
            return
        }
        val nativeRequest = runCatching {
            ElecKoiJson.parseToJsonElement(request.body.toString(Charsets.UTF_8)).jsonObject
        }.getOrElse { error ->
            writeJsonError(output, 400, "Provider JSON 无效：${error.message}")
            return
        }
        val captureId = route.activeRequestCaptureId.get().orEmpty()
        if (captureId.isNotBlank()) {
            AgentRequestDiagnostics.recordHarnessRequest(
                captureId = captureId,
                requestId = requestId,
                requestBody = request.body.toString(Charsets.UTF_8),
            )
        }
        streamProxy.proxyNativeProviderRequest(
            output = output,
            request = prepareNativeWireRequest(nativeRequest, wireFormat, resolved.modelConfig),
            format = wireFormat,
            requestId = requestId,
            captureId = captureId,
            route = route,
            routeModelConfig = resolved.modelConfig,
            protocolHeaders = request.headers.filterKeys(ForwardedProtocolHeaders::contains),
        )
    }

    private fun wireFormatFor(relativePath: String): ProviderWireFormat? = when {
        relativePath == "responses/v1/responses" -> ProviderWireFormat.Responses
        relativePath == "chat/v1/chat/completions" -> ProviderWireFormat.ChatCompletions
        relativePath == "deepseek/v1/chat/completions" -> ProviderWireFormat.ChatCompletions
        relativePath == "anthropic/v1/messages" -> ProviderWireFormat.AnthropicMessages
        relativePath.startsWith("google/models/") &&
            relativePath.endsWith(":streamGenerateContent") -> ProviderWireFormat.GoogleGemini
        else -> null
    }

    private fun wireFormatFor(api: DshProviderApi): ProviderWireFormat = when (api) {
        DshProviderApi.OpenAiResponses -> ProviderWireFormat.Responses
        DshProviderApi.OpenAiCompletions -> ProviderWireFormat.ChatCompletions
        DshProviderApi.AnthropicMessages -> ProviderWireFormat.AnthropicMessages
        DshProviderApi.Google -> ProviderWireFormat.GoogleGemini
    }

    private fun prepareNativeWireRequest(
        request: JsonObject,
        format: ProviderWireFormat,
        config: ModelConfig,
    ): JsonObject {
        val projected = ProviderNativeWebSearchProjector.project(
            request = request,
            format = format,
            modelConfig = config,
        ).withProviderCompatibility(format)
        return buildJsonObject {
            projected.forEach { (key, value) -> put(key, value) }
            if (format != ProviderWireFormat.GoogleGemini) put("model", config.model.trim())
        }
    }

    private fun JsonObject.withProviderCompatibility(format: ProviderWireFormat): JsonObject {
        if (format != ProviderWireFormat.GoogleGemini || "store" !in this) return this
        return JsonObject(this - "store")
    }

    private companion object {
        const val SessionIdHeader = "x-deepseek-harness-session-id"
        val SessionId = Regex("^[A-Za-z0-9._:-]{1,160}$")
        val ForwardedProtocolHeaders = setOf("anthropic-version", "anthropic-beta")
    }
}
