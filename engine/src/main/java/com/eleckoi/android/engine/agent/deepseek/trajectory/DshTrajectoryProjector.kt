package com.eleckoi.android.engine.agent.deepseek.trajectory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class DshTrajectoryProjection(
    val records: List<DshTrajectoryRecord>,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
)

/** Projects DSH's append-only event vocabulary into the same trajectory model used by PC. */
object DshTrajectoryProjector {
    fun project(
        input: List<JsonObject>,
        header: JsonObject = JsonObject(emptyMap()),
    ): DshTrajectoryProjection {
        val events = input.mapIndexed(::normalizeEvent).sortedBy(NormalizedEvent::seq)
        val requestNumbers = events
            .filter { event -> event.type == "step/start" || event.type == "compaction/start" }
            .mapIndexed { index, event -> event.seq to index + 1 }
            .toMap()
        val records = mutableListOf<MutableRecord>()
        val stepStarts = mutableMapOf<String, Long>()
        val toolRecords = mutableMapOf<String, MutableRecord>()
        val subtoolRecords = mutableMapOf<String, MutableRecord>()
        val compactionRecords = mutableMapOf<String, MutableRecord>()
        val approvalRecords = mutableMapOf<String, MutableRecord>()
        val pendingRequests = mutableListOf<PendingRequest>()
        var currentRequestHeader: RequestHeader? = null
        var activeTurn: Int? = null
        var activeStep: Int? = null

        fun attachRequests(item: MutableRecord, turn: Int?, step: Int?, completedAt: Long?) {
            pendingRequests
                .filter { pending ->
                    !pending.attached && pending.request.turn == turn && pending.request.step == step
                }
                .forEach { pending ->
                    pending.attached = true
                    pending.request.status = if (item.status == DshTrajectoryRecordStatus.Error) {
                        DshTrajectoryRecordStatus.Error
                    } else {
                        DshTrajectoryRecordStatus.Complete
                    }
                    pending.request.durationMillis = duration(pending.request.timeMillis, completedAt)
                    item.requests += pending.request
                }
        }

        events.forEach { event ->
            val data = event.data
            val type = event.type
            val eventTurn = data.positiveInt("turn")
            val eventStep = data.positiveInt("step")
            val time = event.time

            when (type) {
                "turn/start" -> {
                    activeTurn = eventTurn
                    activeStep = null
                    return@forEach
                }

                "step/start" -> {
                    activeTurn = eventTurn ?: activeTurn
                    activeStep = eventStep
                    val turn = activeTurn
                    val step = activeStep
                    if (turn != null && step != null) {
                        if (time != null) stepStarts[stepKey(turn, step)] = time
                        val headerValue = currentRequestHeader
                        pendingRequests += PendingRequest(
                            request = MutableRequest(
                                number = requestNumbers.getValue(event.seq),
                                seq = event.seq,
                                turn = turn,
                                step = step,
                                status = DshTrajectoryRecordStatus.Running,
                                reason = headerValue?.reason ?: "step/start",
                                provider = headerValue?.provider.orEmpty(),
                                model = headerValue?.model.orEmpty(),
                                detail = headerValue?.detail ?: pretty(data),
                                rawJson = pretty(event.raw),
                                timeMillis = time,
                                durationMillis = null,
                            ),
                        )
                    }
                    return@forEach
                }

                "step/end" -> {
                    activeStep = null
                    return@forEach
                }

                "turn/end" -> {
                    activeTurn = null
                    activeStep = null
                    return@forEach
                }
            }

            val turn = eventTurn ?: activeTurn
            val step = eventStep ?: activeStep
            when (type) {
                "request/header" -> {
                    val requestHeader = data.objectValue("header")
                    val visibleRequestHeader = requestHeader.withoutKey("system")
                    val reason = data.text("reason")
                    val config = visibleRequestHeader.objectValue("config")
                    val activeRequestHeader = RequestHeader(
                        reason = reason.ifBlank { "request/header" },
                        provider = config.text("provider"),
                        model = config.text("model"),
                        detail = pretty(visibleRequestHeader),
                    )
                    currentRequestHeader = activeRequestHeader
                    pendingRequests.asReversed().firstOrNull { pending ->
                        !pending.attached && pending.request.turn == turn && pending.request.step == step
                    }?.request?.let { request ->
                        request.reason = activeRequestHeader.reason
                        request.provider = activeRequestHeader.provider
                        request.model = activeRequestHeader.model
                        request.detail = activeRequestHeader.detail
                        request.rawJson = pretty(
                            buildJsonArray {
                                add(parseJson(request.rawJson))
                                add(event.raw.withRequestHeader(visibleRequestHeader))
                            },
                        )
                    }
                }

                "user/message" -> {
                    val message = messageFrom(data)
                    val messageSource = message.objectValue("source")
                    if (messageSource.text("plugin") in ElecKoiInternalContextPlugins) return@forEach
                    val sourceKind = messageSource.text("kind")
                    val content = contentText(message["content"])
                    val checkpointCompactionId = messageSource.text("compactionId")
                        .takeIf {
                            sourceKind == "plugin" &&
                                messageSource.text("plugin") == DshCompactionCheckpointPlugin
                        }
                    if (checkpointCompactionId != null) {
                        val compaction = compactionRecords[checkpointCompactionId]
                        if (compaction != null) {
                            // DSH persists the completed summary twice on purpose: once as the
                            // compaction lifecycle result and once as the checkpoint user message
                            // that replaces the compacted surface. They are one logical operation,
                            // so retain the checkpoint as raw provenance instead of a second row.
                            compaction.rawJson = appendRaw(compaction.rawJson, event.raw)
                            return@forEach
                        }
                    }
                    val kind = if (sourceKind.isBlank() || sourceKind == "user") {
                        DshTrajectoryRecordKind.User
                    } else {
                        DshTrajectoryRecordKind.Context
                    }
                    records += baseRecord(
                        event = event,
                        kind = kind,
                        title = if (kind == DshTrajectoryRecordKind.User) "用户消息" else contextTitle(sourceKind),
                        preview = preview(content),
                        source = sourceKind.ifBlank { "user" },
                        input = content,
                        detail = pretty(message),
                        turn = turn,
                        step = step,
                    )
                }

                "assistant/message" -> {
                    val message = messageFrom(data)
                    val content = contentText(message["content"])
                    val source = message.objectValue("source")
                    val item = baseRecord(
                        event = event,
                        kind = DshTrajectoryRecordKind.Assistant,
                        title = "助手消息",
                        preview = preview(content.ifBlank { assistantFallback(message["content"]) }),
                        source = listOf(source.text("provider"), source.text("model"))
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .ifBlank { "model" },
                        output = content,
                        detail = pretty(
                            buildJsonObject {
                                put("message", message)
                                data["usage"]?.let { put("usage", it) }
                            },
                        ),
                        durationMillis = if (turn != null && step != null) {
                            duration(stepStarts[stepKey(turn, step)], time)
                        } else {
                            null
                        },
                        turn = turn,
                        step = step,
                    )
                    attachRequests(item, turn, step, time)
                    records += item
                }

                "tool/call" -> {
                    val callId = data.text("callId")
                    if (callId.isBlank()) return@forEach
                    val name = data.text("name").ifBlank { "tool" }
                    val inputText = prettyValue(data["arguments"])
                    val item = baseRecord(
                        event = event,
                        kind = DshTrajectoryRecordKind.Tool,
                        title = name,
                        preview = preview(toolTarget(data["arguments"]).ifBlank { inputText.ifBlank { name } }),
                        source = callId,
                        input = inputText,
                        detail = pretty(data),
                        turn = turn,
                        step = step,
                        status = DshTrajectoryRecordStatus.Running,
                    )
                    attachRequests(item, turn, step, time)
                    toolRecords[callId] = item
                    records += item
                }

                "tool/result" -> {
                    val message = messageFrom(data)
                    val source = message.objectValue("source")
                    val resultBlock = (message["content"] as? JsonArray)
                        ?.mapNotNull { it as? JsonObject }
                        ?.firstOrNull { it.text("type") == "tool-result" }
                    val callId = source.text("callId").ifBlank {
                        resultBlock?.text("toolCallId").orEmpty()
                    }
                    val output = contentText(resultBlock?.get("content") ?: message["content"])
                    val item = callId.takeIf(String::isNotBlank)?.let(toolRecords::get)
                    if (item != null) {
                        item.output = output
                        item.status = if (
                            resultBlock?.boolean("isError") == true || data.containsKey("error")
                        ) {
                            DshTrajectoryRecordStatus.Error
                        } else {
                            DshTrajectoryRecordStatus.Complete
                        }
                        item.durationMillis = duration(item.timeMillis, time)
                        item.detail = pretty(
                            buildJsonObject {
                                put("call", parseJson(item.input))
                                put("result", data)
                            },
                        )
                        item.rawJson = pretty(
                            buildJsonArray {
                                add(parseJson(item.rawJson))
                                add(event.raw)
                            },
                        )
                    } else {
                        records += baseRecord(
                            event = event,
                            kind = DshTrajectoryRecordKind.Tool,
                            title = "工具结果",
                            preview = preview(output),
                            source = callId.ifBlank { "tool" },
                            output = output,
                            detail = pretty(data),
                            turn = turn,
                            step = step,
                            status = if (resultBlock?.boolean("isError") == true) {
                                DshTrajectoryRecordStatus.Error
                            } else {
                                DshTrajectoryRecordStatus.Complete
                            },
                        )
                    }
                }

                "tool/code-dispatch-start" -> {
                    val callId = data.text("subCallId")
                    if (callId.isBlank()) return@forEach
                    val name = data.text("name").ifBlank { "subtool" }
                    val inputText = prettyValue(data["arguments"])
                    val item = baseRecord(
                        event = event,
                        kind = DshTrajectoryRecordKind.Tool,
                        title = name,
                        preview = preview(toolTarget(data["arguments"]).ifBlank { inputText.ifBlank { name } }),
                        source = callId,
                        input = inputText,
                        detail = pretty(data),
                        turn = turn,
                        step = step,
                        status = DshTrajectoryRecordStatus.Running,
                    )
                    subtoolRecords[callId] = item
                    records += item
                }

                "tool/code-dispatch" -> {
                    val callId = data.text("subCallId")
                    subtoolRecords[callId]?.let { item ->
                        item.output = contentText(data["content"])
                        item.status = if (data.boolean("isError") == true) {
                            DshTrajectoryRecordStatus.Error
                        } else {
                            DshTrajectoryRecordStatus.Complete
                        }
                        item.durationMillis = duration(item.timeMillis, time)
                        item.detail = pretty(data)
                        item.rawJson = appendRaw(item.rawJson, event.raw)
                    }
                }

                "compaction/start" -> {
                    val id = data.text("compactionId").ifBlank { event.seq.toString() }
                    val item = baseRecord(
                        event = event,
                        kind = DshTrajectoryRecordKind.Compaction,
                        title = "上下文压缩",
                        preview = "正在压缩上下文",
                        source = id,
                        detail = pretty(data),
                        turn = turn,
                        step = step,
                        status = DshTrajectoryRecordStatus.Running,
                    )
                    item.requests += MutableRequest(
                        number = requestNumbers.getValue(event.seq),
                        seq = event.seq,
                        turn = turn,
                        step = null,
                        status = DshTrajectoryRecordStatus.Running,
                        reason = "compaction",
                        provider = "",
                        model = "",
                        detail = pretty(data),
                        rawJson = pretty(event.raw),
                        timeMillis = time,
                        durationMillis = null,
                    )
                    compactionRecords[id] = item
                    records += item
                }

                "compaction/summary" -> {
                    compactionRecords[data.text("compactionId")]?.let { item ->
                        item.output = contentText(data["summary"])
                        item.preview = preview(item.output.ifBlank { "上下文摘要已生成" })
                        item.detail = pretty(data)
                        item.rawJson = appendRaw(item.rawJson, event.raw)
                    }
                }

                "compaction/end" -> {
                    compactionRecords[data.text("compactionId")]?.let { item ->
                        item.status = if (data.containsKey("error")) {
                            DshTrajectoryRecordStatus.Error
                        } else {
                            DshTrajectoryRecordStatus.Complete
                        }
                        item.durationMillis = duration(item.timeMillis, time)
                        item.detail = pretty(data)
                        item.rawJson = appendRaw(item.rawJson, event.raw)
                        item.requests.forEach { request ->
                            request.status = item.status
                            request.durationMillis = item.durationMillis
                            request.detail = item.detail
                            request.rawJson = item.rawJson
                        }
                    }
                }

                "approval/asked" -> {
                    val id = data.text("id")
                    if (id.isBlank()) return@forEach
                    val item = baseRecord(
                        event = event,
                        kind = DshTrajectoryRecordKind.Tool,
                        title = "授权请求",
                        preview = preview(data.text("reason")),
                        source = data.text("toolName").ifBlank { id },
                        input = pretty(data),
                        detail = pretty(data),
                        turn = turn,
                        step = step,
                        status = DshTrajectoryRecordStatus.Running,
                    )
                    approvalRecords[id] = item
                    records += item
                }

                "approval/decided" -> {
                    approvalRecords[data.text("id")]?.let { item ->
                        item.status = when (data.text("outcome")) {
                            "allowed-once" -> DshTrajectoryRecordStatus.Complete
                            "cancelled" -> DshTrajectoryRecordStatus.Cancelled
                            else -> DshTrajectoryRecordStatus.Error
                        }
                        item.output = pretty(data)
                        item.durationMillis = duration(item.timeMillis, time)
                        item.detail = pretty(data)
                        item.rawJson = appendRaw(item.rawJson, event.raw)
                    }
                }
            }
        }

        val times = events.mapNotNull(NormalizedEvent::time)
        val createdAt = header.nonnegativeLong("createdAt")
        return DshTrajectoryProjection(
            records = records.mapIndexed { index, item -> item.toModel(index + 1) },
            startedAtMillis = createdAt ?: times.firstOrNull(),
            completedAtMillis = times.lastOrNull() ?: createdAt,
        )
    }

