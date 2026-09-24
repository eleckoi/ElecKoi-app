package com.eleckoi.android.feature.studio.ui.assistant.workspace.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme

/** Owns only the Compose gesture, animation, and surface state for the workspace drawer. */
@Composable
internal fun CreationWorkspaceDrawerLayout(
    drawerOpen: Boolean,
    appearance: AppearanceTheme,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = drawerOpen, onBack = onCloseDrawer)
    val density = LocalDensity.current
    val flingThresholdPxPerSecond = with(density) { 125.dp.toPx() }
    val shortSwipeMinDistancePx = with(density) { 24.dp.toPx() }
    var progress by remember { mutableFloatStateOf(if (drawerOpen) 1f else 0f) }
    var gestureDragging by remember { mutableStateOf(false) }

    LaunchedEffect(drawerOpen, gestureDragging) {
        if (!gestureDragging) {
            val target = if (drawerOpen) 1f else 0f
            val remaining = kotlin.math.abs(target - progress)
            if (remaining <= 0.001f) {
                progress = target
            } else {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = (70 + (130 * remaining)).toInt(),
                        easing = LinearOutSlowInEasing,
                    ),
                ) { value, _ ->
                    progress = value
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileSurface),
    ) {
        val drawerWidth = minOf(maxWidth * 0.82f, 360.dp)
        val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        val surfaceScale = 1f - (0.036f * progress)
        val liftProgress = kotlin.math.sqrt(progress.coerceIn(0f, 1f))
        val surfaceShape = RoundedCornerShape((26f * progress).dp)
        val horizontalDrawerGesture = Modifier.pointerInput(
            drawerOpen,
            drawerWidthPx,
            flingThresholdPxPerSecond,
            shortSwipeMinDistancePx,
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Final,
                )
                val startProgress = progress
                val pointerId = down.id
                val velocityTracker = VelocityTracker().apply {
                    addPosition(down.uptimeMillis, down.position)
                }
                var horizontalDistance = 0f
                var verticalDistance = 0f
                var lastEventTimeMillis = down.uptimeMillis
                var horizontalDragStarted = false
                var childClaimedGesture = false

                while (true) {
                    // Let horizontally scrollable content claim the gesture before the drawer.
                    val event = awaitPointerEvent(pass = PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (change.pressed && change.isConsumed) {
                        childClaimedGesture = true
                        break
                    }
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    lastEventTimeMillis = change.uptimeMillis
                    horizontalDistance = change.position.x - down.position.x
                    verticalDistance = change.position.y - down.position.y

                    if (!horizontalDragStarted) {
                        val slop = viewConfiguration.touchSlop
                        if (
                            kotlin.math.abs(verticalDistance) > slop &&
                            kotlin.math.abs(verticalDistance) >= kotlin.math.abs(horizontalDistance)
                        ) {
                            break
                        }
                        if (
                            kotlin.math.abs(horizontalDistance) > slop &&
                            kotlin.math.abs(horizontalDistance) >=
                                kotlin.math.abs(verticalDistance) * HorizontalLockRatio
                        ) {
                            val movesTowardDrawer = if (drawerOpen) {
                                horizontalDistance < 0f
                            } else {
                                horizontalDistance > 0f
                            }
                            if (movesTowardDrawer) {
                                horizontalDragStarted = true
                                gestureDragging = true
                            } else {
                                break
                            }
                        }
                    }

                    if (horizontalDragStarted) {
                        val slop = viewConfiguration.touchSlop
                        val dragDistance = horizontalDistance - if (horizontalDistance > 0f) slop else -slop
                        progress = (startProgress + dragDistance / drawerWidthPx).coerceIn(0f, 1f)
                        change.consume()
                    }

                    if (!change.pressed) break
                }

                if (horizontalDragStarted && !childClaimedGesture) {
                    val velocity = velocityTracker.calculateVelocity()
                    val releaseIsHorizontal =
                        kotlin.math.abs(horizontalDistance) >=
                            kotlin.math.abs(verticalDistance) * ReleaseHorizontalRatio
                    val horizontalFling =
                        kotlin.math.abs(velocity.x) >= flingThresholdPxPerSecond &&
                            kotlin.math.abs(velocity.x) >=
                            kotlin.math.abs(velocity.y) * HorizontalVelocityRatio
                    val directionalFling = horizontalFling && if (drawerOpen) {
                        velocity.x < 0f
                    } else {
                        velocity.x > 0f
                    }
                    val directionMatchesState = if (drawerOpen) {
                        horizontalDistance < 0f
                    } else {
                        horizontalDistance > 0f
                    }
                    val quickDirectionalSwipe =
                        directionMatchesState &&
                            releaseIsHorizontal &&
                            lastEventTimeMillis - down.uptimeMillis <= QuickDirectionalSwipeMaxDurationMillis &&
                            kotlin.math.abs(horizontalDistance) >= shortSwipeMinDistancePx
                    val settleOpen = if (directionalFling || quickDirectionalSwipe) {
                        !drawerOpen
                    } else if (!releaseIsHorizontal) {
                        drawerOpen
                    } else if (drawerOpen) {
                        horizontalDistance >= 0f || progress >= 0.5f
                    } else {
                        horizontalDistance > 0f && progress >= 0.5f
                    }
                    if (settleOpen) onOpenDrawer() else onCloseDrawer()
                }
                if (horizontalDragStarted) gestureDragging = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(horizontalDrawerGesture),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.mobileSurface),
            ) {
                Box(
                    modifier = Modifier
                        .width(drawerWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = -drawerWidthPx * (1f - progress)
                        },
                ) {
                    drawerContent()
                }
                if (progress < 0.999f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f * (1f - progress))),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = drawerWidthPx * progress
                        scaleX = surfaceScale
                        scaleY = surfaceScale
                        shadowElevation = 40.dp.toPx() * liftProgress
                        ambientShadowColor = Color.Black.copy(alpha = 0.85f * liftProgress)
                        spotShadowColor = Color.Black.copy(alpha = 0.95f * liftProgress)
                        shape = surfaceShape
                        clip = progress > 0.001f
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                content()
                if (progress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.08f * progress))
                            .clickable(enabled = drawerOpen, onClick = onCloseDrawer),
                    )
                }
            }
        }
    }
}

private const val HorizontalLockRatio = 1.2f
private const val ReleaseHorizontalRatio = 1.1f
private const val HorizontalVelocityRatio = 1.05f
private const val QuickDirectionalSwipeMaxDurationMillis = 220L
