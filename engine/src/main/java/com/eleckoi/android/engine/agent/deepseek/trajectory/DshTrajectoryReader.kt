package com.eleckoi.android.engine.agent.deepseek.trajectory

import android.content.Context
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Reads DSH's authoritative session log without routing its contents through the chat ledger. */
class DshTrajectoryReader(context: Context) {
    private val paths = RuntimePaths(context.applicationContext)

    fun read(
        runtimeThreadId: String,
        options: DshTrajectoryReadOptions = DshTrajectoryReadOptions(),
    ): DshTrajectoryPage {
        if (!ThreadId.matches(runtimeThreadId)) return DshTrajectoryPage.empty(runtimeThreadId)
        val log = paths.persistentDeepSeekSessionLog(runtimeThreadId)
            ?: return DshTrajectoryPage.empty(runtimeThreadId)
        val decoded = readLog(log, runtimeThreadId)
        val projection = DshTrajectoryProjector.project(decoded.events, decoded.header)
        val eligible = options.beforeIndex?.let { before ->
            projection.records.filter { record -> record.index < before }
        } ?: projection.records
        val limit = options.limit.coerceIn(1, MaximumPageSize)
        val start = (eligible.size - limit).coerceAtLeast(0)
        val records = eligible.subList(start, eligible.size)
        return DshTrajectoryPage(
            runtimeThreadId = runtimeThreadId,
            records = records,
            totalRecords = projection.records.size,
            hasMore = start > 0,
            beforeIndex = records.firstOrNull()?.index,
            startedAtMillis = projection.startedAtMillis,
            completedAtMillis = projection.completedAtMillis,
        )
    }

    private fun readLog(log: File, runtimeThreadId: String): DecodedLog {
        openLog(log).use { input ->
            val raw = InputStreamReader(input, Charsets.UTF_8).use { reader -> reader.readText() }
            val finalLineComplete = raw.endsWith('\n') || raw.endsWith('\r')
            val lines = raw.split('\n').map { line -> line.trimEnd('\r') }
            val headerLineIndex = lines.indexOfFirst(String::isNotBlank)
            if (headerLineIndex < 0) {
                throw IllegalStateException("DSH 轨迹日志缺少有效的会话头。")
            }
            val header = parseObject(lines[headerLineIndex])
            check(header.text("type") == "session" && header.text("id") == runtimeThreadId) {
                "DSH 轨迹日志的会话标识不匹配。"
            }
            val events = buildList {
                lines.forEachIndexed { index, line ->
                    if (index <= headerLineIndex || line.isBlank()) return@forEachIndexed
                    val event = runCatching { parseObject(line) }.getOrElse { error ->
                        // The active writer can leave the final compressed frame and JSON row
                        // unfinished. Match the desktop reader by accepting the complete prefix.
                        if (index == lines.lastIndex && !finalLineComplete) return@forEachIndexed
                        throw IllegalStateException("DSH 轨迹日志包含损坏的记录。", error)
                    }
                    // DSH packs only assistant/chunk deltas into these storage rows. The PC
                    // trajectory projector ignores those deltas too, so they never need to be
                    // expanded just to reconstruct the semantic trajectory.
                    if (event.text("type") !in PackedChunkRowTypes) add(event)
                }
            }
            return DecodedLog(header, events)
        }
    }

    private fun openLog(log: File): InputStream {
        val input = log.inputStream().buffered()
        if (!log.name.endsWith(".zstd")) return input
        return try {
            ZstdInputStreamNoFinalizer(input).setContinuous(true)
        } catch (error: Throwable) {
            input.close()
            throw IllegalStateException("无法解压 DSH 轨迹日志。", error)
        }
    }

    private fun parseObject(line: String): JsonObject =
        Json.parseToJsonElement(line) as? JsonObject
            ?: throw IllegalStateException("DSH 轨迹记录不是 JSON 对象。")

    private data class DecodedLog(
        val header: JsonObject,
        val events: List<JsonObject>,
    )

    private companion object {
        const val MaximumPageSize = 1_000
        val ThreadId = Regex("^[A-Za-z0-9._:-]{1,160}$")
        val PackedChunkRowTypes = setOf("text-chunks", "reasoning-chunks", "tool-call-chunks")
    }
}

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
