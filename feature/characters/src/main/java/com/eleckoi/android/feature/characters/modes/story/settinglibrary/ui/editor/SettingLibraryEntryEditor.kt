package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHistoryCompactionEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold

private class EntryEditorState {
    var orderPreviewExpanded by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)
    var keywordRulesExpanded by mutableStateOf(false)
    var subpage by mutableStateOf(EntryEditorSubpage.Editor)

    fun toggleOrderPreview() {
        orderPreviewExpanded = !orderPreviewExpanded
    }

    fun toggleKeywordRules() {
        keywordRulesExpanded = !keywordRulesExpanded
    }

    fun requestDelete() {
        confirmDelete = true
    }

    fun closeDelete() {
        confirmDelete = false
    }

    fun openPositionPicker() {
        subpage = EntryEditorSubpage.PositionPicker
    }

    fun closeSubpage() {
        subpage = EntryEditorSubpage.Editor
    }
}

@Composable
private fun rememberEntryEditorState(entryId: String): EntryEditorState {
    return remember(entryId) { EntryEditorState() }
}

internal enum class EntryEditorSection(val label: String) {
    Base("基础"),
    Trigger("触发"),
    Content("正文"),
    Insert("插入"),
}

private enum class EntryEditorSubpage {
    Editor,
    PositionPicker,
}

