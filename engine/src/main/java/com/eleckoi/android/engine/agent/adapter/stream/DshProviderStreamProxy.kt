package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.readBounded
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeJsonError
import com.eleckoi.android.engine.agent.adapter.AdapterHttpCodec.writeProxyHeaders
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.foundation.network.SensitiveTextSanitizer
import java.io.OutputStream
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Response

/** Forwards the request serialized by the official DSH adapter without protocol conversion. */
internal class DshProviderStreamProxy(
    private val upstreams: MutableSet<Call>,
    private val deepSeekVisionFiles: DeepSeekVisionFilesAdapter?,
) {
    suspend fun proxyNativeProviderRequest(
        output: OutputStream,
        request: JsonObject,
        format: ProviderWireFormat,
        requestId: String,
        captureId: String,
        route: AdapterProviderRoute,
        routeModelConfig: ModelConfig,
        protocolHeaders: Map<String, String>,
    ) {
        val imageFormat = when (format) {
            ProviderWireFormat.Responses -> DeepSeekImageRequestFormat.Responses
            ProviderWireFormat.ChatCompletions -> DeepSeekImageRequestFormat.ChatCompletions
            ProviderWireFormat.AnthropicMessages,
            ProviderWireFormat.GoogleGemini,
            -> null
        }
        val nativeRequest = imageFormat?.let { target ->
            deepSeekVisionFiles?.prepare(request, routeModelConfig, target)?.body
        } ?: request
        val providerRequestBody = nativeRequest.toString()
        if (captureId.isNotBlank()) {
            AgentRequestDiagnostics.recordProviderRequest(captureId, requestId, providerRequestBody)
        }
        val payload = providerRequestBody.toByteArray(Charsets.UTF_8)
        require(payload.size <= MaxBodyBytes) { "Provider 请求超过 24 MiB" }
        val call = runCatching {
            AdapterUpstreamClient.openNativeCall(payload, routeModelConfig, format, protocolHeaders)
        }.getOrElse { error ->
            val message = safeErrorMessage(error, routeModelConfig)
            writeJsonError(output, AdapterUpstreamClient.failureStatus(error), message)
            return
        }
        var response: Response? = null
        var started = false
        upstreams += call
        try {
            response = call.execute()
            val status = response.code
            if (status !in 200..299) {
                val body = readBounded(response.body?.byteStream(), MaxErrorBytes).toString(Charsets.UTF_8)
                val sanitized = SensitiveTextSanitizer.sanitize(body, routeModelConfig.apiKey)
                writeJsonError(output, status, sanitized.ifBlank { "Provider 上游请求失败" })
                return
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                val body = readBounded(response.body?.byteStream(), MaxErrorBytes).toString(Charsets.UTF_8)
                val sanitized = SensitiveTextSanitizer.sanitize(body, routeModelConfig.apiKey)
                writeJsonError(output, 502, sanitized.ifBlank { "Provider 上游没有返回流式 SSE" })
                return
            }
            writeProxyHeaders(output, status, contentType)
            started = true
            val input = requireNotNull(response.body) { "Provider 上游缺少响应正文" }.byteStream()
            val buffer = ByteArray(8_192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MaxNativeUpstreamBytes) { "Provider 流超过大小上限" }
                writeDownstream {
                    output.write(buffer, 0, count)
                    output.flush()
                }
            }
        } catch (closed: DownstreamConnectionClosed) {
            call.cancel()
            route.failTurn("Agent Harness 提前关闭本地响应连接，上游生成未完成")
        } catch (error: Exception) {
            val message = safeErrorMessage(error, routeModelConfig)
            route.failTurn(message)
            if (!started) runCatching {
                writeJsonError(output, AdapterUpstreamClient.failureStatus(error), message)
            }
        } finally {
            upstreams -= call
            response?.close()
        }
    }

    private inline fun writeDownstream(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            throw DownstreamConnectionClosed(error)
        }
    }

    private fun safeErrorMessage(error: Throwable, routeModelConfig: ModelConfig): String =
        SensitiveTextSanitizer.sanitize(
            error.message ?: "模型上游流处理失败",
            routeModelConfig.apiKey,
            maxChars = MaxClientErrorChars,
        )

    private class DownstreamConnectionClosed(cause: Throwable) : RuntimeException(cause)

    private companion object {
        const val MaxBodyBytes = 24 * 1024 * 1024
        const val MaxErrorBytes = 64 * 1024
        const val MaxClientErrorChars = 2_000
        const val MaxNativeUpstreamBytes = 32L * 1024L * 1024L
    }
}
