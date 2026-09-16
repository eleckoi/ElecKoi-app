package com.eleckoi.android.engine.agent.adapter.request

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Projects product history and prompt-position injections before pi-ai chooses a wire protocol. */
internal object DshRequestContextProjector {
    fun project(
        request: JsonObject,
        turnContext: AgentTurnRequestContext,
        requestIndex: Int,
    ): JsonObject {
        // ElecKoi owns the complete model context for every product session. DSH's generated
        // system block describes process-wide coding tools, including tools this route has not
        // enabled, so forwarding it would waste tokens and compete with product instructions.
        // Capability probes do not enter this projector and keep their isolated Harness context.
        val baseRequest = request.withoutHarnessSystem()
        val originalMessages = baseRequest["messages"] as? JsonArray ?: return baseRequest
        val activeInjections = turnContext.injections
            .filter { injection -> injection.isActive(originalMessages, requestIndex) }
            .sortedWith(compareBy(AgentContextInjection::order, AgentContextInjection::id))
        val instructionInjections = activeInjections.filter { it.anchor == AgentContextAnchor.Instructions }
        val messageInjections = activeInjections.filterNot { it.anchor == AgentContextAnchor.Instructions }
        val currentUserIndex = originalMessages.indexOfLast { message ->
            message.matchesCurrentUserMessage(turnContext.userMessage)
        }
        if (currentUserIndex < 0) return baseRequest.withSystemInjections(instructionInjections)

        val messages = originalMessages.toMutableList()
        val firstDialogue = messages.indexOfFirst { it.isCleanDialogueMessage() }
            .takeIf { it in 0..currentUserIndex }
            ?: currentUserIndex
        val productHistory = LegacyResponsesHistoryToDsh.convert(turnContext.parsedHistory)
        when (turnContext.historyProjection) {
            AgentHistoryProjection.Native -> Unit
            AgentHistoryProjection.SeedProductHistory -> messages.addAll(firstDialogue, productHistory)
            AgentHistoryProjection.ReplacePreviousTurns -> {
                val previousNative = messages.subList(firstDialogue, currentUserIndex).toList()
                val authoritative = compactedProjection(productHistory, previousNative) ?: productHistory
                val projected = authoritative.preserveNativeImageMessages(previousNative)
                messages.subList(firstDialogue, currentUserIndex).clear()
                messages.addAll(firstDialogue, projected)
            }
        }

        if (messageInjections.isNotEmpty()) {
            val projectedCurrentUser = messages.indexOfLast { message ->
                message.matchesCurrentUserMessage(turnContext.userMessage)
            }
            if (projectedCurrentUser >= 0) {
                applyInjections(messages, messageInjections, projectedCurrentUser)
            }
        }
        return buildJsonObject {
            baseRequest.forEach { (key, value) -> put(key, value) }
            put("messages", JsonArray(messages))
        }.withSystemInjections(instructionInjections)
    }

    private fun JsonObject.withoutHarnessSystem(): JsonObject = buildJsonObject {
        this@withoutHarnessSystem.forEach { (key, value) ->
            if (key != "system") put(key, value)
        }
    }

    private fun JsonObject.withSystemInjections(
        injections: List<AgentContextInjection>,
    ): JsonObject {
        if (injections.isEmpty()) return this
        val existing = string("system").orEmpty()
        return buildJsonObject {
            this@withSystemInjections.forEach { (key, value) -> put(key, value) }
            put(
                "system",
                buildList {
                    existing.takeIf(String::isNotBlank)?.let(::add)
                    injections.mapTo(this) { it.content }
                }.joinToString("\n\n"),
            )
        }
    }

