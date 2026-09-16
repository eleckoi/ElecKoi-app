package com.eleckoi.android.foundation.design.components

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.core.graphics.PathParser as AndroidPathParser
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun AnimatedNavIcon(
    tab: NavIconKind,
    active: Boolean,
    activeColor: Color,
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    val activeAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "navActiveAlpha",
    )
    val activeScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.94f,
        animationSpec = tween(durationMillis = 280),
        label = "navActiveScale",
    )
    val baseAlpha by animateFloatAsState(
        targetValue = if (active) 0f else 0.96f,
        animationSpec = tween(durationMillis = 280),
        label = "navBaseAlpha",
    )

    Box(
        modifier = modifier.size(25.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavIcon(tab, color = baseColor.copy(alpha = baseAlpha), filled = false, modifier = Modifier.size(25.dp))
        NavIcon(
            tab = tab,
            color = activeColor.copy(alpha = activeAlpha),
            filled = true,
            modifier = Modifier.size((25f * activeScale).dp),
        )
    }
}

enum class NavIconKind {
    Messages,
    Characters,
    Models,
    Presets,
    Plugins,
}

@Composable
fun NavIcon(tab: NavIconKind, color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    when (tab) {
        NavIconKind.Messages -> MessageNavIcon(color, filled, modifier)
        NavIconKind.Characters -> PersonNavIcon(color, filled, modifier)
        NavIconKind.Models -> ModelNavIcon(color, filled, modifier)
        NavIconKind.Presets -> StrokeSvgIcon(
            paths = AppIconPaths.CardStack,
            color = color,
            modifier = modifier,
            strokeWidth = if (filled) 2.15f else 1.85f,
        )
        NavIconKind.Plugins -> StrokeSvgIcon(
            paths = AppIconPaths.Plug,
            color = color,
            modifier = modifier,
            strokeWidth = if (filled) 2.15f else 1.85f,
        )
    }
}

@Composable
fun StrokeSvgIcon(
    paths: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    strokeWidth: Float = 1.85f,
    circles: List<SvgCircle> = emptyList(),
) {
    Canvas(modifier = modifier.size(iconSize)) {
        drawSvg(paths = paths, color = color, strokeWidth = strokeWidth, circles = circles)
    }
}

@Composable
fun FilledSvgIcon(
    paths: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    viewportSize: Float = 24f,
    cutouts: List<SvgCircle> = emptyList(),
) {
    Canvas(modifier = modifier.size(iconSize)) {
        drawSvg(
            paths = emptyList(),
            color = color,
            fillPaths = paths,
            fillColor = color,
            viewportSize = viewportSize,
            fillCutouts = cutouts,
        )
    }
}

data class SvgCircle(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val fill: Boolean = false,
)

data class TranslatedSvgPath(
    val path: String,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
)

@Composable
fun TranslatedFilledSvgIcon(
    paths: List<TranslatedSvgPath>,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    viewportSize: Float = 24f,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        drawSvg(
            paths = emptyList(),
            color = color,
            translatedFillPaths = paths,
            fillColor = color,
            viewportSize = viewportSize,
        )
    }
}

@Composable
private fun MessageNavIcon(color: Color, filled: Boolean, modifier: Modifier) {
    val bubble = "M6.2 5.1h11.6a3.2 3.2 0 0 1 3.2 3.2v5.2a3.2 3.2 0 0 1-3.2 3.2h-5.5L7.1 20v-3.3h-.9A3.2 3.2 0 0 1 3 13.5V8.3a3.2 3.2 0 0 1 3.2-3.2Z"
    Canvas(modifier = modifier) {
        drawSvg(
            paths = listOf(bubble),
            color = color,
            strokeWidth = 1.8f,
            fillPaths = if (filled) listOf(bubble) else emptyList(),
            fillColor = color,
            circles = if (filled) listOf(
                SvgCircle(9.5f, 10.9f, 1.15f, fill = true),
                SvgCircle(14.5f, 10.9f, 1.15f, fill = true),
            ) else emptyList(),
            circleOverrideColor = if (filled) Color.White else null,
        )
    }
}

@Composable
private fun PersonNavIcon(color: Color, filled: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val fill = if (filled) listOf(
            "M4.1 20.2c.72-4.25 3.15-6.35 7.2-6.35 2.3 0 4.15.68 5.42 2.04l-1.3 4.31H4.1Z",
        ) else emptyList()
        drawSvg(
            paths = listOf(
                "M11.3 11.9a4.2 4.2 0 1 0 0-8.4 4.2 4.2 0 0 0 0 8.4Z",
                "M4.1 20.2c.72-4.25 3.15-6.35 7.2-6.35 2.3 0 4.15.68 5.42 2.04",
                "M17.6 8.1h3.2M17.6 11h2.35",
            ),
            color = color,
            strokeWidth = 1.8f,
            fillPaths = fill,
            fillColor = color,
            circles = if (filled) listOf(SvgCircle(11.3f, 7.7f, 4.2f, fill = true)) else emptyList(),
        )
    }
}

