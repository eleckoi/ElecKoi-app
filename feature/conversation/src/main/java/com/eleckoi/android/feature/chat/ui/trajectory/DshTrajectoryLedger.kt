package com.eleckoi.android.feature.chat.ui.trajectory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecord
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordStatus
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRequest
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.noRippleClickable
import kotlin.math.max

@Composable
internal fun DshTrajectoryTitleBar(
    eventCount: Int?,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("轨迹", color = appearance.mobileText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (eventCount != null) {
            Text(
                text = "$eventCount 条事件",
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭轨迹", tint = appearance.mobileText)
        }
    }
}

@Composable
internal fun DshTrajectoryToolbar(
    actualDuration: Boolean,
    allTurnsCollapsed: Boolean,
    callsCollapsed: Boolean,
    searchQuery: String,
    appearance: AppearanceTheme,
    onToggleDuration: () -> Unit,
    onToggleTurns: () -> Unit,
    onToggleCalls: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrajectoryToggle("◷  耗时", actualDuration, appearance, onToggleDuration)
            TrajectoryToggle(if (allTurnsCollapsed) "⊞  轮次" else "⊟  轮次", allTurnsCollapsed, appearance, onToggleTurns)
            TrajectoryToggle(if (callsCollapsed) "⊞  调用" else "⊟  调用", callsCollapsed, appearance, onToggleCalls)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(appearance.mobileInputBg, RoundedCornerShape(13.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = appearance.mobileMuted,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = appearance.mobileText, fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isBlank()) {
                            Text("搜索轨迹", color = appearance.mobileMuted, fontSize = 14.sp)
                        }
                        input()
                    }
                },
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索", tint = appearance.mobileMuted)
                }
            }
        }
    }
}

@Composable
private fun TrajectoryToggle(
    label: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) appearance.mobileBlue.copy(alpha = 0.13f) else appearance.mobileInputBg,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier
            .height(42.dp)
            .noRippleClickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun DshTrajectoryOverview(
    records: List<DshTrajectoryRecord>,
    selectedRecordId: String,
    actualDuration: Boolean,
    appearance: AppearanceTheme,
    onSelect: (DshTrajectoryRecord) -> Unit,
) {
    if (records.isEmpty()) return
    val positions = trajectoryPositions(records, actualDuration)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.mobileInputBg.copy(alpha = 0.58f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OverviewLane("输入", records, positions, selectedRecordId, appearance, onSelect) { kind ->
            kind == DshTrajectoryRecordKind.System ||
                kind == DshTrajectoryRecordKind.User ||
                kind == DshTrajectoryRecordKind.Context
        }
        OverviewLane("模型", records, positions, selectedRecordId, appearance, onSelect) { kind ->
            kind == DshTrajectoryRecordKind.Assistant || kind == DshTrajectoryRecordKind.Compaction
        }
        OverviewLane("工具", records, positions, selectedRecordId, appearance, onSelect) { kind ->
            kind == DshTrajectoryRecordKind.Tool
        }
    }
}

@Composable
private fun OverviewLane(
    label: String,
    records: List<DshTrajectoryRecord>,
    positions: List<TrajectoryPosition>,
    selectedRecordId: String,
    appearance: AppearanceTheme,
    onSelect: (DshTrajectoryRecord) -> Unit,
    accepts: (DshTrajectoryRecordKind) -> Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = appearance.mobileMuted, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(appearance.mobileLine.copy(alpha = 0.46f)),
        ) {
            positions.forEachIndexed { index, position ->
                val record = records[index]
                if (!accepts(record.kind)) return@forEachIndexed
                val width = maxWidth * (position.width / 100f)
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * (position.left / 100f))
                        .width(width.coerceAtLeast(4.dp))
                        .height(14.dp)
                        .background(
                            color = trajectoryKindColor(record.kind).copy(
                                alpha = if (record.id == selectedRecordId) 1f else 0.76f,
                            ),
                            shape = RoundedCornerShape(3.dp),
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = "查看 ${record.title}"
                        }
                        .noRippleClickable { onSelect(record) },
                )
            }
        }
    }
}

