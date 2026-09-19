package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular

@Composable
fun MobileTabBar(
    activeTab: BottomTab,
    appearance: AppearanceTheme,
    onChange: (BottomTab) -> Unit,
    onCenterAction: () -> Unit = {},
) {
    val inactiveColor = lerp(appearance.mobileMuted, appearance.mobileSoft, 0.55f)
    val centerActionColor = appearance.mobileText
    MobileRootChromeBar(
        appearance = appearance,
        placement = MobileRootChromePlacement.Bottom,
        chromeColor = mobileTabBarContainerColor(appearance),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab.MobileBarTabs.take(2).forEach { tab ->
                    MobileTextTab(tab, activeTab, appearance, inactiveColor, onChange)
                }
                MobileCenterAction(centerActionColor, onCenterAction)
                BottomTab.MobileBarTabs.drop(2).forEach { tab ->
                    MobileTextTab(tab, activeTab, appearance, inactiveColor, onChange)
                }
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun RowScope.MobileTextTab(
    tab: BottomTab,
    activeTab: BottomTab,
    appearance: AppearanceTheme,
    inactiveColor: Color,
    onChange: (BottomTab) -> Unit,
) {
    val active = tab == activeTab
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .height(64.dp)
            .semantics {
                selected = active
                role = Role.Tab
            }
            .noRippleClickable { onChange(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tab.label,
            modifier = Modifier.offset(y = (-8).dp),
            color = if (active) appearance.mobileText else inactiveColor,
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun RowScope.MobileCenterAction(
    actionColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .semantics {
                contentDescription = "AI 创作助手"
                role = Role.Button
            }
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-8).dp)
                .size(width = 36.dp, height = 30.dp)
                .border(
                    width = 1.5.dp,
                    color = actionColor,
                    shape = RoundedCornerShape(9.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            FilledSvgIcon(
                paths = listOf(PhosphorRegular.StarFourFill),
                color = actionColor,
                iconSize = 15.dp,
                viewportSize = 256f,
            )
        }
    }
}

fun mobileTabBarContainerColor(appearance: AppearanceTheme): Color =
    appearance.mobileTabbarBg
