package com.eleckoi.android.engine.agent.deepseek.trajectory

import android.content.Context
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Projects the logical artifact decoded by DSH without routing it through the chat ledger. */
class DshTrajectoryReader(
    context: Context,
    private val inspectSession: suspend (String) -> DshSessionInspection?,
) {
    private val paths = RuntimePaths(context.applicationContext)
    private val requestContextStore = DshRequestContextStore(paths)

    suspend fun read(
        runtimeThreadId: String,
        options: DshTrajectoryReadOptions = DshTrajectoryReadOptions(),
    ): DshTrajectoryPage {
        if (!ThreadId.matches(runtimeThreadId)) return DshTrajectoryPage.empty(runtimeThreadId)
        val decoded = inspectSession(runtimeThreadId)
            ?: return DshTrajectoryPage.empty(runtimeThreadId)
        requireMatchingDshSessionHeader(decoded.header, runtimeThreadId)
        val projection = DshTrajectoryProjector.project(
            input = decoded.events,
            header = decoded.header,
        )
        val contextByRequest = requestContextStore.read(runtimeThreadId)
            .associateBy(DshRequestContextSnapshot::requestSeq)
        val projectedRecords = projection.records.map { record ->
            record.copy(
                requests = record.requests.map { request ->
                    request.copy(context = contextByRequest[request.seq]?.items.orEmpty())
                },
            )
        }
        return paginateDshTrajectory(runtimeThreadId, projectedRecords, projection, options)
    }

    private companion object {
        val ThreadId = Regex("^[A-Za-z0-9._:-]{1,160}$")
    }
}

internal fun paginateDshTrajectory(
    runtimeThreadId: String,
    projectedRecords: List<DshTrajectoryRecord>,
    projection: DshTrajectoryProjection,
    options: DshTrajectoryReadOptions,
): DshTrajectoryPage {
    val eligible = options.beforeIndex?.let { before ->
        projectedRecords.filter { record -> record.index < before }
    } ?: projectedRecords
    val limit = options.limit.coerceIn(1, MaximumTrajectoryPageSize)
    val start = (eligible.size - limit).coerceAtLeast(0)
    val records = eligible.subList(start, eligible.size)
    return DshTrajectoryPage(
        runtimeThreadId = runtimeThreadId,
        records = records,
        totalRecords = projectedRecords.size,
        hasMore = start > 0,
        beforeIndex = records.firstOrNull()?.index,
        startedAtMillis = projection.startedAtMillis,
        completedAtMillis = projection.completedAtMillis,
    )
}

private const val MaximumTrajectoryPageSize = 1_000

/** DSH returns a logical SessionHeader here; the physical JSONL-only `type` tag is absent. */
internal fun requireMatchingDshSessionHeader(header: JsonObject, runtimeThreadId: String) {
    check(header.text("id") == runtimeThreadId) { "DSH 轨迹日志的会话标识不匹配。" }
}

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
