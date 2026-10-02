package com.eleckoi.android.engine.creator.plugins

import com.eleckoi.android.engine.agent.api.*
import kotlinx.serialization.json.*

/** Project the same frozen context positions for a supporting generation without Agent tools. */
fun projectPluginMessages(history: JsonArray, injections: List<AgentContextInjection>): JsonArray {
    val dialogue = history.indices.filter { history[it].jsonObject["role"]?.jsonPrimitive?.content in listOf("user", "assistant") }
    val latestUser = history.indexOfLast { it.jsonObject["role"]?.jsonPrimitive?.content == "user" }.let { if (it < 0) history.size else it }
    val groups = injections.sortedWith(compareBy({ it.order }, { it.id })).groupBy { entry ->
        entry.historyDepth?.let { depth ->
            if (depth == 0) history.size else dialogue.getOrNull(dialogue.size - depth) ?: 0
        } ?: when (entry.anchor) {
            AgentContextAnchor.Instructions, AgentContextAnchor.BeforeToolContext, AgentContextAnchor.ToolContext,
            AgentContextAnchor.AfterToolContext, AgentContextAnchor.BeforeHistory -> 0
            AgentContextAnchor.AfterHistory, AgentContextAnchor.BeforeLatestUserInput -> latestUser
            AgentContextAnchor.AfterLatestUserInput -> (latestUser + 1).coerceAtMost(history.size)
            AgentContextAnchor.BeforeToolFlow, AgentContextAnchor.AfterToolFlow -> history.size
        }
    }
    return buildJsonArray {
        (0..history.size).forEach { index ->
            groups[index].orEmpty().forEach { add(buildJsonObject { put("role", it.role.wireValue); put("content", it.content) }) }
            if (index < history.size) add(history[index])
        }
    }
}
