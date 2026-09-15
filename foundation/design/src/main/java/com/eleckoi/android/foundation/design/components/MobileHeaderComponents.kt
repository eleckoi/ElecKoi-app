package com.eleckoi.android.foundation.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.overlayScrim

private val ModalBubbleWidth = 172.dp
private val ModalBubblePointerHeight = 9.dp

private object TopEndBubbleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val pointerHeight = with(density) { ModalBubblePointerHeight.toPx() }
        val pointerHalfWidth = with(density) { 9.dp.toPx() }
        val pointerCenterX = size.width - with(density) { 21.dp.toPx() }
        val radius = with(density) { 16.dp.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(radius, pointerHeight)
                lineTo(pointerCenterX - pointerHalfWidth, pointerHeight)
                lineTo(pointerCenterX, 0f)
                lineTo(pointerCenterX + pointerHalfWidth, pointerHeight)
                lineTo(size.width - radius, pointerHeight)
                quadraticTo(size.width, pointerHeight, size.width, pointerHeight + radius)
                lineTo(size.width, size.height - radius)
                quadraticTo(size.width, size.height, size.width - radius, size.height)
                lineTo(radius, size.height)
                quadraticTo(0f, size.height, 0f, size.height - radius)
                lineTo(0f, pointerHeight + radius)
                quadraticTo(0f, pointerHeight, radius, pointerHeight)
                close()
            },
        )
    }
}

data class MobileHeaderMenuAction(
    val label: String,
    val icon: List<String>,
    // Null means the menu's own text colour. Set it only where the action is destructive.
    val tint: Color? = null,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun MobileHeaderOverflowGlyph(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Rounded.MoreVert,
        contentDescription = null,
        tint = appearance.mobileText,
        modifier = modifier.size(27.dp),
    )
}

@Composable
fun MobileHeaderSidebarButton(
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = "打开侧边栏"
                role = Role.Button
            }
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = null,
            tint = appearance.mobileText,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A visible but deliberately quiet Up affordance for every full-screen child destination. */
@Composable
fun QuietBackButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 21.dp,
) {
    Box(
        modifier = modifier
            .semantics {
                contentDescription = "返回"
                role = Role.Button
            }
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(
            paths = AppIconPaths.Back,
            color = color,
            iconSize = iconSize,
            strokeWidth = 1.9f,
        )
    }
}

@Composable
fun BubbleActionMenu(
    expanded: Boolean,
    actions: List<MobileHeaderMenuAction>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    modalTopEnd: Boolean = false,
) {
    val menuShape = RoundedCornerShape(16.dp)
    if (!modalTopEnd) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 180.dp, max = 260.dp),
            containerColor = appearance.mobileSurface,
            shape = menuShape,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            BubbleActionMenuItems(actions, appearance, onDismiss)
        }
        return
    }
    if (!expanded) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(appearance.overlayScrim().alpha)
            dialogWindow?.setWindowAnimations(0)
        }
        val menuVisibility = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .noRippleClickable(onClick = onDismiss),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp, end = 12.dp),
            ) {
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        initialScale = 0.78f,
                        transformOrigin = TransformOrigin(0.9f, 0f),
                    ),
                ) {
                    Surface(
                        modifier = Modifier.width(ModalBubbleWidth),
                        color = appearance.mobileSurface,
                        shape = TopEndBubbleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(modifier = Modifier.padding(top = ModalBubblePointerHeight)) {
                            BubbleActionMenuItems(actions, appearance, onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleActionMenuItems(
    actions: List<MobileHeaderMenuAction>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(6.dp))
        actions.forEachIndexed { index, action ->
            if (action.dividerBefore && index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .height(1.dp)
                        .background(appearance.mobileLine),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .noRippleClickable {
                        onDismiss()
                        action.onClick()
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = action.tint ?: appearance.mobileText
                StrokeSvgIcon(action.icon, tint, iconSize = 21.dp, strokeWidth = 1.85f)
                Text(
                    action.label,
                    modifier = Modifier.padding(start = 12.dp),
                    color = tint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun MobileProfileHeader(
    userName: String,
    userAvatarPath: String,
    title: String,
    subtitle: String,
    appearance: AppearanceTheme,
    onSearch: (() -> Unit)? = null,
    onAdd: () -> Unit,
    onOpenProfile: () -> Unit,
    addMenuActions: List<MobileHeaderMenuAction> = emptyList(),
    addMenuExpanded: Boolean? = null,
    onAddMenuExpandedChange: (Boolean) -> Unit = {},
) {
    var internalAddMenuOpen by remember { mutableStateOf(false) }
    val addMenuOpen = addMenuExpanded ?: internalAddMenuOpen
    fun setAddMenuOpen(open: Boolean) {
        if (addMenuExpanded == null) internalAddMenuOpen = open
        onAddMenuExpandedChange(open)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 15.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .noRippleClickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(
                name = userName.ifBlank { "用户" },
                avatarPath = userAvatarPath,
                size = 32,
                fontSize = 13,
                appearance = appearance,
            )
            Column(
                modifier = Modifier
                    .padding(start = 9.dp)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    color = appearance.mobileText,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = appearance.mobileMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onSearch != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "搜索"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                DshSearchGlyph(tint = appearance.mobileText, iconSize = 21.dp)
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .noRippleClickable {
                    if (addMenuActions.isEmpty()) onAdd() else setAddMenuOpen(true)
                },
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(paths = AppIconPaths.Plus, color = appearance.mobileText, iconSize = 24.dp)
            BubbleActionMenu(
                expanded = addMenuOpen,
                actions = addMenuActions,
                appearance = appearance,
                onDismiss = { setAddMenuOpen(false) },
                modalTopEnd = addMenuExpanded != null,
            )
        }
    }
}

@Composable
fun MobileRootActionHeader(
    title: String,
    appearance: AppearanceTheme,
    onOpenSidebar: () -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    addMenuActions: List<MobileHeaderMenuAction> = emptyList(),
    addMenuExpanded: Boolean? = null,
    onAddMenuExpandedChange: (Boolean) -> Unit = {},
    useOverflowAction: Boolean = false,
) {
    var internalAddMenuOpen by remember { mutableStateOf(false) }
    val addMenuOpen = addMenuExpanded ?: internalAddMenuOpen
    fun setAddMenuOpen(open: Boolean) {
        if (addMenuExpanded == null) internalAddMenuOpen = open
        onAddMenuExpandedChange(open)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 8.dp, end = 9.dp),
    ) {
        MobileHeaderSidebarButton(
            appearance = appearance,
            onClick = onOpenSidebar,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 104.dp),
            color = appearance.mobileText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "搜索"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                DshSearchGlyph(tint = appearance.mobileText, iconSize = 21.dp)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (useOverflowAction) "更多操作" else "新增"
                        role = Role.Button
                    }
                    .noRippleClickable {
                        if (addMenuActions.isEmpty()) onAdd() else setAddMenuOpen(true)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (useOverflowAction) {
                    MobileHeaderOverflowGlyph(appearance = appearance)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AddCircleOutline,
                        contentDescription = null,
                        tint = appearance.mobileText,
                        modifier = Modifier.size(25.dp),
                    )
                }
                BubbleActionMenu(
                    expanded = addMenuOpen,
                    actions = addMenuActions,
                    appearance = appearance,
                    onDismiss = { setAddMenuOpen(false) },
                    modalTopEnd = addMenuExpanded != null,
                )
            }
        }
    }
}
