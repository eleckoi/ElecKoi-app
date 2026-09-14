package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun AuthorMessageSnapshot.toAuthorMessageJson() = buildJsonObject {
    put("id", id)
    put("role", role)
    put("content", content)
    put("reasoningContent", reasoningContent)
    put("provider", provider)
    put("model", model)
    put("createdAt", createdAt)
    put("pending", pending)
    put("variableStateJson", variableStateJson)
    put("hasAgentProcess", hasAgentProcess)
    put("agentActivity", agentActivity?.let { activity ->
        buildJsonObject {
            put("label", activity.label)
            put("running", activity.running)
            put("thinking", activity.thinking)
        }
    } ?: JsonNull)
    put("toolCalls", buildJsonArray {
        toolCalls.forEach { call ->
            add(buildJsonObject {
                put("callId", call.callId)
                put("name", call.name)
                put("arguments", call.arguments)
                put("result", call.result)
                put("state", call.state)
                put("rollbackOnAbort", call.rollbackOnAbort)
            })
        }
    })
}
