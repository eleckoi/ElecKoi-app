package com.eleckoi.android.engine.creator.plugins

import com.eleckoi.android.engine.generation.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Independent supporting generations do not create chat turns or run character tools. */
class PluginGenerationClient {
    private val running = ConcurrentHashMap<String, Call>()

    fun cancel(id: String): Boolean = running[id]?.let { it.cancel(); true } ?: false

    suspend fun invoke(config: ModelConfig, params: JsonObject, onDelta: suspend (String, String) -> Unit): JsonObject = withContext(Dispatchers.IO) {
        val id = params.getValue("id").jsonPrimitive.content
        val format = config.effectiveApiFormat()
        val messages = params.getValue("messages").jsonArray
        val stream = params["stream"]?.jsonPrimitive?.booleanOrNull ?: false
        val base = config.resolvedProviderBaseUrl()
        val model = params["model"]?.jsonPrimitive?.contentOrNull ?: config.model
        val body = requestBody(format, model, messages, params, stream)
        val url = when (format) {
            ModelApiFormat.ChatCompletions -> "$base/chat/completions"
            ModelApiFormat.Responses -> "$base/responses"
            ModelApiFormat.AnthropicMessages -> "$base/messages"
            ModelApiFormat.GoogleGemini -> "$base/models/$model:${if (stream) "streamGenerateContent?alt=sse" else "generateContent"}"
        }
        val request = Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).apply {
            when (format) {
                ModelApiFormat.AnthropicMessages -> { header("x-api-key", config.apiKey); header("anthropic-version", "2023-06-01") }
                ModelApiFormat.GoogleGemini -> header("x-goog-api-key", config.apiKey)
                else -> header("Authorization", "Bearer ${config.apiKey}")
            }
            config.customHeaders.forEach { (name, value) -> header(name, value) }
        }.build()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).apply {
            if (config.proxyUrl.isNotBlank()) {
                val proxy = URI(config.proxyUrl)
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, if (proxy.port < 0) 8080 else proxy.port)))
            }
        }.build()
        val call = client.newCall(request)
        check(running.putIfAbsent(id, call) == null) { "生成 id 已在运行：$id" }
        val cancellation = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                val responseBody = checkNotNull(response.body) { "模型响应缺少 body" }
                check(response.isSuccessful) { "模型 HTTP ${response.code}: ${responseBody.string()}" }
                val text = StringBuilder(); val reasoning = StringBuilder()
                if (stream) {
                    val reader = responseBody.charStream().buffered()
                    val data = StringBuilder()
                    suspend fun consume() {
                        if (data.isEmpty()) return
                        val value = data.toString(); data.clear()
                        if (value == "[DONE]") return
                        val packet = Json.parseToJsonElement(value).jsonObject
                        packet["error"]?.let { error("模型流错误：$it") }
                        val (delta, thought) = extract(packet, format, true)
                        text.append(delta); reasoning.append(thought)
                        if (delta.isNotEmpty() || thought.isNotEmpty()) onDelta(delta, thought)
                    }
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) consume() else if (line.startsWith("data:")) {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                    }
                    consume()
                } else {
                    val (result, thought) = extract(Json.parseToJsonElement(responseBody.string()).jsonObject, format, false)
                    text.append(result); reasoning.append(thought)
                }
                buildJsonObject { put("id", id); put("content", text.toString()); put("reasoning", reasoning.toString()); put("model", model) }
            }
        } finally { running.remove(id, call); cancellation.cancel() }
    }

    companion object {
        fun requestBody(format: ModelApiFormat, model: String, messages: JsonArray, params: JsonObject, stream: Boolean): JsonObject {
            val system = messages.filter { it.jsonObject["role"]?.jsonPrimitive?.content == "system" }
                .joinToString("\n\n") { it.jsonObject.getValue("content").jsonPrimitive.content }
            val dialogue = JsonArray(messages.filterNot { it.jsonObject["role"]?.jsonPrimitive?.content == "system" })
            return buildJsonObject {
                if (format != ModelApiFormat.GoogleGemini) put("model", model)
                when (format) {
                    ModelApiFormat.ChatCompletions -> { put("messages", messages); put("stream", stream) }
                    ModelApiFormat.Responses -> { put("input", messages); put("stream", stream) }
                    ModelApiFormat.AnthropicMessages -> { put("system", system); put("messages", dialogue); put("stream", stream); put("max_tokens", params["maxTokens"] ?: JsonPrimitive(4096)) }
                    ModelApiFormat.GoogleGemini -> {
                        put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
                        put("contents", buildJsonArray { dialogue.forEach { add(buildJsonObject {
                            put("role", if (it.jsonObject["role"]?.jsonPrimitive?.content == "assistant") "model" else "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", it.jsonObject.getValue("content")) }) })
                        }) } })
                    }
                }
                if (params["responseFormat"]?.jsonPrimitive?.contentOrNull == "json") when (format) {
                    ModelApiFormat.ChatCompletions -> put("response_format", buildJsonObject { put("type", "json_object") })
                    ModelApiFormat.Responses -> put("text", buildJsonObject { put("format", buildJsonObject { put("type", "json_object") }) })
                    ModelApiFormat.GoogleGemini -> put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
                    else -> Unit
                }
                (params["parameters"] as? JsonObject)?.forEach { (key, value) -> put(key, value) }
            }
        }

        private fun extract(packet: JsonObject, format: ModelApiFormat, stream: Boolean): Pair<String, String> {
            fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            return when (format) {
                ModelApiFormat.ChatCompletions -> {
                    val choice = (packet["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                    val message = choice?.get(if (stream) "delta" else "message")?.jsonObject
                    (message?.text("content").orEmpty()) to (message?.text("reasoning_content").orEmpty())
                }
                ModelApiFormat.Responses -> if (stream) {
                    when (packet.text("type")) {
                        "response.output_text.delta" -> packet.text("delta") to ""
                        "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> "" to packet.text("delta")
                        "response.failed", "error" -> error("模型流失败：$packet")
                        else -> "" to ""
                    }
                } else {
                    val output = (packet["output"] as? JsonArray).orEmpty()
                    output.flatMap { (it.jsonObject["content"] as? JsonArray).orEmpty() }.joinToString("") { it.jsonObject.text("text") } to
                        output.flatMap { (it.jsonObject["summary"] as? JsonArray).orEmpty() }.joinToString("") { it.jsonObject.text("text") }
                }
                ModelApiFormat.AnthropicMessages -> if (stream) {
                    val delta = (packet["delta"] as? JsonObject) ?: buildJsonObject {}
                    delta.text("text") to delta.text("thinking")
                } else {
                    val parts = (packet["content"] as? JsonArray).orEmpty()
                    parts.joinToString("") { it.jsonObject.text("text") } to parts.joinToString("") { it.jsonObject.text("thinking") }
                }
                ModelApiFormat.GoogleGemini -> {
                    val parts = (packet["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts") as? JsonArray
                    parts.orEmpty().filterNot { it.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull == true }.joinToString("") { it.jsonObject.text("text") } to
                        parts.orEmpty().filter { it.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull == true }.joinToString("") { it.jsonObject.text("text") }
                }
            }
        }
    }
}
