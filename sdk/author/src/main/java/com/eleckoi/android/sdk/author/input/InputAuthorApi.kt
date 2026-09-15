package com.eleckoi.android.sdk.author.input

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.requireChatGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object InputAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("input.get")) { environment, _ ->
            val input = environment.runtime.chatGateway?.snapshot()?.input
                ?: environment.runtime.inputText
            buildJsonObject { put("text", input.orEmpty()) }
        },
        AuthorApiRoute(AuthorApiCatalog.require("input.set")) { environment, params ->
            val text = (params["text"] as? JsonPrimitive)?.content.orEmpty()
            environment.requireChatGateway().setInput(text).toAuthorJson()
            buildJsonObject { put("text", text) }
        },
        AuthorApiRoute(AuthorApiCatalog.require("input.append")) { environment, params ->
            val gateway = environment.requireChatGateway()
            val text = (params["text"] as? JsonPrimitive)?.content.orEmpty()
            val next = gateway.snapshot().input + text
            gateway.setInput(next).toAuthorJson()
            buildJsonObject { put("text", next) }
        },
        AuthorApiRoute(AuthorApiCatalog.require("input.clear")) { environment, _ ->
            environment.requireChatGateway().setInput("").toAuthorJson()
            buildJsonObject { put("text", "") }
        },
        AuthorApiRoute(AuthorApiCatalog.require("input.send")) { environment, _ ->
            val gateway = environment.requireChatGateway()
            val text = gateway.snapshot().input
            if (text.isBlank()) {
                buildJsonObject { put("submitted", false) }
            } else {
                gateway.send(text).toAuthorJson()
                buildJsonObject { put("submitted", true) }
            }
        },
    )
}
