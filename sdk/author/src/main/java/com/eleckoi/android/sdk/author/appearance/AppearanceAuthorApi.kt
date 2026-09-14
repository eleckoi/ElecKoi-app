package com.eleckoi.android.sdk.author.appearance

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object AppearanceAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("appearance.getChatBackground")) { environment, _ ->
            val snapshot = environment.runtime.chatBackground
            buildJsonObject {
                put("available", snapshot != null)
                put("mode", snapshot?.mode.orEmpty())
                put("imageUrl", snapshot?.imageUrl.orEmpty())
                put("opacity", snapshot?.opacity ?: 0.72)
                put("blur", snapshot?.blur ?: 0.0)
                put("scrim", snapshot?.scrim ?: 0.22)
                put("hasImage", snapshot?.hasImage ?: false)
            }
        },
        AuthorApiRoute(AuthorApiCatalog.require("appearance.openChatBackgroundSettings")) { environment, _ ->
            val handler = environment.runtime.openChatBackgroundSettings
            if (handler == null) {
                buildJsonObject {
                    put("accepted", false)
                    put("available", false)
                }
            } else {
                handler.invoke()
                buildJsonObject {
                    put("accepted", true)
                    put("available", true)
                }
            }
        },
    )
}
