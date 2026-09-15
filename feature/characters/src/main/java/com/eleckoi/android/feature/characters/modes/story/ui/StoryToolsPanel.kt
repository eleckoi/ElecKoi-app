package com.eleckoi.android.feature.characters.modes.story.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Schema
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.ui.settings.characterSettingsTrayColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun StoryToolsPanel(
    appearance: AppearanceTheme,
    layoutScale: Float = 1f,
    onOpenAiCreationAssistant: () -> Unit,
    onOpenSettingLibrary: () -> Unit,
    onOpenDynamicSettings: () -> Unit,
    onOpenVariableConfig: () -> Unit,
    onOpenRegexRules: () -> Unit,
    onOpenFrontendBeauty: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val trayColor = characterSettingsTrayColor(appearance)
    val typeScale = layoutScale / LocalDensity.current.fontScale
    val navigationPending = remember { mutableStateOf(false) }
    val sectionShape = RoundedCornerShape((16f * layoutScale).dp)

    fun openToolOnce(action: () -> Unit) {
        if (navigationPending.value) return
        navigationPending.value = true
        scope.launch {
            delay(90)
            action()
            delay(600)
            navigationPending.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = (8f * layoutScale).dp)
            .background(trayColor, sectionShape)
            .padding(horizontal = (12f * layoutScale).dp, vertical = (12f * layoutScale).dp),
    ) {
        Text(
            text = "创作与设定",
            color = appearance.mobileText.copy(alpha = 0.90f),
            fontSize = (16f * typeScale).sp,
            lineHeight = (24f * typeScale).sp,
            fontWeight = FontWeight.Medium,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height((288f * layoutScale).dp),
            pageSpacing = (12f * layoutScale).dp,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (page == 0) {
                    StoryToolRow(layoutScale) {
                        StoryToolCard(
                            "入门教程", "快速上手", Icons.Rounded.School, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                        )
                        StoryToolCard(
                            "AI创作助手", "自动设计", Icons.Rounded.AutoFixHigh, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenAiCreationAssistant) },
                        )
                    }
                    StoryToolRow(layoutScale) {
                        StoryToolCard(
                            "设定库", "世界书", Icons.Rounded.Book, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenSettingLibrary) },
                        )
                        StoryToolCard(
                            "变量配置", "状态骨架", Icons.Rounded.Schema, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenVariableConfig) },
                        )
                    }
                    StoryToolRow(layoutScale) {
                        StoryToolCard(
                            "正则规则", "替换处理", Icons.Rounded.Code, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenRegexRules) },
                        )
                        StoryToolCard(
                            "前端美化", "界面效果", Icons.Rounded.AutoAwesome, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenFrontendBeauty) },
                        )
                    }
                } else {
                    StoryToolRow(layoutScale) {
                        StoryToolCard(
                            "动态设定", "对话演化", Icons.Rounded.Folder, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                            onClick = { openToolOnce(onOpenDynamicSettings) },
                        )
                        StoryToolCard(
                            "资产库", "图片音乐", Icons.Rounded.PermMedia, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                        )
                    }
                    StoryToolRow(layoutScale) {
                        StoryToolCard(
                            "快捷动作", "按钮脚本", Icons.Rounded.Bolt, appearance,
                            Modifier.weight(1f), layoutScale = layoutScale,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            // Arrows replace the old centered page dots; keep them together so they
            // read as one pager control rather than two unrelated edge buttons.
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterPagerArrow(
                enabled = pagerState.currentPage > 0,
                appearance = appearance,
                layoutScale = layoutScale,
                paths = AppIconPaths.ChevronLeft,
                contentDescription = "上一页",
                modifier = Modifier.padding(end = (12f * layoutScale).dp),
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            )
            CharacterPagerArrow(
                enabled = pagerState.currentPage < 1,
                appearance = appearance,
                layoutScale = layoutScale,
                paths = AppIconPaths.ChevronRight,
                contentDescription = "下一页",
                modifier = Modifier.padding(start = (12f * layoutScale).dp),
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            )
        }
    }
}

@Composable
private fun CharacterPagerArrow(
    enabled: Boolean,
    appearance: AppearanceTheme,
    layoutScale: Float,
    paths: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (enabled) appearance.mobileText.copy(alpha = 0.72f) else appearance.mobileMuted.copy(alpha = 0.26f)
    Box(
        modifier = modifier
            .size((44f * layoutScale).dp)
            .clip(RoundedCornerShape((12f * layoutScale).dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(
            paths = paths,
            color = tint,
            iconSize = (18f * layoutScale).dp,
            strokeWidth = 1.8f,
        )
    }
}

@Composable
private fun StoryToolRow(
    layoutScale: Float,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = (12f * layoutScale).dp),
        horizontalArrangement = Arrangement.spacedBy((8f * layoutScale).dp),
        content = content,
    )
}
