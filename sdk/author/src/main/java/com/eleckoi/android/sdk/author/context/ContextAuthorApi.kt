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
            buildJsonObject {
                put("surface", runtime.surface)
                put("characterId", runtime.characterId)
                put("characterName", runtime.characterName)
                put("characterAvatarUrl", runtime.characterAvatarUrl)
                put("messageId", runtime.currentMessage?.id.orEmpty())
                put("messageRole", runtime.currentMessage?.role.orEmpty())
                put("hasChat", snapshot?.draft != null || runtime.chatSession != null || runtime.currentMessage != null)
                put("hasVariableConfig", runtime.variableConfig != null)
                put("hasSettingLibrary", runtime.settingLibrary != null)
                put("hasInput", snapshot != null || runtime.inputText != null)
                put("isGenerating", snapshot?.isGenerating ?: false)
            }
        },
    )
}
