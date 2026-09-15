package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.requireChatGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object MessageAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("messages.list")) { environment, _ ->
            val runtime = environment.runtime
            val messages = runtime.chatGateway?.snapshot()?.draft?.session?.messages
                ?: runtime.chatSession?.messages
                ?: runtime.currentMessage?.let(::listOf)
                ?: emptyList()
            buildJsonArray { messages.forEach { add(it.toAuthorMessageJson()) } }
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.get")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            if (id.isBlank()) {
                throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "messages.get 需要消息 id")
            }
            val runtime = environment.runtime
            val scopedMessage = runtime.currentMessage
            if (runtime.chatGateway == null && scopedMessage != null && scopedMessage.id != id) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.PermissionDenied,
                    "消息内前端只能读取所属消息",
                )
            }
            val message = runtime.chatGateway?.snapshot()?.draft?.session?.messages?.firstOrNull { it.id == id }
                ?: runtime.chatSession?.messages?.firstOrNull { it.id == id }
                ?: scopedMessage?.takeIf { it.id == id }
            if (message == null && scopedMessage == null && runtime.chatGateway == null && runtime.chatSession == null) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.ContextUnavailable,
                    "当前页面没有聊天消息上下文",
                )
            }
            message?.toAuthorMessageJson() ?: throw AuthorApiCallException(
                AuthorApiErrorCode.NotFound,
                "没有找到消息：$id",
            )
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.current")) { environment, _ ->
            val runtime = environment.runtime
            val message = runtime.chatGateway?.snapshot()?.draft?.session?.messages?.lastOrNull()
                ?: runtime.chatSession?.messages?.lastOrNull()
                ?: runtime.currentMessage
            message?.toAuthorMessageJson() ?: throw AuthorApiCallException(
                AuthorApiErrorCode.ContextUnavailable,
                "当前页面没有聊天消息上下文",
            )
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.deleteFrom")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty().trim()
            if (id.isBlank()) {
                throw AuthorApiCallException(AuthorApiErrorCode.InvalidParams, "messages.deleteFrom 需要消息 id")
            }
            val result = environment.requireChatGateway().deleteMessagesFrom(id)
            buildJsonObject {
                put("ok", true)
                put("deletedMessageCount", result.deletedMessageCount)
                put("remainingMessageCount", result.remainingMessageCount)
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.regenerate")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().regenerate(id).toAuthorJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("messages.editAndRegenerate")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            val text = (params["text"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().editAndRegenerate(id, text).toAuthorJson()
        },
    )
}
