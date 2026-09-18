package com.eleckoi.android.engine.agent.deepseek.protocol

import com.eleckoi.android.engine.agent.api.AgentFailureReason
import com.eleckoi.android.engine.agent.api.AgentActionCall
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.agent.protocol.AssistantActionCall
import com.eleckoi.android.engine.agent.protocol.AssistantActionCallChunk
import com.eleckoi.android.engine.agent.protocol.AssistantActionCallDecoder
import com.eleckoi.android.engine.agent.protocol.stripAssistantActionCalls
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Projects DeepSeek's append-only event log into ElecKoi's stable UI event vocabulary. */
internal class DeepSeekHarnessEventMapper {
    private val usageByStep = mutableMapOf<String, AgentTokenUsage>()
    private val activeTurns = mutableMapOf<String, Int>()
    private val activeToolNames = mutableMapOf<String, String>()
    private val activeToolArguments = mutableMapOf<String, String>()
    private val completedTools = mutableMapOf<String, CompletedTool>()
    private val actionDecoders = mutableMapOf<ActionStreamKey, AssistantActionCallDecoder>()
    private val emittedActionCounts = mutableMapOf<ActionStreamKey, MutableMap<ActionSignature, Int>>()
    private val reasoningBlockIndexes = mutableMapOf<ActionStreamKey, MutableList<Int>>()
    private val activeCompactions = mutableMapOf<String, CompactionProgress>()
    private val liveAttempts = mutableMapOf<String, AssistantAttempt>()
    private val liveChunkSteps = mutableSetOf<ActionStreamKey>()
    private var totalUsage = ZeroUsage
    private var modelContextWindow: Long? = null

    fun map(notification: DeepSeekNotification): List<AgentSessionEvent> {
        if (notification.method == AssistantStreamMethod) return mapAssistantStream(notification)
        if (notification.method != "session.event") return emptyList()
        val threadId = notification.params.string("sessionId") ?: return emptyList()
        val envelope = notification.params.obj("event") ?: return emptyList()
        val type = envelope.string("type") ?: return emptyList()
        val time = envelope.long("time") ?: 0L
        val data = envelope.obj("data") ?: JsonObject(emptyMap())
        val turn = data.int("turn") ?: activeTurns[threadId]
        val turnId = turn?.let { turnId(threadId, it) }

        return when (type) {
            "turn/start" -> turnId?.let {
                activeTurns[threadId] = requireNotNull(turn)
                listOf(AgentSessionEvent.TurnStarted(threadId, it, time))
            }.orEmpty()
            "step/start" -> mapStepStart(threadId, turnId, data, time)
            "assistant/chunk" -> {
                val step = data.int("step") ?: 0
                val key = turnId?.let { ActionStreamKey(it, step) }
                if (key != null && key in liveChunkSteps) emptyList()
                else mapChunk(threadId, turnId, data, time)
            }
            "assistant/message" -> mapAssistantMessage(threadId, turnId, data, time)
            "tool/call" -> mapToolCall(threadId, turnId, data, time)
            "tool/result" -> mapToolResult(threadId, turnId, data, time)
            "compaction/start" -> mapCompactionStart(threadId, turnId, data, time)
            "compaction/summary" -> mapCompactionSummary(turnId, data)
            "compaction/end" -> mapCompactionEnd(threadId, turnId, data, time)
            "step/end" -> mapStepEnd(threadId, turnId, data, time)
            "request/context" -> {
                modelContextWindow = data.long("contextWindow")
                emptyList()
            }
            "turn/end" -> mapTurnEnd(threadId, turnId, data, time).also {
                activeTurns.remove(threadId)
                liveAttempts.entries.removeAll { (_, attempt) ->
                    attempt.threadId == threadId && attempt.turnId == turnId
                }
                liveChunkSteps.removeAll { key -> key.turnId == turnId }
            }
            else -> emptyList()
        }
    }

    private fun mapCompactionStart(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val compactionId = data.string("compactionId") ?: return emptyList()
        activeCompactions[compactionId] = CompactionProgress(turnId = turnId)
        return listOf(
            AgentSessionEvent.WorkItemStarted(
                threadId = threadId,
                turnId = turnId,
                itemId = compactionItemId(compactionId),
                type = AgentWorkItemType.ContextCompaction,
                label = "正在自动压缩",
                startedAtMillis = time,
            ),
        )
    }

