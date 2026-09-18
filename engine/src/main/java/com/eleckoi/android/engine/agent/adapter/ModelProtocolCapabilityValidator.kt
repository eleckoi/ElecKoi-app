package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.api.AgentErrorCode
import com.eleckoi.android.engine.agent.api.AgentException
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.effectiveApiFormat
import com.eleckoi.android.foundation.network.SensitiveTextSanitizer
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Verifies the selected model protocol directly against its configured upstream.
 *
 * The probe deliberately reuses [AdapterUpstreamClient], so endpoint resolution, authentication,
 * proxy handling and custom headers stay identical to the official DSH provider-wire path. It
 * never starts the local Harness server and never translates one provider protocol into another.
 */
internal class ModelProtocolCapabilityValidator {
    suspend fun verify(config: ModelConfig): Unit = withContext(Dispatchers.IO) {
        try {
            when (config.effectiveApiFormat()) {
                ModelApiFormat.Responses -> verifyResponses(config)
                ModelApiFormat.ChatCompletions -> verifyChatCompletions(config)
                ModelApiFormat.AnthropicMessages -> verifyAnthropicMessages(config)
                ModelApiFormat.GoogleGemini -> verifyGoogleGemini(config)
            }
        } catch (error: AgentException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw AgentException(
                AgentErrorCode.InvalidEndpoint,
                "模型接口配置无效：${error.message.orEmpty()}",
                cause = error,
            )
        } catch (error: IOException) {
            throw AgentException(
                AgentErrorCode.NetworkError,
                "无法连接模型接口：${SensitiveTextSanitizer.sanitize(error.message.orEmpty(), config.apiKey)}",
                cause = error,
            )
        }
    }

    private fun verifyResponses(config: ModelConfig) {
        val tool = JSONObject()
            .put("type", "function")
            .put("name", ProbeToolName)
            .put("description", ProbeToolDescription)
            .put("parameters", probeParameters())
        val userMessage = responsesMessage("user", ProbePrompt)
        val first = postJson(
            config = config,
            format = ProviderWireFormat.Responses,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put("instructions", FirstInstruction)
                .put("input", JSONArray().put(userMessage))
                .put("tools", JSONArray().put(tool)),
        )
        val output = first.optJSONArray("output") ?: protocolFailure("Responses 没有返回 output。")
        val reasoning = output.firstObject { it.optString("type") == "reasoning" }
        val call = output.firstObject { it.optString("type") == "function_call" }
            ?: toolsUnsupported("模型没有返回标准 function_call。")
        assertProbeCall(call.opt("name"), call.opt("arguments"))
        val callId = call.optString("call_id").ifBlank {
            toolsUnsupported("工具调用缺少 call_id。")
        }

        val secondInput = JSONArray().put(userMessage)
        reasoning?.let(secondInput::put)
        secondInput.put(call)
        secondInput.put(
            JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", ProbeResult),
        )
        val second = postJson(
            config = config,
            format = ProviderWireFormat.Responses,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put("instructions", SecondInstruction)
                .put("input", secondInput),
        )
        val secondOutput = second.optJSONArray("output")
            ?: toolsUnsupported("工具结果回传后没有返回 output。")
        if (secondOutput.firstObject { it.optString("type") == "message" } == null) {
            toolsUnsupported("工具结果回传后没有返回 assistant 消息。")
        }
    }

    private fun verifyChatCompletions(config: ModelConfig) {
        val tool = JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", ProbeToolName)
                    .put("description", ProbeToolDescription)
                    .put("parameters", probeParameters()),
            )
        val userMessage = JSONObject().put("role", "user").put("content", ProbePrompt)
        val first = postJson(
            config = config,
            format = ProviderWireFormat.ChatCompletions,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put("messages", JSONArray().put(userMessage))
                .put("tools", JSONArray().put(tool))
                .put("max_tokens", ProbeMaxTokens),
        )
        val message = firstChoiceMessage(first)
        val call = message.optJSONArray("tool_calls")?.optJSONObject(0)
            ?: toolsUnsupported("模型没有返回标准 tool_calls。")
        val function = call.optJSONObject("function")
            ?: toolsUnsupported("工具调用缺少 function。")
        assertProbeCall(function.opt("name"), function.opt("arguments"))
        val callId = call.optString("id").ifBlank {
            toolsUnsupported("工具调用缺少 tool_call_id。")
        }

