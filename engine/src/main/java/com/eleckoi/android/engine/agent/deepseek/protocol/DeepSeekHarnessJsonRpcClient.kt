package com.eleckoi.android.engine.agent.deepseek.protocol

import android.util.Base64
import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshSessionInspection
import com.eleckoi.android.engine.generation.reasoning.DshResolvedModelCapabilities
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class DeepSeekNotification(
    val method: String,
    val params: JsonObject,
)

internal enum class DeepSeekPromptMode(val wireValue: String) {
    FollowUp("followup"),
    Steer("steer"),
}

internal enum class DeepSeekPermissionPreset(val wireValue: String) {
    AskForApproval("ask-for-approval"),
    ApproveForMe("approve-for-me"),
    FullAccess("full-access"),
}

internal enum class DeepSeekApprovalOutcome(val wireValue: String) {
    AllowedOnce("allowed-once"),
    Rejected("rejected"),
    Cancelled("cancelled"),
}

/** Strict newline-delimited JSON-RPC 2.0 client for DeepSeek Harness' SDK runtime. */
internal class DeepSeekHarnessJsonRpcClient(
    private val transport: DeepSeekHarnessTransport,
    private val scope: CoroutineScope,
) {
    private val requestIds = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val _notifications = MutableSharedFlow<DeepSeekNotification>(extraBufferCapacity = 128)
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var readerJob: Job? = null
    val notifications: SharedFlow<DeepSeekNotification> = _notifications.asSharedFlow()
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    suspend fun start(cwd: String, provider: String, model: String, maxTokens: Int?) {
        check(readerJob == null) { "DeepSeek JSON-RPC client 已启动" }
        transport.start()
        readerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                transport.incomingLines.collect(::acceptLine)
            }.onFailure(::failProtocol)
        }
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = InitializeTimeoutMillis,
            operation = "初始化 DeepSeek Harness 协议",
        ) {
            request(
                method = "initialize",
                params = buildJsonObject {
                    put("cwd", cwd)
                    put("provider", provider)
                    put("model", model)
                    maxTokens?.let { put("maxTokens", it) }
                },
            )
        }.jsonObject
        val serverName = (result["serverInfo"] as? JsonObject)
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
        require(serverName == ExpectedServerName) { "DeepSeek Harness 返回了未知的服务身份" }
    }

    suspend fun prompt(
        sessionId: String,
        text: String,
        images: List<AgentInputImage> = emptyList(),
        mode: DeepSeekPromptMode = DeepSeekPromptMode.FollowUp,
        cwd: String,
    ): String {
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "提交消息到 DeepSeek Harness",
        ) {
            request(
                method = "session/prompt",
                params = buildJsonObject {
                    put("sessionId", sessionId)
                    put("mode", mode.wireValue)
                    put("cwd", cwd)
                    put("contentBlocks", buildJsonArray {
                        if (text.isNotBlank()) {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            })
                        }
                        images.forEach { image ->
                            val file = File(image.localPath)
                            require(file.isFile) { "待发送图片不存在：${image.name.ifBlank { file.name }}" }
                            require(file.length() in 1L..MaxUploadImageBytes) {
                                "图片超过 20 MiB：${image.name.ifBlank { file.name }}"
                            }
                            add(buildJsonObject {
                                put("type", "image")
                                put("mimeType", image.mediaType)
                                put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                                image.name.takeIf(String::isNotBlank)?.let { put("name", it) }
                            })
                        }
                    })
                },
            )
        }.jsonObject
        return result["messageId"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的 messageId")
    }

    suspend fun cancel(sessionId: String): Boolean {
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "发送 DeepSeek Harness 停止请求",
        ) {
            request(
                method = "session/cancel",
                params = buildJsonObject { put("sessionId", sessionId) },
            )
        }.jsonObject
        return result["accepted"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的取消确认")
    }

    suspend fun inspectSession(sessionId: String): DshSessionInspection {
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "读取 DeepSeek Harness 会话轨迹",
        ) {
            request(
                method = "session/inspect",
                params = buildJsonObject { put("sessionId", sessionId) },
            )
        }.jsonObject
        val header = result["header"] as? JsonObject
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的会话头")
        val inheritedEventCount = result["inheritedEventCount"]?.jsonPrimitive?.intOrNull
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的继承事件数量")
        val events = result["events"]?.jsonArray?.map { event ->
            event as? JsonObject
                ?: throw DeepSeekProtocolException("DeepSeek Harness 返回了无效的会话事件")
        } ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回会话事件")
        return DshSessionInspection(
            header = header,
            inheritedEventCount = inheritedEventCount,
            events = events,
        )
    }

    suspend fun resolveModel(provider: String, model: String): DshResolvedModelCapabilities {
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "读取 DeepSeek Harness 模型能力",
        ) {
            request(
                method = "model/resolve",
                params = buildJsonObject {
                    put("provider", provider)
                    put("model", model)
                },
            )
        }.jsonObject
        val reasoningEffortIds = (result["reasoning"] as? JsonObject)
            ?.get("efforts")
            ?.jsonArray
            ?.map { effort ->
                effort.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?: throw DeepSeekProtocolException("DeepSeek Harness 返回了无效的推理档位")
            }
            .orEmpty()
        val contextWindow = (result["context"] as? JsonObject)
            ?.get("contextWindow")
            ?.jsonPrimitive
            ?.intOrNull
        val maxTokens = result["defaultMaxTokens"]?.jsonPrimitive?.intOrNull
        val modalities = result["inputModalities"]?.jsonArray?.mapNotNull { modality ->
            modality.jsonPrimitive.contentOrNull
        }
        return DshResolvedModelCapabilities(
            reasoningEffortIds = reasoningEffortIds,
            contextWindowTokens = contextWindow,
            maxOutputTokens = maxTokens,
            supportsImageInput = modalities?.let { "image" in it },
        )
    }

    suspend fun setPermission(
        sessionId: String,
        cwd: String,
        preset: DeepSeekPermissionPreset,
    ): DeepSeekPermissionPreset {
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "更新 DeepSeek Harness 会话权限",
        ) {
            request(
                method = "session/set_permission",
                params = buildJsonObject {
                    put("sessionId", sessionId)
                    put("cwd", cwd)
                    put("preset", preset.wireValue)
                },
            )
        }.jsonObject
        val effective = result["preset"]?.jsonPrimitive?.contentOrNull
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的权限模式")
        return DeepSeekPermissionPreset.entries.firstOrNull { it.wireValue == effective }
            ?: throw DeepSeekProtocolException("DeepSeek Harness 返回了未知的权限模式：$effective")
    }

    suspend fun resolveApproval(requestId: Long, outcome: DeepSeekApprovalOutcome): Boolean {
        require(requestId > 0L) { "DeepSeek 审批请求编号无效" }
        val result = withDeepSeekProtocolTimeout(
            timeoutMillis = PromptAcceptanceTimeoutMillis,
            operation = "提交 DeepSeek Harness 审批结果",
        ) {
            request(
                method = "session/resolve_approval",
                params = buildJsonObject {
                    put("requestId", requestId)
                    put("outcome", outcome.wireValue)
                },
            )
        }.jsonObject
        return result["accepted"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: throw DeepSeekProtocolException("DeepSeek Harness 没有返回有效的审批确认")
    }

    suspend fun shutdown() {
        if (readerJob == null) return
        withTimeoutOrNull(ShutdownTimeoutMillis) {
            runCatching { request("shutdown", null) }
        }
        transport.stop()
        readerJob?.cancel()
        readerJob = null
        failPending(DeepSeekProtocolException("DeepSeek JSON-RPC client 已关闭"))
    }

    private suspend fun request(method: String, params: JsonObject?): JsonElement {
        val id = requestIds.incrementAndGet()
        val deferred = CompletableDeferred<JsonElement>()
        check(pending.putIfAbsent(id, deferred) == null) { "DeepSeek JSON-RPC 请求编号重复" }
        try {
            transport.sendLine(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    params?.let { put("params", it) }
                }.toString(),
            )
            return deferred.await()
        } finally {
            pending.remove(id, deferred)
        }
    }

    private suspend fun acceptLine(line: String) {
        val message = runCatching { ProtocolJson.parseToJsonElement(line).jsonObject }
            .getOrElse { throw DeepSeekProtocolException("DeepSeek Harness 返回了无效 JSON-RPC", it) }
        require(message["jsonrpc"]?.jsonPrimitive?.content == "2.0") { "DeepSeek JSON-RPC 版本无效" }
        val method = message["method"]?.jsonPrimitive?.contentOrNull
        if (method != null) {
            val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
            _notifications.emit(DeepSeekNotification(method, params))
            return
        }
        val id = message["id"]?.jsonPrimitive?.longOrNull
            ?: throw DeepSeekProtocolException("DeepSeek JSON-RPC 响应缺少请求编号")
        val deferred = pending[id] ?: return
        val error = message["error"] as? JsonObject
        if (error != null) {
            val detail = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
            deferred.completeExceptionally(DeepSeekProtocolException(detail))
        } else {
            deferred.complete(message["result"] ?: JsonObject(emptyMap()))
        }
    }

    private fun failProtocol(error: Throwable) {
        val normalized = error as? DeepSeekProtocolException
            ?: DeepSeekProtocolException(error.message ?: "DeepSeek Harness 协议连接失败", error)
        failPending(normalized)
        _failures.tryEmit(normalized.message.orEmpty())
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private companion object {
        val ProtocolJson = Json { ignoreUnknownKeys = false }
        const val InitializeTimeoutMillis = 20_000L
        const val PromptAcceptanceTimeoutMillis = 20_000L
        const val ShutdownTimeoutMillis = 3_000L
        const val MaxUploadImageBytes: Long = 20L * 1024L * 1024L
        const val ExpectedServerName = "deepseek-harness-sdk-runtime"
    }
}
