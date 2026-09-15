package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorAgentProcessSnapshot
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun AuthorMessageSnapshot.toAuthorMessageJson() = buildJsonObject {
    put("id", id)
    put("conversationId", conversationId)
    put("role", role)
    put("content", content)
    put("displayContent", displayContent)
    put("variableState", variableStateJson.toJsonObject())
    put("status", status)
    put("createdAt", createdAt)
    put("turnId", turnId)
    put("speakerId", speakerId)
    put("speakerName", speakerName)
    put("speakerAvatar", speakerAvatar)
    sequence?.let { put("sequence", it) } ?: put("sequence", JsonNull)
    responseIndex?.let { put("responseIndex", it) } ?: put("responseIndex", JsonNull)
    put("process", buildJsonArray {
        process.forEach { item ->
            add(item.toAuthorProcessJson())
        }
    })
    put("attachments", buildJsonArray {
        attachments.forEach { attachment ->
            add(attachment.toAuthorMediaJson())
        }
    })
    put("openingOptions", buildJsonArray {
        openingOptions.forEach { opening ->
            add(buildJsonObject {
                put("id", opening.id)
                put("title", opening.title)
                put("content", opening.content)
                opening.displayContent?.let { put("displayContent", it) }
                put("initialVariableState", opening.initialVariableStateJson.toJsonObject())
            })
        }
    })
    put("selectedOpeningId", selectedOpeningId)
}

fun com.eleckoi.android.sdk.author.AuthorMediaResourceSnapshot.toAuthorMediaJson() = buildJsonObject {
    put("id", id)
    put("type", type)
    put("url", url)
    put("mimeType", mimeType)
    put("name", name)
    put("size", size)
    width?.let { put("width", it) } ?: put("width", JsonNull)
    height?.let { put("height", it) } ?: put("height", JsonNull)
    duration?.let { put("duration", it) } ?: put("duration", JsonNull)
    put("metadata", metadata)
}

fun AuthorAgentProcessSnapshot.toAuthorProcessJson() = buildJsonObject {
    put("id", id)
    put("kind", kind)
    put("status", status)
    put("toolName", toolName)
    put("arguments", arguments)
    put("summary", summary)
    put("detail", detail)
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    parentId?.let { put("parentId", it) }
    delegatedModel?.let { put("delegatedModel", it) }
}

private fun String.toJsonObject(): JsonObject = runCatching {
    ElecKoiJson.parseToJsonElement(ifBlank { "{}" }) as? JsonObject
}.getOrNull() ?: buildJsonObject {}