    private fun normalizeEvent(index: Int, event: JsonObject) = NormalizedEvent(
        type = event.text("type").ifBlank { "unknown" },
        seq = event.nonnegativeLong("seq") ?: index.toLong(),
        time = event.nonnegativeLong("time"),
        data = event.objectValue("data"),
        raw = event,
    )

    private fun baseRecord(
        event: NormalizedEvent,
        kind: DshTrajectoryRecordKind,
        title: String,
        preview: String,
        source: String,
        input: String = "",
        output: String = "",
        detail: String,
        durationMillis: Long? = null,
        turn: Int?,
        step: Int?,
        status: DshTrajectoryRecordStatus = DshTrajectoryRecordStatus.Complete,
    ) = MutableRecord(
        id = "${event.type}:${event.seq}",
        seq = event.seq,
        type = event.type,
        kind = kind,
        title = title,
        preview = preview,
        source = source,
        input = input,
        output = output,
        detail = detail,
        rawJson = pretty(event.raw),
        timeMillis = event.time,
        durationMillis = durationMillis,
        turn = turn,
        step = step,
        status = status,
    )
}

private data class NormalizedEvent(
    val type: String,
    val seq: Long,
    val time: Long?,
    val data: JsonObject,
    val raw: JsonObject,
)

private data class RequestHeader(
    val reason: String,
    val provider: String,
    val model: String,
    val detail: String,
)

