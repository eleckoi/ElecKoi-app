package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.defaultBaseUrlForProvider
import com.eleckoi.android.foundation.network.SecureModelHttpClientFactory
import com.eleckoi.android.foundation.network.StrictProxyParser
import java.net.URI
import java.net.URL
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import com.eleckoi.android.engine.generation.provider.applyCustomHeaders
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object AdapterUpstreamClient {
    private val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val ConnectTimeoutMillis = 10_000
    private const val ReadTimeoutMillis = 180_000

    fun openChatCall(
        payload: ByteArray,
        modelConfig: ModelConfig,
    ): Call = openCall(
        payload,
        modelConfig,
        resolveChatEndpoint(modelConfig.baseUrl, modelConfig.provider),
    )

    fun openResponsesCall(
        payload: ByteArray,
        modelConfig: ModelConfig,
    ): Call = openCall(
        payload,
        modelConfig,
        resolveResponsesEndpoint(modelConfig.baseUrl, modelConfig.provider),
    )

    fun openNativeCall(
        payload: ByteArray,
        modelConfig: ModelConfig,
        format: ProviderWireFormat,
        protocolHeaders: Map<String, String> = emptyMap(),
    ): Call = openCall(
        payload = payload,
        modelConfig = modelConfig,
        endpoint = when (format) {
            ProviderWireFormat.Responses -> resolveResponsesEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.ChatCompletions -> resolveChatEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.AnthropicMessages -> resolveAnthropicEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.GoogleGemini -> resolveGoogleEndpoint(
                modelConfig.baseUrl,
                modelConfig.provider,
                modelConfig.model,
                stream = true,
            )
        },
        format = format,
        protocolHeaders = protocolHeaders,
    )

    /** Uses the same endpoint, authentication, proxy and custom-header rules as a DSH request. */
    fun openNativeJsonCall(
        payload: ByteArray,
        modelConfig: ModelConfig,
        format: ProviderWireFormat,
    ): Call = openCall(
        payload = payload,
        modelConfig = modelConfig,
        endpoint = when (format) {
            ProviderWireFormat.Responses -> resolveResponsesEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.ChatCompletions -> resolveChatEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.AnthropicMessages -> resolveAnthropicEndpoint(modelConfig.baseUrl, modelConfig.provider)
            ProviderWireFormat.GoogleGemini -> resolveGoogleEndpoint(
                modelConfig.baseUrl,
                modelConfig.provider,
                modelConfig.model,
                stream = false,
            )
        },
        format = format,
        accept = "application/json",
    )

    private fun openCall(
        payload: ByteArray,
        modelConfig: ModelConfig,
        endpoint: URL,
        format: ProviderWireFormat? = null,
        protocolHeaders: Map<String, String> = emptyMap(),
        accept: String = "text/event-stream",
    ): Call {
        val proxy = StrictProxyParser.parse(modelConfig.proxyUrl)
        require(!endpoint.protocol.equals("http", ignoreCase = true) || proxy == null) {
            "本机 HTTP 模型接口不能经过代理，以免 API Key 被代理读取"
        }
        val client = SecureModelHttpClientFactory.create(
            explicitProxy = proxy,
            connectTimeoutMillis = ConnectTimeoutMillis,
            readTimeoutMillis = ReadTimeoutMillis,
        )
        val request = Request.Builder()
            .url(endpoint.toString())
            .applyCustomHeaders(modelConfig)
            .apply {
                when (format) {
                    ProviderWireFormat.AnthropicMessages -> {
                        header("x-api-key", modelConfig.apiKey.trim())
                        header("anthropic-version", protocolHeaders["anthropic-version"] ?: "2023-06-01")
                        protocolHeaders["anthropic-beta"]?.takeIf(String::isNotBlank)?.let {
                            header("anthropic-beta", it)
                        }
                    }
                    ProviderWireFormat.GoogleGemini -> header("x-goog-api-key", modelConfig.apiKey.trim())
                    ProviderWireFormat.Responses,
                    ProviderWireFormat.ChatCompletions,
                    null,
                    -> header("Authorization", "Bearer ${modelConfig.apiKey.trim()}")
                }
            }
            .header("Accept", accept)
            .post(payload.toRequestBody(JsonMediaType))
            .build()
        return client.newCall(request)
    }

    fun failureStatus(error: Throwable): Int {
        var current: Throwable? = error
        while (current != null) {
            if (current is SSLPeerUnverifiedException || current is SSLHandshakeException) {
                return 400
            }
            current = current.cause
        }
        return 502
    }

    private fun resolveChatEndpoint(configuredBaseUrl: String, provider: String): URL {
        val base = removeEndpointSuffix(
            resolvedBaseUrl(configuredBaseUrl, provider),
            "/responses",
        )
        val value = if (base.endsWith("/chat/completions", ignoreCase = true)) {
            base
        } else {
            "$base/chat/completions"
        }
        return validateEndpoint(value, "Chat Completions")
    }

    private fun resolveResponsesEndpoint(configuredBaseUrl: String, provider: String): URL {
        val base = removeEndpointSuffix(
            resolvedBaseUrl(configuredBaseUrl, provider),
            "/chat/completions",
        )
        val value = if (base.endsWith("/responses", ignoreCase = true)) {
            base
        } else {
            "$base/responses"
        }
        return validateEndpoint(value, "Responses")
    }

    private fun resolveAnthropicEndpoint(configuredBaseUrl: String, provider: String): URL {
        val base = deepSeekAnthropicBase(resolvedBaseUrl(configuredBaseUrl, provider))
        val value = when {
            base.endsWith("/messages", ignoreCase = true) -> base
            base.endsWith("/v1", ignoreCase = true) -> "$base/messages"
            else -> "$base/v1/messages"
        }
        return validateEndpoint(value, "Claude Messages")
    }

    /** DeepSeek exposes Anthropic Messages below `/anthropic`, not at its OpenAI API root. */
    private fun deepSeekAnthropicBase(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        if (!uri.host.equals(DeepSeekOfficialHost, ignoreCase = true)) return value
        return when (uri.path.trimEnd('/')) {
            "", "/v1", "/messages", "/v1/messages" ->
                "${uri.scheme}://${uri.rawAuthority}/anthropic"
            else -> value
        }
    }

    private fun resolveGoogleEndpoint(
        configuredBaseUrl: String,
        provider: String,
        model: String,
        stream: Boolean,
    ): URL {
        val base = resolvedBaseUrl(configuredBaseUrl, provider)
            .removeSuffix("/v1beta")
            .removeSuffix("/v1")
            .trimEnd('/')
        val modelPath = model.trim().let { value ->
            when {
                value.startsWith("models/") || value.startsWith("tunedModels/") -> value
                else -> "models/$value"
            }
        }
        require(GoogleModelPath.matches(modelPath)) { "Google Gemini 模型名无效" }
        val operation = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return validateEndpoint(
            "$base/v1beta/$modelPath:$operation",
            "Google Gemini",
            allowQuery = stream,
        )
    }

    private fun resolvedBaseUrl(configuredBaseUrl: String, provider: String): String =
        configuredBaseUrl.trim().ifBlank {
            defaultBaseUrlForProvider(provider)
                ?: error("自定义模型提供商必须填写 Base URL")
        }.trimEnd('/')

    private fun removeEndpointSuffix(value: String, suffix: String): String =
        if (value.endsWith(suffix, ignoreCase = true)) {
            value.dropLast(suffix.length).trimEnd('/')
        } else {
            value
        }

    private fun validateEndpoint(
        value: String,
        formatName: String,
        allowQuery: Boolean = false,
    ): URL {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        require(scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "$formatName Base URL 无效"
        }
        require(scheme == "https" || isStrictLoopbackHost(uri.host)) {
            "远程 $formatName 接口必须使用 HTTPS"
        }
        require(uri.userInfo == null && (allowQuery || uri.query == null) && uri.fragment == null) {
            "Base URL 不能包含账号或查询参数"
        }
        return uri.toURL()
    }

    private fun isStrictLoopbackHost(host: String?): Boolean = when (host?.lowercase()) {
        "localhost", "127.0.0.1" -> true
        else -> false
    }

    private val GoogleModelPath = Regex("^(?:models|tunedModels)/[A-Za-z0-9._-]{1,200}$")
    private const val DeepSeekOfficialHost = "api.deepseek.com"
}
