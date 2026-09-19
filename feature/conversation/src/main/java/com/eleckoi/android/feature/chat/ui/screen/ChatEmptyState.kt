package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun ChatCenteredStatus(
    text: String,
    appearance: AppearanceTheme,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appearance.mobileMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
internal fun EmptyChatState(
    hasCharacter: Boolean,
    appearance: AppearanceTheme,
    onCreateChat: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledSvgIcon(
                paths = DshIconPaths.NewChat,
                color = appearance.mobileSoft,
                iconSize = 44.dp,
                viewportSize = DshIconPaths.Viewport16,
            )
            Text(
                text = if (hasCharacter) "还没有对话" else "还没有角色",
                color = appearance.mobileText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = if (hasCharacter) "点击新建，开始这段对话" else "请先创建角色",
                color = appearance.mobileMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (hasCharacter) {
                Row(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .noRippleClickable(onClick = onCreateChat)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StrokeSvgIcon(
                        paths = AppIconPaths.Plus,
                        color = appearance.mobileAccentFg,
                        iconSize = 17.dp,
                        strokeWidth = 1.8f,
                    )
                    Text(
                        text = "新建对话",
                        color = appearance.mobileAccentFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
}