@Composable
internal fun DshTrajectoryLedger(
    groups: List<DshTrajectoryTurnGroup>,
    collapsedTurns: Set<String>,
    selectedId: String,
    hasMore: Boolean,
    loadingOlder: Boolean,
    error: String,
    appearance: AppearanceTheme,
    onToggleTurn: (String) -> Unit,
    onSelectRecord: (DshTrajectoryRecord) -> Unit,
    onSelectRequest: (DshTrajectoryRequest) -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        if (hasMore) {
            item(key = "load-older") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .noRippleClickable(enabled = !loadingOlder, onClick = onLoadOlder),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingOlder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = appearance.mobileBlue,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("加载更早记录", color = appearance.mobileBlue, fontSize = 13.sp)
                    }
                }
            }
        }
        groups.forEach { group ->
            item(key = "header:${group.key}") {
                TurnHeader(
                    group = group,
                    collapsed = group.key in collapsedTurns,
                    appearance = appearance,
                    onToggle = { onToggleTurn(group.key) },
                )
            }
            if (group.key !in collapsedTurns) {
                items(group.records, key = DshTrajectoryRecord::id) { record ->
                    DshTrajectoryRow(
                        record = record,
                        selectedId = selectedId,
                        appearance = appearance,
                        onSelectRecord = { onSelectRecord(record) },
                        onSelectRequest = onSelectRequest,
                    )
                }
            }
        }
        if (error.isNotBlank()) {
            item(key = "inline-error") {
                Text(
                    text = error,
                    color = Color(0xFFD74D55),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TurnHeader(
    group: DshTrajectoryTurnGroup,
    collapsed: Boolean,
    appearance: AppearanceTheme,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(appearance.mobileBg.copy(alpha = 0.74f))
            .noRippleClickable(onClick = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = appearance.mobileMuted,
            modifier = Modifier.size(20.dp),
        )
        Text(
            group.label,
            color = appearance.mobileText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(group.records.size.toString(), color = appearance.mobileMuted, fontSize = 12.sp)
    }
}

@Composable
private fun DshTrajectoryRow(
    record: DshTrajectoryRecord,
    selectedId: String,
    appearance: AppearanceTheme,
    onSelectRecord: () -> Unit,
    onSelectRequest: (DshTrajectoryRequest) -> Unit,
) {
    val selected = selectedId == "record:${record.id}"
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = if (selected) appearance.mobileBlue.copy(alpha = 0.09f) else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onSelectRecord),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = record.index.toString(),
                        color = appearance.mobileMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.widthIn(min = 32.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    KindPill(record.kind.label(), trajectoryKindColor(record.kind), appearance)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = record.title,
                            color = appearance.mobileText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (record.preview.isNotBlank()) {
                            Text(
                                text = record.preview,
                                color = appearance.mobileMuted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    StatusDot(record.status)
                    Text(
                        text = formatTrajectoryDuration(record.durationMillis),
                        color = appearance.mobileMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    thickness = 0.5.dp,
                    color = appearance.mobileLine.copy(alpha = 0.2f),
                )
            }
        }
        record.requests.forEachIndexed { requestIndex, request ->
            val requestSelected = selectedId == "request:${request.seq}"
            val pointColor = if (request.status == DshTrajectoryRecordStatus.Error) {
                requestStatusColor(request.status)
            } else {
                appearance.mobileBlue
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 6.dp + 10.dp * requestIndex, y = (-14).dp)
                    .size(28.dp)
                    .semantics { contentDescription = "查看 Request #${request.number}" }
                    .noRippleClickable { onSelectRequest(request) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (requestSelected) 10.dp else 7.dp)
                        .background(
                            if (requestSelected) appearance.mobileBg else pointColor,
                            CircleShape,
                        )
                        .then(
                            if (requestSelected) {
                                Modifier.border(1.5.dp, pointColor, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: DshTrajectoryRecordStatus) {
    Canvas(
        modifier = Modifier
            .size(12.dp)
            .semantics { contentDescription = status.label() },
    ) {
        drawCircle(color = requestStatusColor(status), radius = size.minDimension / 2f)
        if (status == DshTrajectoryRecordStatus.Running) {
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = size.minDimension / 5f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
internal fun DshTrajectoryState(
    text: String,
    appearance: AppearanceTheme,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = appearance.mobileBlue,
                strokeWidth = 2.5.dp,
            )
        }
        Text(text, color = appearance.mobileMuted, fontSize = 14.sp)
        if (action != null) {
            Surface(
                color = appearance.mobileBlue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .height(44.dp)
                    .noRippleClickable(onClick = onAction),
            ) {
                Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(action, color = appearance.mobileBlue, fontSize = 14.sp)
                }
            }
        }
    }
}

private data class TrajectoryPosition(val left: Float, val width: Float)

private fun trajectoryPositions(
    records: List<DshTrajectoryRecord>,
    actualDuration: Boolean,
): List<TrajectoryPosition> {
    if (records.isEmpty()) return emptyList()
    fun equalPositions(): List<TrajectoryPosition> {
        val width = 100f / records.size
        return records.indices.map { index ->
            TrajectoryPosition(index * width, max(width - 0.2f, 0.7f))
        }
    }
    if (!actualDuration) return equalPositions()
    val timed = records.filter { it.timeMillis != null }
    if (timed.isEmpty()) return equalPositions()
    val start = timed.minOf { requireNotNull(it.timeMillis) }
    val end = timed.maxOf { record ->
        requireNotNull(record.timeMillis) + max(record.durationMillis ?: 0L, 4L)
    }
    val span = max(end - start, 1L).toFloat()
    val equal = equalPositions()
    return records.mapIndexed { index, record ->
        val time = record.timeMillis ?: return@mapIndexed equal[index]
        TrajectoryPosition(
            left = ((time - start) / span) * 100f,
            width = max((max(record.durationMillis ?: 0L, 4L) / span) * 100f, 0.7f),
        )
    }
}

private fun requestStatusColor(status: DshTrajectoryRecordStatus): Color = when (status) {
    DshTrajectoryRecordStatus.Running -> Color(0xFF2F80ED)
    DshTrajectoryRecordStatus.Complete -> Color(0xFF18A673)
    DshTrajectoryRecordStatus.Error -> Color(0xFFD74D55)
    DshTrajectoryRecordStatus.Cancelled -> Color(0xFF8B93A1)
}
