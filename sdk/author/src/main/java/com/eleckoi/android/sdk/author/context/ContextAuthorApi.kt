package com.eleckoi.android.sdk.author.context

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object ContextAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("context.current")) { environment, _ ->
            val runtime = environment.runtime
            val snapshot = runtime.chatGateway?.snapshot()
            val session = snapshot?.draft?.session ?: runtime.chatSession
            val message = runtime.currentMessage ?: session?.messages?.lastOrNull()
            buildJsonObject {
                put("surface", if (runtime.surface == "inline_message") "message-renderer" else "chat-ui")
                put("scope", "current-character")
                put("conversationId", session?.id ?: message?.conversationId.orEmpty())
                put("conversationTitle", session?.title.orEmpty())
                put("messageId", message?.id.orEmpty())
                put("characterId", session?.characterId?.ifBlank { runtime.characterId } ?: runtime.characterId)
            }
        },
    )
}
