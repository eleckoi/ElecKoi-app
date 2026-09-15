package com.eleckoi.android.sdk.author.character

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CharacterAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("character.current")) { environment, _ ->
            val runtime = environment.runtime
            val session = runtime.chatGateway?.snapshot()?.draft?.session ?: runtime.chatSession
            if (session == null && runtime.characterId.isBlank()) {
                kotlinx.serialization.json.JsonNull
            } else {
                buildJsonObject {
                    put("id", session?.characterId ?: runtime.characterId)
                    put("name", session?.characterName ?: runtime.characterName)
                    put("avatar", session?.characterAvatar.orEmpty())
                    put("persona", session?.characterPersona ?: buildJsonObject {})
                }
            }
        },
    )
}
