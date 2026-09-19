package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.isVisuallyDark
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.ui.list.CharacterDrawerList
import kotlin.math.abs

@Composable
internal fun MobileMorePanel(
    visible: Boolean,
    gesturesEnabled: Boolean,
    user: UserProfile,
    characters: CharactersPayload?,
    useCoverArtwork: Boolean,
    appearance: AppearanceTheme,
    appUpdateAvailable: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleAllCharactersExpanded: () -> Unit,
    onToggleCharacterGroupExpanded: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val drawerContainerColor = mobileDrawerContainerColor(appearance)
    val edgeSwipeWidthPx = with(density) { DrawerEdgeSwipeWidth.toPx() }
    val flingThresholdPxPerSecond = with(density) { DrawerFlingThreshold.toPx() }
    val quickSwipeDistancePx = with(density) { DrawerQuickSwipeDistance.toPx() }
    var progress by remember { mutableFloatStateOf(if (visible) 1f else 0f) }
    var gestureDragging by remember { mutableStateOf(false) }
    var characterSearch by remember { mutableStateOf("") }

    LaunchedEffect(visible, gestureDragging) {
        if (!gestureDragging) {
            val target = if (visible) 1f else 0f
            if (abs(target - progress) <= DrawerSettledEpsilon) {
                progress = target
            } else {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = DrawerSettledEpsilon,
                    ),
                ) { value, _ -> progress = value.coerceIn(0f, 1f) }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth * DrawerWidthFraction, DrawerMaximumWidth)
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val drawerScale = DrawerClosedScale + ((1f - DrawerClosedScale) * progress)
        val contentScale = 1f - ((1f - DrawerOpenContentScale) * progress)
        val cornerRadius = DrawerContentCornerRadius * progress
        val contentContainerColor = appearance.mobileSurface
        val gestureModifier = Modifier.pointerInput(
            gesturesEnabled,
            visible,
            panelWidthPx,
            edgeSwipeWidthPx,
            flingThresholdPxPerSecond,
            quickSwipeDistancePx,
        ) {
            if (!gesturesEnabled || panelWidthPx <= 0f) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                if (!visible && down.position.x > edgeSwipeWidthPx) return@awaitEachGesture

                val pointerId = down.id
                val startProgress = progress
                val velocityTracker = VelocityTracker().apply {
                    addPosition(down.uptimeMillis, down.position)
                }
                var horizontalDistance = 0f
                var verticalDistance = 0f
                var lastEventTimeMillis = down.uptimeMillis
                var horizontalDragStarted = false

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    lastEventTimeMillis = change.uptimeMillis
                    horizontalDistance = change.position.x - down.position.x
                    verticalDistance = change.position.y - down.position.y

                    if (!horizontalDragStarted) {
                        val slop = viewConfiguration.touchSlop
                        if (
                            abs(verticalDistance) > slop &&
                            abs(verticalDistance) >= abs(horizontalDistance)
                        ) {
                            break
                        }
                        if (
                            abs(horizontalDistance) > slop &&
                            abs(horizontalDistance) >= abs(verticalDistance) * DrawerHorizontalLockRatio
                        ) {
                            val movesTowardTarget = if (visible) {
                                horizontalDistance < 0f
                            } else {
                                horizontalDistance > 0f
                            }
                            if (!movesTowardTarget) break
                            horizontalDragStarted = true
                            gestureDragging = true
                        }
                    }

                    if (horizontalDragStarted) {
                        val slop = viewConfiguration.touchSlop
                        val dragDistance = horizontalDistance -
                            if (horizontalDistance > 0f) slop else -slop
                        progress = (startProgress + dragDistance / panelWidthPx).coerceIn(0f, 1f)
                        change.consume()
                    }

                    if (!change.pressed) break
                }

                if (horizontalDragStarted) {
                    val velocity = velocityTracker.calculateVelocity()
                    val releaseIsHorizontal =
                        abs(horizontalDistance) >= abs(verticalDistance) * DrawerReleaseHorizontalRatio
                    val horizontalFling =
                        abs(velocity.x) >= flingThresholdPxPerSecond &&
                            abs(velocity.x) >= abs(velocity.y) * DrawerHorizontalVelocityRatio
                    val directionalFling = horizontalFling && if (visible) {
                        velocity.x < 0f
                    } else {
                        velocity.x > 0f
                    }
                    val directionMatchesState = if (visible) {
                        horizontalDistance < 0f
                    } else {
                        horizontalDistance > 0f
                    }
                    val quickDirectionalSwipe =
                        directionMatchesState &&
                            releaseIsHorizontal &&
                            lastEventTimeMillis - down.uptimeMillis <= DrawerQuickSwipeDurationMillis &&
                            abs(horizontalDistance) >= quickSwipeDistancePx
                    val settleOpen = when {
                        directionalFling || quickDirectionalSwipe -> !visible
                        !releaseIsHorizontal -> visible
                        else -> progress >= DrawerSettleOpenFraction
                    }
                    if (settleOpen) onOpen() else onClose()
                    gestureDragging = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerContainerColor)
                .then(gestureModifier),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .graphicsLayer {
                        translationX = -panelWidthPx * (1f - progress)
                        scaleX = drawerScale
                        scaleY = drawerScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .background(drawerContainerColor),
            ) {
                SidebarSearchHeader(
                    keyword = characterSearch,
                    appearance = appearance,
                    onKeywordChange = { characterSearch = it },
                )
                CharacterDrawerList(
                    characters = characters,
                    keyword = characterSearch,
                    appearance = appearance,
                    containerColor = drawerContainerColor,
                    useCoverArtwork = useCoverArtwork,
                    onToggleAllCharactersExpanded = onToggleAllCharactersExpanded,
                    onToggleCharacterGroupExpanded = onToggleCharacterGroupExpanded,
                    onOpenCharacter = onOpenCharacter,
                    onSaveCharacters = onSaveCharacters,
                    modifier = Modifier.weight(1f),
                )
                SidebarFooter(
                    user = user,
                    appearance = appearance,
                    appUpdateAvailable = appUpdateAvailable,
                    onOpenProfile = onOpenProfile,
                    onOpenUpdate = onOpenUpdate,
                    onOpenSettings = onOpenSettings,
                )
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            if (progress < 0.999f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = DrawerClosingScrimAlpha * (1f - progress))),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = panelWidthPx * progress
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                val contentShape = RoundedCornerShape(
                    topStart = cornerRadius,
                    bottomStart = cornerRadius,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .dropShadow(
                            shape = contentShape,
                            color = Color.Black.copy(alpha = DrawerShadowAlpha * progress),
                            blur = DrawerNearShadowRadius * progress,
                            offsetX = DrawerShadowOffsetX * progress,
                            offsetY = DrawerShadowOffsetY * progress,
                        )
                        .dropShadow(
                            shape = contentShape,
                            color = Color.Black.copy(alpha = DrawerShadowAlpha * progress),
                            blur = DrawerAmbientShadowRadius * progress,
                            offsetX = DrawerShadowOffsetX * progress,
                            offsetY = DrawerShadowOffsetY * progress,
                        )
                        .clip(contentShape)
                        .background(contentContainerColor),
                ) {
                    content()
                    if (progress > DrawerSettledEpsilon) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .noRippleClickable(onClick = onClose),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarSearchHeader(
    keyword: String,
    appearance: AppearanceTheme,
    onKeywordChange: (String) -> Unit,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarHeight + 88.dp),
    ) {
        Spacer(modifier = Modifier.height(statusBarHeight + 16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AppSearchField(
                keyword = keyword,
                placeholder = "搜索角色名称",
                appearance = appearance,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
                fontSize = 16.sp,
                iconSize = 20.dp,
                containerColor = if (appearance.mobileSurface.isVisuallyDark()) {
                    appearance.mobileSearchBg
                } else {
                    DeepSeekDrawerSearchLight
                },
                onKeywordChange = onKeywordChange,
            )
        }
    }
}

@Composable
private fun SidebarFooter(
    user: UserProfile,
    appearance: AppearanceTheme,
    appUpdateAvailable: Boolean,
    onOpenProfile: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .noRippleClickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(
                name = user.userName.ifBlank { "用户" },
                size = 32,
                fontSize = 13,
                appearance = appearance,
                avatarPath = user.userAvatar,
            )
            Text(
                text = user.userName.ifBlank { "用户" },
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SidebarFooterImageButton(
            contentDescription = "检查更新",
            icon = if (appUpdateAvailable) Icons.Rounded.ErrorOutline else Icons.Rounded.SystemUpdate,
            tint = if (appUpdateAvailable) MaterialTheme.colorScheme.error else appearance.mobileText,
            onClick = onOpenUpdate,
        )
        SidebarFooterPathButton(
            contentDescription = "设置",
            icon = AppIconPaths.Gear,
            tint = appearance.mobileText,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun SidebarFooterPathButton(
    contentDescription: String,
    icon: List<String>,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(
            paths = icon,
            color = tint,
            iconSize = 20.dp,
            strokeWidth = 1.9f,
        )
    }
}

@Composable
private fun SidebarFooterImageButton(
    contentDescription: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val DrawerWidthFraction = 0.8f
private const val DrawerClosedScale = 0.95f
private const val DrawerOpenContentScale = 0.95f
private const val DrawerClosingScrimAlpha = 0.20f
private const val DrawerSettleOpenFraction = 0.5f
private const val DrawerSettledEpsilon = 0.001f
private const val DrawerHorizontalLockRatio = 1.2f
private const val DrawerReleaseHorizontalRatio = 1.1f
private const val DrawerHorizontalVelocityRatio = 1.05f
private const val DrawerQuickSwipeDurationMillis = 220L
private val DrawerMaximumWidth = 326.dp
private val DrawerContentCornerRadius = 36.dp
private const val DrawerShadowAlpha = 0.10f
private val DrawerNearShadowRadius = 8.dp
private val DrawerAmbientShadowRadius = 40.dp
private val DrawerShadowOffsetX = 4.dp
private val DrawerShadowOffsetY = 2.dp
private val DrawerEdgeSwipeWidth = 20.dp
private val DrawerFlingThreshold = 400.dp
private val DrawerQuickSwipeDistance = 20.dp
private val DeepSeekDrawerSearchLight = Color(0xFFF4F4F4)
private val DeepSeekDrawerSurfaceLight = Color(0xFFFAFAFA)

internal fun mobileDrawerContainerColor(appearance: AppearanceTheme): Color =
    if (appearance.mobileSurface.isVisuallyDark()) {
        appearance.mobileSurface
    } else {
        DeepSeekDrawerSurfaceLight
    }
