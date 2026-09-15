package com.eleckoi.android.sdk.author.chat

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorSendImageAttachment
import com.eleckoi.android.sdk.author.requireChatGateway
import com.eleckoi.android.sdk.author.requireMessageSendGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal object ChatAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("chat.current")) { environment, _ ->
            val snapshot = environment.runtime.chatGateway?.snapshot()
            val session = snapshot?.draft?.session ?: environment.runtime.chatSession
            session?.toSummaryJson() ?: throw AuthorApiCallException(
                AuthorApiErrorCode.ContextUnavailable,
                "当前页面没有聊天上下文",
            )
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.list")) { environment, _ ->
            val gateway = environment.requireChatGateway()
            val characterId = environment.scopedCharacterId()
            buildJsonObject {
                put("items", buildJsonArray {
                    gateway.snapshot().sessions.filter { it.characterId == characterId }.forEach { item ->
                        add(buildJsonObject {
                            put("id", item.id)
                            put("title", item.title)
                            put("characterId", item.characterId)
                            put("characterName", item.characterName)
                            put("characterAvatar", item.characterAvatar)
                            put("preview", item.summary)
                            put("createdAt", item.createdAt)
                            put("updatedAt", item.updatedAt)
                        })
                    }
                })
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.getGenerationState")) { environment, _ ->
            val snapshot = environment.requireChatGateway().snapshot()
            val conversationId = snapshot.draft?.session?.id.orEmpty()
            if (snapshot.isGenerating && snapshot.activeMessageId.isNotBlank()) {
                buildJsonObject {
                    put("active", true)
                    put("conversationId", conversationId)
                    put("runId", snapshot.activeRunId)
                    put("messageId", snapshot.activeMessageId)
                    put("accumulated", snapshot.activeOutput)
                    put("sequence", 0)
                    put("stats", JsonNull)
                }
            } else {
                buildJsonObject {
                    put("active", false)
                    put("conversationId", conversationId)
                    put("stats", JsonNull)
                }
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.getAgentTrajectory")) { environment, params ->
            val session = environment.requireChatGateway().snapshot().draft?.session
                ?: throw AuthorApiCallException(
                    AuthorApiErrorCode.ContextUnavailable,
                    "当前页面没有聊天上下文",
                )
            session.toAgentTrajectory(
                beforeIndex = params["beforeIndex"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
                requestedLimit = params["limit"]?.jsonPrimitive?.intOrNull
                    ?.takeIf { it > 0 }
                    ?.coerceAtMost(200),
            )
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.getModels")) { environment, _ ->
            val snapshot = environment.requireChatGateway().snapshot()
            buildJsonObject {
                put("current", buildJsonObject {
                    put("configId", snapshot.draft?.selectedConfigId.orEmpty())
                    put("model", snapshot.draft?.selectedModel.orEmpty())
                })
                put("items", buildJsonArray {
                    snapshot.modelConfigs.forEach { config ->
                        add(buildJsonObject {
                            put("configId", config.id)
                            put("name", config.name)
                            put("provider", config.provider)
                            put("defaultModel", config.model)
                            put("models", buildJsonArray {
                                config.modelOptions.forEach { option ->
                                    add(buildJsonObject {
                                        put("id", option.id)
                                        put("name", option.name)
                                        option.contextWindowTokens?.let { put("contextWindowTokens", it) }
                                        option.autoCompactTokenLimit?.let { put("autoCompactTokenLimit", it) }
                                        option.maxOutputTokens?.let { put("maxOutputTokens", it) }
                                        option.temperature?.let { put("temperature", it) }
                                        option.topP?.let { put("topP", it) }
                                        option.reasoningEffort?.let { put("reasoningEffort", it) }
                                        put("supportsImageInput", option.supportsImageInput)
                                    })
                                }
                            })
                        })
                    }
                })
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.send")) { environment, params ->
            val text = (params["text"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.trim()
                .orEmpty()
            val attachments = parseSendImages(params)
            if (text.isEmpty() && attachments.isEmpty()) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.InvalidParams,
                    "发送内容和图片不能同时为空",
                )
            }
            if (text.length > MaxAuthorMessageLength) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.InvalidParams,
                    "发送内容过长",
                )
            }
            environment.requireMessageSendGateway().send(text, attachments).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.stopGeneration")) { environment, _ ->
            val gateway = environment.requireChatGateway()
            if (!gateway.snapshot().isGenerating) {
                buildJsonObject { put("cancelled", false) }
            } else {
                gateway.stopGeneration().toAuthorJson()
                buildJsonObject { put("cancelled", true) }
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.create")) { environment, params ->
            val characterId = environment.scopedCharacterId()
            val requestedCharacterId = (params["characterId"] as? JsonPrimitive)?.content.orEmpty()
            if (requestedCharacterId.isNotBlank() && requestedCharacterId != characterId) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.PermissionDenied,
                    "作者前端只能为当前角色创建对话",
                )
            }
            environment.requireChatGateway().createNewChat(characterId).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.open")) { environment, params ->
            val sessionId = (params["sessionId"] as? JsonPrimitive)?.content.orEmpty()
            val gateway = environment.requireChatGateway()
            environment.requireSessionInScope(gateway.snapshot(), sessionId)
            gateway.openChat(sessionId).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.delete")) { environment, params ->
            val sessionId = (params["sessionId"] as? JsonPrimitive)?.content.orEmpty()
            val gateway = environment.requireChatGateway()
            environment.requireSessionInScope(gateway.snapshot(), sessionId)
            gateway.deleteChat(sessionId).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("chat.selectModel")) { environment, params ->
            val configId = (params["configId"] as? JsonPrimitive)?.content.orEmpty()
            val model = (params["model"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().selectModel(
                configId = configId,
                model = model,
            ).toAuthorJson()
            buildJsonObject {
                put("configId", configId)
                put("model", model)
            }
        },
    )
}

private fun parseSendImages(params: JsonObject): List<AuthorSendImageAttachment> {
    val value = params["attachments"] ?: return emptyList()
    val items = value as? JsonArray ?: throw AuthorApiCallException(
        AuthorApiErrorCode.InvalidParams,
        "每条消息最多发送 $MaxAuthorMessageImages 张图片",
    )
    if (items.size > MaxAuthorMessageImages) {
        throw AuthorApiCallException(
            AuthorApiErrorCode.InvalidParams,
            "每条消息最多发送 $MaxAuthorMessageImages 张图片",
        )
    }
    return items.map { item ->
        val input = item as? JsonObject ?: throw AuthorApiCallException(
            AuthorApiErrorCode.InvalidParams,
            "消息附件格式不正确",
        )
        val type = input.string("type")
        val mediaType = input.string("mediaType").lowercase()
        val data = input.string("data").filterNot(Char::isWhitespace)
        if (type != "image" || mediaType !in SupportedAuthorImageTypes) {
            throw AuthorApiCallException(
                AuthorApiErrorCode.InvalidParams,
                "Agent 消息附件仅支持 PNG、JPEG、WebP 和 GIF 图片",
            )
        }
        if (data.isEmpty()) {
            throw AuthorApiCallException(
                AuthorApiErrorCode.InvalidParams,
                "图片附件内容不能为空",
            )
        }
        AuthorSendImageAttachment(
            mediaType = mediaType,
            data = data,
            name = input.string("name").trim().take(MaxAuthorAttachmentNameLength),
        )
    }
}

private fun JsonObject.string(name: String): String =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

private const val MaxAuthorMessageImages = 4
private const val MaxAuthorMessageLength = 100_000
private const val MaxAuthorAttachmentNameLength = 255
private val SupportedAuthorImageTypes = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

private fun com.eleckoi.android.sdk.author.AuthorChatSessionSnapshot.toAgentTrajectory(
    beforeIndex: Int?,
    requestedLimit: Int?,
): kotlinx.serialization.json.JsonObject {
    val allRecords = buildList {
        messages.forEach { message ->
            add(
                TrajectoryRecord(
                    id = "message:${message.id}",
                    type = "message",
                    kind = if (message.role == "user") "user" else "assistant",
                    title = if (message.role == "user") "用户消息" else "AI 回复",
                    preview = message.content.take(240),
                    source = message.role,
                    input = if (message.role == "user") message.content else "",
                    output = if (message.role == "assistant") message.content else "",
                    detail = message.reasoningContent,
                    rawJson = message.modelHistoryItems.joinToString(separator = "\n"),
                    timeMillis = message.turnStartedAtMillis.takeIf { it > 0L },
                    durationMillis = duration(message.turnStartedAtMillis, message.turnCompletedAtMillis),
                    status = message.status.toTrajectoryStatus(),
                ),
            )
            message.process.forEach { process ->
                add(
                    TrajectoryRecord(
                        id = "process:${message.id}:${process.id}",
                        type = process.kind,
                        kind = if (process.kind == "compaction") "compaction" else "tool",
                        title = process.summary.ifBlank { process.toolName.ifBlank { "Agent 处理" } },
                        preview = process.detail.ifBlank { process.arguments }.take(240),
                        source = process.toolName,
                        input = process.arguments,
                        output = process.detail,
                        detail = process.detail,
                        rawJson = "",
                        timeMillis = process.startedAtMillis.takeIf { it > 0L },
                        durationMillis = duration(process.startedAtMillis, process.completedAtMillis),
                        status = process.status.toTrajectoryStatus(),
                    ),
                )
            }
        }
    }.mapIndexed { index, record -> record.copy(index = index, seq = index) }
    val eligible = beforeIndex?.let { boundary -> allRecords.filter { it.index < boundary } } ?: allRecords
    val limit = requestedLimit ?: 400
    val pageStart = (eligible.size - limit).coerceAtLeast(0)
    val page = eligible.drop(pageStart)
    val runtimeThreadId = messages.asReversed()
        .firstOrNull { it.runtimeThreadId.isNotBlank() }
        ?.runtimeThreadId
    return buildJsonObject {
        put("conversationId", id)
        put("runtimeThreadId", runtimeThreadId?.let(::JsonPrimitive) ?: JsonNull)
        put("records", buildJsonArray { page.forEach { add(it.toJson()) } })
        put("totalRecords", allRecords.size)
        put("hasMore", pageStart > 0)
        put("beforeIndex", page.firstOrNull()?.index?.let(::JsonPrimitive) ?: JsonNull)
        put("startedAtMillis", allRecords.mapNotNull { it.timeMillis }.minOrNull()?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "completedAtMillis",
            messages.mapNotNull { it.turnCompletedAtMillis }.maxOrNull()?.let(::JsonPrimitive) ?: JsonNull,
        )
    }
}

private data class TrajectoryRecord(
    val id: String,
    val index: Int = 0,
    val seq: Int = 0,
    val type: String,
    val kind: String,
    val title: String,
    val preview: String,
    val source: String,
    val input: String,
    val output: String,
    val detail: String,
    val rawJson: String,
    val timeMillis: Long?,
    val durationMillis: Long?,
    val status: String,
) {
    fun toJson() = buildJsonObject {
        put("id", id)
        put("index", index)
        put("seq", seq)
        put("type", type)
        put("kind", kind)
        put("title", title)
        put("preview", preview)
        put("source", source)
        put("input", input)
        put("output", output)
        put("detail", detail)
        put("rawJson", rawJson)
        put("timeMillis", timeMillis?.let(::JsonPrimitive) ?: JsonNull)
        put("durationMillis", durationMillis?.let(::JsonPrimitive) ?: JsonNull)
        put("turn", JsonNull)
        put("step", JsonNull)
        put("status", status)
        put("requests", buildJsonArray {})
    }
}

private fun duration(startedAtMillis: Long, completedAtMillis: Long?): Long? =
    completedAtMillis?.takeIf { startedAtMillis > 0L }?.let { (it - startedAtMillis).coerceAtLeast(0L) }

private fun String.toTrajectoryStatus(): String = when (this) {
    "streaming", "running" -> "running"
    "error" -> "error"
    "cancelled" -> "cancelled"
    else -> "complete"
}

private fun com.eleckoi.android.sdk.author.AuthorChatSessionSnapshot.toSummaryJson() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("preview", messages.lastOrNull()?.displayContent.orEmpty())
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("characterId", characterId)
    put("characterName", characterName)
    put("characterAvatar", characterAvatar)
}

private fun AuthorApiEnvironment.scopedCharacterId(): String {
    return runtime.chatGateway?.snapshot()?.draft?.session?.characterId
        ?.takeIf { it.isNotBlank() }
        ?: runtime.characterId
}

private fun AuthorApiEnvironment.requireSessionInScope(
    snapshot: com.eleckoi.android.sdk.author.AuthorChatSnapshot,
    sessionId: String,
) {
    val characterId = snapshot.sessions.firstOrNull { it.id == sessionId }?.characterId
        ?: snapshot.draft?.session?.takeIf { it.id == sessionId }?.characterId
    if (characterId == null || characterId != scopedCharacterId()) {
        throw AuthorApiCallException(
            AuthorApiErrorCode.PermissionDenied,
            "作者前端只能操作当前角色的对话",
        )
    }
}