    private fun mapCompactionSummary(
        turnId: String?,
        data: JsonObject,
    ): List<AgentSessionEvent> {
        val compactionId = data.string("compactionId") ?: return emptyList()
        val existing = activeCompactions[compactionId]
            ?: turnId?.let(::CompactionProgress)
            ?: return emptyList()
        val summaryText = data["summary"].assistantTextContent().trim()
        activeCompactions[compactionId] = existing.copy(
            shadowedTokenCount = data.long("shadowedTokenCount")
                ?: existing.shadowedTokenCount,
            summaryText = summaryText.ifBlank { existing.summaryText },
        )
        return emptyList()
    }

    private fun mapCompactionEnd(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        val compactionId = data.string("compactionId") ?: return emptyList()
        val progress = activeCompactions.remove(compactionId)
        val ownerTurnId = progress?.turnId ?: turnId ?: return emptyList()
        val error = data.string("error").orEmpty()
        val status = if (error.isBlank()) AgentWorkStatus.Completed else AgentWorkStatus.Failed
        val summary = if (error.isBlank()) {
            "上下文已自动压缩"
        } else {
            compactionFailureMessage(error)
        }
        val detail = if (error.isBlank()) {
            listOfNotNull(
                progress?.summaryText?.takeIf(String::isNotBlank),
                progress?.shadowedTokenCount
                    ?.takeIf { it > 0L }
                    ?.let { "已替换约 $it Token 的历史上下文" },
            ).joinToString("\n\n").ifBlank { "自动压缩已完成" }
        } else {
            summary
        }
        return listOf(
            AgentSessionEvent.WorkItemCompleted(
                threadId = threadId,
                turnId = ownerTurnId,
                itemId = compactionItemId(compactionId),
                type = AgentWorkItemType.ContextCompaction,
                status = status,
                summary = summary,
                detail = detail,
                completedAtMillis = time,
            ),
        )
    }

    private fun compactionFailureMessage(error: String): String {
        NotSmallerCompactionError.find(error)?.let { match ->
            val (summaryTokens, historyTokens) = match.destructured
            return "摘要没有比被替换的历史更短（摘要约 $summaryTokens Token，原历史约 $historyTokens Token）"
        }
        return when {
            error.contains("summarization produced no text summary content", ignoreCase = true) ->
                "摘要模型没有返回可用的文本内容"
            error.contains("summary did not shrink history", ignoreCase = true) ->
                "生成的摘要没有缩短历史上下文"
            else -> error
        }
    }

    private fun mapStepStart(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val step = data.int("step") ?: return emptyList()
        return listOf(AgentSessionEvent.StepStarted(threadId, turnId, step, time))
    }

    private fun mapStepEnd(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val step = data.int("step") ?: return emptyList()
        reasoningBlockIndexes.remove(ActionStreamKey(turnId, step))
        return finishActionStream(threadId, turnId, step) +
            AgentSessionEvent.StepCompleted(threadId, turnId, step, time)
    }

    private fun mapChunk(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val step = data.int("step") ?: 0
        val chunk = data.obj("chunk") ?: return emptyList()
        val index = chunk.int("index") ?: 0
        val actionKey = ActionStreamKey(turnId, step)
        if (chunk.isReasoningBlockEvent()) {
            reasoningBlockIndexes.getOrPut(actionKey, ::mutableListOf).run {
                if (index !in this) add(index)
            }
        }
        val itemId = "assistant-$turnId-$step"
        return when (chunk.string("type")) {
            "text-delta" -> chunk.string("text")?.let { delta ->
                val decoded = actionDecoders
                    .getOrPut(actionKey, ::AssistantActionCallDecoder)
                    .accept(delta)
                mapActionChunk(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    key = actionKey,
                    decoded = decoded,
                    displayText = delta,
                    observedAtMillis = time,
                )
            }.orEmpty()
            "reasoning-delta" -> chunk.string("text")?.let { delta ->
                listOf(
                    AgentSessionEvent.ReasoningTextDelta(
                        threadId,
                        turnId,
                        reasoningItemId(turnId, step, index),
                        index,
                        delta,
                        step,
                        time,
                    ),
                )
            }.orEmpty()
            "tool-call-delta" -> {
                val name = chunk.string("name")
                val argumentsDelta = chunk.string("argumentsDelta").orEmpty()
                val observed = name != null || argumentsDelta.isNotEmpty()
                if (!observed) {
                    emptyList()
                } else {
                    listOf(
                        AgentSessionEvent.AssistantDelta(
                            threadId = threadId,
                            turnId = turnId,
                            itemId = itemId,
                            delta = argumentsDelta.ifEmpty { name.orEmpty() },
                            step = step,
                            observedAtMillis = time,
                            visible = false,
                            tokenObserved = true,
                        ),
                    )
                }
            }
            "usage" -> mapUsage(threadId, turnId, step, chunk.obj("usage"))
            else -> emptyList()
        }
    }

