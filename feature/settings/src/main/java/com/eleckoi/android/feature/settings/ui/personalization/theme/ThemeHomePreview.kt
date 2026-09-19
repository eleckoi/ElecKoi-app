package com.eleckoi.android.feature.settings.ui.personalization.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.BottomTab
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.MobileRootBackdrop
import com.eleckoi.android.foundation.design.components.MobileRootActionHeader
import com.eleckoi.android.foundation.design.components.MobileRootChromeBar
import com.eleckoi.android.foundation.design.components.MobileRootChromePlacement
import com.eleckoi.android.foundation.design.components.MobileRootTopBar
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.mobileTabBarContainerColor

@Composable
internal fun ProportionalHomePreview(
    appearance: AppearanceTheme,
    previewBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val previewShape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(previewShape)
            .clipToBounds()
            .border(1.dp, appearance.mobileMuted.copy(alpha = 0.18f), previewShape)
            .clearAndSetSemantics { contentDescription = "主页预览" },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MobileRootBackdrop(
                appearance = appearance,
                previewModel = previewBitmap,
            )
            HomePreviewMock(appearance = appearance)
        }
    }
}

@Composable
private fun HomePreviewMock(
    appearance: AppearanceTheme,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MobileRootTopBar(
            appearance = appearance,
            includeStatusBarInset = false,
        ) {
            MobileRootActionHeader(
                title = "消息",
                appearance = appearance,
                onOpenSidebar = {},
                onSearch = {},
                onAdd = {},
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            MockConversationRow("吃白饭的大肥鱼", "一脸震惊地盯着屏幕…", "22:12", appearance)
            MockConversationRow("星见绫音", "教室里安静下来，只剩窗外的雨…", "22:11", appearance)
            MockConversationRow("还是好鱼嘛", "你好呀，小鱼～", "11:57", appearance)
        }
        MockTabBar(appearance)
    }
}

@Composable
private fun PlaceholderAvatar(size: Dp, appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(appearance.mobileSurface.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(AppIconPaths.User, appearance.mobileMuted, iconSize = size * 0.56f)
    }
}

@Composable
private fun MockConversationRow(
    title: String,
    subtitle: String,
    sideText: String,
    appearance: AppearanceTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceholderAvatar(43.dp, appearance)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp),
        ) {
            Text(
                title,
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(sideText, color = appearance.mobileSoft, fontSize = 11.5.sp)
    }
}

@Composable
private fun MockTabBar(
    appearance: AppearanceTheme,
) {
    val inactiveColor = lerp(appearance.mobileMuted, appearance.mobileSoft, 0.55f)
    MobileRootChromeBar(
        appearance = appearance,
        placement = MobileRootChromePlacement.Bottom,
        chromeColor = mobileTabBarContainerColor(appearance),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab.MobileBarTabs.take(2).forEach { tab ->
                    val active = tab == BottomTab.Messages
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            modifier = Modifier.offset(y = (-5).dp),
                            color = if (active) appearance.mobileText else inactiveColor,
                            fontSize = 14.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-5).dp)
                            .size(width = 36.dp, height = 30.dp)
                            .border(
                                width = 1.5.dp,
                                color = inactiveColor,
                                shape = RoundedCornerShape(9.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilledSvgIcon(
                            paths = listOf(PhosphorRegular.StarFourFill),
                            color = inactiveColor,
                            iconSize = 15.dp,
                            viewportSize = 256f,
                        )
                    }
                }
                BottomTab.MobileBarTabs.drop(2).forEach { tab ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            modifier = Modifier.offset(y = (-5).dp),
                            color = inactiveColor,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(appearance.mobileText.copy(alpha = 0.24f)),
            )
        }
    }
}
