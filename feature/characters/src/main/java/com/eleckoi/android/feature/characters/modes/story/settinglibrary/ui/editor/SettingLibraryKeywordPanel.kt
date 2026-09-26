package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.imeBringIntoViewOnFocus
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun KeywordMatchConditionToggle(
    enabled: Boolean,
    appearance: AppearanceTheme,
    onChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = StoryEditorCardSpacing)) {
        Text(
            "判断条件",
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        KeywordSwitchRow(
            title = "关键词命中",
            description = if (enabled) "命中后列为本轮必读" else "关闭时由 AI 选读",
            checked = enabled,
            appearance = appearance,
            onChange = onChange,
        )
    }
}

@Composable
internal fun KeywordRulesPanel(
    entry: SettingLibraryEntry,
    expanded: Boolean,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onToggleExpanded: () -> Unit,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
) {
    val effectiveCondition = when {
        entry.conditionKeywords.isEmpty() -> SettingLibraryKeywordCondition.None
        entry.keywordCondition == SettingLibraryKeywordCondition.None -> SettingLibraryKeywordCondition.Any
        else -> entry.keywordCondition
    }
    val conditionOptions = if (entry.conditionKeywords.isEmpty()) {
        listOf(SettingLibraryKeywordCondition.None)
    } else {
        SettingLibraryKeywordCondition.entries.filterNot { it == SettingLibraryKeywordCondition.None }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = StoryEditorCardSpacing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(appearance.mobileSurface),
        ) {
            Text(
                "匹配规则",
                color = appearance.mobileText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable(onClick = onToggleExpanded)
                    .height(58.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "高级匹配设置",
                    modifier = Modifier.weight(1f),
                    color = appearance.mobileText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(if (expanded) "收起" else "展开", color = appearance.mobileMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 7.dp))
                StrokeSvgIcon(
                    paths = if (expanded) AppIconPaths.ChevronDown else AppIconPaths.ChevronRight,
                    color = appearance.mobileSoft,
                    iconSize = 16.dp,
                    strokeWidth = 1.8f,
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(appearance.mobileMuted.copy(alpha = 0.08f)),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                    KeywordRuleSectionLabel("扫描范围", appearance)
                    KeywordRuleGroup(appearance = appearance) {
                        KeywordScanDepthRow(
                            value = entry.keywordScanDepth,
                            appearance = appearance,
                            scrollState = scrollState,
                            imeBottomPx = imeBottomPx,
                            onValueChange = { value -> onEntryChange { it.copy(keywordScanDepth = value.coerceIn(1, MaxKeywordScanDepth)) } },
                        )
                        KeywordRuleDivider(appearance)
                        KeywordSwitchRow(
                            title = "递归匹配",
                            description = if (entry.keywordRecursionDepth > 0) {
                                "本条目正文还会继续关联其他关键词"
                            } else {
                                "只根据最近消息匹配，不使用本条目正文"
                            },
                            checked = entry.keywordRecursionDepth > 0,
                            appearance = appearance,
                            grouped = true,
                            onChange = { checked ->
                                onEntryChange {
                                    it.copy(keywordRecursionDepth = if (checked) 1 else 0)
                                }
                            },
                        )
                        if (entry.keywordRecursionDepth > 0) {
                            KeywordRuleDivider(appearance)
                            KeywordRecursionDepthRow(
                                value = entry.keywordRecursionDepth,
                                appearance = appearance,
                                scrollState = scrollState,
                                imeBottomPx = imeBottomPx,
                                onValueChange = { value ->
                                    onEntryChange {
                                        it.copy(keywordRecursionDepth = value.coerceIn(1, MaxKeywordRecursionDepth))
                                    }
                                },
                            )
                        }
                    }
                    KeywordRuleSectionLabel("附加条件", appearance, topPadding = 18.dp)
                    KeywordRuleGroup(appearance = appearance) {
                        KeywordInput(
                            label = "附加关键词",
                            keywords = entry.conditionKeywords,
                            appearance = appearance,
                            scrollState = scrollState,
                            imeBottomPx = imeBottomPx,
                            placeholder = "例如：旅行、夏天",
                            groupedStyle = true,
                            onKeywordsChange = { keywords ->
                                onEntryChange {
                                    it.copy(
                                        conditionKeywords = keywords,
                                        keywordCondition = when {
                                            keywords.isEmpty() -> SettingLibraryKeywordCondition.None
                                            it.keywordCondition == SettingLibraryKeywordCondition.None -> SettingLibraryKeywordCondition.Any
                                            else -> it.keywordCondition
                                        },
                                    )
                                }
                            },
                        )
                        KeywordRuleDivider(appearance)
                        DropdownField(
                            label = "匹配要求",
                            value = DropdownOption(effectiveCondition.label, keywordConditionDescription(effectiveCondition)),
                            placeholder = "无需",
                            options = conditionOptions.map {
                                DropdownOption(it.label, keywordConditionDescription(it))
                            },
                            appearance = appearance,
                            groupedStyle = true,
                            onSelect = { index ->
                                onEntryChange { it.copy(keywordCondition = conditionOptions[index]) }
                            },
                        )
                    }
                    KeywordRuleSectionLabel("匹配方式", appearance, topPadding = 18.dp)
                    KeywordRuleGroup(appearance = appearance) {
                        KeywordSwitchRow(
                            title = "正则表达式",
                            description = if (entry.keywordUseRegex) {
                                "按酒馆关键词正则规则匹配"
                            } else {
                                "按普通文本关键词匹配"
                            },
                            checked = entry.keywordUseRegex,
                            appearance = appearance,
                            grouped = true,
                            onChange = { checked -> onEntryChange { it.copy(keywordUseRegex = checked) } },
                        )
                        KeywordRuleDivider(appearance)
                        KeywordSwitchRow(
                            title = "忽略大小写",
                            description = if (entry.keywordIgnoreCase) "英文关键词不区分大小写" else "英文关键词区分大小写",
                            checked = entry.keywordIgnoreCase,
                            appearance = appearance,
                            grouped = true,
                            onChange = { checked -> onEntryChange { it.copy(keywordIgnoreCase = checked) } },
                        )
                        KeywordRuleDivider(appearance)
                        KeywordSwitchRow(
                            title = "完整词匹配",
                            description = if (entry.keywordWholeWord) "只匹配完整英文单词" else "允许匹配英文单词的一部分",
                            checked = entry.keywordWholeWord,
                            appearance = appearance,
                            grouped = true,
                            onChange = { checked -> onEntryChange { it.copy(keywordWholeWord = checked) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordScanDepthRow(
    value: Int,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onValueChange: (Int) -> Unit,
) {
    KeywordStepRow(
        title = "扫描深度",
        description = if (value <= 1) "只扫描用户最新消息" else "扫描最近 $value 条消息",
        value = value,
        minValue = 1,
        maxValue = MaxKeywordScanDepth,
        appearance = appearance,
        scrollState = scrollState,
        imeBottomPx = imeBottomPx,
        onValueChange = onValueChange,
    )
}

@Composable
private fun KeywordStepRow(
    title: String,
    description: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onValueChange: (Int) -> Unit,
) {
    val field = appearance.fieldPalette()
    var focused by remember { mutableStateOf(false) }
    var draft by remember(value) {
        val text = value.coerceIn(minValue, maxValue).toString()
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    fun commit(next: Int) {
        val normalized = next.coerceIn(minValue, maxValue)
        val text = normalized.toString()
        draft = TextFieldValue(text = text, selection = TextRange(text.length))
        onValueChange(normalized)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = appearance.mobileText.copy(alpha = 0.68f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                description,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (focused) {
                Text(
                    "$minValue-$maxValue",
                    color = appearance.mobileBlue,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            StepButton("-", appearance) { commit(value - 1) }
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 32.dp)
                    .border(
                        if (focused) 1.dp else 0.5.dp,
                        if (focused) field.focusedBorder else field.border,
                        RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { raw ->
                        val digits = raw.text.filter { it.isDigit() }.take(4)
                        val selection = raw.selection.end.coerceIn(0, digits.length)
                        draft = TextFieldValue(text = digits, selection = TextRange(selection))
                        digits.toIntOrNull()?.let { onValueChange(it.coerceIn(minValue, maxValue)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imeBringIntoViewOnFocus(scrollState, imeBottomPx)
                        .onFocusChanged { state ->
                            val wasFocused = focused
                            focused = state.isFocused
                            when {
                                state.isFocused && !wasFocused -> {
                                    draft = draft.copy(selection = TextRange(0, draft.text.length))
                                }
                                !state.isFocused && wasFocused -> {
                                    commit(draft.text.toIntOrNull() ?: minValue)
                                }
                            }
                        },
                    textStyle = TextStyle(
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(appearance.mobileBlue),
                )
            }
            StepButton("+", appearance) { commit(value + 1) }
        }
    }
}

@Composable
private fun KeywordRecursionDepthRow(
    value: Int,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onValueChange: (Int) -> Unit,
) {
    KeywordStepRow(
        title = "递归次数",
        description = "正文再匹配 $value 轮；每轮可同时命中多个设定",
        value = value,
        minValue = 1,
        maxValue = MaxKeywordRecursionDepth,
        appearance = appearance,
        scrollState = scrollState,
        imeBottomPx = imeBottomPx,
        onValueChange = onValueChange,
    )
}

@Composable
private fun KeywordSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    appearance: AppearanceTheme,
    grouped: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (grouped) Modifier.padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp)
                else Modifier
                    .padding(top = 10.dp)
                    .clip(StoryEditorShapes.Control)
                    .background(appearance.mobileSurface)
                    .border(1.dp, appearance.mobileMuted.copy(alpha = 0.12f), StoryEditorShapes.Control)
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = appearance.mobileText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                description,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        StoryToolSwitch(checked = checked, appearance = appearance, onCheckedChange = onChange)
    }
}

@Composable
private fun KeywordRuleSectionLabel(
    label: String,
    appearance: AppearanceTheme,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Text(
        text = label,
        color = appearance.mobileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 2.dp, top = topPadding, bottom = 7.dp),
    )
}

@Composable
private fun KeywordRuleGroup(
    appearance: AppearanceTheme,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StoryEditorShapes.Control)
            .background(appearance.mobileSurface)
            .border(1.dp, appearance.mobileMuted.copy(alpha = 0.12f), StoryEditorShapes.Control),
    ) {
        content()
    }
}

@Composable
private fun KeywordRuleDivider(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(appearance.mobileMuted.copy(alpha = 0.08f)),
    )
}

private const val MaxKeywordScanDepth = 1000
private const val MaxKeywordRecursionDepth = 10
