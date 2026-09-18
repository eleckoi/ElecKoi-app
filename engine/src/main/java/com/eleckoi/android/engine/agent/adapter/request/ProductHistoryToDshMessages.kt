package com.eleckoi.android.engine.agent.adapter.request

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Converts Room-owned product history into DSH's provider-neutral Message/ContentBlock shape. */
internal object ProductHistoryToDshMessages {
    fun convert(items: List<JsonObject>): List<JsonObject> =
        items.mapIndexedNotNull(::convertItem)

    private fun convertItem(index: Int, item: JsonObject): JsonObject? = when (item.string("type")) {
        null, "message" -> message(index, item)
        "function_call", "custom_tool_call" -> toolCall(index, item)
        "function_call_output", "custom_tool_call_output" -> toolResult(index, item)
        "reasoning" -> reasoning(index, item)
        else -> null
    }

    private fun message(index: Int, item: JsonObject): JsonObject? {
        val sourceRole = item.string("role") ?: return null
        val role = when (sourceRole) {
            "user" -> "user"
            "assistant" -> "assistant"
            else -> return null
        }
        val blocks = buildJsonArray {
            when (val content = item["content"]) {
                is JsonPrimitive -> content.contentOrNull
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add(textBlock(it)) }
                is JsonArray -> content.forEach { part ->
                    val value = part as? JsonObject ?: return@forEach
                    when (value.string("type")) {
                        "input_text", "output_text", "text" -> value.string("text")
                            ?.takeIf(String::isNotEmpty)
                            ?.let { add(textBlock(it)) }
                        "input_image", "image_url" -> value.string("image_url")
                            ?.takeIf(String::isNotBlank)
                            ?.let { add(dataImageBlock(it)) }
                    }
                }
                else -> Unit
            }
        }
        if (blocks.isEmpty()) return null
        return dshMessage(
            id = "eleckoi-product-history-$index",
            role = role,
            content = blocks,
            source = pluginSource(),
        )
    }

    private fun toolCall(index: Int, item: JsonObject): JsonObject? {
        val callId = item.string("call_id") ?: item.string("id") ?: return null
        val name = item.string("name") ?: return null
        val arguments = item.string("arguments") ?: "{}"
        return dshMessage(
            id = "eleckoi-product-tool-call-$index",
            role = "assistant",
            content = buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool-call")
                    put("id", callId)
                    put("name", name)
                    put("arguments", arguments)
                })
            },
            source = pluginSource(),
        )
    }

    private fun toolResult(index: Int, item: JsonObject): JsonObject? {
        val callId = item.string("call_id") ?: item.string("id") ?: return null
        val output = item["output"].asToolResultText()
        return dshMessage(
            id = "eleckoi-product-tool-result-$index",
            role = "user",
            content = buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool-result")
                    put("toolCallId", callId)
                    put("content", buildJsonArray { add(textBlock(output.ifEmpty { "(no output)" })) })
                    item.boolean("is_error")?.let { put("isError", it) }
                })
            },
            source = buildJsonObject {
                put("kind", "tool")
                put("callId", callId)
            },
        )
    }

    private fun reasoning(index: Int, item: JsonObject): JsonObject? {
        val text = when (val summary = item["summary"]) {
            is JsonArray -> summary.mapNotNull { part ->
                (part as? JsonObject)?.string("text")
            }.joinToString("\n")
            is JsonPrimitive -> summary.contentOrNull.orEmpty()
            else -> item.string("text").orEmpty()
        }.takeIf(String::isNotBlank) ?: return null
        return dshMessage(
            id = "eleckoi-product-reasoning-$index",
            role = "assistant",
            content = buildJsonArray {
                add(buildJsonObject {
                    put("type", "reasoning")
                    put("text", text)
                })
            },
            source = pluginSource(),
        )
    }

    private fun dshMessage(
        id: String,
        role: String,
        content: JsonArray,
        source: JsonObject,
    ) = buildJsonObject {
        put("id", id)
        put("role", role)
        put("content", content)
        put("source", source)
    }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun dataImageBlock(dataUrl: String) = buildJsonObject {
        put("type", "eleckoi-data-image")
        put("dataUrl", dataUrl)
    }

    private fun pluginSource() = buildJsonObject {
        put("kind", "plugin")
        put("plugin", "eleckoi-product-history")
    }

    private fun JsonElement?.asToolResultText(): String = when (this) {
        null, JsonNull -> ""
        is JsonPrimitive -> contentOrNull.orEmpty()
        else -> toString()
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