@Composable
private fun ModelNavIcon(color: Color, filled: Boolean, modifier: Modifier) {
    // The old glyph was an "A" beside three bars — the universal mark for type size, which is why
    // this tab kept reading as font settings. A cube shares no silhouette with the bubble or the
    // person beside it, which is the only thing that separates three icons at 25dp.
    Canvas(modifier = modifier) {
        val outer = "M12 2.9 20.2 7.35v9.3L12 21.1 3.8 16.65v-9.3Z"
        val inner = listOf(
            "M3.8 7.35 12 11.9l8.2-4.55",
            "M12 11.9v9.2",
        )
        val faces = if (filled) listOf(
            "M12 2.9 20.2 7.35 12 11.9 3.8 7.35Z",
            "M3.8 7.35 12 11.9v9.2L3.8 16.65Z",
            "M12 11.9 20.2 7.35v9.3L12 21.1Z",
        ) else emptyList()

        // Keep the cube's internal construction visible in the active state. The faces are blue,
        // while the inner edges use a light knockout so they do not disappear into the fill.
        drawSvg(
            paths = listOf(outer),
            color = color,
            strokeWidth = 1.8f,
            fillPaths = faces,
            fillColor = color,
        )
        drawSvg(
            paths = inner,
            color = if (filled) Color.White.copy(alpha = color.alpha) else color,
            strokeWidth = 1.65f,
        )
    }
}

private fun DrawScope.drawSvg(
    paths: List<String>,
    color: Color,
    strokeWidth: Float = 1.85f,
    circles: List<SvgCircle> = emptyList(),
    fillPaths: List<String> = emptyList(),
    translatedFillPaths: List<TranslatedSvgPath> = emptyList(),
    fillColor: Color = color,
    circleOverrideColor: Color? = null,
    viewportSize: Float = 24f,
    fillCutouts: List<SvgCircle> = emptyList(),
) {
    val scale = min(size.width, size.height) / viewportSize
    val dx = (size.width - viewportSize * scale) / 2f
    val dy = (size.height - viewportSize * scale) / 2f
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        native.clipRect(0f, 0f, size.width, size.height)
        native.translate(dx, dy)
        native.scale(scale, scale)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = StrokeCap.Round.toAndroidCap()
            strokeJoin = StrokeJoin.Round.toAndroidJoin()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = fillColor.toArgb()
            style = Paint.Style.FILL
        }
        for (path in fillPaths) {
            val fillPath = AndroidPathParser.createPathFromPathData(path)
            if (fillCutouts.isNotEmpty()) {
                val cutoutPath = AndroidPath().apply {
                    fillCutouts.forEach { cutout ->
                        addCircle(cutout.cx, cutout.cy, cutout.r, AndroidPath.Direction.CW)
                    }
                }
                fillPath.op(cutoutPath, AndroidPath.Op.DIFFERENCE)
            }
            native.drawPath(fillPath, fillPaint)
        }
        for (path in translatedFillPaths) {
            val pathCheckpoint = native.save()
            native.translate(path.translateX, path.translateY)
            native.drawPath(AndroidPathParser.createPathFromPathData(path.path), fillPaint)
            native.restoreToCount(pathCheckpoint)
        }
        for (path in paths) {
            native.drawPath(AndroidPathParser.createPathFromPathData(path), strokePaint)
        }
        for (circle in circles) {
            val paint = if (circle.fill) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = (circleOverrideColor ?: fillColor).toArgb()
                    style = Paint.Style.FILL
                }
            } else {
                strokePaint
            }
            native.drawCircle(circle.cx, circle.cy, circle.r, paint)
        }
        native.restoreToCount(checkpoint)
    }
}

private fun StrokeCap.toAndroidCap(): Paint.Cap = when (this) {
    StrokeCap.Round -> Paint.Cap.ROUND
    StrokeCap.Square -> Paint.Cap.SQUARE
    else -> Paint.Cap.BUTT
}

private fun StrokeJoin.toAndroidJoin(): Paint.Join = when (this) {
    StrokeJoin.Round -> Paint.Join.ROUND
    StrokeJoin.Bevel -> Paint.Join.BEVEL
    else -> Paint.Join.MITER
}
