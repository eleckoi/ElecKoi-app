package com.eleckoi.android.foundation.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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

/** The three-node agent preset mark shared with the desktop navigation. */
@Composable
fun DshPresetGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Box(modifier = modifier.size(iconSize)) {
        FilledSvgIcon(
            paths = DshPresetRingPaths,
            color = tint,
            iconSize = iconSize,
            viewportSize = DshIconPaths.Viewport16,
            cutouts = DshPresetNodeCutouts,
        )
        FilledSvgIcon(
            paths = DshPresetNodePaths,
            color = tint,
            iconSize = iconSize,
            viewportSize = DshIconPaths.Viewport16,
        )
    }
}

private val DshPresetRingPaths = listOf(
    "M12.2881 11.0425C12.6002 11.3723 13.0413 11.5786 13.5312 11.5786L13.5342 11.5776C13.1476 12.3233 12.6119 12.9785 11.9639 13.5005C10.9327 14.3309 9.6199 14.8286 8.19336 14.8286C7.29864 14.8285 6.45056 14.6313 5.6875 14.2808C6.08309 14.0281 6.36707 13.6189 6.45215 13.1392C6.99022 13.3561 7.57767 13.476 8.19336 13.4761C9.30019 13.4761 10.3157 13.0915 11.1152 12.4478C11.5935 12.0626 11.9924 11.5848 12.2881 11.0425ZM4.14746 4.36475C4.25569 4.83228 4.55488 5.2247 4.95898 5.4585C4.07956 6.30639 3.53144 7.49605 3.53125 8.81396C3.53125 9.69534 3.77613 10.5202 4.20117 11.2231C3.74959 11.3817 3.38395 11.7232 3.19531 12.1597C2.5541 11.2032 2.17969 10.052 2.17969 8.81396C2.17989 7.05087 2.93868 5.4646 4.14746 4.36475ZM8.19336 2.80029C8.85717 2.80029 9.49784 2.90834 10.0967 3.10791C12.3237 3.85044 13.9725 5.86061 14.1846 8.28369C13.9832 8.20048 13.7627 8.15382 13.5312 8.15381C13.2802 8.15381 13.042 8.20907 12.8271 8.30615C12.6281 6.47264 11.3666 4.95616 9.66895 4.39014C9.2063 4.236 8.70989 4.15186 8.19336 4.15186C7.96112 4.15189 7.7329 4.16981 7.50977 4.20264C7.51947 4.12886 7.52637 4.05348 7.52637 3.97705C7.52628 3.56604 7.3811 3.18914 7.13965 2.89404C7.48183 2.83352 7.83381 2.80033 8.19336 2.80029Z",
)

private val DshPresetNodeCutouts = listOf(
    SvgCircle(cx = 7.9995f, cy = 3.28319f, r = 1.712f),
    SvgCircle(cx = 3.51122f, cy = 11.3855f, r = 1.712f),
    SvgCircle(cx = 12.4878f, cy = 11.3855f, r = 1.712f),
)

private val DshPresetNodePaths = listOf(
    "M9.1123 3.28271C9.11205 2.66858 8.61322 2.17041 7.99902 2.17041C7.38504 2.17067 6.88697 2.66874 6.88672 3.28271C6.88672 3.89691 7.38489 4.39574 7.99902 4.396C8.61338 4.396 9.1123 3.89707 9.1123 3.28271ZM10.3115 3.28271C10.3115 4.55981 9.27612 5.59521 7.99902 5.59521C6.72214 5.59496 5.6875 4.55965 5.6875 3.28271C5.68776 2.00599 6.7223 0.971447 7.99902 0.971191C9.27596 0.971191 10.3113 2.00584 10.3115 3.28271Z",
    "M4.62402 11.385C4.62377 10.7709 4.12494 10.2727 3.51074 10.2727C2.89676 10.273 2.39869 10.771 2.39844 11.385C2.39844 11.9992 2.89661 12.498 3.51074 12.4983C4.1251 12.4983 4.62402 11.9994 4.62402 11.385ZM5.82324 11.385C5.82324 12.6621 4.78784 13.6975 3.51074 13.6975C2.23386 13.6973 1.19922 12.6619 1.19922 11.385C1.19947 10.1083 2.23402 9.07374 3.51074 9.07349C4.78768 9.07349 5.82299 10.1081 5.82324 11.385Z",
    "M13.6006 11.385C13.6003 10.7709 13.1015 10.2727 12.4873 10.2727C11.8733 10.273 11.3753 10.771 11.375 11.385C11.375 11.9992 11.8732 12.498 12.4873 12.4983C13.1017 12.4983 13.6006 11.9994 13.6006 11.385ZM14.7998 11.385C14.7998 12.6621 13.7644 13.6975 12.4873 13.6975C11.2104 13.6973 10.1758 12.6619 10.1758 11.385C10.176 10.1083 11.2106 9.07374 12.4873 9.07349C13.7642 9.07349 14.7995 10.1081 14.7998 11.385Z",
)