@Composable
internal fun EntryEditorPage(
    entry: SettingLibraryEntry,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
    promptPositions: List<SettingLibraryPromptPosition>,
    allowCustomPromptPositions: Boolean,
    genericPageTitle: String = "设定条目",
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
    onEntriesChange: (List<SettingLibraryEntry>) -> Unit,
    onPromptPositionsChange: (List<SettingLibraryPromptPosition>) -> Unit,
    onOpenEntry: (String) -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    val density = LocalDensity.current
    val editorPalette = appearance.storyEditorPalette()
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottom = with(density) { imeBottomPx.toDp() }
    val contentEditorHeight = ((LocalConfiguration.current.screenHeightDp - 310) / 2).coerceIn(240, 360)
    val scrollState = rememberScrollState()
    val editorState = rememberEntryEditorState(entry.id)
    var selectedSection by remember(entry.id) { mutableStateOf(EntryEditorSection.Base) }
    var actionMenuExpanded by remember(entry.id) { mutableStateOf(false) }
    var conflictingOrder by remember(entry.id) { mutableStateOf<Int?>(null) }
    with(editorState) {
    val fixedOpening = entry.isOpeningEntry()
    val fixedHistoryCompaction = entry.isHistoryCompactionEntry()
    val fixedEntry = entry.isFixedEntry()
    val cacheEntry = entry.triggerMode == SettingLibraryTriggerMode.Cache
    val editorSections = if (cacheEntry) {
        listOf(EntryEditorSection.Base, EntryEditorSection.Content, EntryEditorSection.Insert)
    } else {
        EntryEditorSection.entries
    }

    if (entry.dynamicMode == SettingLibraryDynamicMode.EjsReference) {
        EjsReferenceEditorPage(
            entry = entry,
            appearance = appearance,
            onBack = onBack,
            onEntryChange = onEntryChange,
            onDeleteConfirmed = onDeleteConfirmed,
        )
        return
    }

    if (fixedOpening) {
        MultiOpeningEditorPage(
            entry = entry,
            appearance = appearance,
            onBack = onBack,
            onEntryChange = onEntryChange,
        )
        return
    }

    when (subpage) {
        EntryEditorSubpage.PositionPicker -> {
            SettingLibraryPositionPickerPage(
                currentEntryId = entry.id,
                entries = entries,
                promptPositions = promptPositions,
                allowCustomPromptPositions = allowCustomPromptPositions,
                appearance = appearance,
                onBack = ::closeSubpage,
                onEntryChange = onEntryChange,
                onEntriesChange = onEntriesChange,
                onPromptPositionsChange = onPromptPositionsChange,
            )
            return
        }

        EntryEditorSubpage.Editor -> Unit
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(selectedSection) {
        scrollState.scrollTo(0)
    }

    PinnedStatusScaffold(appearance = appearance, imeAware = false, backgroundColor = editorPalette.pageBg) {
        EntryEditorTopBar(
            title = when {
                fixedOpening -> "AI角色开场白"
                fixedHistoryCompaction -> "自动压缩摘要模板"
                cacheEntry -> "缓存设定"
                else -> genericPageTitle
            },
            appearance = appearance,
            onBack = onBack,
            menuVisible = !fixedEntry,
            menuExpanded = actionMenuExpanded,
            onMenuExpandedChange = { actionMenuExpanded = it },
            onDelete = ::requestDelete,
            trailingActionLabel = null,
            onTrailingAction = {},
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(editorPalette.pageBg)
                // The viewport shrinks with the keyboard rather than the content growing a
                // keyboard-sized tail. Padding the content left the visible area the same size, so
                // nothing moved until a scroll correction fired after the keyboard had finished
                // animating — the jump you saw. Shrinking the viewport moves the content in step
                // with the slide, and the text field's own cursor tracking rides along with it.
                .imePadding()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            // The rail scrolls away with everything else. Pinned, it held a fixed 58dp of the
            // screen for four words that stop mattering the moment you start typing.
            if (!fixedEntry) {
                EntryEditorSectionTabs(
                    selected = selectedSection,
                    sections = editorSections,
                    contentLabel = if (entry.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                        "EJS 代码"
                    } else {
                        "正文"
                    },
                    appearance = appearance,
                    onSelect = { selectedSection = it },
                )
            }
            if (fixedEntry) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    if (fixedHistoryCompaction) {
                        HistoryCompactionPromptEditor(
                            value = entry.content,
                            appearance = appearance,
                            scrollState = scrollState,
                            imeBottomPx = imeBottomPx,
                            onChange = { value -> onEntryChange { it.copy(content = value) } },
                        )
                    } else {
                        PlainInput(
                            label = "开场白正文",
                            value = entry.content,
                            appearance = appearance,
                            scrollState = scrollState,
                            imeBottomPx = imeBottomPx,
                            minHeight = 260,
                            placeholder = "作为聊天记录中的第一条 Assistant 开场白使用。",
                            immersiveTitle = "开场白正文",
                            groupedStyle = true,
                            onChange = { value -> onEntryChange { it.copy(content = value) } },
                        )
                    }
                }
            } else {
                when (selectedSection) {
                    EntryEditorSection.Base -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    EntryTitleField(
                        value = entry.title,
                        appearance = appearance,
                        scrollState = scrollState,
                        imeBottomPx = imeBottomPx,
                        placeholder = "条目标题/待命名",
                        onChange = { value -> onEntryChange { it.copy(title = value.take(60)) } },
                    )
                    EntryIconPicker(
                        selectedIconId = entry.iconId,
                        appearance = appearance,
                        onSelect = { iconId -> onEntryChange { it.copy(iconId = iconId) } },
                    )
                }
                    EntryEditorSection.Trigger -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    EntryTriggerModePicker(
                        selected = entry.triggerMode,
                        appearance = appearance,
                        onSelect = { mode -> onEntryChange { it.copy(triggerMode = mode) } },
                    )
                    if (entry.triggerMode == SettingLibraryTriggerMode.AgentTool) {
                        AgentReadStrategySettings(
                            selected = entry.agentReadStrategy,
                            appearance = appearance,
                            onSelect = { strategy ->
                                onEntryChange { current ->
                                    current.copy(
                                        agentReadStrategy = strategy,
                                        dynamicMode = if (strategy == SettingLibraryAgentReadStrategy.VariableCondition) {
                                            current.dynamicMode
                                        } else {
                                            SettingLibraryDynamicMode.SingleCondition
                                        },
                                    )
                                }
                            },
                        )
                        when (entry.agentReadStrategy) {
                            SettingLibraryAgentReadStrategy.Keyword -> {
                                KeywordInput(
                                    label = "触发关键词",
                                    keywords = entry.keywords,
                                    appearance = appearance,
                                    scrollState = scrollState,
                                    imeBottomPx = imeBottomPx,
                                    placeholder = "中/英逗号分隔",
                                    groupedStyle = true,
                                    onKeywordsChange = { keywords -> onEntryChange { it.copy(keywords = keywords) } },
                                )
                                KeywordRulesPanel(
                                    entry = entry,
                                    expanded = keywordRulesExpanded,
                                    appearance = appearance,
                                    scrollState = scrollState,
                                    imeBottomPx = imeBottomPx,
                                    onToggleExpanded = ::toggleKeywordRules,
                                    onEntryChange = onEntryChange,
                                )
                            }
                            SettingLibraryAgentReadStrategy.Normal -> AgentToolTriggerSettings(
                                currentEntry = entry,
                                entries = entries,
                                groups = groups,
                                showSelectionHint = true,
                                appearance = appearance,
                                scrollState = scrollState,
                                imeBottomPx = imeBottomPx,
                                onEntryChange = onEntryChange,
                            )
                            SettingLibraryAgentReadStrategy.VariableCondition -> {
                                DynamicModeSettings(
                                    entry = entry,
                                    appearance = appearance,
                                    onEntryChange = onEntryChange,
                                )
                                if (entry.dynamicMode == SettingLibraryDynamicMode.SingleCondition) {
                                    PlainInput(
                                        label = "变量条件",
                                        value = entry.agentReadCondition,
                                        appearance = appearance,
                                        scrollState = scrollState,
                                        imeBottomPx = imeBottomPx,
                                        minHeight = 110,
                                        placeholder = "getvar('剧情.已完成事件', { defaults: 0 }) >= 3",
                                        immersiveTitle = "变量条件",
                                        groupedStyle = true,
                                        onChange = { value ->
                                            onEntryChange { it.copy(agentReadCondition = value) }
                                        },
                                    )
                                }
                            }
                            SettingLibraryAgentReadStrategy.Required -> Unit
                        }
                        if (entry.agentReadStrategy != SettingLibraryAgentReadStrategy.Normal) {
                            AgentToolTriggerSettings(
                                currentEntry = entry,
                                entries = entries,
                                groups = groups,
                                showSelectionHint = false,
                                appearance = appearance,
                                scrollState = scrollState,
                                imeBottomPx = imeBottomPx,
                                onEntryChange = onEntryChange,
                            )
                        }
                    }
                }
                    EntryEditorSection.Content -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                val contentLabel = when (entry.dynamicMode) {
                    SettingLibraryDynamicMode.EjsController -> "EJS 代码"
                    SettingLibraryDynamicMode.EjsReference -> "引用正文"
                    SettingLibraryDynamicMode.SingleCondition -> "设定正文"
                }
                PlainInput(
                    label = contentLabel,
                    value = entry.content,
                    appearance = appearance,
                    scrollState = scrollState,
                    imeBottomPx = imeBottomPx,
                    minHeight = contentEditorHeight,
                    placeholder = when (entry.dynamicMode) {
                        SettingLibraryDynamicMode.EjsController -> "使用 <% … %> 编写判断；可通过 getvar 读取变量、getwi 读取已开启的EJS引用设定"
                        SettingLibraryDynamicMode.EjsReference -> "填写供 EJS 控制器读取的引用内容"
                        SettingLibraryDynamicMode.SingleCondition -> "写入世界观、人物背景、地点规则、隐藏信息等"
                    },
                    immersiveTitle = contentLabel,
                    groupedStyle = true,
                    onChange = { value -> onEntryChange { it.copy(content = value) } },
                )
                if (entry.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                    val referencedTitles = literalGetwiTargets(entry.content)
                    EjsControllerReferencesPanel(
                        references = entries.filter { candidate ->
                            candidate.dynamicMode == SettingLibraryDynamicMode.EjsReference &&
                                candidate.title in referencedTitles
                        },
                        appearance = appearance,
                        onOpenReference = onOpenEntry,
                    )
                }
                }
                    EntryEditorSection.Insert -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    if (cacheEntry) {
                        CachedEntryInsertSettingsGroup(appearance = appearance)
                        EntryPositionOrderGroup(
                            entry = entry,
                            entries = entries,
                            appearance = appearance,
                            scrollState = scrollState,
                            imeBottomPx = imeBottomPx,
                            previewExpanded = orderPreviewExpanded,
                            onTogglePreview = ::toggleOrderPreview,
                            onOrderConflict = { conflictingOrder = it },
                            onEntryChange = onEntryChange,
                        )
                    } else if (entry.triggerMode == SettingLibraryTriggerMode.AgentTool) {
                        Text(
                            "AI 读取这条设定时，正文会直接作为工具结果返回，并在当前 Agent 回合的后续推理中继续保留；无需配置插入位置。",
                            color = appearance.mobileMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(appearance.mobileSurface)
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                        )
                    } else {
                        EntryInsertSettingsGroup(
                            entry = entry,
                            promptPositions = promptPositions,
                            insertRole = entry.insertRole,
                            appearance = appearance,
                            onOpenPosition = ::openPositionPicker,
                            onRoleChange = { role -> onEntryChange { it.copy(insertRole = role) } },
                        )
                        if (entry.position != null) {
                            EntryPositionOrderGroup(
                                entry = entry,
                                entries = entries,
                                appearance = appearance,
                                scrollState = scrollState,
                                imeBottomPx = imeBottomPx,
                                previewExpanded = orderPreviewExpanded,
                                onTogglePreview = ::toggleOrderPreview,
                                onOrderConflict = { conflictingOrder = it },
                                onEntryChange = onEntryChange,
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (confirmDelete) {
        val title = entry.title.trim().ifBlank { "这条待命名设定" }
        ConfirmDialog(
            title = if (entry.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                "删除这个控制器？"
            } else {
                "删除这条设定？"
            },
            message = if (entry.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                "将移除“$title”；它读取的EJS引用设定会保留。此操作会自动保存。"
            } else {
                "将从设定库移除“$title”，此操作会自动保存。"
            },
            appearance = appearance,
            onDismiss = ::closeDelete,
            onConfirm = {
                closeDelete()
                onDeleteConfirmed()
            },
        )
    }
    conflictingOrder?.let { order ->
        SettingLibraryOrderConflictDialog(
            order = order,
            appearance = appearance,
            onDismiss = { conflictingOrder = null },
        )
    }
    }
}
