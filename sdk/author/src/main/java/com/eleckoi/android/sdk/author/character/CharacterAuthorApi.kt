package com.eleckoi.android.sdk.author.character

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CharacterAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("character.current")) { environment, _ ->
            val runtime = environment.runtime
            buildJsonObject {
                put("available", runtime.characterId.isNotBlank())
                put("id", runtime.characterId)
                put("name", runtime.characterName)
                put("avatarUrl", runtime.characterAvatarUrl)
            }
        },
    )
}
