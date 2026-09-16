package com.eleckoi.android.engine.agent.deepseek.trajectory

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class DshTrajectoryContextEntry(
    val key: String,
    val id: String,
    val title: String,
    val source: String,
    val anchor: String,
    val role: String,
    val content: String,
)

internal data class DshTrajectoryContextActivation(
    val turn: Int,
    val capturedAtMillis: Long,
    val entries: List<DshTrajectoryContextEntry>,
)

/**
 * Persists only ElecKoi-owned context injections, not complete provider requests.
 *
 * Content definitions are addressed by hash and written once per session. Each turn then stores a
 * compact ordered list of hashes, so a large stable cache setting is not duplicated on every turn.
 */
internal class DshTrajectoryContextStore private constructor(
    private val contextLog: (String) -> java.io.File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        paths: RuntimePaths,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(paths::persistentDeepSeekTrajectoryContextLog, clock)

    internal constructor(
        root: java.io.File,
        clock: () -> Long = System::currentTimeMillis,
    ) : this({ sessionId -> java.io.File(root, "$sessionId.jsonl") }, clock)

    private val knownDefinitionKeys = mutableMapOf<String, MutableSet<String>>()

    @Synchronized
    fun recordTurn(
        sessionId: String,
        runtimeTurnId: String,
        injections: List<AgentContextInjection>,
    ) {
        val turn = runtimeTurnId.substringAfterLast(':').toIntOrNull()?.takeIf { it > 0 } ?: return
        val visible = injections
            .asSequence()
            .filter { injection ->
                injection.content.isNotBlank() &&
                    (injection.activation == AgentContextActivation.Immediate ||
                        injection.activation == AgentContextActivation.FirstModelRequest)
            }
            .sortedWith(compareBy(AgentContextInjection::order, AgentContextInjection::id))
            .map(::contextEntry)
            .toList()
        if (visible.isEmpty()) return

        val log = contextLog(sessionId)
        val known = knownDefinitionKeys.getOrPut(sessionId) { readDefinitionKeys(log) }
        val rows = buildList {
            visible.forEach { entry ->
                if (known.add(entry.key)) add(definitionRow(entry))
            }
            add(
                buildJsonObject {
                    put("type", ActivationType)
                    put("turn", turn)
                    put("time", clock())
                    put("keys", buildJsonArray { visible.forEach { add(JsonPrimitive(it.key)) } })
                },
            )
        }
        log.parentFile?.mkdirs()
        log.appendText(rows.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }

    @Synchronized
    fun read(sessionId: String): List<DshTrajectoryContextActivation> {
        val log = contextLog(sessionId)
        if (!log.isFile) return emptyList()
        val definitions = linkedMapOf<String, DshTrajectoryContextEntry>()
        val activations = mutableListOf<DshTrajectoryContextActivation>()
        log.useLines(Charsets.UTF_8) { lines ->
            lines.filter(String::isNotBlank).forEach { line ->
                val row = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                    ?: return@forEach
                when (row.text("type")) {
                    DefinitionType -> row.toContextEntry()?.let { definitions[it.key] = it }
                    ActivationType -> {
                        val turn = row["turn"]?.jsonPrimitive?.longOrNull
                            ?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }
                            ?.toInt()
                            ?: return@forEach
                        val time = row["time"]?.jsonPrimitive?.longOrNull ?: return@forEach
                        val entries = (row["keys"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { element ->
                                (element as? JsonPrimitive)?.contentOrNull?.let(definitions::get)
                            }
                        if (entries.isNotEmpty()) {
                            activations += DshTrajectoryContextActivation(turn, time, entries)
                        }
                    }
                }
            }
        }
        knownDefinitionKeys[sessionId] = definitions.keys.toMutableSet()
        return activations
    }

    private fun contextEntry(injection: AgentContextInjection): DshTrajectoryContextEntry {
        val title = injection.traceTitle.trim().ifBlank { "上下文注入" }
        val source = injection.traceSource.trim().ifBlank { injection.anchor.wireValue }
        val signature = listOf(
            injection.id,
            title,
            source,
            injection.anchor.wireValue,
            injection.role.wireValue,
            injection.content,
        ).joinToString("\u0000")
        return DshTrajectoryContextEntry(
            key = signature.sha256(),
            id = injection.id,
            title = title,
            source = source,
            anchor = injection.anchor.wireValue,
            role = injection.role.wireValue,
            content = injection.content,
        )
    }

    private fun definitionRow(entry: DshTrajectoryContextEntry): JsonObject = buildJsonObject {
        put("type", DefinitionType)
        put("key", entry.key)
        put("id", entry.id)
        put("title", entry.title)
        put("source", entry.source)
        put("anchor", entry.anchor)
        put("role", entry.role)
        put("content", entry.content)
    }

    private fun JsonObject.toContextEntry(): DshTrajectoryContextEntry? {
        val key = text("key").takeIf(String::isNotBlank) ?: return null
        return DshTrajectoryContextEntry(
            key = key,
            id = text("id"),
            title = text("title").ifBlank { "上下文注入" },
            source = text("source"),
            anchor = text("anchor"),
            role = text("role"),
            content = text("content"),
        )
    }

    private fun readDefinitionKeys(log: java.io.File): MutableSet<String> {
        if (!log.isFile) return mutableSetOf()
        return log.useLines(Charsets.UTF_8) { lines ->
            lines.mapNotNull { line ->
                val row = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                row?.takeIf { it.text("type") == DefinitionType }?.text("key")
            }.filter(String::isNotBlank).toMutableSet()
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun JsonObject.text(name: String): String =
        (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val DefinitionType = "context/definition"
        const val ActivationType = "context/activation"
    }
}