        val assistant = JSONObject()
            .put("role", "assistant")
            .put("content", message.opt("content") ?: JSONObject.NULL)
            .put("tool_calls", JSONArray().put(call))
        message.optString("reasoning_content").takeIf(String::isNotBlank)?.let {
            assistant.put("reasoning_content", it)
        }
        val second = postJson(
            config = config,
            format = ProviderWireFormat.ChatCompletions,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put(
                    "messages",
                    JSONArray()
                        .put(userMessage)
                        .put(assistant)
                        .put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", callId)
                                .put("name", ProbeToolName)
                                .put("content", ProbeResult),
                        ),
                )
                .put("max_tokens", ProbeMaxTokens),
        )
        val finalMessage = firstChoiceMessage(second)
        if ((finalMessage.optJSONArray("tool_calls")?.length() ?: 0) > 0) {
            toolsUnsupported("工具结果回传后模型仍返回工具调用。")
        }
        val content = finalMessage.opt("content")
        if (content !is String && content !is JSONArray) {
            toolsUnsupported("工具结果回传后没有返回 assistant 消息。")
        }
    }

    private fun verifyAnthropicMessages(config: ModelConfig) {
        val tool = JSONObject()
            .put("name", ProbeToolName)
            .put("description", ProbeToolDescription)
            .put("input_schema", probeParameters())
        val first = postJson(
            config = config,
            format = ProviderWireFormat.AnthropicMessages,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put("max_tokens", ProbeMaxTokens)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", ProbePrompt)),
                )
                .put("tools", JSONArray().put(tool))
                .put(
                    "tool_choice",
                    JSONObject().put("type", "tool").put("name", ProbeToolName),
                ),
        )
        val call = first.optJSONArray("content")
            ?.firstObject { it.optString("type") == "tool_use" }
            ?: toolsUnsupported("模型没有返回标准 tool_use。")
        assertProbeCall(call.opt("name"), call.opt("input"))
        val callId = call.optString("id").ifBlank {
            toolsUnsupported("工具调用缺少 tool_use_id。")
        }
        val second = postJson(
            config = config,
            format = ProviderWireFormat.AnthropicMessages,
            payload = JSONObject()
                .put("model", config.model.trim())
                .put("max_tokens", ProbeMaxTokens)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "user").put("content", ProbePrompt))
                        .put(
                            JSONObject().put("role", "assistant").put(
                                "content",
                                JSONArray().put(
                                    JSONObject()
                                        .put("type", "tool_use")
                                        .put("id", callId)
                                        .put("name", ProbeToolName)
                                        .put("input", JSONObject().put("value", "ok")),
                                ),
                            ),
                        )
                        .put(
                            JSONObject().put("role", "user").put(
                                "content",
                                JSONArray().put(
                                    JSONObject()
                                        .put("type", "tool_result")
                                        .put("tool_use_id", callId)
                                        .put("content", ProbeResult),
                                ),
                            ),
                        ),
                ),
        )
        if (second.optJSONArray("content")
                ?.firstObject { it.optString("type") == "text" } == null
        ) {
            toolsUnsupported("工具结果回传后没有返回 assistant 文本。")
        }
    }

    private fun verifyGoogleGemini(config: ModelConfig) {
        val declaration = JSONObject()
            .put("name", ProbeToolName)
            .put("description", ProbeToolDescription)
            .put("parameters", googleProbeParameters())
        val tools = JSONArray().put(
            JSONObject().put("functionDeclarations", JSONArray().put(declaration)),
        )
        val first = postJson(
            config = config,
            format = ProviderWireFormat.GoogleGemini,
            payload = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", ProbePrompt))),
                    ),
                )
                .put("tools", tools)
                .put("generationConfig", JSONObject().put("maxOutputTokens", ProbeMaxTokens)),
        )
        val content = first.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
            ?: toolsUnsupported("接口没有返回候选消息。")
        val call = content.optJSONArray("parts")
            ?.firstObject { it.optJSONObject("functionCall") != null }
            ?.optJSONObject("functionCall")
            ?: toolsUnsupported("模型没有返回标准 functionCall。")
        assertProbeCall(call.opt("name"), call.opt("args"))
        val callId = call.optString("id")
        val functionResponse = JSONObject()
            .put("name", ProbeToolName)
            .put("response", JSONObject().put("accepted", true))
        if (callId.isNotBlank()) functionResponse.put("id", callId)
        val second = postJson(
            config = config,
            format = ProviderWireFormat.GoogleGemini,
            payload = JSONObject()
                .put(
                    "contents",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", ProbePrompt))),
                        )
                        .put(content)
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("functionResponse", functionResponse)),
                                ),
                        ),
                )
                .put("tools", tools)
                .put("generationConfig", JSONObject().put("maxOutputTokens", ProbeMaxTokens)),
        )
        val finalParts = second.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        if (finalParts?.firstObject { it.opt("text") is String } == null) {
            toolsUnsupported("工具结果回传后没有返回模型文本。")
        }
    }

    private fun postJson(
        config: ModelConfig,
        format: ProviderWireFormat,
        payload: JSONObject,
    ): JSONObject {
        AdapterUpstreamClient.openNativeJsonCall(
            payload = payload.toString().toByteArray(Charsets.UTF_8),
            modelConfig = config,
            format = format,
        ).execute().use { response ->
            val responseBody = response.body
            val contentLength = responseBody?.contentLength() ?: 0L
            if (contentLength > MaxResponseBytes) protocolFailure("工具调用测试响应超过安全上限。")
            val text = if (responseBody == null) {
                ""
            } else {
                val source = responseBody.source()
                if (source.request(MaxResponseBytes + 1L)) {
                    protocolFailure("工具调用测试响应超过安全上限。")
                }
                source.readUtf8()
            }
            if (!response.isSuccessful) {
                val detail = safeProviderMessage(text, config.apiKey)
                throw AgentException(
                    AgentErrorCode.HttpError,
                    "工具调用测试失败（HTTP ${response.code}）${detail.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}",
                    httpStatus = response.code,
                )
            }
            return try {
                JSONObject(text)
            } catch (error: JSONException) {
                throw AgentException(
                    AgentErrorCode.ProtocolError,
                    "工具调用测试返回的不是有效 JSON 对象。",
                    cause = error,
                )
            }
        }
    }

    private fun firstChoiceMessage(payload: JSONObject): JSONObject =
        payload.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: toolsUnsupported("接口没有返回 assistant 消息。")

    private fun assertProbeCall(name: Any?, arguments: Any?) {
        if (name?.toString() != ProbeToolName) {
            toolsUnsupported("模型没有调用指定的测试工具。")
        }
        val value = when (arguments) {
            is JSONObject -> arguments.optString("value")
            is String -> runCatching { JSONObject(arguments).optString("value") }.getOrNull()
            else -> null
        }
        if (value != "ok") toolsUnsupported("工具参数不是要求的 {\"value\":\"ok\"}。")
    }

    private fun probeParameters(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put(
                "value",
                JSONObject().put("type", "string").put("enum", JSONArray().put("ok")),
            ),
        )
        .put("required", JSONArray().put("value"))
        .put("additionalProperties", false)

    private fun googleProbeParameters(): JSONObject = JSONObject()
        .put("type", "OBJECT")
        .put(
            "properties",
            JSONObject().put(
                "value",
                JSONObject().put("type", "STRING").put("enum", JSONArray().put("ok")),
            ),
        )
        .put("required", JSONArray().put("value"))

    private fun responsesMessage(role: String, text: String): JSONObject = JSONObject()
        .put("type", "message")
        .put("role", role)
        .put(
            "content",
            JSONArray().put(
                JSONObject()
                    .put("type", if (role == "assistant") "output_text" else "input_text")
                    .put("text", text),
            ),
        )

    private fun safeProviderMessage(raw: String, apiKey: String): String {
        var current = raw.trim()
        repeat(MaxNestedErrorDepth) {
            val payload = runCatching { JSONObject(current) }.getOrNull()
                ?: return SensitiveTextSanitizer.sanitize(current, apiKey).take(MaxErrorChars)
            val error = payload.opt("error")
            val next = when (error) {
                null -> payload.optString("message")
                is JSONObject -> error.optString("message")
                is String -> error
                else -> payload.optString("message")
            }.trim()
            if (next.isBlank() || next == current) {
                return SensitiveTextSanitizer.sanitize(current, apiKey).take(MaxErrorChars)
            }
            current = next
        }
        return SensitiveTextSanitizer.sanitize(current, apiKey).take(MaxErrorChars)
    }

    private fun JSONArray.firstObject(predicate: (JSONObject) -> Boolean): JSONObject? {
        for (index in 0 until length()) {
            val value = optJSONObject(index) ?: continue
            if (predicate(value)) return value
        }
        return null
    }

    private fun toolsUnsupported(message: String): Nothing = throw AgentException(
        AgentErrorCode.ToolsUnsupported,
        "当前接口不能完整支持 Agent 工具调用：$message",
    )

    private fun protocolFailure(message: String): Nothing = throw AgentException(
        AgentErrorCode.ProtocolError,
        "Agent 接口能力检测失败：$message",
    )

    private companion object {
        const val ProbeToolName = "eleckoi_capability_probe"
        const val ProbeToolDescription = "Return the exact protocol probe value."
        const val ProbePrompt = "Call eleckoi_capability_probe exactly once with value ok."
        const val ProbeResult = "{\"accepted\":true}"
        const val FirstInstruction =
            "This is a protocol check. You must call the supplied capability tool exactly once."
        const val SecondInstruction = "Acknowledge the tool result with a short text response."
        const val ProbeMaxTokens = 64
        const val MaxResponseBytes = 4L * 1024L * 1024L
        const val MaxErrorChars = 2_000
        const val MaxNestedErrorDepth = 4
    }
}
