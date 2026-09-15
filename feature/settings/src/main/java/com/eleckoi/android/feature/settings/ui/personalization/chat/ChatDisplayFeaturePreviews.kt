package com.eleckoi.android.feature.settings.ui.personalization.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.markdownReadingColors
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.feature.preferences.ChatReasoningDisplayMode

/**
 * Preview for the setting that changes the compact line below the composer. It deliberately uses
 * the normal chat surface rather than the roleplay reading veil: statistics are a composer
 * affordance, not part of a character's scene background.
 */
@Composable
internal fun ChatGenerationStatsPreview(
    enabled: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.mobileChatBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = appearance.mobileSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, appearance.mobileLine),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(appearance.mobileComposerBg, RoundedCornerShape(11.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "说点什么…",
                        color = appearance.mobileSoft,
                        fontSize = 11.5.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statColor = if (enabled) appearance.mobileMuted else appearance.mobileSoft
                    Text(
                        text = "3 轮 · 5 步  |  LLM 4.2s · 工具调用 1.1s  |  " +
                            "首 token 平均 0.8s · 38 tok/s  |  缓存命中 82%  |  " +
                            "输入 6.7K tok · 输出 1.2K tok",
                        color = statColor,
                        fontSize = 9.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** Preview for the collapsed/expanded reasoning presentation inside an assistant reply. */
@Composable
internal fun ChatReasoningDisplayPreview(
    mode: ChatReasoningDisplayMode,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    val expanded = mode == ChatReasoningDisplayMode.Expanded
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.mobileChatBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PreviewSpeakerLabel("AI", appearance)
        Surface(
            color = appearance.mobileSurface,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, appearance.mobileLine),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = appearance.mobileBlue,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = if (expanded) "思维链 · 全量展开" else "思维链 · 适当收起",
                        color = appearance.mobileText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 7.dp),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = appearance.mobileMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
                if (expanded) {
                    ReasoningPreviewLine("先读取相关设定与变量", appearance)
                    ReasoningPreviewLine("再组织回答并检查格式", appearance)
                } else {
                    Text(
                        text = "思考过程已收起，需要时点击展开查看",
                        color = appearance.mobileMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            color = appearance.mobileChatMessageBg,
            shape = RoundedCornerShape(11.dp),
        ) {
            Text(
                text = "这是最终回答内容。",
                color = appearance.mobileChatMessageFg,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

/** Preview for the two Markdown code treatments and their wrapping/height switches. */
@Composable
internal fun ChatCodeBlockPreview(
    style: ChatCodeBlockStyle,
    wrapLines: Boolean,
    showAll: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    val code = appearance.markdownReadingColors(isUser = false)
    val workbench = style == ChatCodeBlockStyle.Workbench
    val rows = if (wrapLines) {
        listOf("val answer = generate()", "render(answer)")
    } else {
        listOf("val answer = generate()  // preview")
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.mobileChatBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PreviewSpeakerLabel("AI", appearance)
        Text(
            text = "下面是代码示例：",
            color = appearance.mobileText,
            fontSize = 12.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = code.codeBackground,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, code.codeForeground.copy(alpha = 0.16f)),
        ) {
            Column {
                if (workbench) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(code.codeForeground.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "kotlin",
                            color = code.codeForeground.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制代码",
                            tint = code.codeForeground.copy(alpha = 0.72f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "复制",
                            color = code.codeForeground.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                    HorizontalDivider(color = code.codeForeground.copy(alpha = 0.14f))
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rows.forEachIndexed { index, row ->
                        Row(verticalAlignment = Alignment.Top) {
                            if (workbench) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = code.codeForeground.copy(alpha = 0.44f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                            }
                            Text(
                                text = row,
                                color = code.codeForeground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = if (wrapLines) Int.MAX_VALUE else 1,
                                softWrap = wrapLines,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = buildString {
                append(if (wrapLines) "自动换行" else "可横向滑动")
                append("  ·  ")
                append(if (showAll) "全部显示" else "限制高度")
            },
            color = appearance.mobileMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun PreviewSpeakerLabel(label: String, appearance: AppearanceTheme) {
    Text(
        text = label,
        color = appearance.mobileText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ReasoningPreviewLine(text: String, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(appearance.mobileBlue, RoundedCornerShape(50)),
        )
        Text(
            text = text,
            color = appearance.mobileMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}
