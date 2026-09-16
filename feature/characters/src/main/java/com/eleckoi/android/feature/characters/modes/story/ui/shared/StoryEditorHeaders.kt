package com.eleckoi.android.feature.characters.modes.story.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.DshSearchGlyph
import com.eleckoi.android.foundation.design.components.MobileHeaderSidebarButton
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun StoryEditorHeader(
    title: String,
    appearance: AppearanceTheme,
    onBack: (() -> Unit)?,
    onOpenSidebar: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    actionWidth: Dp = 48.dp,
    titleHorizontalPadding: Dp = actionWidth + 8.dp,
    backgroundColor: Color = appearance.mobileBg,
    backButtonElevation: Dp = 4.dp,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .shadow(
                        elevation = backButtonElevation,
                        shape = CircleShape,
                        ambientColor = appearance.mobileText.copy(alpha = 0.12f),
                        spotColor = appearance.mobileText.copy(alpha = 0.10f),
                    )
                    .clip(CircleShape)
                    .background(appearance.mobileSurface)
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 23.dp, strokeWidth = 1.9f)
            }
        } else if (onOpenSidebar != null) {
            MobileHeaderSidebarButton(
                appearance = appearance,
                onClick = onOpenSidebar,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = titleHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = appearance.mobileText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 1.dp),
                    color = appearance.mobileMuted,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(48.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            action?.invoke()
        }
    }
}

@Composable
internal fun StoryHeaderSearchAction(
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = "搜索"
                role = Role.Button
            }
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DshSearchGlyph(
            tint = appearance.mobileText,
            iconSize = 20.dp,
        )
    }
}

@Composable
internal fun StorySearchHeader(
    query: String,
    placeholder: String,
    appearance: AppearanceTheme,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = appearance.mobileBg,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = appearance.mobileText.copy(alpha = 0.12f),
                    spotColor = appearance.mobileText.copy(alpha = 0.10f),
                )
                .clip(CircleShape)
                .background(appearance.mobileSurface)
                .semantics {
                    contentDescription = "退出搜索"
                    role = Role.Button
                }
                .noRippleClickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 23.dp, strokeWidth = 1.9f)
        }
        AppSearchField(
            keyword = query,
            placeholder = placeholder,
            appearance = appearance,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 54.dp),
            height = 44.dp,
            inputModifier = Modifier.focusRequester(focusRequester),
            onKeywordChange = onQueryChange,
        )
    }
}

@Composable
internal fun SettingLibraryHeader(
    managerOpen: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenManager: () -> Unit,
) {
    val gearRotation by animateFloatAsState(
        targetValue = if (managerOpen) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "setting_library_gear_rotation",
    )
    StoryEditorHeader(
        title = "设定编辑页",
        appearance = appearance,
        onBack = onBack,
        actionWidth = 96.dp,
        modifier = modifier,
        action = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                StoryHeaderSearchAction(appearance = appearance, onClick = onSearch)
                Box(
                    modifier = Modifier.size(48.dp).noRippleClickable(onClick = onOpenManager),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(
                        AppIconPaths.Gear,
                        appearance.mobileText,
                        modifier = Modifier.rotate(gearRotation),
                        iconSize = 25.dp,
                        strokeWidth = 1.75f,
                    )
                }
            }
        },
    )
}