private data class PendingRequest(
    val request: MutableRequest,
    var attached: Boolean = false,
)

private data class MutableRequest(
    val number: Int,
    val seq: Long,
    val turn: Int?,
    val step: Int?,
    var status: DshTrajectoryRecordStatus,
    var reason: String,
    var provider: String,
    var model: String,
    var detail: String,
    var rawJson: String,
    val timeMillis: Long?,
    var durationMillis: Long?,
) {
    fun toModel() = DshTrajectoryRequest(
        number = number,
        seq = seq,
        turn = turn,
        step = step,
        status = status,
        reason = reason,
        provider = provider,
        model = model,
        detail = detail,
        rawJson = rawJson,
        context = emptyList(),
        timeMillis = timeMillis,
        durationMillis = durationMillis,
    )
}

private data class MutableRecord(
    val id: String,
    val seq: Long,
    val type: String,
    val kind: DshTrajectoryRecordKind,
    val title: String,
    var preview: String,
    val source: String,
    val input: String,
    var output: String,
    var detail: String,
    var rawJson: String,
    val timeMillis: Long?,
    var durationMillis: Long?,
    val turn: Int?,
    val step: Int?,
    var status: DshTrajectoryRecordStatus,
    val requests: MutableList<MutableRequest> = mutableListOf(),
) {
    fun toModel(index: Int) = DshTrajectoryRecord(
        id = id,
        index = index,
        seq = seq,
        type = type,
        kind = kind,
        title = title,
        preview = preview,
        source = source,
        input = input,
        output = output,
        detail = detail,
        rawJson = rawJson,
        timeMillis = timeMillis,
        durationMillis = durationMillis,
        turn = turn,
        step = step,
        status = status,
        requests = requests.map(MutableRequest::toModel),
    )
}

