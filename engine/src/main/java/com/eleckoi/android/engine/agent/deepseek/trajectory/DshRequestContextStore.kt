package com.eleckoi.android.engine.agent.deepseek.trajectory

import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Reads exact provider-facing message snapshots recorded immediately before each model request. */
internal class DshRequestContextStore private constructor(
    private val contextLog: (String) -> File,
) {
    constructor(paths: RuntimePaths) : this(paths::persistentDeepSeekRequestContextLog)

    internal constructor(root: File) : this(
        { sessionId -> File(root, "${safeRuntimeThreadFile(sessionId)}.jsonl") },
    )

    fun read(sessionId: String): List<DshRequestContextSnapshot> {
        val log = contextLog(sessionId)
        if (!log.isFile) return emptyList()
        val definitions = linkedMapOf<String, DshRequestContextDefinition>()
        val snapshots = linkedMapOf<Long, DshRequestContextSnapshot>()
        log.useLines(Charsets.UTF_8) { lines ->
            lines.filter(String::isNotBlank).forEach { line ->
                val row = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                    ?: return@forEach
                when (row.text("type")) {
                    DefinitionType -> row.toDefinition()?.let { definition ->
                        definitions[definition.key] = definition
                    }
                    RequestType -> row.toSnapshot(definitions)?.let { snapshot ->
                        snapshots[snapshot.requestSeq] = snapshot
                    }
                }
            }
        }
        return snapshots.values.sortedBy(DshRequestContextSnapshot::requestSeq)
    }

    private fun JsonObject.toDefinition(): DshRequestContextDefinition? {
        val key = text("key").takeIf(String::isNotBlank) ?: return null
        val role = text("role").toContextRole() ?: return null
        val kind = text("kind").toContextKind() ?: return null
        val content = text("content").takeIf(String::isNotBlank) ?: return null
        return DshRequestContextDefinition(
            key = key,
            messageId = text("messageId"),
            role = role,
            kind = kind,
            title = text("title").ifBlank { role.defaultTitle() },
            source = text("source"),
            anchor = text("anchor"),
            content = content,
        )
    }

    private fun JsonObject.toSnapshot(
        definitions: Map<String, DshRequestContextDefinition>,
    ): DshRequestContextSnapshot? {
        val requestSeq = nonnegativeLong("requestSeq") ?: return null
        val turn = positiveInt("turn") ?: return null
        val step = positiveInt("step") ?: return null
        val timeMillis = nonnegativeLong("timeMillis") ?: return null
        val items = (get("items") as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            val reference = element as? JsonObject ?: return@mapIndexedNotNull null
            val definition = definitions[reference.text("key")] ?: return@mapIndexedNotNull null
            val order = reference.positiveInt("order") ?: index + 1
            definition.toItem(order)
        }.sortedBy(DshRequestContextItem::order)
        return DshRequestContextSnapshot(requestSeq, turn, step, timeMillis, items)
    }

    private fun JsonObject.text(name: String): String =
        (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.positiveInt(name: String): Int? =
        get(name)?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }

    private fun JsonObject.nonnegativeLong(name: String): Long? =
        get(name)?.jsonPrimitive?.longOrNull?.takeIf { it >= 0L }

    private companion object {
        const val DefinitionType = "definition"
        const val RequestType = "request"
    }
}

private data class DshRequestContextDefinition(
    val key: String,
    val messageId: String,
    val role: DshRequestContextRole,
    val kind: DshRequestContextKind,
    val title: String,
    val source: String,
    val anchor: String,
    val content: String,
) {
    fun toItem(order: Int) = DshRequestContextItem(
        order = order,
        messageId = messageId,
        role = role,
        kind = kind,
        title = title,
        source = source,
        anchor = anchor,
        content = content,
    )
}

private fun String.toContextRole(): DshRequestContextRole? = when (this) {
    "system" -> DshRequestContextRole.System
    "user" -> DshRequestContextRole.User
    "assistant" -> DshRequestContextRole.Assistant
    else -> null
}

private fun String.toContextKind(): DshRequestContextKind? = when (this) {
    "system" -> DshRequestContextKind.System
    "prompt" -> DshRequestContextKind.Prompt
    "history" -> DshRequestContextKind.History
    "user" -> DshRequestContextKind.User
    "assistant" -> DshRequestContextKind.Assistant
    "tool" -> DshRequestContextKind.Tool
    "context" -> DshRequestContextKind.Context
    else -> null
}

private fun DshRequestContextRole.defaultTitle(): String = when (this) {
    DshRequestContextRole.System -> "系统提示词"
    DshRequestContextRole.User -> "用户消息"
    DshRequestContextRole.Assistant -> "助手消息"
}

private fun safeRuntimeThreadFile(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(160).ifBlank { "default" }
