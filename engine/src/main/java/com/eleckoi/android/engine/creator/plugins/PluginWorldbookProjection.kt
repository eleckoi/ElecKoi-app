package com.eleckoi.android.engine.creator.plugins

import kotlinx.serialization.json.*

/** Basic portable activation. Advanced ST recursion/vector rules remain compatibility metadata. */
fun projectPluginWorldbooks(books: Map<String, JsonObject>, messages: List<String>, scanText: String, conversationId: String): JsonArray {
    return buildJsonArray {
        books.toSortedMap().forEach { (name, book) ->
            (book["entries"] as? JsonArray).orEmpty().forEachIndexed { index, item ->
                val entry = item.jsonObject
                if (entry["enabled"]?.jsonPrimitive?.booleanOrNull == false) return@forEachIndexed
                val strategy = entry["strategy"] as? JsonObject
                val native = strategy == null && entry.containsKey("trigger_mode")
                val type = strategy?.get("type")?.jsonPrimitive?.content ?: if (native) {
                    if (entry["trigger_mode"]?.jsonPrimitive?.content == "always") "constant" else "selective"
                } else if (entry["constant"]?.jsonPrimitive?.booleanOrNull == true) "constant" else "selective"
                if (type == "vectorized") return@forEachIndexed
                val depth = strategy?.get("scan_depth")?.jsonPrimitive?.intOrNull ?: entry["keyword_scan_depth"]?.jsonPrimitive?.intOrNull
                val text = (if (depth == null) messages else messages.takeLast(depth.coerceAtLeast(0))).joinToString("\n") + "\n" + scanText
                val ignoreCase = entry["keyword_ignore_case"]?.jsonPrimitive?.booleanOrNull ?: true
                fun matches(key: JsonElement): Boolean {
                    val value = key.jsonPrimitive.content
                    val regex = Regex("^/([\\s\\S]*)/([a-z]*)$").matchEntire(value)
                    return if (regex != null || entry["keyword_use_regex"]?.jsonPrimitive?.booleanOrNull == true) {
                        Regex(regex?.groupValues?.get(1) ?: value, if (ignoreCase || regex?.groupValues?.get(2)?.contains('i') == true) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(text)
                    } else text.contains(value, ignoreCase)
                }
                val primary = (strategy?.get("keys") as? JsonArray) ?: (entry["keys"] as? JsonArray) ?: (entry["keywords"] as? JsonArray) ?: JsonArray(emptyList())
                val secondary = strategy?.get("keys_secondary") as? JsonObject
                val extra = (secondary?.get("keys") as? JsonArray) ?: (entry["condition_keywords"] as? JsonArray) ?: JsonArray(emptyList())
                val logic = secondary?.get("logic")?.jsonPrimitive?.content ?: entry["keyword_condition"]?.jsonPrimitive?.content ?: "and_any"
                val secondaryMatches = extra.isEmpty() || when (logic) {
                    "and_all" -> extra.all(::matches)
                    "not_all" -> !extra.all(::matches)
                    "not_any" -> extra.none(::matches)
                    else -> extra.any(::matches)
                }
                if (type != "constant" && (!primary.any(::matches) || !secondaryMatches)) return@forEachIndexed
                val position = entry["position"] as? JsonObject
                if (position?.get("type")?.jsonPrimitive?.content == "outlet") return@forEachIndexed
                add(buildJsonObject {
                    put("id", "$name:${entry["uid"]?.jsonPrimitive?.content ?: entry["id"]?.jsonPrimitive?.content ?: index}")
                    put("content", entry.getValue("content")); put("conversationId", conversationId)
                    put("role", position?.get("role") ?: entry["insert_role"] ?: JsonPrimitive("system"))
                    put("order", position?.get("order") ?: entry["order"] ?: JsonPrimitive(0))
                    position?.get("depth")?.let { put("depth", it) }
                })
            }
        }
    }
}
