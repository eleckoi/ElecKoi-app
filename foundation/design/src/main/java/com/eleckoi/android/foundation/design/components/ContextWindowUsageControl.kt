package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.math.BigDecimal
import java.math.RoundingMode

/** Latest native token sample used to explain the active model context budget. */
data class ContextWindowUsage(
    val latestTokens: Long? = null,
    val totalTokens: Long? = null,
    val modelContextWindow: Long? = null,
    val systemTokens: Long? = null,
    val toolsTokens: Long? = null,
    val messageTokens: Long? = null,
)

@Composable
fun ContextWindowUsageControl(
    usage: ContextWindowUsage?,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .noRippleClickable { expanded = true }
                .semantics { contentDescription = "上下文窗口用量" },
            contentAlignment = Alignment.Center,
        ) {
            ContextWindowRing(usage = usage, appearance = appearance)
        }
        ContextWindowUsagePopup(
            expanded = expanded,
            usage = usage,
            appearance = appearance,
            onDismissRequest = { expanded = false },
        )
    }
}

@Composable
private fun ContextWindowRing(
    usage: ContextWindowUsage?,
    appearance: AppearanceTheme,
) {
    val contextWindow = usage?.modelContextWindow?.takeIf { it > 0L }
    val usedTokens = usage?.latestTokens?.coerceAtLeast(0L)
    val usedFraction = if (contextWindow != null && usedTokens != null) {
        (usedTokens.toFloat() / contextWindow.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 2.25.dp.toPx()
        drawArc(
            color = appearance.mobileMuted.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        if (usedFraction > 0f) {
            drawArc(
                color = appearance.mobileMuted,
                startAngle = -90f,
                sweepAngle = usedFraction * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ContextWindowUsagePopup(
    expanded: Boolean,
    usage: ContextWindowUsage?,
    appearance: AppearanceTheme,
    onDismissRequest: () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AboveAnchorPopupPositionProvider(
            windowMarginPx = with(density) { 8.dp.roundToPx() },
            anchorGapPx = with(density) { 5.dp.roundToPx() },
            anchorInsetPx = with(density) { 92.dp.roundToPx() },
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier.width(272.dp),
            shape = RoundedCornerShape(16.dp),
            color = appearance.mobileSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, appearance.mobileLine),
        ) {
            val presentation = usage.toContextWindowUsagePresentation()
            Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "上下文已用",
                        color = appearance.mobileText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        presentation.percentLabel,
                        color = appearance.mobileText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        presentation.tokenRatioLabel,
                        color = appearance.mobileText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ContextUsageProgress(
                    fraction = presentation.usedFraction,
                    appearance = appearance,
                )
                if (presentation.hasBreakdown) {
                    Spacer(Modifier.height(12.dp))
                    ContextBreakdownRow(
                        label = "系统提示词",
                        tokens = usage?.systemTokens,
                        markerColor = ContextSystemColor,
                        appearance = appearance,
                    )
                    ContextBreakdownRow(
                        label = "工具定义",
                        tokens = usage?.toolsTokens,
                        markerColor = ContextToolsColor,
                        appearance = appearance,
                    )
                    ContextBreakdownRow(
                        label = "对话消息",
                        tokens = usage?.messageTokens,
                        markerColor = ContextMessagesColor,
                        appearance = appearance,
                    )
                    ContextBreakdownRow(
                        label = "其余上下文",
                        tokens = presentation.unclassifiedTokens,
                        markerColor = ContextUnclassifiedColor,
                        appearance = appearance,
                    )
                } else if (!presentation.hasNativeSample) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "等待 DSH 返回上下文统计",
                        color = appearance.mobileMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextUsageProgress(
    fraction: Float,
    appearance: AppearanceTheme,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(appearance.mobileMuted.copy(alpha = 0.18f)),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(appearance.mobileBlue),
            )
        }
    }
}

@Composable
private fun ContextBreakdownRow(
    label: String,
    tokens: Long?,
    markerColor: Color,
    appearance: AppearanceTheme,
) {
    if (tokens == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(markerColor),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = appearance.mobileMuted,
            fontSize = 11.5.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "~${formatTokenCount(tokens.coerceAtLeast(0L))}",
            color = appearance.mobileText,
            fontSize = 11.5.sp,
            lineHeight = 20.sp,
        )
    }
}

internal data class ContextWindowUsagePresentation(
    val percentLabel: String,
    val tokenRatioLabel: String,
    val usedFraction: Float,
    val hasNativeSample: Boolean,
    val hasBreakdown: Boolean,
    val unclassifiedTokens: Long?,
)

internal fun ContextWindowUsage?.toContextWindowUsagePresentation(): ContextWindowUsagePresentation {
    val contextWindow = this?.modelContextWindow?.takeIf { it > 0L }
    val activeTokens = this?.latestTokens?.coerceAtLeast(0L)
    val hasNativeSample = contextWindow != null && activeTokens != null
    val usedFraction = if (hasNativeSample) {
        (activeTokens.toDouble() / contextWindow.toDouble()).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    val classifiedTokens = listOf(
        this?.systemTokens,
        this?.toolsTokens,
        this?.messageTokens,
    )
    val unclassifiedTokens = if (activeTokens != null && classifiedTokens.all { it != null }) {
        (activeTokens - classifiedTokens.sumOf { it!! }).coerceAtLeast(0L)
    } else {
        null
    }
    return ContextWindowUsagePresentation(
        percentLabel = if (hasNativeSample) formatUsagePercent(usedFraction * 100.0) else "—",
        tokenRatioLabel = if (hasNativeSample) {
            "~${formatTokenCount(activeTokens)} / ${formatTokenCount(contextWindow)}"
        } else {
            "— / —"
        },
        usedFraction = usedFraction.toFloat(),
        hasNativeSample = hasNativeSample,
        hasBreakdown = this?.let {
            it.systemTokens != null || it.toolsTokens != null || it.messageTokens != null
        } == true,
        unclassifiedTokens = unclassifiedTokens?.takeIf { it > 0L },
    )
}

private fun formatUsagePercent(percent: Double): String {
    val scale = if (percent >= 0.0 && percent < 1.0) 1 else 0
    return BigDecimal.valueOf(percent.coerceIn(0.0, 100.0))
        .setScale(scale, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString() + "%"
}

internal fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000L -> formatCompactTokenCount(tokens, 1_000_000L, "M")
    tokens >= 1_000L -> formatCompactTokenCount(tokens, 1_000L, "K")
    else -> tokens.toString()
}

private fun formatCompactTokenCount(tokens: Long, unit: Long, suffix: String): String {
    val whole = tokens / unit
    val remainder = tokens % unit
    if (remainder == 0L) return "$whole$suffix"
    val tenths = ((remainder * 10L) / unit).coerceIn(0L, 9L)
    return if (tenths == 0L) "$whole$suffix" else "$whole.$tenths$suffix"
}

private val ContextSystemColor = Color(0xFF9AA4B2)
private val ContextToolsColor = Color(0xFF8B6DFF)
private val ContextMessagesColor = Color(0xFF4A7FF3)
private val ContextUnclassifiedColor = Color(0xFFE09A32)
