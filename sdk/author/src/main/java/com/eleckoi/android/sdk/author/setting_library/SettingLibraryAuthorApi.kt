package com.eleckoi.android.sdk.author.setting_library

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SettingLibraryAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("settingLibrary.current")) { environment, _ ->
            environment.runtime.chatGateway?.snapshot()?.draft?.settingLibrary
                ?.document
                ?: environment.runtime.settingLibrary?.document
                ?: JsonNull
        },
        AuthorApiRoute(AuthorApiCatalog.require("settingLibrary.getSummary")) { environment, _ ->
            val library = environment.runtime.chatGateway?.snapshot()?.draft?.settingLibrary
                ?: environment.runtime.settingLibrary
            library?.let {
                buildJsonObject {
                        put("characterId", it.characterId)
                        put("name", it.name)
                        put("entryCount", it.entryCount)
                        put("groupCount", it.groupCount)
                        put("activeVersionId", it.activeVersionId)
                }
            } ?: JsonNull
        },
    )
}
