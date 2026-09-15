package com.eleckoi.android.feature.characters.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun ScrapbookFooter(
    layoutScale: Float,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    val ink = Color(0xFF1C2026)
    val typeScale = layoutScale / LocalDensity.current.fontScale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(44.dp, (47f * layoutScale).dp))
            .padding(top = (9f * layoutScale).dp),
        horizontalArrangement = Arrangement.spacedBy((3f * layoutScale).dp, Alignment.End),
        verticalAlignment = Alignment.Top,
    ) {
        ScrapbookFooterControl(
            hitWidth = 44.dp,
            visualWidth = (38f * layoutScale).dp,
            visualHeight = (38f * layoutScale).dp,
            contentDescription = "查看修改记录",
            enabled = true,
            borderColor = Color(0xFF2B3440),
            onClick = {},
        ) {
            StrokeSvgIcon(
                paths = AppIconPaths.History,
                color = ink,
                iconSize = (17f * layoutScale).dp,
                strokeWidth = 1.75f,
            )
        }
        ScrapbookFooterControl(
            hitWidth = maxOf(44.dp, (110f * layoutScale).dp),
            visualWidth = (104f * layoutScale).dp,
            visualHeight = (38f * layoutScale).dp,
            contentDescription = "发送消息",
            enabled = enabled,
            borderColor = Color(0xFF2B3440),
            onClick = onSend,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledSvgIcon(
                    paths = DshIconPaths.NewChat,
                    color = ink,
                    iconSize = (15f * layoutScale).dp,
                    viewportSize = DshIconPaths.Viewport16,
                )
                Text(
                    text = "发送消息",
                    modifier = Modifier.padding(start = (7f * layoutScale).dp),
                    color = ink,
                    fontSize = (13.5f * typeScale).sp,
                    lineHeight = (17f * typeScale).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CharacterDraftFooter(
    layoutScale: Float,
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    val ink = Color(0xFF1C2026)
    val typeScale = layoutScale / LocalDensity.current.fontScale
    val shape = RoundedCornerShape((12f * layoutScale).dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = (8f * layoutScale).dp)
            .height(maxOf(44.dp, (46f * layoutScale).dp))
            .semantics { contentDescription = "创建角色" }
            .clip(shape)
            .background(ink.copy(alpha = if (enabled) 1f else 0.42f))
            .noRippleClickable(enabled = enabled, onClick = onCreate),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "创建角色",
            color = Color.White,
            fontSize = (14f * typeScale).sp,
            lineHeight = (18f * typeScale).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ScrapbookFooterControl(
    hitWidth: Dp,
    visualWidth: Dp,
    visualHeight: Dp,
    contentDescription: String,
    enabled: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val faceShape = RoundedCornerShape(1.dp)
    Box(
        modifier = Modifier
            .width(hitWidth)
            .height(maxOf(44.dp, visualHeight))
            .semantics { this.contentDescription = contentDescription }
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = visualWidth, height = visualHeight)
                .offset(x = 3.dp, y = 3.dp)
                .background(borderColor.copy(alpha = 0.20f), faceShape),
        )
        Box(
            modifier = Modifier
                .size(width = visualWidth, height = visualHeight)
                .background(Color.White, faceShape)
                .border(1.5.dp, borderColor.copy(alpha = if (enabled) 1f else 0.46f), faceShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
internal fun EmptyCharacterSettings(appearance: AppearanceTheme, onBack: () -> Unit) {
    PinnedStatusScaffold(
        appearance = appearance,
        imeAware = false,
        backgroundColor = appearance.mobileBg,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(appearance.mobileBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 29.dp)
            }
            Text(
                "角色设定",
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(appearance.mobileBg),
            contentAlignment = Alignment.Center,
        ) {
            Text("还没有角色", color = appearance.mobileMuted, fontSize = 16.sp)
        }
    }
}
