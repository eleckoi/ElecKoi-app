package com.eleckoi.android.feature.chat.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AboveAnchorPopupPositionProvider
import java.text.NumberFormat
import kotlin.math.roundToLong

/** Two session statistics below the composer; the context circle stays at its original position. */
@Composable
fun ChatGenerationStatsLine(
    metrics: ChatGenerationMetrics,
    appearance: AppearanceTheme,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = generationStatsGroups(metrics)
    if (!enabled || groups.isEmpty()) return
    var openGroup by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(ChatGenerationStatsSlotHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            groups.forEachIndexed { index, group ->
                val isTime = index == 0 && metrics.steps > 0
                StatsGroup(
                    label = group,
                    title = if (isTime) "会话统计" else "Token 用量",
                    rows = if (isTime) generationTimeRows(metrics) else generationUsageRows(metrics),
                    isTime = isTime,
                    expanded = openGroup == index,
                    onToggle = { openGroup = if (openGroup == index) null else index },
                    onDismiss = { openGroup = null },
                    appearance = appearance,
                )
            }
        }
    }
}

private data class StatsRow(val label: String, val value: String)

@Composable
private fun StatsGroup(
    label: String,
    title: String,
    rows: List<StatsRow>,
    isTime: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    appearance: AppearanceTheme,
) {
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AboveAnchorPopupPositionProvider(
            windowMarginPx = with(density) { 12.dp.roundToPx() },
            anchorGapPx = with(density) { 6.dp.roundToPx() },
            anchorInsetPx = 0,
        )
    }
    Box {
        Row(
            modifier = Modifier
                .height(ChatGenerationStatsSlotHeight)
                .clickable(role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isTime) Icons.Outlined.Speed else Icons.Outlined.Storage,
                contentDescription = null,
                tint = appearance.mobileMuted,
                modifier = Modifier.width(15.dp).height(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        if (expanded) Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            ChatComposerMenuSurface(appearance = appearance, modifier = Modifier.width(280.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = appearance.mobileMuted.copy(alpha = 0.18f))
                    Spacer(Modifier.height(8.dp))
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(row.label, color = appearance.mobileMuted, fontSize = 12.sp)
                            Text(row.value, color = appearance.mobileText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

internal val ChatGenerationStatsSlotHeight = 24.dp

internal fun generationStatsGroups(metrics: ChatGenerationMetrics): List<String> = buildList {
    if (metrics.steps > 0) {
        val speed = if (metrics.decodeDurationMillis > 0L) {
            val tokensPerSecond = metrics.decodeOutputTokens * 1_000.0 / metrics.decodeDurationMillis
            " · ${formatStatsNumber(tokensPerSecond)} tok/s"
        } else ""
        add("${metrics.turns} 轮 ${metrics.steps} 步$speed")
    }
    val totalTokens = metrics.billedInputTokens + metrics.outputTokens
    if (totalTokens > 0L) {
        val cacheHit = metrics.cacheHitPercent?.let { " · 缓存命中 $it%" }.orEmpty()
        add("${formatStatsTokens(totalTokens)} tok$cacheHit")
    }
}

internal fun retainVisibleGenerationMetrics(
    previous: ChatGenerationMetrics,
    next: ChatGenerationMetrics,
): ChatGenerationMetrics = when {
    generationStatsGroups(next).isNotEmpty() -> next
    generationStatsGroups(previous).isNotEmpty() -> previous
    else -> next
}

private fun generationTimeRows(metrics: ChatGenerationMetrics): List<StatsRow> = buildList {
    if (metrics.llmDurationMillis > 0L) add(StatsRow("模型用时", formatStatsDuration(metrics.llmDurationMillis)))
    if (metrics.toolDurationMillis > 0L) add(StatsRow("工具调用用时", formatStatsDuration(metrics.toolDurationMillis)))
    if (metrics.firstTokenSamples > 0) add(StatsRow(
        "首 token 平均（TTFT）",
        formatStatsDuration(metrics.firstTokenDelayMillis / metrics.firstTokenSamples),
    ))
    if (metrics.decodeDurationMillis > 0L) add(StatsRow(
        "输出速度（TPS）",
        "${formatStatsNumber(metrics.decodeOutputTokens * 1_000.0 / metrics.decodeDurationMillis)} tok/s",
    ))
}

private fun generationUsageRows(metrics: ChatGenerationMetrics): List<StatsRow> = buildList {
    add(StatsRow("Token 用量", "${formatExactTokens(metrics.billedInputTokens + metrics.outputTokens)} tok"))
    metrics.cacheHitPercent?.let { add(StatsRow("缓存命中", "$it%")) }
    add(StatsRow("未缓存输入", "${formatExactTokens(metrics.inputTokens)} tok"))
    add(StatsRow("缓存读取", "${formatExactTokens(metrics.cacheReadTokens)} tok"))
    if (metrics.cacheWriteTokens > 0L) add(StatsRow("缓存写入", "${formatExactTokens(metrics.cacheWriteTokens)} tok"))
    add(StatsRow("输出", "${formatExactTokens(metrics.outputTokens)} tok"))
}

private fun formatExactTokens(tokens: Long): String = NumberFormat.getIntegerInstance().format(tokens)

private fun formatStatsDuration(millis: Long): String {
    val seconds = millis / 1_000.0
    return if (seconds < 60.0) "${formatStatsNumber(seconds)}秒" else {
        val rounded = seconds.roundToLong()
        "${rounded / 60}分${rounded % 60}秒"
    }
}

private fun formatStatsTokens(tokens: Long): String = when {
    tokens < 1_000L -> tokens.toString()
    tokens < 1_000_000L -> "${formatStatsNumber(tokens / 1_000.0)}K"
    else -> "${formatStatsNumber(tokens / 1_000_000.0)}M"
}

private fun formatStatsNumber(value: Double): String {
    val rounded = if (value >= 100.0) value.roundToLong().toDouble() else (value * 10.0).roundToLong() / 10.0
    return rounded.toString().removeSuffix(".0")
}
