package com.eleckoi.android.feature.settings.ui.personalization.chat

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.common.TunerSliderRow
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.ChatReasoningDisplayMode
import com.eleckoi.android.feature.preferences.ChatTimelineThinkingAnimation
import com.eleckoi.android.feature.preferences.ChatToolTimelineStyle
import com.eleckoi.android.feature.preferences.ChatWaitingAnimation
import com.eleckoi.android.feature.preferences.RoleplayLayoutDefaults

// Label plus a card, so each group reads as one block instead of loose rows on the page.
@Composable
internal fun ChatSection(
    label: String,
    appearance: AppearanceTheme,
    resetEnabled: Boolean = false,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (onReset != null) {
            ResetValueButton(
                enabled = resetEnabled,
                appearance = appearance,
                contentDescription = "恢复${label}默认值",
                onClick = onReset,
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appearance.mobileSurface)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 2.dp),
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ResetValueButton(
    enabled: Boolean,
    appearance: AppearanceTheme,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = contentDescription,
            tint = if (enabled) appearance.mobileMuted else appearance.mobileLine,
            modifier = Modifier.size(17.dp),
        )
    }
}

// The groups the page splits into. Each one is a leaf: it edits the same draft and watches the same
// preview as the hub, so it lives inside this page rather than on the app's navigator.
internal enum class ChatDisplaySection(val title: String, val icon: ImageVector) {
    AvatarAndName("头像与名字", Icons.Rounded.AccountCircle),
    BodyText("正文文字", Icons.Rounded.TextFields),
    Spacing("间距与留白", Icons.Rounded.FormatLineSpacing),
    WaitingAnimation("时间线动画", Icons.Rounded.HourglassEmpty),
    GenerationStats("生成统计", Icons.Rounded.Memory),
    ToolTimeline("AI助手设置", Icons.Rounded.AccountTree),
    ReasoningDisplay("思维链显示", Icons.Rounded.Tune),
    CodeBlockStyle("代码块样式", Icons.Rounded.KeyboardAlt),
    Bubble("气泡样式", Icons.Rounded.ChatBubbleOutline),
}

internal fun ChatDisplaySection.titleFor(layoutMode: ChatLayoutMode): String =
    if (this == ChatDisplaySection.AvatarAndName && layoutMode == ChatLayoutMode.Social) {
        "头像与气泡"
    } else {
        title
    }

internal val ChatLayoutMode.label: String
    get() = when (this) {
        ChatLayoutMode.Social -> "社交软件布局"
        ChatLayoutMode.Agent -> "Agent布局"
        ChatLayoutMode.Roleplay -> "角色扮演布局"
    }

internal val ChatLayoutMode.blurb: String
    get() = when (this) {
        ChatLayoutMode.Social -> "左右分栏，像日常聊天软件"
        ChatLayoutMode.Agent -> "头像在上，回复占满宽度"
        ChatLayoutMode.Roleplay -> "去掉气泡，为长段角色对话设计"
    }

internal val ChatAvatarShape.label: String
    get() = when (this) {
        ChatAvatarShape.Circle -> "圆形"
        ChatAvatarShape.RoundedSquare -> "圆角正方形"
        ChatAvatarShape.Portrait -> "圆角矩形"
    }