    private fun applyInjections(
        messages: MutableList<JsonElement>,
        injections: List<AgentContextInjection>,
        currentUserIndex: Int,
    ) {
        val firstDialogue = messages.indexOfFirst { it.isCleanDialogueMessage() }
            .takeIf { it >= 0 } ?: messages.size
        val beforeTool = injections.filter { it.anchor == AgentContextAnchor.BeforeToolContext }
        if (beforeTool.isNotEmpty()) messages.addAll(0, beforeTool.map(::injectionMessage))

        val leading = listOf(
            AgentContextAnchor.ToolContext,
            AgentContextAnchor.AfterToolContext,
            AgentContextAnchor.BeforeHistory,
        ).flatMap { anchor -> injections.filter { it.anchor == anchor } }
        if (leading.isNotEmpty()) {
            messages.addAll(
                (firstDialogue + beforeTool.size).coerceAtMost(messages.size),
                leading.map(::injectionMessage),
            )
        }

        var projectedCurrentUserIndex = messages.indexOfLast { message ->
            message.isCleanDialogueMessage() && message.messageRole() == "user"
        }.takeIf { it >= 0 } ?: currentUserIndex
        val beforeLatestUser = listOf(
            AgentContextAnchor.AfterHistory,
            AgentContextAnchor.BeforeLatestUserInput,
        ).flatMap { anchor -> injections.filter { it.anchor == anchor } }
        if (beforeLatestUser.isNotEmpty()) {
            messages.addAll(projectedCurrentUserIndex, beforeLatestUser.map(::injectionMessage))
            projectedCurrentUserIndex += beforeLatestUser.size
        }

        val afterLatestUser = listOf(
            AgentContextAnchor.AfterLatestUserInput,
            AgentContextAnchor.BeforeToolFlow,
        ).flatMap { anchor -> injections.filter { it.anchor == anchor } }
        if (afterLatestUser.isNotEmpty()) {
            messages.addAll(projectedCurrentUserIndex + 1, afterLatestUser.map(::injectionMessage))
        }

        val afterToolFlow = injections.filter { it.anchor == AgentContextAnchor.AfterToolFlow }
        if (afterToolFlow.isNotEmpty()) messages.addAll(afterToolFlow.map(::injectionMessage))
    }

