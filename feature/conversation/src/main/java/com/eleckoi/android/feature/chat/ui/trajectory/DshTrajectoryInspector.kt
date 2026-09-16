package com.eleckoi.android.feature.chat.ui.trajectory

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecord
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRequest
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun DshTrajectoryInspector(
    selection: DshTrajectorySelection,
    tab: DshTrajectoryDetailTab,
    appearance: AppearanceTheme,
    compact: Boolean,
    onTabChange: (DshTrajectoryDetailTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = (selection as? DshTrajectorySelection.Record)?.value
    val request = (selection as? DshTrajectorySelection.Request)?.value
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.mobileBg),
    ) {
        InspectorHeading(
            record = record,
            request = request,
            compact = compact,
            appearance = appearance,
            onClose = onClose,
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = appearance.mobileLine.copy(alpha = 0.22f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DshTrajectoryDetailTab.entries.forEach { entry ->
                val selected = entry == tab
                Surface(
                    color = if (selected) appearance.mobileBlue.copy(alpha = 0.13f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .noRippleClickable { onTabChange(entry) },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = entry.label,
                            color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = appearance.mobileLine.copy(alpha = 0.22f),
        )
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    DshTrajectoryDetailTab.Summary -> {
                        if (request != null) RequestSummary(request, appearance)
                        if (record != null) RecordSummary(record, appearance)
                    }
                    DshTrajectoryDetailTab.Preview -> InspectorCode(
                        value = request?.detail
                            ?: record?.output?.takeIf(String::isNotBlank)
                            ?: record?.input?.takeIf(String::isNotBlank)
                            ?: record?.preview?.takeIf(String::isNotBlank)
                            ?: record?.detail.orEmpty(),
                        appearance = appearance,
                    )
                    DshTrajectoryDetailTab.Raw -> InspectorCode(
                        value = request?.rawJson ?: record?.rawJson.orEmpty(),
                        appearance = appearance,
                    )
                    DshTrajectoryDetailTab.Source -> {
                        InspectorSection(
                            title = "来源",
                            value = request?.reason ?: record?.source.orEmpty(),
                            appearance = appearance,
                        )
                        InspectorSection(
                            title = "事件详情",
                            value = request?.detail ?: record?.detail.orEmpty(),
                            appearance = appearance,
                            code = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorHeading(
    record: DshTrajectoryRecord?,
    request: DshTrajectoryRequest?,
    compact: Boolean,
    appearance: AppearanceTheme,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = if (compact) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                contentDescription = if (compact) "返回轨迹列表" else "关闭事件详情",
                tint = appearance.mobileText,
            )
        }
        if (request != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(appearance.mobileBlue, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Request #${request.number}",
                    color = appearance.mobileText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = request.turn?.let { "轮次 $it" } ?: "请求",
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                )
            }
        } else if (record != null) {
            KindPill(record.kind.label(), trajectoryKindColor(record.kind), appearance)
            Spacer(Modifier.width(10.dp))
            Text(
                text = record.title,
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("#${record.index}", color = appearance.mobileMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecordSummary(record: DshTrajectoryRecord, appearance: AppearanceTheme) {
    DetailTerm("事件", record.type, appearance)
    DetailTerm("状态", record.status.label(), appearance)
    DetailTerm("轮次", record.turn?.toString() ?: "—", appearance)
    DetailTerm("步骤", record.step?.toString() ?: "—", appearance)
    DetailTerm("时间", formatTrajectoryTime(record.timeMillis), appearance)
    DetailTerm("耗时", formatTrajectoryDuration(record.durationMillis), appearance)
}

@Composable
private fun RequestSummary(request: DshTrajectoryRequest, appearance: AppearanceTheme) {
    DetailTerm("状态", request.status.label(), appearance)
    DetailTerm("提供方", request.provider.ifBlank { "—" }, appearance)
    DetailTerm("模型", request.model.ifBlank { "—" }, appearance)
    DetailTerm("轮次", request.turn?.toString() ?: "—", appearance)
    DetailTerm("步骤", request.step?.toString() ?: "—", appearance)
    DetailTerm("时间", formatTrajectoryTime(request.timeMillis), appearance)
    DetailTerm("耗时", formatTrajectoryDuration(request.durationMillis), appearance)
}

@Composable
private fun DetailTerm(label: String, value: String, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = appearance.mobileMuted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        Text(value, color = appearance.mobileText, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InspectorSection(
    title: String,
    value: String,
    appearance: AppearanceTheme,
    code: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = appearance.mobileText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (code) InspectorCode(value, appearance) else Text(
            text = value.ifBlank { "—" },
            color = appearance.mobileMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun InspectorCode(value: String, appearance: AppearanceTheme) {
    if (value.isBlank()) {
        Text("没有可预览的内容", color = appearance.mobileMuted, fontSize = 13.sp)
        return
    }
    Text(
        text = value,
        color = appearance.mobileText,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = FontFamily.Monospace,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.mobileInputBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

@Composable
internal fun KindPill(
    label: String,
    color: Color,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.13f),
    ) {
        Text(
            text = label,
            color = color.takeUnless { it == Color.Transparent } ?: appearance.mobileMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

internal fun trajectoryKindColor(kind: com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind): Color = when (kind) {
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.System -> Color(0xFF7C5CE7)
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.User -> Color(0xFF1688D4)
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.Context -> Color(0xFF64748B)
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.Assistant -> Color(0xFF18A673)
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.Tool -> Color(0xFFE58A15)
    com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.Compaction -> Color(0xFFC44FC7)
}
