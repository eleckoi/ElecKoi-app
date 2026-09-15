package com.eleckoi.android.feature.chat.ui.message

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutDefaults
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import kotlin.math.roundToInt

@Composable
internal fun AvatarBubble(
    name: String,
    avatarPath: String,
    appearance: AppearanceTheme,
    size: Float,
    shape: ChatAvatarShape,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick == null) Modifier else Modifier.noRippleClickable(onClick = onClick)
    val width = size.coerceIn(ChatLayoutDefaults.AvatarSizeMin, ChatLayoutDefaults.AvatarSizeMax)
    AvatarCircle(
        name = name,
        avatarPath = avatarPath,
        size = width.roundToInt(),
        height = shape.heightFor(width.dp).value.roundToInt(),
        shape = shape.shape(width.dp),
        fontSize = (size * 0.39f).coerceIn(10f, 18f).roundToInt(),
        appearance = appearance,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.userMessageActions(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
internal fun UserMessageMenu(
    expanded: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(appearance.mobileSurface),
    ) {
        UserMessageMenuItem(AppIconPaths.Copy, "", "复制", appearance, onCopy)
        UserMessageMenuItem(emptyList(), "T", "选择文本", appearance, onSelectText)
        UserMessageMenuItem(AppIconPaths.Pencil, "", "修改", appearance, onEdit)
    }
}

@Composable
private fun UserMessageMenuItem(
    iconPaths: List<String>,
    iconText: String,
    text: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, color = appearance.mobileText, fontSize = 15.sp) },
        leadingIcon = {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (iconPaths.isNotEmpty()) {
                    StrokeSvgIcon(
                        paths = iconPaths,
                        color = appearance.mobileText,
                        iconSize = 20.dp,
                        strokeWidth = 1.85f,
                    )
                } else {
                    Text(iconText, color = appearance.mobileText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        onClick = onClick,
    )
}