private val PrettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
private val CompactWhitespace = Regex("\\s+")
private val ElecKoiInternalContextPlugins = setOf(
    "eleckoi-agent-session-bridge",
    "eleckoi-request-projection",
)
private const val DshCompactionCheckpointPlugin = "compact"

private fun messageFrom(data: JsonObject): JsonObject = data["message"] as? JsonObject ?: data

private fun contentText(value: JsonElement?): String = when (value) {
    null, JsonNull -> ""
    is JsonPrimitive -> value.contentOrNull.orEmpty()
    is JsonArray -> value.map(::contentText).filter(String::isNotBlank).joinToString("\n")
    is JsonObject -> when (value.text("type")) {
        "text", "reasoning" -> value.text("text")
        "image" -> "[图片]"
        "tool-call" -> {
            val name = value.text("name").ifBlank { "tool" }
            val arguments = prettyValue(value["arguments"])
            if (arguments.isBlank()) "调用 $name" else "调用 $name\n$arguments"
        }
        "tool-result" -> contentText(value["content"])
        else -> value.text("text").ifBlank { contentText(value["content"]) }
    }
}

private fun assistantFallback(content: JsonElement?): String {
    val names = (content as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.filter { it.text("type") == "tool-call" }
        ?.map { it.text("name") }
        ?.filter(String::isNotBlank)
        .orEmpty()
    return if (names.isEmpty()) "助手事件" else "调用 ${names.joinToString("、")}"
}

private fun contextTitle(source: String): String = when (source) {
    "agent-instructions" -> "Agent 指令"
    "runtime-context" -> "运行时上下文"
    "system-reminder" -> "系统提醒"
    "tool" -> "工具上下文"
    else -> "上下文"
}

private fun toolTarget(value: JsonElement?): String {
    val parsed = parseJsonOrValue(value)
    val objectValue = parsed as? JsonObject ?: return ""
    return listOf("path", "pattern", "query", "command", "description", "task", "url")
        .firstNotNullOfOrNull { key -> objectValue.text(key).takeIf(String::isNotBlank) }
        .orEmpty()
}

private fun appendRaw(rawJson: String, event: JsonObject): String {
    val existing = parseJson(rawJson)
    val values = if (existing is JsonArray) existing.toMutableList() else mutableListOf(existing)
    values += event
    return pretty(JsonArray(values))
}

private fun duration(start: Long?, end: Long?): Long? {
    if (start == null || end == null || end < start) return null
    return end - start
}

private fun preview(value: String): String {
    val compact = value.replace(CompactWhitespace, " ").trim()
    return if (compact.length > 180) "${compact.take(179)}…" else compact
}

private fun prettyValue(value: JsonElement?): String {
    val parsed = parseJsonOrValue(value)
    return when (parsed) {
        JsonNull -> ""
        is JsonPrimitive -> parsed.contentOrNull.orEmpty()
        else -> pretty(parsed)
    }
}

private fun parseJsonOrValue(value: JsonElement?): JsonElement {
    val primitive = value as? JsonPrimitive
    if (primitive == null || !primitive.isString) return value ?: JsonNull
    return runCatching { Json.parseToJsonElement(primitive.content) }.getOrDefault(primitive)
}

private fun parseJson(value: String): JsonElement =
    runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }

private fun pretty(value: JsonElement): String =
    runCatching { PrettyJson.encodeToString(JsonElement.serializer(), value) }
        .getOrDefault(value.toString())

private fun stepKey(turn: Int, step: Int): String = "$turn\u0000$step"

private fun JsonObject.objectValue(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.withoutKey(key: String): JsonObject = buildJsonObject {
    this@withoutKey.forEach { (name, value) -> if (name != key) put(name, value) }
}
private fun JsonObject.withRequestHeader(header: JsonObject): JsonObject = buildJsonObject {
    this@withRequestHeader.forEach { (name, value) ->
        if (name != "data") {
            put(name, value)
        } else {
            put("data", buildJsonObject {
                this@withRequestHeader.objectValue("data").forEach { (dataName, dataValue) ->
                    put(dataName, if (dataName == "header") header else dataValue)
                }
            })
        }
    }
}
private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.nonnegativeLong(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0L }
private fun JsonObject.positiveInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
