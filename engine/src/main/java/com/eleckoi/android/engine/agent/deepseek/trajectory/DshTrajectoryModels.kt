package com.eleckoi.android.engine.agent.deepseek.trajectory

import kotlinx.serialization.json.JsonObject

/** Logical current-format artifact returned by DSH's persistence service. */
data class DshSessionInspection(
    val header: JsonObject,
    val inheritedEventCount: Int,
    val events: List<JsonObject>,
)

enum class DshTrajectoryRecordKind {
    System,
    User,
    Context,
    Assistant,
    Tool,
    Compaction,
}

enum class DshTrajectoryRecordStatus {
    Running,
    Complete,
    Error,
    Cancelled,
}

enum class DshRequestContextRole {
    System,
    User,
    Assistant,
}

enum class DshRequestContextKind {
    System,
    Prompt,
    History,
    User,
    Assistant,
    Tool,
    Context,
}

data class DshRequestContextItem(
    val order: Int,
    val messageId: String,
    val role: DshRequestContextRole,
    val kind: DshRequestContextKind,
    val title: String,
    val source: String,
    val anchor: String,
    val content: String,
)

data class DshRequestContextSnapshot(
    val requestSeq: Long,
    val turn: Int,
    val step: Int,
    val timeMillis: Long,
    val items: List<DshRequestContextItem>,
)

data class DshTrajectoryRequest(
    val number: Int,
    val seq: Long,
    val turn: Int?,
    val step: Int?,
    val status: DshTrajectoryRecordStatus,
    val reason: String,
    val provider: String,
    val model: String,
    val detail: String,
    val rawJson: String,
    val context: List<DshRequestContextItem> = emptyList(),
    val timeMillis: Long?,
    val durationMillis: Long?,
)

data class DshTrajectoryRecord(
    val id: String,
    val index: Int,
    val seq: Long,
    val type: String,
    val kind: DshTrajectoryRecordKind,
    val title: String,
    val preview: String,
    val source: String,
    val input: String,
    val output: String,
    val detail: String,
    val rawJson: String,
    val timeMillis: Long?,
    val durationMillis: Long?,
    val turn: Int?,
    val step: Int?,
    val status: DshTrajectoryRecordStatus,
    val requests: List<DshTrajectoryRequest>,
)

data class DshTrajectoryPage(
    val runtimeThreadId: String,
    val records: List<DshTrajectoryRecord>,
    val totalRecords: Int,
    val hasMore: Boolean,
    val beforeIndex: Int?,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
) {
    companion object {
        fun empty(runtimeThreadId: String = "") = DshTrajectoryPage(
            runtimeThreadId = runtimeThreadId,
            records = emptyList(),
            totalRecords = 0,
            hasMore = false,
            beforeIndex = null,
            startedAtMillis = null,
            completedAtMillis = null,
        )
    }
}

data class DshTrajectoryReadOptions(
    val beforeIndex: Int? = null,
    val limit: Int = 400,
)