@Composable
internal fun ChatDisplayHub(
    draft: ChatLayoutDraft,
    appearance: AppearanceTheme,
    onDraftChange: (ChatLayoutDraft) -> Unit,
    generationStatsEnabled: Boolean,
    onOpenSection: (ChatDisplaySection) -> Unit,
    onOpenMarkdownReadingColors: () -> Unit,
) {
    ChatSection("对话布局", appearance) {
        LayoutModePicker(
            selected = draft.layoutMode,
            appearance = appearance,
            onSelect = { onDraftChange(draft.copy(layoutMode = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    ChatSection("通用", appearance) {
        ChatEntryRow(
            title = ChatDisplaySection.AvatarAndName.titleFor(draft.layoutMode),
            icon = ChatDisplaySection.AvatarAndName.icon,
            value = "${draft.resolvedAvatarShape.label} · ${formatChatValue(draft.avatarSize)}",
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.AvatarAndName) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.BodyText.title,
            icon = ChatDisplaySection.BodyText.icon,
            value = "${formatChatValue(draft.fontSize)} · ${formatChatValue(draft.lineHeight)}×",
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.BodyText) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.Spacing.title,
            icon = ChatDisplaySection.Spacing.icon,
            value = "${formatChatValue(draft.horizontalPadding)} / ${formatChatValue(draft.turnSpacing)}",
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.Spacing) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.WaitingAnimation.title,
            icon = ChatDisplaySection.WaitingAnimation.icon,
            value = buildString {
                append(if (draft.waitingAnimation == ChatWaitingAnimation.Cat) "鲸鱼娘" else "三点")
                append(" · ")
                append(
                    when (draft.timelineThinkingAnimation) {
                        ChatTimelineThinkingAnimation.Bars -> "三竖条"
                        ChatTimelineThinkingAnimation.HalfBody -> "半身"
                        ChatTimelineThinkingAnimation.BigHead -> "大头"
                    },
                )
            },
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.WaitingAnimation) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.GenerationStats.title,
            icon = ChatDisplaySection.GenerationStats.icon,
            value = if (generationStatsEnabled) "显示" else "关闭",
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.GenerationStats) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.ToolTimeline.title,
            icon = ChatDisplaySection.ToolTimeline.icon,
            value = when (draft.toolTimelineStyle) {
                ChatToolTimelineStyle.Codex -> "Codex · 已处理"
                ChatToolTimelineStyle.Dsh -> "DSH · 流程行"
            },
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.ToolTimeline) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.ReasoningDisplay.title,
            icon = ChatDisplaySection.ReasoningDisplay.icon,
            value = when (draft.reasoningDisplayMode) {
                ChatReasoningDisplayMode.Collapsed -> "适当收起"
                ChatReasoningDisplayMode.Expanded -> "全量展开"
            },
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.ReasoningDisplay) },
        )
        ChatEntryRow(
            title = ChatDisplaySection.CodeBlockStyle.title,
            icon = ChatDisplaySection.CodeBlockStyle.icon,
            value = when (draft.codeBlockStyle) {
                ChatCodeBlockStyle.Simple -> "简洁"
                ChatCodeBlockStyle.Workbench -> "工作台"
            } + (if (draft.codeBlockWrapEnabled) " · 换行" else "") +
                (if (draft.codeBlockShowAllEnabled) " · 全部显示" else ""),
            appearance = appearance,
            onClick = { onOpenSection(ChatDisplaySection.CodeBlockStyle) },
        )
        ChatEntryRow(
            title = "文本颜色",
            icon = Icons.Rounded.Palette,
            value = "斜体、下划线、引用与代码",
            appearance = appearance,
            onClick = onOpenMarkdownReadingColors,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    // Settings that only exist in one layout. Left visible but greyed with the reason spelled out —
    // hiding them makes the page look like the feature was removed, and people go hunting for it.
    ChatSection("当前布局专属", appearance) {
        if (draft.layoutMode.drawsBubbleBackground) {
            ChatEntryRow(
                title = ChatDisplaySection.Bubble.title,
                icon = ChatDisplaySection.Bubble.icon,
                value = when (draft.layoutMode) {
                    ChatLayoutMode.Social -> "固定开启 · 圆角 ${formatChatValue(draft.cornerRadius)}"
                    ChatLayoutMode.Agent ->
                        "${if (draft.assistantBubbleEnabled) "已开启" else "已关闭"} · " +
                            "圆角 ${formatChatValue(draft.cornerRadius)}"
                    ChatLayoutMode.Roleplay -> error("角色扮演布局不使用气泡")
                },
                appearance = appearance,
                onClick = { onOpenSection(ChatDisplaySection.Bubble) },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "消息底板",
                    color = appearance.mobileText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                ResetValueButton(
                    enabled = draft.roleplayCardPanel != RoleplayLayoutDefaults.CardPanel,
                    appearance = appearance,
                    contentDescription = "恢复消息底板默认值",
                    onClick = {
                        onDraftChange(
                            draft.copy(roleplayCardPanel = RoleplayLayoutDefaults.CardPanel),
                        )
                    },
                )
            }
            MessagePanelPicker(
                cardPanel = draft.roleplayCardPanel,
                appearance = appearance,
                onSelect = { onDraftChange(draft.copy(roleplayCardPanel = it)) },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
