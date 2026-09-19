package com.eleckoi.android.feature.chat.ui.composer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.foundation.design.AppearanceTheme
import kotlin.math.roundToLong

/** A quiet, read-only DSH-style summary below the composer. */
@Composable
fun ChatGenerationStatsLine(
    metrics: ChatGenerationMetrics,
    appearance: AppearanceTheme,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = generationStatsGroups(metrics)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ChatGenerationStatsSlotHeight),
    ) {
        if (enabled && groups.isNotEmpty()) {
            Text(
                text = groups.joinToString("  |  "),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                color = appearance.mobileMuted.copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

internal val ChatGenerationStatsSlotHeight = 22.dp

internal fun generationStatsGroups(metrics: ChatGenerationMetrics): List<String> = buildList {
    if (metrics.steps > 0) {
        add("${metrics.turns} 轮 · ${metrics.steps} 步")
        val durations = buildList {
            if (metrics.llmDurationMillis > 0L) add("LLM ${formatStatsDuration(metrics.llmDurationMillis)}")
            if (metrics.toolDurationMillis > 0L) {
                add("工具调用 ${formatStatsDuration(metrics.toolDurationMillis)}")
            }
        }
        if (durations.isNotEmpty()) add(durations.joinToString(" · "))
        val speeds = buildList {
            if (metrics.firstTokenSamples > 0) {
                add(
                    "首 token 平均 " + formatStatsDuration(
                        metrics.firstTokenDelayMillis / metrics.firstTokenSamples,
                    ),
                )
            }
            if (metrics.decodeDurationMillis > 0L && metrics.decodeOutputTokens > 0L) {
                val tokensPerSecond = metrics.decodeOutputTokens * 1_000.0 / metrics.decodeDurationMillis
                add("${formatStatsNumber(tokensPerSecond)} tok/s")
            }
        }
        if (speeds.isNotEmpty()) add(speeds.joinToString(" · "))
    }
    metrics.cacheHitPercent?.let { add("缓存命中 ${it}%") }
    if (metrics.billedInputTokens > 0L || metrics.outputTokens > 0L) {
        add(
            "输入 ${formatStatsTokens(metrics.billedInputTokens)} tok · " +
                "输出 ${formatStatsTokens(metrics.outputTokens)} tok",
        )
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

private fun formatStatsDuration(millis: Long): String {
    val seconds = millis / 1_000.0
    return if (seconds < 60.0) "${formatStatsNumber(seconds)}s" else {
        val rounded = seconds.roundToLong()
        "${rounded / 60}m${rounded % 60}s"
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
