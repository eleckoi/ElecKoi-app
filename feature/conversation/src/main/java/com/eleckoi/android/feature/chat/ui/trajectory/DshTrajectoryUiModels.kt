package com.eleckoi.android.feature.chat.ui.trajectory

import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecord
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordStatus
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextKind
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextRole
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DshTrajectoryTurnGroup(
    val key: String,
    val label: String,
    val records: List<DshTrajectoryRecord>,
)

internal sealed interface DshTrajectorySelection {
    data class Record(val value: DshTrajectoryRecord) : DshTrajectorySelection
    data class Request(val value: DshTrajectoryRequest) : DshTrajectorySelection
}

internal enum class DshTrajectoryDetailTab(val label: String) {
    Summary("摘要"),
    Context("上下文"),
    Preview("预览"),
    Raw("原始"),
    Source("来源"),
}

internal fun DshRequestContextRole.label(): String = when (this) {
    DshRequestContextRole.System -> "系统"
    DshRequestContextRole.User -> "用户"
    DshRequestContextRole.Assistant -> "AI"
}

internal fun DshRequestContextKind.isToolResult(): Boolean = this == DshRequestContextKind.Tool

internal fun groupTrajectoryByTurn(records: List<DshTrajectoryRecord>): List<DshTrajectoryTurnGroup> =
    records.groupBy { record -> record.turn }
        .map { (turn, groupRecords) ->
            DshTrajectoryTurnGroup(
                key = turn?.let { "turn:$it" } ?: "setup",
                label = turn?.let { "轮次 $it" } ?: "会话准备",
                records = groupRecords,
            )
        }

internal fun DshTrajectoryRecordKind.label(): String = when (this) {
    DshTrajectoryRecordKind.System -> "系统"
    DshTrajectoryRecordKind.User -> "用户"
    DshTrajectoryRecordKind.Context -> "上下文"
    DshTrajectoryRecordKind.Assistant -> "助手"
    DshTrajectoryRecordKind.Tool -> "工具"
    DshTrajectoryRecordKind.Compaction -> "压缩"
}

internal fun DshTrajectoryRecordStatus.label(): String = when (this) {
    DshTrajectoryRecordStatus.Running -> "运行中"
    DshTrajectoryRecordStatus.Complete -> "已完成"
    DshTrajectoryRecordStatus.Error -> "失败"
    DshTrajectoryRecordStatus.Cancelled -> "已取消"
}

internal fun formatTrajectoryDuration(value: Long?): String = when {
    value == null -> "—"
    value < 1_000L -> "$value ms"
    value < 60_000L -> {
        val seconds = value / 1_000.0
        if (value < 10_000L) String.format(Locale.US, "%.1f s", seconds) else "${seconds.toLong()} s"
    }
    else -> "${value / 60_000L}m ${(value % 60_000L + 500L) / 1_000L}s"
}

internal fun formatTrajectoryTime(value: Long?): String = value?.let {
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(it))
} ?: "—"

internal fun DshTrajectoryRecord.matches(query: String): Boolean {
    val normalized = query.trim().lowercase(Locale.getDefault())
    if (normalized.isBlank()) return true
    return listOf(title, preview, source, type).any { value ->
        value.lowercase(Locale.getDefault()).contains(normalized)
    }
}

internal fun DshTrajectorySelection.id(): String = when (this) {
    is DshTrajectorySelection.Record -> "record:${value.id}"
    is DshTrajectorySelection.Request -> "request:${value.seq}"
}
