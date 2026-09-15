package com.eleckoi.android.sdk.author.events

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiEventAccess
import com.eleckoi.android.sdk.author.AuthorApiRoute
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

internal object AuthorEventApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("events.list")) { environment, _ ->
            listAvailableEvents(environment.permissions)
        },
    )

    internal fun listAvailableEvents(permissions: Set<com.eleckoi.android.sdk.author.AuthorApiPermission>) =
        buildJsonObject {
            put("items", buildJsonArray {
                AuthorApiEventAccess.knownEventNames
                    .filter { name -> AuthorApiEventAccess.canReceive(name, permissions) }
                    .forEach { name -> add(JsonPrimitive(name)) }
            })
        }
}