    private fun mapAssistantMessage(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val step = data.int("step") ?: 0
        val message = data.obj("message") ?: return emptyList()
        val messageId = "assistant-$turnId-$step"
        val actionKey = ActionStreamKey(turnId, step)
        val hadStreamingText = actionDecoders.remove(actionKey) != null
        // `assistant/message` is the authoritative completed typed-content snapshot. Some
        // Responses providers expose reasoning only in the final block instead of deltas, while
        // others emit both. Project every completed reasoning block as a replacing snapshot so
        // the UI neither drops final-only reasoning nor duplicates streamed reasoning.
        val streamedReasoningIndexes = reasoningBlockIndexes.remove(actionKey).orEmpty()
        val reasoning = message["content"].reasoningTextContent(streamedReasoningIndexes)
        val completedReasoningIndexes = (streamedReasoningIndexes + reasoning.map(IndexedReasoningText::index))
            .distinct()
        val text = message["content"].assistantTextContent()
        val decodedText = stripAssistantActionCalls(text)
        val snapshotActions = unseenSnapshotActions(actionKey, decodedText.calls)
        return buildList {
            reasoning.forEach { block ->
                add(
                    AgentSessionEvent.ReasoningTextDelta(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = reasoningItemId(turnId, step, block.index),
                        contentIndex = block.index,
                        delta = block.text,
                        step = step,
                        observedAtMillis = time,
                        completeSnapshot = true,
                    ),
                )
            }
            completedReasoningIndexes.forEach { index ->
                add(
                    AgentSessionEvent.WorkItemCompleted(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = reasoningItemId(turnId, step, index),
                        type = AgentWorkItemType.Reasoning,
                        status = AgentWorkStatus.Completed,
                        completedAtMillis = time,
                        step = step,
                    ),
                )
            }
            addAll(
                mapActionChunk(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = messageId,
                    key = actionKey,
                    decoded = AssistantActionCallChunk(
                        visibleText = text.takeUnless { hadStreamingText }.orEmpty(),
                        calls = snapshotActions,
                    ),
                    observedAtMillis = time,
                ),
            )
            // DSH's assistant/message is the LLM completion boundary even when the response only
            // contains reasoning or a native tool call. A blank summary remains invisible in the
            // timeline, but the boundary is required for session-stats timing parity.
            add(
                AgentSessionEvent.WorkItemCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = messageId,
                    type = AgentWorkItemType.AssistantMessage,
                    status = AgentWorkStatus.Completed,
                    summary = text,
                    completedAtMillis = time,
                    step = step,
                ),
            )
            if (text.isNotBlank()) {
                add(
                    AgentSessionEvent.ModelHistoryItemCompleted(
                        threadId,
                        turnId,
                        DeepSeekHistoryEncoding.responseMessage("assistant", text),
                    ),
                )
            }
            addAll(mapUsage(threadId, turnId, step, data.obj("usage")))
        }
    }

    private fun mapToolCall(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val callId = data.string("callId") ?: return emptyList()
        val name = data.string("name").orEmpty()
        val arguments = data.string("arguments").orEmpty()
        completedTools.remove(callId)
        activeToolNames[callId] = name
        activeToolArguments[callId] = arguments
        return listOf(
            AgentSessionEvent.WorkItemStarted(
                threadId = threadId,
                turnId = turnId,
                itemId = callId,
                type = AgentWorkItemType.Tool,
                label = name.ifBlank { "DeepSeek tool" },
                toolName = name,
                toolArguments = arguments,
                startedAtMillis = time,
            ),
            AgentSessionEvent.ModelHistoryItemCompleted(
                threadId,
                turnId,
                buildJsonObject {
                    put("type", "function_call")
                    put("call_id", callId)
                    put("name", name)
                    put("arguments", arguments)
                }.toString(),
            ),
        )
    }

    private fun mapToolResult(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val message = data.obj("message") ?: return emptyList()
        val source = message.obj("source")
        val resultBlock = (message["content"] as? JsonArray)
            ?.firstOrNull()
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.takeIf { it.string("type") == "tool-result" }
        val callId = source?.string("callId") ?: resultBlock?.string("toolCallId") ?: return emptyList()
        if (callId in completedTools) return emptyList()
        val toolName = activeToolNames.remove(callId) ?: return emptyList()
        val toolArguments = activeToolArguments.remove(callId).orEmpty()
        val error = data.obj("error")
        val isError = error != null || resultBlock?.get("isError")?.jsonPrimitive?.contentOrNull == "true"
        val detail = message["content"].toolResultTextContent()
        completedTools[callId] = CompletedTool(toolName, toolArguments)
        return listOf(
            AgentSessionEvent.WorkItemCompleted(
                threadId = threadId,
                turnId = turnId,
                itemId = callId,
                type = AgentWorkItemType.Tool,
                status = if (isError) AgentWorkStatus.Failed else AgentWorkStatus.Completed,
                // Match ElecKoi's Harness-neutral timeline contract: the reducer persists a
                // tool's summary as its visible result and uses detail as the operation label.
                summary = detail,
                detail = toolName.ifBlank { "DeepSeek tool" },
                toolName = toolName,
                toolArguments = toolArguments,
                completedAtMillis = time,
            ),
            AgentSessionEvent.ModelHistoryItemCompleted(
                threadId,
                turnId,
                buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", detail)
                }.toString(),
            ),
        )
    }

    private fun mapTurnEnd(
        threadId: String,
        turnId: String?,
        data: JsonObject,
        time: Long,
    ): List<AgentSessionEvent> {
        turnId ?: return emptyList()
        val reason = data.obj("reason")
        val kind = reason?.string("kind").orEmpty()
        val error = reason?.obj("error")
        val errorMessage = error?.string("message")
        val status = when (kind) {
            "completed", "max-tokens" -> AgentWorkStatus.Completed
            "aborted", "interrupted" -> AgentWorkStatus.Interrupted
            else -> AgentWorkStatus.Failed
        }
        val failureReason = if (
            error?.string("code")?.contains("CONTEXT", ignoreCase = true) == true ||
            errorMessage?.contains("context window", ignoreCase = true) == true
        ) {
            AgentFailureReason.ContextWindowExceeded
        } else {
            AgentFailureReason.Other
        }
        val terminalEvents = listOf(
            AgentSessionEvent.TurnCompleted(
                threadId = threadId,
                turnId = turnId,
                status = status,
                errorMessage = errorMessage ?: kind.takeUnless { status == AgentWorkStatus.Completed },
                failureReason = failureReason,
                completedAtMillis = time,
            ),
        )
        val events = finishActionStreamsForTurn(threadId, turnId) + terminalEvents
        emittedActionCounts.keys.removeAll { it.turnId == turnId }
        return events
    }

    private fun mapActionChunk(
        threadId: String,
        turnId: String,
        itemId: String,
        key: ActionStreamKey,
        decoded: AssistantActionCallChunk,
        displayText: String = decoded.visibleText,
        observedAtMillis: Long = 0L,
    ): List<AgentSessionEvent> {
        if (displayText.isEmpty() && decoded.calls.isEmpty()) return emptyList()
        recordEmittedActions(key, decoded.calls)
        return listOf(
            AgentSessionEvent.AssistantDelta(
                threadId = threadId,
                turnId = turnId,
                itemId = itemId,
                delta = displayText,
                step = key.step,
                observedAtMillis = observedAtMillis,
                actionCalls = decoded.calls.map { call ->
                    AgentActionCall(call.name, call.argumentsJson)
                },
            ),
        )
    }

    private fun finishActionStream(
        threadId: String,
        turnId: String,
        step: Int,
    ): List<AgentSessionEvent> {
        val key = ActionStreamKey(turnId, step)
        val decoder = actionDecoders.remove(key) ?: return emptyList()
        return mapActionChunk(
            threadId = threadId,
            turnId = turnId,
            itemId = "assistant-$turnId-$step",
            key = key,
            decoded = decoder.finish(),
            // Every raw text fragment has already entered the process transcript. Finishing the
            // side-channel decoder may discover an EOF-bounded action, but must not repeat text.
            displayText = "",
        )
    }

    private fun finishActionStreamsForTurn(
        threadId: String,
        turnId: String,
    ): List<AgentSessionEvent> = actionDecoders.keys
        .filter { it.turnId == turnId }
        .sortedBy(ActionStreamKey::step)
        .flatMap { key -> finishActionStream(threadId, turnId, key.step) }

    private fun unseenSnapshotActions(
        key: ActionStreamKey,
        calls: List<AssistantActionCall>,
    ): List<AssistantActionCall> {
        val remaining = emittedActionCounts[key]?.toMutableMap().orEmpty().toMutableMap()
        return calls.filter { call ->
            val signature = ActionSignature(call.name, call.argumentsJson)
            val count = remaining[signature] ?: 0
            if (count > 0) {
                remaining[signature] = count - 1
                false
            } else {
                true
            }
        }
    }

    private fun recordEmittedActions(
        key: ActionStreamKey,
        calls: List<AssistantActionCall>,
    ) {
        if (calls.isEmpty()) return
        val counts = emittedActionCounts.getOrPut(key, ::mutableMapOf)
        calls.forEach { call ->
            val signature = ActionSignature(call.name, call.argumentsJson)
            counts[signature] = (counts[signature] ?: 0) + 1
        }
    }

    private fun mapUsage(
        threadId: String,
        turnId: String,
        step: Int,
        source: JsonObject?,
    ): List<AgentSessionEvent> {
        source ?: return emptyList()
        val next = source.toUsage()
        val key = "$turnId:$step"
        usageByStep[key] = next
        totalUsage = usageByStep.values.fold(ZeroUsage) { total, usage -> total.plus(usage) }
        return listOf(
            AgentSessionEvent.TokenUsageUpdated(
                threadId = threadId,
                turnId = turnId,
                step = step,
                total = totalUsage,
                last = next,
                modelContextWindow = modelContextWindow,
            ),
        )
    }

    private fun JsonObject.toUsage() = AgentTokenUsage(
        inputTokens = long("inputTokens") ?: 0L,
        cacheReadTokens = long("cacheReadTokens") ?: 0L,
        cacheWriteTokens = long("cacheWriteTokens") ?: 0L,
        cacheUsageReported = containsKey("cacheReadTokens") || containsKey("cacheWriteTokens"),
        outputTokens = long("outputTokens") ?: 0L,
        reasoningOutputTokens = long("reasoningTokens") ?: 0L,
        totalTokens = (long("inputTokens") ?: 0L) +
            (long("cacheReadTokens") ?: 0L) +
            (long("cacheWriteTokens") ?: 0L) +
            (long("outputTokens") ?: 0L),
    )

    private fun AgentTokenUsage.plus(other: AgentTokenUsage) = AgentTokenUsage(
        totalTokens + other.totalTokens,
        inputTokens + other.inputTokens,
        cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens + other.cacheWriteTokens,
        cacheUsageReported || other.cacheUsageReported,
        outputTokens + other.outputTokens,
        reasoningOutputTokens + other.reasoningOutputTokens,
    )

    private fun mapAssistantStream(notification: DeepSeekNotification): List<AgentSessionEvent> {
        val threadId = notification.params.string("sessionId") ?: return emptyList()
        val frame = notification.params.obj("frame") ?: return emptyList()
        val attemptId = frame.string("attemptId") ?: return emptyList()
        val attemptKey = "$threadId:$attemptId"
        return when (frame.string("type")) {
            "start" -> {
                val turn = frame.int("turn") ?: return emptyList()
                val step = frame.int("step") ?: return emptyList()
                val attempt = AssistantAttempt(
                    threadId = threadId,
                    turnId = turnId(threadId, turn),
                    step = step,
                )
                liveAttempts[attemptKey] = attempt
                liveChunkSteps += ActionStreamKey(attempt.turnId, step)
                emptyList()
            }
            "chunk" -> {
                val attempt = liveAttempts[attemptKey] ?: return emptyList()
                val chunk = frame.obj("chunk") ?: return emptyList()
                mapChunk(
                    threadId = threadId,
                    turnId = attempt.turnId,
                    data = buildJsonObject {
                        put("step", attempt.step)
                        put("chunk", chunk)
                    },
                    time = frame.long("time") ?: 0L,
                )
            }
            "end" -> {
                val attempt = liveAttempts.remove(attemptKey) ?: return emptyList()
                val kind = frame.obj("outcome")?.string("kind")
                if (kind != "abandoned") return emptyList()
                val key = ActionStreamKey(attempt.turnId, attempt.step)
                reasoningBlockIndexes[key].orEmpty().map { index ->
                    AgentSessionEvent.WorkItemCompleted(
                        threadId = attempt.threadId,
                        turnId = attempt.turnId,
                        itemId = reasoningItemId(attempt.turnId, attempt.step, index),
                        type = AgentWorkItemType.Reasoning,
                        status = AgentWorkStatus.Interrupted,
                        completedAtMillis = frame.long("time") ?: 0L,
                        step = attempt.step,
                    )
                }
            }
            else -> emptyList()
        }
    }

    companion object {
        fun turnId(sessionId: String, turn: Int): String = "$sessionId:$turn"

        private val ZeroUsage = AgentTokenUsage(0, 0, 0, 0, false, 0, 0)

    }
}

