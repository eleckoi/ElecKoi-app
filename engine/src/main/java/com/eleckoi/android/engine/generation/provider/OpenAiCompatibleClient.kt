package com.eleckoi.android.engine.generation.provider

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.generation.model.effectiveApiFormat
import com.eleckoi.android.engine.generation.model.resolvedProviderBaseUrl
import com.eleckoi.android.foundation.network.SecureModelHttpClientFactory
import com.eleckoi.android.foundation.network.SensitiveTextSanitizer
import com.eleckoi.android.foundation.network.StrictProxyParser
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.objects
import com.eleckoi.android.foundation.storage.stringOrEmpty
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.Proxy
import java.net.URI

/**
 * Provider configuration probe used by the model settings screen.
 *
 * Character replies are deliberately not implemented here. The small non-streaming completion is
 * reserved for hidden supporting work such as converting a finished scene into an image prompt;
 * all visible character replies still run through [com.eleckoi.android.engine.agent.api.AgentSession].
 */
class OpenAiCompatibleClient {
    fun fetchModels(config: ModelConfig): List<ModelOption> {
        if (config.apiKey.trim().isEmpty()) throw ElecKoiDataException("缺少 API Key")
        val format = config.effectiveApiFormat()
        val data = getJson(
            url = modelListUrl(config, format),
            config = config,
            label = "${config.provider} 模型列表请求",
            format = format,
        )
        val arrayName = if (format == ModelApiFormat.GoogleGemini) "models" else "data"
        return data.optJSONArray(arrayName)
            ?.objects()
            ?.mapNotNull { item ->
                if (format == ModelApiFormat.GoogleGemini && !item.supportsGenerateContent()) {
                    return@mapNotNull null
                }
                val idKey = if (format == ModelApiFormat.GoogleGemini) "name" else "id"
                val id = item.stringOrEmpty(idKey).trim()
                id.takeIf(String::isNotBlank)?.let {
                    ModelOption(
                        id = id,
                        name = id,
                        contextWindowTokens = item.firstPositiveInt(
                            "inputTokenLimit",
                            "context_window",
                            "context_length",
                            "max_context_length",
                            "max_model_len",
                            "contextWindow",
                            "contextLength",
                        ),
                        maxOutputTokens = item.firstPositiveInt(
                            "outputTokenLimit",
                            "max_output_tokens",
                            "max_completion_tokens",
                            "max_tokens",
                            "maxOutputTokens",
                        ),
                    )
                }
            }
            ?.toList()
            ?: emptyList()
    }

