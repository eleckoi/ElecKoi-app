package com.eleckoi.android.engine.creator.plugins

import com.eleckoi.android.engine.agent.api.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/** Request-scoped material. A plugin never writes its prompt into the conversation ledger. */
object PluginPromptPipeline {
    private val prompts = ConcurrentHashMap<String, JsonArray>()
    private val macros = ConcurrentHashMap<String, MutableMap<String, String>>()
    /** Extra messages are request-local inputs for contextual auxiliary calls, not ledger writes. */
    var beforeGeneration: (suspend (String, String, List<String>) -> Unit)? = null

    fun set(owner: String, entries: JsonArray) { prompts[owner] = entries }
    fun remove(owner: String) { prompts.remove(owner); macros.remove(owner) }
    fun scanText(conversationId: String): String = prompts.values.flatMap { it.map(JsonElement::jsonObject) }.filter {
        it["should_scan"]?.jsonPrimitive?.booleanOrNull == true &&
            it["conversationId"]?.jsonPrimitive?.contentOrNull.let { id -> id.isNullOrBlank() || id == conversationId }
    }.joinToString("\n") { it.getValue("content").jsonPrimitive.content }
    fun registerMacro(name: String, value: String, owner: String = "frontend") { macros.computeIfAbsent(owner) { ConcurrentHashMap() }[name] = value }
    fun unregisterMacro(name: String, owner: String = "frontend") { macros[owner]?.remove(name) }
    fun expand(text: String): String = macros.toSortedMap().values.flatMap { it.toSortedMap().entries }.fold(text) { result, (name, value) ->
        result.replace("{{$name}}", value)
    }

    fun snapshot(conversationId: String, consumeOnce: Boolean = true): List<AgentContextInjection> =
        prompts.entries.flatMap { (owner, values) ->
            val selected = values.map { it.jsonObject }.filter {
                it["conversationId"]?.jsonPrimitive?.contentOrNull.let { id -> id.isNullOrBlank() || id == conversationId }
            }
            if (consumeOnce) {
                val used = selected.filter { it["once"]?.jsonPrimitive?.booleanOrNull == true }.toSet()
                prompts.computeIfPresent(owner) { _, current -> JsonArray(current.filterNot { it in used }) }
            }
            selected.filter { it["position"]?.jsonPrimitive?.content != "none" }.map { entry ->
                val anchor = entry["anchor"]?.jsonPrimitive?.contentOrNull
                AgentContextInjection(
                    id = "plugin:$owner:${entry.getValue("id").jsonPrimitive.content}",
                    anchor = if (anchor == null) AgentContextAnchor.BeforeLatestUserInput else
                        AgentContextAnchor.entries.first { it.wireValue == anchor },
                    role = AgentContextRole.entries.first { it.wireValue == (entry["role"]?.jsonPrimitive?.content ?: "system") },
                    activation = AgentContextActivation.FirstModelRequest,
                    content = expand(entry.getValue("content").jsonPrimitive.content),
                    order = entry["order"]?.jsonPrimitive?.intOrNull ?: 0,
                    traceSource = "plugin:$owner",
                    historyDepth = entry["depth"]?.jsonPrimitive?.intOrNull,
                )
            }
        }.sortedWith(compareBy({ it.order }, { it.id }))
}
