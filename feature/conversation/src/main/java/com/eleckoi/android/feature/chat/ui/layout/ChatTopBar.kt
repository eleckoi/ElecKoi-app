package com.eleckoi.android.feature.chat.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Route
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.DshPresetGlyph
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.SvgCircle
import com.eleckoi.android.foundation.design.components.noRippleClickable
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTopBar(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onMore: () -> Unit,
    moreMenuExpanded: Boolean,
    onDismissMoreMenu: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenTrajectory: () -> Unit,
    onCustomizeBackground: () -> Unit,
    onCreateChat: () -> Unit,
    effectiveBackgroundPath: String = appearance.textureImagePath,
    compact: Boolean = false,
    // In the roleplay layout the bar is not chrome floating over the scene, it is the top edge of
    // the same slab the messages sit on. Given a colour it paints status bar and title row as one
    // band, so the first message can butt straight against it instead of drifting below it.
    bandColor: Color? = null,
    stableStatusBarInset: Boolean = false,
) {
    val hasBackgroundImage = remember(effectiveBackgroundPath) {
        effectiveBackgroundPath.takeIf(String::isNotBlank)?.let(::File)?.exists() == true
    }
    val headerBackground = when {
        bandColor != null -> bandColor
        !hasBackgroundImage -> appearance.mobileChatHeaderBg.copy(alpha = 0.92f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (bandColor != null) 1f else 0f)
            .background(headerBackground),
    ) {
        if (stableStatusBarInset) {
            Spacer(
                modifier = Modifier.windowInsetsTopHeight(
                    WindowInsets.statusBarsIgnoringVisibility,
                ),
            )
        } else {
            Box(modifier = Modifier.statusBarsPadding())
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Exactly one icon tall. Nothing above it, nothing below it.
                .height(if (compact) CompactIconSize else 48.dp)
                .padding(start = 8.dp, end = if (compact) 8.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) CompactIconSize else 36.dp)
                    .semantics {
                        contentDescription = "返回"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(
                    paths = AppIconPaths.Back,
                    color = appearance.mobileText,
                    iconSize = if (compact) 22.dp else 28.dp,
                    strokeWidth = 1.85f,
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp),
                color = appearance.mobileText,
                fontSize = if (compact) 15.sp else 16.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(if (compact) CompactIconSize else 36.dp)
                    .semantics {
                        contentDescription = "对话操作"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                FilledSvgIcon(
                    paths = listOf(PhosphorRegular.SlidersHorizontalBold),
                    color = appearance.mobileText,
                    iconSize = if (compact) 19.dp else 23.dp,
                    viewportSize = 256f,
                )
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = onDismissMoreMenu,
                    modifier = Modifier.background(appearance.mobileSurface),
                ) {
                    DropdownMenuItem(
                        text = { Text("预设", color = appearance.mobileText, fontSize = 14.sp) },
                        leadingIcon = {
                            DshPresetGlyph(tint = appearance.mobileMuted, iconSize = 19.dp)
                        },
                        onClick = {
                            onDismissMoreMenu()
                            onOpenPresets()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("工具", color = appearance.mobileText, fontSize = 14.sp) },
                        leadingIcon = {
                            StrokeSvgIcon(
                                paths = AppIconPaths.Plug,
                                color = appearance.mobileMuted,
                                iconSize = 19.dp,
                                strokeWidth = 1.85f,
                            )
                        },
                        onClick = {
                            onDismissMoreMenu()
                            onOpenTools()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("轨迹", color = appearance.mobileText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Route,
                                contentDescription = null,
                                tint = appearance.mobileMuted,
                                modifier = Modifier.size(19.dp),
                            )
                        },
                        onClick = {
                            onDismissMoreMenu()
                            onOpenTrajectory()
                        },
                    )
                    HorizontalDivider(color = appearance.mobileLine)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "自定义背景",
                                color = appearance.mobileText,
                                fontSize = 14.sp,
                            )
                        },
                        leadingIcon = {
                            StrokeSvgIcon(
                                paths = AppIconPaths.PictureFrame,
                                color = appearance.mobileMuted,
                                iconSize = 19.dp,
                                circles = listOf(SvgCircle(9f, 10f, 1.4f)),
                            )
                        },
                        onClick = {
                            onDismissMoreMenu()
                            onCustomizeBackground()
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(if (compact) CompactIconSize else 36.dp)
                    .semantics {
                        contentDescription = "新建对话"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onCreateChat),
                contentAlignment = Alignment.Center,
            ) {
                FilledSvgIcon(
                    paths = DshIconPaths.NewChat,
                    color = appearance.mobileText,
                    iconSize = if (compact) 19.dp else 23.dp,
                    viewportSize = DshIconPaths.Viewport16,
                )
            }
        }
    }
}

private val CompactIconSize = 30.dp