private data class CompactionProgress(
    val turnId: String,
    val shadowedTokenCount: Long = 0L,
    val summaryText: String = "",
)

private fun compactionItemId(compactionId: String): String = "compaction-$compactionId"

private val NotSmallerCompactionError = Regex(
    "summary is not smaller than the shadowed content \\((\\d+) estimated framed tokens >= (\\d+)\\)",
    RegexOption.IGNORE_CASE,
)

private data class ActionStreamKey(
    val turnId: String,
    val step: Int,
)

private data class AssistantAttempt(
    val threadId: String,
    val turnId: String,
    val step: Int,
)

private data class CompletedTool(
    val name: String,
    val arguments: String,
)

private fun reasoningItemId(turnId: String, step: Int, index: Int): String =
    "reasoning-$turnId-$step-$index"

private const val AssistantStreamMethod = "agent.assistant-stream"

private data class ActionSignature(
    val name: String,
    val argumentsJson: String,
)

private data class IndexedReasoningText(
    val index: Int,
    val text: String,
)

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonElement?.assistantTextContent(): String = when (this) {
    is JsonArray -> mapNotNull { block ->
        val payload = runCatching { block.jsonObject }.getOrNull() ?: return@mapNotNull null
        payload.string("text").takeIf { payload.string("type") == "text" }
    }.joinToString("\n")
    is JsonObject -> string("text").orEmpty().takeIf { string("type") == "text" }.orEmpty()
    else -> ""
}