    fun completeText(
        config: ModelConfig,
        systemPrompt: String,
        userPrompt: String,
        onRequestBody: (String) -> Unit = {},
    ): String {
        if (config.apiKey.trim().isEmpty()) throw ElecKoiDataException("缺少聊天模型 API Key")
        if (config.model.trim().isEmpty()) throw ElecKoiDataException("缺少聊天模型名称")
        val payload = textCompletionPayload(
            config = config,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
        onRequestBody(payload.toString())
        val response = postJson(
            url = chatCompletionsUrl(config),
            config = config,
            label = "画面提示词生成请求",
            payload = payload,
        )
        return textFromCompletionResponse(response)
    }

    private fun JSONObject.firstPositiveInt(vararg keys: String): Int? {
        return keys.firstNotNullOfOrNull { key ->
            if (!has(key) || isNull(key)) return@firstNotNullOfOrNull null
            optString(key).trim().toLongOrNull()
                ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
                ?.toInt()
        }
    }

    private fun openAiBaseUrl(config: ModelConfig): String {
        val custom = config.resolvedProviderBaseUrl()
        if (custom.isNotEmpty()) return custom
        throw ElecKoiDataException("请填写自定义模型提供商 API 地址")
    }

    private fun JSONObject.supportsGenerateContent(): Boolean {
        val methods = optJSONArray("supportedGenerationMethods") ?: return true
        return (0 until methods.length()).any { index ->
            methods.optString(index).equals("generateContent", ignoreCase = true)
        }
    }

    private fun chatCompletionsUrl(config: ModelConfig): String {
        val base = openAiBaseUrl(config)
        return if (base.endsWith("/chat/completions", ignoreCase = true)) base
        else "$base/chat/completions"
    }

    private fun modelListUrl(config: ModelConfig, format: ModelApiFormat): String {
        val base = openAiBaseUrl(config)
        if (format != ModelApiFormat.GoogleGemini) return "$base/models"

        val baseUri = runCatching { URI(base) }.getOrElse {
            throw ElecKoiDataException("模型接口地址格式无效")
        }
        if (baseUri.userInfo != null || baseUri.query != null || baseUri.fragment != null) {
            throw ElecKoiDataException("模型接口地址不能包含账号、查询参数或片段")
        }
        val apiRoot = base
            .removeSuffixIgnoringCase("/v1beta/openai")
            .removeSuffixIgnoringCase("/v1beta")
            .removeSuffixIgnoringCase("/v1")
            .trimEnd('/')
        return "$apiRoot/v1beta/models?pageSize=1000"
    }

    private fun getJson(
        url: String,
        config: ModelConfig,
        label: String,
        format: ModelApiFormat,
    ): JSONObject {
        val requestTarget = validatedRequestTarget(
            url = url,
            config = config,
            allowQuery = format == ModelApiFormat.GoogleGemini,
        )
        val client = SecureModelHttpClientFactory.create(
            explicitProxy = requestTarget.proxy,
            connectTimeoutMillis = 8_000,
            readTimeoutMillis = 18_000,
        )
        val request = Request.Builder()
            .url(requestTarget.uri.toString())
            .applyCustomHeaders(config)
            .apply {
                if (format == ModelApiFormat.GoogleGemini) {
                    header("x-goog-api-key", config.apiKey.trim())
                    removeHeader("Authorization")
                } else {
                    header("Authorization", "Bearer ${config.apiKey.trim()}")
                }
            }
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = readBody(
                stream = response.body?.byteStream(),
                error = !response.isSuccessful,
            )
            if (!response.isSuccessful) {
                throw ElecKoiDataException(
                    "$label HTTP ${response.code}：${SensitiveTextSanitizer.sanitize(body, config.apiKey)}",
                )
            }
            return JSONObject(body)
        }
    }

