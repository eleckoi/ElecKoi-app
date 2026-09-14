package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.requireChatGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object MessageAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("messages.list")) { environment, _ ->
            val runtime = environment.runtime
            val messages = runtime.currentMessage?.let(::listOf)
                ?: runtime.chatGateway?.snapshot()?.draft?.session?.messages
                ?: runtime.chatSession?.messages
                ?: emptyList()
            buildJsonObject {
                put("available", messages.isNotEmpty())
                put("items", buildJsonArray { messages.forEach { add(it.toAuthorMessageJson()) } })
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.get")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            if (id.isBlank()) {
                throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "messages.get 需要消息 id")
            }
            val runtime = environment.runtime
            val scopedMessage = runtime.currentMessage
            if (scopedMessage != null && scopedMessage.id != id) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.PermissionDenied,
                    "消息内前端只能读取所属消息",
                )
            }
            val message = scopedMessage
                ?: runtime.chatGateway?.snapshot()?.draft?.session?.messages?.firstOrNull { it.id == id }
                ?: runtime.chatSession?.messages?.firstOrNull { it.id == id }
            if (message == null && scopedMessage == null && runtime.chatGateway == null && runtime.chatSession == null) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.ContextUnavailable,
                    "当前页面没有聊天消息上下文",
                )
            }
            buildJsonObject {
                put("message", message?.toAuthorMessageJson() ?: JsonNull)
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.current")) { environment, _ ->
            val runtime = environment.runtime
            val message = runtime.currentMessage
                ?: runtime.chatGateway?.snapshot()?.draft?.session?.messages?.lastOrNull()
                ?: runtime.chatSession?.messages?.lastOrNull()
            buildJsonObject {
                put("available", message != null)
                put("message", message?.toAuthorMessageJson() ?: JsonNull)
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.regenerate")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().regenerate(id).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.edit")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            val text = (params["text"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().editMessage(id, text).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.editAndRegenerate")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            val text = (params["text"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().editAndRegenerate(id, text).toAuthorJson()
        },
    )
}
