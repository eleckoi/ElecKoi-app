package com.eleckoi.android.sdk.author.media

import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.messages.toAuthorMediaJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object MediaAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("media.getMessageAttachments")) { environment, params ->
            val messages = environment.authorMessages()
            val requestedId = (params["messageId"] as? JsonPrimitive)?.content.orEmpty().trim()
            val message = if (requestedId.isBlank()) {
                environment.runtime.currentMessage ?: messages.lastOrNull()
            } else {
                messages.firstOrNull { it.id == requestedId }
            } ?: throw AuthorApiCallException(AuthorApiErrorCode.NotFound, "没有找到消息：$requestedId")
            buildJsonObject {
                put("items", buildJsonArray {
                    message.attachments.forEach { add(it.toAuthorMediaJson()) }
                })
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("media.getMessageAttachment")) { environment, params ->
            val messageId = (params["messageId"] as? JsonPrimitive)?.content.orEmpty().trim()
            val attachmentId = (params["attachmentId"] as? JsonPrimitive)?.content.orEmpty().trim()
            if (messageId.isBlank() || attachmentId.isBlank()) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.InvalidParams,
                    "消息 id 和附件 id 不能为空",
                )
            }
            environment.authorMessages()
                .firstOrNull { it.id == messageId }
                ?.attachments
                ?.firstOrNull { it.id == attachmentId }
                ?.toAuthorMediaJson()
                ?: throw AuthorApiCallException(AuthorApiErrorCode.NotFound, "找不到这条消息里的媒体附件")
        },
    )
}

private fun com.eleckoi.android.sdk.author.AuthorApiEnvironment.authorMessages() =
    runtime.chatGateway?.snapshot()?.draft?.session?.messages
        ?: runtime.chatSession?.messages
        ?: runtime.currentMessage?.let(::listOf)
        ?: emptyList()