    private fun postJson(
        url: String,
        config: ModelConfig,
        label: String,
        payload: JSONObject,
    ): JSONObject {
        val requestTarget = validatedRequestTarget(url, config)
        val client = SecureModelHttpClientFactory.create(
            explicitProxy = requestTarget.proxy,
            connectTimeoutMillis = 10_000,
            readTimeoutMillis = 90_000,
        )
        val request = Request.Builder()
            .url(requestTarget.uri.toString())
            .applyCustomHeaders(config)
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = readBody(response.body?.byteStream(), error = !response.isSuccessful)
            if (!response.isSuccessful) {
                throw ElecKoiDataException(
                    "$label HTTP ${response.code}：${SensitiveTextSanitizer.sanitize(body, config.apiKey)}",
                )
            }
            return runCatching { JSONObject(body) }.getOrElse {
                throw ElecKoiDataException("$label 返回了无效 JSON")
            }
        }
    }

    private fun validatedRequestTarget(
        url: String,
        config: ModelConfig,
        allowQuery: Boolean = false,
    ): RequestTarget {
        val uri = runCatching { URI(url) }.getOrElse {
            throw ElecKoiDataException("模型接口地址格式无效")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw ElecKoiDataException("模型接口必须是有效的 HTTPS 地址")
        }
        if (scheme == "http" && !isStrictLoopbackHost(uri.host)) {
            throw ElecKoiDataException("远程模型接口必须使用 HTTPS；HTTP 仅允许本机回环地址")
        }
        if (uri.userInfo != null || (!allowQuery && uri.query != null) || uri.fragment != null) {
            throw ElecKoiDataException("模型接口地址不能包含账号、查询参数或片段")
        }
        val proxy = proxyFrom(config.proxyUrl)
        if (scheme == "http" && proxy != null) {
            throw ElecKoiDataException("本机 HTTP 模型接口不能经过代理，以免 API Key 被代理读取")
        }
        return RequestTarget(uri = uri, proxy = proxy)
    }

    private fun isStrictLoopbackHost(host: String?): Boolean = when (host?.lowercase()) {
        "localhost", "127.0.0.1" -> true
        else -> false
    }

    private fun proxyFrom(value: String): Proxy? {
        return try {
            StrictProxyParser.parse(value)
        } catch (error: IllegalArgumentException) {
            throw ElecKoiDataException("代理配置无效：${error.message.orEmpty()}")
        }
    }

    private fun readBody(stream: java.io.InputStream?, error: Boolean): String {
        if (stream == null) return ""
        val maxChars = if (error) MaxErrorChars else MaxResponseChars
        val output = StringBuilder(minOf(maxChars, 16 * 1024))
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val remaining = maxChars - output.length
                if (count > remaining) {
                    if (error) {
                        output.append(buffer, 0, remaining.coerceAtLeast(0))
                        output.append("\n…上游错误内容已截断")
                        break
                    }
                    throw ElecKoiDataException("模型响应超过安全上限 4 MiB")
                }
                output.append(buffer, 0, count)
            }
        }
        return output.toString()
    }

    private data class RequestTarget(
        val uri: URI,
        val proxy: Proxy?,
    )

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val MaxResponseChars = 4 * 1024 * 1024
        const val MaxErrorChars = 64 * 1024
        const val MaxPromptChars = 48 * 1024
    }
}

private fun String.removeSuffixIgnoringCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

internal fun textCompletionPayload(
    config: ModelConfig,
    systemPrompt: String,
    userPrompt: String,
): JSONObject = JSONObject()
    .put("model", config.model.trim())
    .put("stream", false)
    .put(
        "messages",
        JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt.take(48 * 1024)))
            .put(JSONObject().put("role", "user").put("content", userPrompt.take(48 * 1024))),
    )
    .apply {
        config.configuredMaxOutputTokens()?.let { put("max_tokens", it) }
    }

internal fun textFromCompletionResponse(response: JSONObject): String {
    val choice = response.optJSONArray("choices")?.optJSONObject(0)
    val message = choice?.optJSONObject("message")
    val content = message?.opt("content")
    val text = when (content) {
        is String -> content
        is JSONArray -> buildString {
            content.objects().forEach { part ->
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }
        else -> ""
    }.trim()
    if (text.isNotBlank()) return text

    val finishReason = choice?.optString("finish_reason")
        ?.takeIf(String::isNotBlank)
        ?: "missing"
    val reasoningChars = (message?.opt("reasoning_content") as? String)?.length ?: 0
    val usage = response.optJSONObject("usage")
    val completionTokens = usage
        ?.takeIf { it.has("completion_tokens") && !it.isNull("completion_tokens") }
        ?.optInt("completion_tokens")
    val details = buildList {
        add("finish_reason=$finishReason")
        add("reasoning_chars=$reasoningChars")
        completionTokens?.let { add("completion_tokens=$it") }
    }.joinToString(", ")
    throw ElecKoiDataException("聊天模型没有返回画面提示词（$details）")
}

// Call this before the app sets its own headers, so Authorization and the SSE Accept always win: a
// user experimenting with gateway headers should not be able to break authentication or streaming.
// Malformed entries are skipped rather than thrown, so a half-typed row in the editor cannot make
// every request fail.
internal fun Request.Builder.applyCustomHeaders(config: ModelConfig): Request.Builder {
    config.customHeaders.forEach { (name, value) ->
        val headerName = name.trim()
        if (headerName.isBlank()) return@forEach
        runCatching { header(headerName, value.trim()) }
    }
    return this
}
