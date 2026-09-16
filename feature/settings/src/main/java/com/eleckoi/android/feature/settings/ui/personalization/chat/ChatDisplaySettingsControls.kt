package com.eleckoi.android.feature.settings.ui.personalization.chat

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.common.TunerSliderRow
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.feature.preferences.ChatCodeBlockDefaults
import com.eleckoi.android.feature.preferences.ChatLayoutDefaults
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.ChatReasoningDisplayMode
import com.eleckoi.android.feature.preferences.ChatToolTimelineStyle
import com.eleckoi.android.feature.preferences.layoutDefaults

@Composable
internal fun ChatDisplaySettingsControls(
    section: ChatDisplaySection?,
    draft: ChatLayoutDraft,
    appearance: AppearanceTheme,
    generationStatsEnabled: Boolean,
    onDraftChange: (ChatLayoutDraft) -> Unit,
    onOpenSection: (ChatDisplaySection) -> Unit,
    onOpenMarkdownReadingColors: () -> Unit,
    onGenerationStatsEnabledChange: (Boolean) -> Unit,
) {
    val defaults = draft.layoutMode.layoutDefaults
    when (section) {
        null -> ChatDisplayHub(
            draft = draft,
            appearance = appearance,
            onDraftChange = onDraftChange,
            generationStatsEnabled = generationStatsEnabled,
            onOpenSection = onOpenSection,
            onOpenMarkdownReadingColors = onOpenMarkdownReadingColors,
        )

        ChatDisplaySection.AvatarAndName -> {
            ChatSection(
                label = "头像形状",
                appearance = appearance,
                resetEnabled = draft.avatarShape != defaults.avatarShape,
                onReset = { onDraftChange(draft.copy(avatarShape = defaults.avatarShape)) },
            ) {
                AvatarShapePicker(
                    selected = draft.avatarShape,
                    layoutMode = draft.layoutMode,
                    appearance = appearance,
                    onSelect = { shape ->
                        onDraftChange(draft.copy(avatarShape = shape))
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatSection("大小与间距", appearance) {
                TunerSliderRow(
                    title = "头像大小",
                    value = draft.avatarSize,
                    range = ChatLayoutDefaults.AvatarSizeMin..ChatLayoutDefaults.AvatarSizeMax,
                    appearance = appearance,
                    defaultValue = defaults.avatarSize,
                    onValueChange = { onDraftChange(draft.copy(avatarSize = it)) },
                )
                if (draft.layoutMode == ChatLayoutMode.Social) {
                    TunerSliderRow(
                        title = "头像与气泡间距",
                        value = draft.nameSpacing,
                        range = 0f..20f,
                        appearance = appearance,
                        defaultValue = defaults.nameAvatarSpacing,
                        onValueChange = { onDraftChange(draft.copy(nameSpacing = it)) },
                    )
                } else {
                    TunerSliderRow(
                        title = "名字大小",
                        value = draft.nameFontSize,
                        range = ChatLayoutDefaults.NameFontSizeMin..ChatLayoutDefaults.NameFontSizeMax,
                        appearance = appearance,
                        defaultValue = defaults.nameFontSize,
                        onValueChange = { onDraftChange(draft.copy(nameFontSize = it)) },
                    )
                    TunerSliderRow(
                        title = "名字间距",
                        value = draft.nameSpacing,
                        range = 0f..20f,
                        appearance = appearance,
                        defaultValue = defaults.nameAvatarSpacing,
                        onValueChange = { onDraftChange(draft.copy(nameSpacing = it)) },
                    )
                }
            }
        }

        ChatDisplaySection.BodyText -> ChatSection("正文文字", appearance) {
            TunerSliderRow(
                title = "字号",
                value = draft.fontSize,
                range = ChatLayoutDefaults.MessageFontSizeMin..ChatLayoutDefaults.MessageFontSizeMax,
                appearance = appearance,
                defaultValue = defaults.messageFontSize,
                onValueChange = { onDraftChange(draft.copy(fontSize = it)) },
            )
            TunerSliderRow(
                title = "行距",
                value = draft.lineHeight,
                range = 0.8f..1.6f,
                appearance = appearance,
                step = 0.05f,
                decimalPlaces = 2,
                suffix = "×",
                defaultValue = defaults.lineHeightMultiplier,
                onValueChange = { onDraftChange(draft.copy(lineHeight = it)) },
            )
            TunerSliderRow(
                title = "字距",
                value = draft.letterSpacing,
                range = -1f..4f,
                appearance = appearance,
                defaultValue = defaults.letterSpacing,
                onValueChange = { onDraftChange(draft.copy(letterSpacing = it)) },
            )
            TunerSliderRow(
                title = "段距",
                value = draft.paragraphSpacing,
                range = 0f..24f,
                appearance = appearance,
                defaultValue = defaults.paragraphSpacing,
                onValueChange = { onDraftChange(draft.copy(paragraphSpacing = it)) },
            )
        }

        ChatDisplaySection.Spacing -> ChatSection("间距与留白", appearance) {
            TunerSliderRow(
                title = "左右边距",
                value = draft.horizontalPadding,
                range = 0f..32f,
                appearance = appearance,
                defaultValue = defaults.horizontalPadding,
                onValueChange = { onDraftChange(draft.copy(horizontalPadding = it)) },
            )
            when (draft.layoutMode) {
                ChatLayoutMode.Social -> {
                    TunerSliderRow(
                        title = "消息间距",
                        value = draft.turnSpacing,
                        range = 0f..32f,
                        appearance = appearance,
                        defaultValue = defaults.turnSpacing,
                        onValueChange = { onDraftChange(draft.copy(turnSpacing = it)) },
                    )
                }
                ChatLayoutMode.Roleplay -> {
                    TunerSliderRow(
                        title = "回复间距",
                        value = draft.replySpacing,
                        range = 0f..32f,
                        appearance = appearance,
                        defaultValue = defaults.replySpacing,
                        onValueChange = { onDraftChange(draft.copy(replySpacing = it)) },
                    )
                    TunerSliderRow(
                        title = "消息间距",
                        value = draft.turnSpacing,
                        range = 0f..32f,
                        appearance = appearance,
                        defaultValue = defaults.turnSpacing,
                        onValueChange = { onDraftChange(draft.copy(turnSpacing = it)) },
                    )
                }
                ChatLayoutMode.Agent -> {
                    TunerSliderRow(
                        title = "回复间距",
                        value = draft.replySpacing,
                        range = 0f..32f,
                        appearance = appearance,
                        defaultValue = defaults.replySpacing,
                        onValueChange = { onDraftChange(draft.copy(replySpacing = it)) },
                    )
                    TunerSliderRow(
                        title = "轮次间距",
                        value = draft.turnSpacing,
                        range = 0f..32f,
                        appearance = appearance,
                        defaultValue = defaults.turnSpacing,
                        onValueChange = { onDraftChange(draft.copy(turnSpacing = it)) },
                    )
                }
            }
        }

        ChatDisplaySection.Bubble -> ChatSection("气泡样式", appearance) {
            if (draft.layoutMode == ChatLayoutMode.Agent) {
                ChatToggleRow(
                    title = "角色气泡",
                    icon = Icons.Rounded.ChatBubbleOutline,
                    checked = draft.assistantBubbleEnabled,
                    appearance = appearance,
                    onCheckedChange = {
                        onDraftChange(draft.copy(assistantBubbleEnabled = it))
                    },
                )
                HorizontalDivider(color = appearance.mobileLine)
            }
            TunerSliderRow(
                title = "圆角",
                value = draft.cornerRadius,
                range = 0f..24f,
                appearance = appearance,
                defaultValue = defaults.bubbleCornerRadius,
                onValueChange = { onDraftChange(draft.copy(cornerRadius = it)) },
            )
        }

        ChatDisplaySection.WaitingAnimation -> {
            ChatSection(
                label = "思考动画",
                appearance = appearance,
                resetEnabled = draft.timelineThinkingAnimation !=
                    defaults.timelineThinkingAnimation,
                onReset = {
                    onDraftChange(draft.copy(
                        timelineThinkingAnimation = defaults.timelineThinkingAnimation,
                    ))
                },
            ) {
                TimelineThinkingAnimationPicker(
                    selected = draft.timelineThinkingAnimation,
                    appearance = appearance,
                    onSelect = {
                        onDraftChange(draft.copy(timelineThinkingAnimation = it))
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        ChatDisplaySection.ReasoningDisplay -> ChatSection(
            label = "思维链显示",
            appearance = appearance,
            resetEnabled = draft.reasoningDisplayMode != ChatReasoningDisplayMode.Default,
            onReset = {
                onDraftChange(draft.copy(
                    reasoningDisplayMode = ChatReasoningDisplayMode.Default,
                ))
            },
        ) {
            ChatTextChoicePicker(
                choices = listOf(
                    ChatTextChoice(
                        value = ChatReasoningDisplayMode.Collapsed,
                        label = "适当收起",
                        blurb = "生成时显示简洁状态，需要时再查看完整过程",
                    ),
                    ChatTextChoice(
                        value = ChatReasoningDisplayMode.Expanded,
                        label = "全量展开",
                        blurb = "完整思维链随生成过程持续流式显示",
                    ),
                ),
                selected = draft.reasoningDisplayMode,
                appearance = appearance,
                onSelect = {
                    onDraftChange(draft.copy(reasoningDisplayMode = it))
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        ChatDisplaySection.ToolTimeline -> ChatSection(
            label = "时间线样式",
            appearance = appearance,
            resetEnabled = draft.toolTimelineStyle != ChatToolTimelineStyle.Default,
            onReset = {
                onDraftChange(draft.copy(
                    toolTimelineStyle = ChatToolTimelineStyle.Default,
                ))
            },
        ) {
            ChatTextChoicePicker(
                choices = listOf(
                    ChatTextChoice(
                        value = ChatToolTimelineStyle.Codex,
                        label = "Codex",
                        blurb = "把本轮思考与工具归入可收起的「已处理」区",
                    ),
                    ChatTextChoice(
                        value = ChatToolTimelineStyle.Dsh,
                        label = "DSH",
                        blurb = "按发生顺序显示独立的思考与工具流程行",
                    ),
                ),
                selected = draft.toolTimelineStyle,
                appearance = appearance,
                onSelect = {
                    onDraftChange(draft.copy(toolTimelineStyle = it))
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        ChatDisplaySection.GenerationStats -> ChatSection(
            label = "生成统计",
            appearance = appearance,
        ) {
            ChatToggleRow(
                title = "显示生成统计",
                icon = Icons.Rounded.Memory,
                checked = generationStatsEnabled,
                appearance = appearance,
                subtitle = "在输入框下方显示轮次、耗时、速度、缓存命中和 Token 用量",
                onCheckedChange = { enabled ->
                    onGenerationStatsEnabledChange(enabled)
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        ChatDisplaySection.CodeBlockStyle -> ChatSection(
            label = "代码块样式",
            appearance = appearance,
            resetEnabled = draft.codeBlockStyle != ChatCodeBlockStyle.Default ||
                draft.codeBlockWrapEnabled != ChatCodeBlockDefaults.WrapEnabled ||
                draft.codeBlockShowAllEnabled != ChatCodeBlockDefaults.ShowAllEnabled,
            onReset = {
                onDraftChange(draft.copy(
                    codeBlockStyle = ChatCodeBlockStyle.Default,
                    codeBlockWrapEnabled = ChatCodeBlockDefaults.WrapEnabled,
                    codeBlockShowAllEnabled = ChatCodeBlockDefaults.ShowAllEnabled,
                ))
            },
        ) {
            ChatCodeBlockStylePicker(
                selected = draft.codeBlockStyle,
                appearance = appearance,
                onSelect = { onDraftChange(draft.copy(codeBlockStyle = it)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChatToggleRow(
                title = "自动换行",
                subtitle = "长代码按屏幕宽度折行；关闭时可左右滑动",
                icon = Icons.AutoMirrored.Rounded.WrapText,
                checked = draft.codeBlockWrapEnabled,
                appearance = appearance,
                onCheckedChange = {
                    onDraftChange(draft.copy(codeBlockWrapEnabled = it))
                },
            )
            ChatToggleRow(
                title = "全部显示",
                subtitle = "完整展开代码块，不再限制高度或内部上下滑动",
                icon = Icons.Rounded.FormatLineSpacing,
                checked = draft.codeBlockShowAllEnabled,
                appearance = appearance,
                onCheckedChange = {
                    onDraftChange(draft.copy(codeBlockShowAllEnabled = it))
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
