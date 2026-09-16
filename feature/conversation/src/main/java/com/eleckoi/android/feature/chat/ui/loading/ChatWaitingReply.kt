package com.eleckoi.android.feature.chat.ui.loading

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** PC-compatible status shown before the first renderable assistant event arrives. */
@Composable
fun ChatWaitingReply(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (isActive) {
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            delay(1_000L - elapsedMs % 1_000L)
        }
    }

    Row(
        modifier = modifier.height(DeepDivingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeepDivingShimmer()
        if (elapsedMs >= DeepDivingClockDelayMillis) {
            Text(
                text = formatDeepDivingDuration(elapsedMs),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun DeepDivingShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "deep-diving-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "deep-diving-shimmer-phase",
    )
    Text(
        text = "Deep diving...",
        color = DeepDivingBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val gradientWidth = size.width * 2.5f
                val gradientStart = -size.width * 1.5f * (1f - phase)
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to DeepDivingBlue,
                            0.4f to DeepDivingBlue,
                            0.5f to DeepDivingHighlight,
                            0.6f to DeepDivingBlue,
                            1f to DeepDivingBlue,
                        ),
                        start = Offset(gradientStart, 0f),
                        end = Offset(gradientStart + gradientWidth, 0f),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    )
}

internal fun formatDeepDivingDuration(elapsedMs: Long): String {
    val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}分${seconds.toString().padStart(2, '0')}秒"
    } else {
        "${seconds}秒"
    }
}

private const val DeepDivingClockDelayMillis = 15_000L
private val DeepDivingRowHeight = 28.dp
private val DeepDivingBlue = Color(0xFF4176E6)
private val DeepDivingHighlight = Color(0xFFD3E2FF)
