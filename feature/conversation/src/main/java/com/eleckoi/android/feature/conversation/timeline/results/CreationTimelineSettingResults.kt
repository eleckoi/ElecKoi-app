package com.eleckoi.android.feature.conversation.timeline.results

import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.components.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.conversation.markdown.CreationMarkdownText
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun GlobToolResultBlock(
    result: AgentGlobToolResult,
    appearance: AppearanceTheme,
) {
    val displayedPaths = (result.requiredPaths + result.paths).distinct()
    val requiredPathSet = result.requiredPaths.toSet()
    val keywordPathSet = result.pathDetails
        .filterValues { detail -> detail.readStrategy == "keyword" }
        .keys
    val variableConditionPathSet = result.pathDetails
        .filterValues { detail -> detail.readStrategy == "variable_condition" }
        .keys
    val onDemandPathSet = result.pathDetails
        .filterValues { detail -> detail.readStrategy == "normal" }
        .keys
    val staticRequiredCount = (requiredPathSet - keywordPathSet - variableConditionPathSet).size
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = buildString {
                if (result.requiredPaths.isEmpty()) {
                    append("匹配结果 · ${result.paths.size} 项")
                } else {
                    append("返回目录 · ${displayedPaths.size} 项")
                }
                if (result.omitted > 0) append("（另有 ${result.omitted} 项未显示）")
            },
            color = appearance.mobileMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (result.requiredPaths.isNotEmpty()) {
            Text(
                text = buildAnnotatedString {
                    append("匹配 ${result.paths.size}")
                    if (staticRequiredCount > 0) {
                        withStyle(SpanStyle(color = appearance.mobileBlue)) {
                            append(" · 必读 $staticRequiredCount")
                        }
                    }
                    if (keywordPathSet.isNotEmpty()) {
                        withStyle(SpanStyle(color = KeywordResultColor)) {
                            append(" · 关键词 ${keywordPathSet.size}")
                        }
                    }
                    if (variableConditionPathSet.isNotEmpty()) {
                        withStyle(SpanStyle(color = KeywordResultColor)) {
                            append(" · 已触发 ${variableConditionPathSet.size}")
                        }
                    }
                },
                color = appearance.mobileBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = buildString {
                append("范围：${result.scope}")
                if (result.pattern.isNotBlank()) append(" · 路径模式：${result.pattern}")
            },
            color = appearance.mobileMuted,
            fontSize = 12.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = appearance.mobileSearchBg,
            border = BorderStroke(
                width = 0.7.dp,
                color = appearance.mobileMuted.copy(alpha = 0.15f),
            ),
        ) {
            if (displayedPaths.isEmpty()) {
                Text(
                    text = "没有匹配路径",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                )
            } else {
                Column {
                    displayedPaths.forEachIndexed { index, path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = appearance.mobileMuted,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = settingLibraryResultDisplayName(path),
                                    color = appearance.mobileText,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                path.substringBeforeLast('/', missingDelimiterValue = "")
                                    .takeIf(String::isNotBlank)
                                    ?.let { parentPath ->
                                        Text(
                                            text = parentPath,
                                            color = appearance.mobileMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                            }
                            when {
                                path in variableConditionPathSet -> {
                                    Text(
                                        text = "已触发",
                                        color = KeywordResultColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                path in keywordPathSet -> {
                                    Text(
                                        text = "关键词",
                                        color = KeywordResultColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                path in requiredPathSet -> {
                                    Text(
                                        text = "必读",
                                        color = appearance.mobileBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                path in onDemandPathSet -> {
                                    Text(
                                        text = "选读",
                                        color = appearance.mobileMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        if (index < displayedPaths.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 42.dp),
                                color = appearance.mobileMuted.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingEntriesResultBlock(
    item: CreationTimelineItem,
    entries: List<SettingEntryToolResult>,
    appearance: AppearanceTheme,
) {
    var expandedEntryIds by remember(item.id) { mutableStateOf(emptySet<String>()) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = if (entries.size == 1) "设定正文" else "设定正文 · ${entries.size} 个条目",
            color = appearance.mobileMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        entries.forEachIndexed { index, entry ->
            val expanded = entry.entryId in expandedEntryIds
            Surface(
                modifier = if (expanded) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(
                            if (entry.resolvedReferences.isEmpty()) {
                                SettingEntryCollapsedHeight
                            } else {
                                DynamicSettingEntryCollapsedHeight
                            },
                        )
                },
                shape = RoundedCornerShape(14.dp),
                color = appearance.mobileSearchBg,
                border = BorderStroke(
                    width = 0.7.dp,
                    color = appearance.mobileMuted.copy(alpha = 0.15f),
                ),
            ) {
                Column {
                    val contentModifier = if (expanded) Modifier else Modifier.weight(1f)
                    Column(
                        modifier = contentModifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.title,
                                modifier = Modifier.weight(1f),
                                color = appearance.mobileText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (expanded) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.truncated) {
                                Text(
                                    text = "已截断",
                                    color = DiffDeletionColor,
                                    fontSize = 12.sp,
                                )
                            }
                            when {
                                entry.readStrategy == "variable_condition" -> Text(
                                    text = "动态设定",
                                    color = KeywordResultColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                entry.readStrategy == "keyword" -> Text(
                                    text = "关键词",
                                    color = KeywordResultColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                entry.readStrategy == "required" -> Text(
                                    text = "必读",
                                    color = appearance.mobileBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                entry.readStrategy == "normal" -> Text(
                                    text = "选读",
                                    color = appearance.mobileMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Text(
                            text = entry.groupPath.ifBlank { "根目录" },
                            color = appearance.mobileMuted,
                            fontSize = 12.sp,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.resolvedReferences.isNotEmpty()) {
                            Text(
                                text = "本回合引用：" + entry.resolvedReferences.joinToString("、") { reference ->
                                    reference.title.ifBlank {
                                        settingLibraryResultDisplayName(reference.path)
                                    }
                                },
                                color = KeywordResultColor,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.selectionHint.isNotBlank()) {
                            Text(
                                text = "作者注释：${entry.selectionHint}",
                                color = appearance.mobileMuted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (expanded) {
                            CreationMarkdownText(
                                item = CreationTimelineItem(
                                    id = "${item.id}-setting-$index",
                                    kind = CreationTimelineKind.Assistant,
                                    text = entry.content.ifBlank { "没有正文内容" },
                                ),
                                appearance = appearance,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = entry.content.trim().ifBlank { "没有正文内容" },
                                modifier = Modifier.fillMaxWidth(),
                                color = appearance.mobileText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(color = appearance.mobileMuted.copy(alpha = 0.12f))
                    ResultCardExpandAction(
                        expanded = expanded,
                        collapsedLabel = "展开正文",
                        expandedLabel = "收起正文",
                        appearance = appearance,
                        onClick = {
                            expandedEntryIds = if (expanded) {
                                expandedEntryIds - entry.entryId
                            } else {
                                expandedEntryIds + entry.entryId
                            }
                        },
                    )
                }
            }
        }
    }
}
