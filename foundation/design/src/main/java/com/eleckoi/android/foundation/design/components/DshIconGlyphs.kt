package com.eleckoi.android.foundation.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val DshTreeMotionDurationMillis = 150
private val DshTreeMotionEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * DeepSeek Harness tree disclosure motion: one filled triangle turns from right to down.
 * Compose's duration scale support makes this settle immediately when system animations are off.
 */
@Composable
fun DshTreeDisclosureGlyph(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(
            durationMillis = DshTreeMotionDurationMillis,
            easing = DshTreeMotionEasing,
        ),
        label = "dsh_tree_disclosure_rotation",
    )
    FilledSvgIcon(
        paths = DshIconPaths.TriangleRightFill,
        color = tint,
        modifier = modifier.graphicsLayer { rotationZ = rotation },
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport14,
    )
}

/**
 * The matching tree-row reveal. Newly mounted descendants only fade in; removing them is immediate,
 * so expanding a folder never stretches or slides the rest of the tree.
 */
@Composable
fun Modifier.dshTreeRowEntrance(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alpha) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = DshTreeMotionDurationMillis,
                easing = DshTreeMotionEasing,
            ),
        )
    }
    return graphicsLayer { this.alpha = alpha.value }
}

@Composable
fun DshFolderGlyph(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    TranslatedFilledSvgIcon(
        paths = if (expanded) DshIconPaths.FolderOpenOutline else DshIconPaths.FolderClose,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport16,
    )
}

@Composable
fun DshProjectAddGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    TranslatedFilledSvgIcon(
        paths = DshIconPaths.ProjectAdd,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport16,
    )
}

@Composable
fun DshGeneralGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
) {
    TranslatedFilledSvgIcon(
        paths = DshIconPaths.Globe,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport14,
    )
}

@Composable
fun DshSettingsGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    TranslatedFilledSvgIcon(
        paths = DshIconPaths.Settings,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport16,
    )
}

@Composable
fun DshSearchGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    TranslatedFilledSvgIcon(
        paths = DshIconPaths.Search,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport16,
    )
}

@Composable
fun DshTrashGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    TranslatedFilledSvgIcon(
        paths = DshIconPaths.Trash,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = DshIconPaths.Viewport16,
    )
}
