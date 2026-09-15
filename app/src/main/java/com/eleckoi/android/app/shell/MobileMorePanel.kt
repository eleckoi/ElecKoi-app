package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.ui.list.CharacterDrawerList
import com.eleckoi.android.foundation.design.overlayScrim

@Composable
internal fun MobileMorePanel(
    visible: Boolean,
    user: UserProfile,
    characters: CharactersPayload?,
    useCoverArtwork: Boolean,
    appearance: AppearanceTheme,
    appUpdateAvailable: Boolean,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleAllCharactersExpanded: () -> Unit,
    onToggleCharacterGroupExpanded: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth * 0.84f, 360.dp)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = drawerFadeTween()),
            exit = fadeOut(animationSpec = drawerFadeTween()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.overlayScrim())
                    .noRippleClickable(onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = drawerSlideTween()) { -it } + fadeIn(animationSpec = drawerFadeTween(), initialAlpha = 0.98f),
            exit = slideOutHorizontally(animationSpec = drawerSlideTween()) { -it } + fadeOut(animationSpec = drawerFadeTween(), targetAlpha = 0.98f),
        ) {
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .background(appearance.mobileSurface),
            ) {
                SidebarProfileHeader(
                    user = user,
                    appearance = appearance,
                    onOpenProfile = onOpenProfile,
                )
                CharacterDrawerList(
                    characters = characters,
                    appearance = appearance,
                    useCoverArtwork = useCoverArtwork,
                    onToggleAllCharactersExpanded = onToggleAllCharactersExpanded,
                    onToggleCharacterGroupExpanded = onToggleCharacterGroupExpanded,
                    onOpenCharacter = onOpenCharacter,
                    onSaveCharacters = onSaveCharacters,
                    modifier = Modifier.weight(1f),
                )
                // Laid out across rather than stacked: these are utilities, not destinations, and a
                // horizontal strip claims a third of the height a list of full-width rows does.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 6.dp, bottom = 10.dp),
                ) {
                    MoreFooterAction(
                        label = "设置",
                        icon = AppIconPaths.Gear,
                        appearance = appearance,
                        onClick = onOpenSettings,
                    )
                    MoreFooterImageAction(
                        label = "更新",
                        icon = if (appUpdateAvailable) {
                            Icons.Rounded.ErrorOutline
                        } else {
                            Icons.Rounded.SystemUpdate
                        },
                        tint = if (appUpdateAvailable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            appearance.mobileText
                        },
                        onClick = onOpenUpdate,
                    )
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun SidebarProfileHeader(
    user: UserProfile,
    appearance: AppearanceTheme,
    onOpenProfile: () -> Unit,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarHeight + 92.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(62.dp)
                .noRippleClickable(onClick = onOpenProfile)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(appearance.mobileSurface.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center,
            ) {
                AvatarCircle(
                    name = user.userName.ifBlank { "用户" },
                    size = 40,
                    fontSize = 15,
                    appearance = appearance,
                    avatarPath = user.userAvatar,
                )
            }
            Text(
                text = user.userName.ifBlank { "用户" },
                color = appearance.mobileText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp),
                maxLines = 1,
            )
            StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileMuted, iconSize = 18.dp)
        }
    }
}

@Composable
private fun MoreFooterImageAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun MoreFooterAction(
    label: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StrokeSvgIcon(icon, appearance.mobileText, iconSize = 23.dp)
        Text(
            text = label,
            color = appearance.mobileText,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

private fun drawerSlideTween() = tween<IntOffset>(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

private fun drawerFadeTween() = tween<Float>(durationMillis = 180, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