    private fun injectionMessage(injection: AgentContextInjection): JsonObject = buildJsonObject {
        put("id", "eleckoi-injection-${injection.id}")
        put(
            "role",
            when (injection.role) {
                AgentContextRole.System -> "system"
                AgentContextRole.User -> "user"
                AgentContextRole.Assistant -> "assistant"
            },
        )
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", injection.content)
            })
        })
        put("source", buildJsonObject {
            put("kind", "plugin")
            put("plugin", "eleckoi-context-injection")
        })
    }

    private fun AgentContextInjection.isActive(
        requestMessages: JsonArray,
        requestIndex: Int,
    ): Boolean = when (val value = activation) {
        AgentContextActivation.Immediate -> true
        AgentContextActivation.FirstModelRequest -> requestIndex == 1
        is AgentContextActivation.AfterToolCall -> requestMessages.any { message ->
            message.toolCalls().any { call -> call.string("name") == value.toolName }
        }
        is AgentContextActivation.AfterToolCallArgumentContains -> requestMessages.any { message ->
            message.toolCalls().any { call ->
                if (call.string("name") != value.toolName) return@any false
                val arguments = call.string("arguments")
                    ?.let { raw -> runCatching { ElecKoiJson.parseToJsonElement(raw).jsonObject }.getOrNull() }
                    ?: return@any false
                when (val candidate = arguments[value.argumentName]) {
                    is JsonPrimitive -> candidate.contentOrNull == value.value
                    is JsonArray -> candidate.any { element ->
                        (element as? JsonPrimitive)?.contentOrNull == value.value
                    }
                    else -> false
                }
            }
        }
    }

    private fun compactedProjection(
        productHistory: List<JsonObject>,
        nativeHistory: List<JsonElement>,
    ): List<JsonObject>? {
        val checkpointIndex = nativeHistory.indexOfLast { it.isCompactionCheckpoint() }
        if (checkpointIndex < 0) return null
        val checkpoint = nativeHistory[checkpointIndex] as? JsonObject ?: return null
        val nativeTail = nativeHistory.drop(checkpointIndex + 1).mapNotNull { it.asDialogueMessage() }
        if (nativeTail.size > productHistory.size) return null
        val productTail = productHistory.takeLast(nativeTail.size)
        if (!productTail.zip(nativeTail).all { (product, native) -> product.matchesNative(native) }) {
            return null
        }
        return listOf(checkpoint) + productTail
    }

    private fun List<JsonObject>.preserveNativeImageMessages(
        nativeMessages: List<JsonElement>,
    ): List<JsonObject> {
        var nativeCursor = 0
        return map { seed ->
            if (!seed.hasDataImage()) return@map seed
            val match = (nativeCursor until nativeMessages.size).firstOrNull { index ->
                val native = nativeMessages[index]
                native.hasNativeImage() && native.messageRole() == seed.messageRole() &&
                    native.matchesPromptText(seed.messageText())
            } ?: return@map seed
            nativeCursor = match + 1
            nativeMessages[match].jsonObject
        }
    }

    private fun JsonElement.toolCalls(): List<JsonObject> =
        ((this as? JsonObject)?.get("content") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it.string("type") == "tool-call" }
            .orEmpty()

    private fun JsonElement.isCleanDialogueMessage(): Boolean {
        val item = this as? JsonObject ?: return false
        if (item.string("role") !in setOf("user", "assistant")) return false
        return item.stringObject("source")?.string("kind") != "tool"
    }

    private fun JsonElement.asDialogueMessage(): JsonObject? =
        (this as? JsonObject)?.takeIf { it.isCleanDialogueMessage() }

    private fun JsonElement.isCompactionCheckpoint(): Boolean =
        messageRole() == "user" && messageText().contains(CompactedSummaryOpenTag)

    private fun JsonObject.matchesNative(native: JsonObject): Boolean {
        val role = messageRole() ?: return false
        if (native.messageRole() != role) return false
        val productText = messageText().normalizedDialogueText(role)
        return if (role == "assistant") {
            native.messageText().normalizedDialogueText(role) == productText
        } else {
            native.textParts().any { it.trim() == productText } || native.messageText().trim() == productText
        }
    }

    private fun JsonElement.matchesCurrentUserMessage(expectedText: String): Boolean {
        if (messageRole() != "user") return false
        return if (expectedText.isEmpty()) hasNativeImage() else matchesPromptText(expectedText)
    }

    private fun JsonElement.matchesPromptText(expectedText: String): Boolean =
        textParts().any { text -> text == expectedText }

    private fun JsonElement.textParts(): List<String> =
        (((this as? JsonObject)?.get("content") as? JsonArray)
            ?.mapNotNull { part ->
                (part as? JsonObject)
                    ?.takeIf { it.string("type") == "text" }
                    ?.string("text")
            }).orEmpty()

    private fun JsonElement.messageText(): String = textParts().joinToString("\n")

    private fun JsonElement.messageRole(): String? = (this as? JsonObject)?.string("role")

    private fun JsonElement.hasNativeImage(): Boolean =
        ((this as? JsonObject)?.get("content") as? JsonArray)
            ?.any { part -> (part as? JsonObject)?.string("type") == "image" } == true

    private fun JsonElement.hasDataImage(): Boolean =
        ((this as? JsonObject)?.get("content") as? JsonArray)
            ?.any { part -> (part as? JsonObject)?.string("type") == "eleckoi-data-image" } == true

    private fun String.normalizedDialogueText(role: String): String {
        val trimmed = trim()
        if (role != "assistant") return trimmed
        val openIndex = trimmed.indexOf(FinalOpenTag)
        if (openIndex < 0) return trimmed
        val start = openIndex + FinalOpenTag.length
        val end = trimmed.lastIndexOf(FinalCloseTag).takeIf { it >= start } ?: trimmed.length
        return trimmed.substring(start, end).trim()
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringObject(name: String): JsonObject? = get(name) as? JsonObject

    private const val CompactedSummaryOpenTag = "<compacted-summary>"
    private const val FinalOpenTag = "<FINAL>"
    private const val FinalCloseTag = "</FINAL>"
}
