package com.eleckoi.android.feature.chat.ui.layout

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal val SocialBubbleTailWidth = 6.dp
internal val SocialBubbleCornerRadiusMax = 6.dp

internal data class SocialBubbleGeometry(
    val body: Rect,
    val tailTipX: Float,
    val tailCenterY: Float,
    val tailTopY: Float,
    val tailBottomY: Float,
)

internal fun socialBubbleGeometry(
    size: Size,
    tailWidth: Float,
    tailHeight: Float,
    tailCenterY: Float,
    user: Boolean,
): SocialBubbleGeometry {
    val clampedTail = tailWidth.coerceIn(0f, size.width / 3f)
    val clampedTailHeight = tailHeight.coerceIn(0f, size.height)
    val halfTailHeight = clampedTailHeight / 2f
    val clampedTailCenterY = tailCenterY.coerceIn(
        halfTailHeight,
        (size.height - halfTailHeight).coerceAtLeast(halfTailHeight),
    )
    return SocialBubbleGeometry(
        body = Rect(
            left = if (user) 0f else clampedTail,
            top = 0f,
            right = if (user) size.width - clampedTail else size.width,
            bottom = size.height,
        ),
        tailTipX = if (user) size.width else 0f,
        tailCenterY = clampedTailCenterY,
        tailTopY = clampedTailCenterY - halfTailHeight,
        tailBottomY = clampedTailCenterY + halfTailHeight,
    )
}

/**
 * Messaging-app bubble with a compact pointer aimed at the speaker avatar. Tail space stays
 * reserved for every Markdown fragment so a long, virtualized message keeps one straight edge.
 */
internal data class SocialChatBubbleShape(
    val user: Boolean,
    val cornerRadius: Dp,
    val roundTop: Boolean = true,
    val roundBottom: Boolean = true,
    val tailVisible: Boolean = true,
    val tailCenterY: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val tailWidthPx = with(density) { SocialBubbleTailWidth.toPx() }
            .coerceAtMost(size.width / 3f)
        val tailHeightPx = with(density) { 10.dp.toPx() }.coerceAtMost(size.height)
        val tailCenterYPx = with(density) { tailCenterY.toPx() }
        val requestedRadius = with(density) { cornerRadius.toPx() }
        val geometry = socialBubbleGeometry(
            size = size,
            tailWidth = tailWidthPx,
            tailHeight = tailHeightPx,
            tailCenterY = tailCenterYPx,
            user = user,
        )
        val bodyLeft = geometry.body.left
        val bodyRight = geometry.body.right
        val bodyWidth = (bodyRight - bodyLeft).coerceAtLeast(0f)
        val radius = requestedRadius.coerceIn(0f, minOf(bodyWidth, size.height) / 2f)
        val hasTail = tailVisible && roundTop && tailWidthPx > 0f && tailHeightPx > 0f
        var topLeftRadius = if (roundTop) radius else 0f
        var topRightRadius = if (roundTop) radius else 0f
        var bottomRightRadius = if (roundBottom) radius else 0f
        var bottomLeftRadius = if (roundBottom) radius else 0f

        // On the tail side, keep the corner curve out of the tail's attachment segment. This lets
        // the whole silhouette remain one contour even at the largest configurable radius.
        if (hasTail && user) {
            topRightRadius = minOf(topRightRadius, geometry.tailTopY)
            bottomRightRadius = minOf(bottomRightRadius, size.height - geometry.tailBottomY)
        } else if (hasTail) {
            topLeftRadius = minOf(topLeftRadius, geometry.tailTopY)
            bottomLeftRadius = minOf(bottomLeftRadius, size.height - geometry.tailBottomY)
        }

        val path = Path().apply {
            // The rounded body and pointer are deliberately traced as one closed contour. Drawing
            // them as separate overlapping sub-paths leaves a hairline seam after anti-aliasing.
            moveTo(bodyLeft + topLeftRadius, 0f)
            lineTo(bodyRight - topRightRadius, 0f)
            quadraticTo(bodyRight, 0f, bodyRight, topRightRadius)
            if (hasTail && user) {
                lineTo(bodyRight, geometry.tailTopY)
                lineTo(geometry.tailTipX, geometry.tailCenterY)
                lineTo(bodyRight, geometry.tailBottomY)
            }
            lineTo(bodyRight, size.height - bottomRightRadius)
            quadraticTo(
                bodyRight,
                size.height,
                bodyRight - bottomRightRadius,
                size.height,
            )
            lineTo(bodyLeft + bottomLeftRadius, size.height)
            quadraticTo(bodyLeft, size.height, bodyLeft, size.height - bottomLeftRadius)
            if (hasTail && !user) {
                lineTo(bodyLeft, geometry.tailBottomY)
                lineTo(geometry.tailTipX, geometry.tailCenterY)
                lineTo(bodyLeft, geometry.tailTopY)
            }
            lineTo(bodyLeft, topLeftRadius)
            quadraticTo(bodyLeft, 0f, bodyLeft + topLeftRadius, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