private fun JsonElement?.reasoningTextContent(
    streamedIndexes: List<Int>,
): List<IndexedReasoningText> = when (this) {
    is JsonArray -> buildList {
        var reasoningOrdinal = 0
        this@reasoningTextContent.forEachIndexed { contentIndex, block ->
            val payload = runCatching { block.jsonObject }.getOrNull() ?: return@forEachIndexed
            val text = payload.string("text").takeIf { payload.string("type") == "reasoning" }
                ?: return@forEachIndexed
            add(
                IndexedReasoningText(
                    index = streamedIndexes.getOrNull(reasoningOrdinal++) ?: contentIndex,
                    text = text,
                ),
            )
        }
    }

    is JsonObject -> string("text")
        ?.takeIf { string("type") == "reasoning" }
        ?.let { text -> listOf(IndexedReasoningText(streamedIndexes.firstOrNull() ?: 0, text)) }
        .orEmpty()
    else -> emptyList()
}

private fun JsonObject.isReasoningBlockEvent(): Boolean = when (string("type")) {
    "reasoning-delta" -> true
    "block-start" -> string("blockType") == "reasoning"
    "block-end" -> obj("block")?.string("type") == "reasoning"
    else -> false
}

private fun JsonElement?.toolResultTextContent(): String = when (this) {
    is JsonArray -> mapNotNull { block ->
        val payload = runCatching { block.jsonObject }.getOrNull() ?: return@mapNotNull null
        when (payload.string("type")) {
            "text" -> payload.string("text")
            "tool-result" -> payload["content"].toolResultTextContent()
            else -> null
        }
    }.joinToString("\n")
    is JsonObject -> when (string("type")) {
        "text" -> string("text").orEmpty()
        "tool-result" -> this["content"].toolResultTextContent()
        else -> ""
    }
    else -> ""
}
