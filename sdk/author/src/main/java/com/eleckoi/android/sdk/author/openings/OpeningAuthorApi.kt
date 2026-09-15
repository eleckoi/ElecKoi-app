package com.eleckoi.android.sdk.author.openings

import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.AuthorOpeningOptionSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningStateSnapshot
import com.eleckoi.android.sdk.author.requireOpeningGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object OpeningAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("openings.list")) { environment, _ ->
            environment.currentOpeningState().toListJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("openings.current")) { environment, _ ->
            environment.currentOpeningState().toCurrentJson()
        },
        AuthorApiRoute(AuthorApiCatalog.require("openings.select")) { environment, params ->
            val id = (params["id"] as? JsonPrimitive)?.content.orEmpty()
            if (id.isBlank()) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.InvalidParams,
                    "openings.select 需要开场白 id",
                )
            }
            environment.requireOpeningGateway().selectOpening(id).toAuthorJson()
            buildJsonObject { put("selectedId", id) }
        },
    )
}

private fun AuthorApiEnvironment.currentOpeningState(): AuthorOpeningStateSnapshot? =
    runtime.openingGateway?.openingSnapshot()

internal fun AuthorOpeningStateSnapshot?.toListJson() = buildJsonObject {
    val state = this@toListJson
    put("items", buildJsonArray {
        state?.items.orEmpty().forEach { option ->
            add(option.toJson())
        }
    })
}

internal fun AuthorOpeningStateSnapshot?.toCurrentJson(): kotlinx.serialization.json.JsonElement {
    val state = this@toCurrentJson
    val selected = state?.items?.firstOrNull { it.id == state.selectedId }
    return selected?.toJson() ?: JsonNull
}

private fun AuthorOpeningOptionSnapshot.toJson() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("content", content)
    put("displayContent", displayContent)
    put(
        "initialVariableState",
        runCatching { Json.parseToJsonElement(initialVariableStateJson) }.getOrNull()
            ?: buildJsonObject {},
    )
}
