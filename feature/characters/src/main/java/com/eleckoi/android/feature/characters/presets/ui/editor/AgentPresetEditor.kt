package com.eleckoi.android.feature.characters.presets.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetOverviewPage
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.EntryEditorPage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.RootNodeId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryGroupNameDialog
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryOrderConflictDialog
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryRenameNodeDialog
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryRequiredFieldsDialog
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingTreeBottomPanel
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingTreeNode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingTreeNodeRow
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.clearSettingTreeSelectionOnBlankTap
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.fileNodeId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.hasOrderConflictIn
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.hasRequiredActivationFields
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.rememberSettingLibraryEditorState
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.rememberSettingTreeDragUiState
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.rememberSettingTreeInternalReorderState
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.settingTreeNodes
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryEditorHeader
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryHeaderSearchAction
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StorySearchHeader
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun AgentPresetEditor(
    preset: AgentPreset,
    initialEntryId: String?,
    startOnTools: Boolean,
    active: Boolean,
    appearance: AppearanceTheme,
    toolGroups: List<AgentToolGroupSnapshot>,
    modelConfigs: List<ModelConfig>,
    onBack: () -> Unit,
    returnToCallerAfterEntry: Boolean,
    onReturnFromExternalEntry: () -> Unit,
    onInitialEntryHandled: () -> Unit,
    onUpdate: (AgentPreset) -> Unit,
    onSetActive: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateProfile: (AgentPresetProfile) -> Unit,
    onUpdateModelTags: (List<AgentPresetModelTag>) -> Unit,
    onUpdateAuthorAvatar: (Map<AvatarSlot, File>) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val editorLibrary = remember(preset) { preset.asEditorLibrary() }
    val editorState = rememberSettingLibraryEditorState(editorLibrary)
    val listState = rememberLazyListState()
    val treeDragUiState = rememberSettingTreeDragUiState()
    val horizontalTreeScroll = rememberScrollState()
    var invalidEnableEntry by remember { mutableStateOf<SettingLibraryEntry?>(null) }
    var conflictingOrderEntry by remember { mutableStateOf<SettingLibraryEntry?>(null) }
    var hiddenTimelineDisableConfirmationOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable(preset.id) { mutableStateOf(false) }
    var selectedTab by rememberSaveable(preset.id) {
        mutableStateOf(if (startOnTools) AgentPresetEditorTab.Tools else AgentPresetEditorTab.Usage)
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        editorState.search = ""
    }

    fun savePending() {
        if (!editorState.dirty) return
        val edited = editorState.editedLibrary(editorLibrary)
        onUpdate(
            preset.copy(
                entries = edited.entries,
                groups = edited.groups,
                promptPositions = edited.promptPositions,
                expandedGroupIds = edited.expandedGroupIds,
            ),
        )
        editorState.dirty = false
    }

    LaunchedEffect(preset.entries, preset.groups, preset.promptPositions, preset.expandedGroupIds) {
        if (!editorState.dirty) editorState.syncFrom(preset.asEditorLibrary())
    }
    LaunchedEffect(preset.id, initialEntryId) {
        val targetEntryId = initialEntryId ?: return@LaunchedEffect
        selectedTab = AgentPresetEditorTab.Prompts
        if (editorState.entries.any { it.id == targetEntryId }) {
            editorState.editorEntryId = targetEntryId
            editorState.selectedTreeNodeId = fileNodeId(targetEntryId)
        }
        onInitialEntryHandled()
    }
    LaunchedEffect(preset.id, startOnTools) {
        if (startOnTools) selectedTab = AgentPresetEditorTab.Tools
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab != AgentPresetEditorTab.Prompts) {
            searchOpen = false
            editorState.search = ""
        }
    }
    LaunchedEffect(
        editorState.entries,
        editorState.groups,
        editorState.promptPositions,
        editorState.expandedGroupIds,
        editorState.dirty,
    ) {
        if (!editorState.dirty) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        savePending()
    }

    val editorEntryId = editorState.editorEntryId
    if (editorEntryId != null) {
        val entry = editorState.entries.firstOrNull { it.id == editorEntryId }
        if (entry != null) {
            EntryEditorPage(
                entry = entry,
                entries = editorState.entries,
                groups = editorState.groups,
                promptPositions = editorState.promptPositions,
                allowCustomPromptPositions = true,
                genericPageTitle = "预设条目",
                appearance = appearance,
                onBack = {
                    savePending()
                    if (returnToCallerAfterEntry) {
                        onReturnFromExternalEntry()
                    } else {
                        editorState.editorEntryId = null
                    }
                },
                onEntryChange = { transform -> editorState.updateEntry(entry.id, transform) },
                onEntriesChange = editorState::update,
                onPromptPositionsChange = editorState::updatePromptPositions,
                onOpenEntry = { editorState.editorEntryId = it },
                onDeleteConfirmed = {
                    editorState.deleteEntry(entry.id)
                    savePending()
                    if (returnToCallerAfterEntry) {
                        onReturnFromExternalEntry()
                    } else {
                        editorState.editorEntryId = null
                    }
                },
            )
            return
        }
        editorState.editorEntryId = null
    }

    val treeNodes = remember(
        editorState.groups,
        editorState.entries,
        editorState.expandedGroupIds,
        treeDragUiState.forceCollapsedGroupIds,
        editorState.search,
    ) {
        settingTreeNodes(
            groups = editorState.groups,
            entries = editorState.entries,
            expandedGroupIds = editorState.expandedGroupIds,
            forceCollapsedGroupIds = treeDragUiState.forceCollapsedGroupIds,
            search = editorState.search,
        )
    }
    val treeInternalReorder = rememberSettingTreeInternalReorderState(
        listState = listState,
        nodes = treeNodes,
        entries = editorState.entries,
        groups = editorState.groups,
        enabled = !searchOpen && editorState.search.trim().isBlank(),
        onTreeChange = editorState::updateTree,
        onFolderDragStarted = treeDragUiState::collapseDraggedFolder,
        onDragStopped = treeDragUiState::clearDragCollapse,
    )
    val displayedTreeNodes = treeInternalReorder.displayNodes()
    val selectedTreeNode = treeNodes.firstOrNull { it.id == editorState.selectedTreeNodeId }

    PinnedStatusScaffold(
        appearance = appearance,
        backgroundColor = appearance.mobileBg,
    ) {
        if (searchOpen && selectedTab == AgentPresetEditorTab.Prompts) {
            StorySearchHeader(
                query = editorState.search,
                placeholder = "搜索预设条目",
                appearance = appearance,
                onQueryChange = { editorState.search = it },
                onClose = {
                    searchOpen = false
                    editorState.search = ""
                },
            )
        } else {
            StoryEditorHeader(
                title = "预设编辑页",
                appearance = appearance,
                onBack = { savePending(); onBack() },
                actionWidth = if (selectedTab == AgentPresetEditorTab.Prompts) 48.dp else 0.dp,
                action = if (selectedTab == AgentPresetEditorTab.Prompts) {
                    {
                        StoryHeaderSearchAction(
                            appearance = appearance,
                            onClick = { searchOpen = true },
                        )
                    }
                } else null,
            )
        }
        AgentPresetEditorTabBar(
            selected = selectedTab,
            appearance = appearance,
            onSelect = { tab ->
                if (selectedTab == AgentPresetEditorTab.Prompts) savePending()
                selectedTab = tab
            },
        )
        when (selectedTab) {
            AgentPresetEditorTab.Usage -> AgentPresetOverviewPage(
                preset = preset,
                active = active,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onSetActive = onSetActive,
                onRename = onRename,
                onUpdateProfile = onUpdateProfile,
                onUpdateModelTags = onUpdateModelTags,
                onUpdateAuthorAvatar = onUpdateAuthorAvatar,
            )
            AgentPresetEditorTab.Tools -> AgentPresetToolsTab(
                preset = preset,
                availableGroups = toolGroups,
                modelConfigs = modelConfigs,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onUpdate = onUpdate,
                onOpenWebSearchSettings = onOpenWebSearchSettings,
                onSaveModelConfig = onSaveModelConfig,
                onRefreshModels = onRefreshModels,
            )
            AgentPresetEditorTab.Regex -> AgentPresetRegexTab(
                rules = preset.regexRules,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onUpdate = { rules -> onUpdate(preset.copy(regexRules = rules)) },
            )
            AgentPresetEditorTab.Prompts -> Box(modifier = Modifier.weight(1f).background(appearance.mobileBg)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clearSettingTreeSelectionOnBlankTap(
                        enabled = !searchOpen && editorState.selectedTreeNodeId != RootNodeId,
                        onClear = { editorState.focusTreeNode(RootNodeId) },
                    ),
                contentPadding = PaddingValues(top = 8.dp, bottom = if (searchOpen) 18.dp else 108.dp),
            ) {
                if (displayedTreeNodes.isNotEmpty()) {
                    items(displayedTreeNodes, key = { it.id }) { node ->
                        val dragging = treeInternalReorder.isDragging(node)
                        SettingTreeNodeRow(
                            node = node,
                            selected = editorState.selectedTreeNodeId == node.id,
                            dropTarget = false,
                            dragging = dragging,
                            expanded = node is SettingTreeNode.Folder &&
                                (editorState.search.isNotBlank() || node.group.id in editorState.expandedGroupIds),
                            horizontalScrollState = horizontalTreeScroll,
                            appearance = appearance,
                            modifier = Modifier
                                .zIndex(if (dragging) 2f else 0f)
                                .offset { IntOffset(0, treeInternalReorder.dragOffsetY(node).roundToInt()) },
                            reorderModifier = treeInternalReorder.dragModifier(node),
                            onSelect = {
                                when (node) {
                                    is SettingTreeNode.File -> editorState.focusTreeNode(node.id)
                                    is SettingTreeNode.Folder -> editorState.selectTreeNode(node.id)
                                }
                            },
                            onOpen = { editorState.openTreeNode(node) },
                            onFileEnabledChange = { entry, enabled ->
                                if (!enabled && entry.isHiddenToolTimelineEntry()) {
                                    hiddenTimelineDisableConfirmationOpen = true
                                } else if (enabled && !entry.hasRequiredActivationFields()) {
                                    invalidEnableEntry = entry
                                } else if (enabled && entry.hasOrderConflictIn(editorState.entries)) {
                                    conflictingOrderEntry = entry
                                } else {
                                    editorState.updateEntry(entry.id) { it.copy(enabled = enabled) }
                                }
                            },
                            onToggle = {
                                if (node is SettingTreeNode.Folder) {
                                    editorState.updateListExpansion(
                                        nextExpandedGroupIds = if (node.group.id in editorState.expandedGroupIds) {
                                            editorState.expandedGroupIds - node.group.id
                                        } else {
                                            editorState.expandedGroupIds + node.group.id
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
            if (!searchOpen) SettingTreeBottomPanel(
                hasSelection = editorState.selectedTreeNodeId != RootNodeId && editorState.canDeleteSelected(),
                canEdit = selectedTreeNode is SettingTreeNode.File,
                canCopy = editorState.canUseTreeClipboardSource(),
                canDelete = editorState.canDeleteSelected(),
                hasClipboard = editorState.hasTreeClipboard(),
                createMenuOpen = editorState.createSettingKindDialogOpen,
                appearance = appearance,
                createStaticLabel = "预设条目",
                modifier = Modifier.align(Alignment.BottomCenter),
                onCreate = editorState::requestAddSetting,
                onDismissCreateMenu = { editorState.createSettingKindDialogOpen = false },
                onCreateFolder = {
                    editorState.createSettingKindDialogOpen = false
                    editorState.requestAddGroup()
                },
                onCreateStatic = {
                    editorState.createSettingKindDialogOpen = false
                    val previousIds = editorState.entries.mapTo(mutableSetOf(), SettingLibraryEntry::id)
                    editorState.requestAddEntry(SettingLibraryTriggerMode.Always)
                    editorState.entries.firstOrNull { it.id !in previousIds }?.let { created ->
                        editorState.updateEntry(created.id) {
                            it.copy(
                                title = "新提示词",
                                enabled = false,
                                triggerMode = SettingLibraryTriggerMode.Always,
                                position = null,
                                promptPositionId = "",
                            )
                        }
                        editorState.focusTreeNode(fileNodeId(created.id))
                        editorState.editorEntryId = created.id
                    }
                },
                onCreateReference = {
                    editorState.createSettingKindDialogOpen = false
                    editorState.addEjsReference()
                },
                onEdit = {
                    if (selectedTreeNode is SettingTreeNode.File) {
                        editorState.openTreeNode(selectedTreeNode)
                    }
                },
                onCopyOrPaste = {
                    val message = if (editorState.hasTreeClipboard()) {
                        editorState.pasteTreeClipboard()
                    } else {
                        editorState.copySelectedTreeNode()
                    }
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                },
                onCutOrCancel = {
                    val message = if (editorState.hasTreeClipboard()) {
                        editorState.cancelTreeClipboard()
                    } else {
                        editorState.cutSelectedTreeNode()
                    }
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                },
                onRename = editorState::requestRenameSelected,
                onDelete = { editorState.confirmDeleteNode = true },
            )
        }
        }
    }

    if (editorState.renameNodeDialogOpen) {
        SettingLibraryRenameNodeDialog(
            value = editorState.renameNodeName,
            appearance = appearance,
            onValueChange = { editorState.renameNodeName = it },
            onDismiss = { editorState.renameNodeDialogOpen = false },
            onConfirm = { editorState.renameNodeDialogOpen = false; editorState.renameSelected(editorState.renameNodeName) },
        )
    }
    if (editorState.createGroupNameDialogOpen) {
        SettingLibraryGroupNameDialog(
            value = editorState.createGroupName,
            groups = editorState.groups,
            appearance = appearance,
            onValueChange = { editorState.createGroupName = it },
            onDismiss = { editorState.createGroupNameDialogOpen = false },
            onConfirm = { editorState.createGroupNameDialogOpen = false; editorState.addGroup(editorState.createGroupName) },
        )
    }
    if (editorState.confirmDeleteNode) {
        ConfirmDialog(
            title = "删除${editorState.selectedTreeKindLabel()}？",
            message = if (editorState.selectedTreeNodeId.startsWith("folder:")) {
                "会同时删除这个文件夹里的全部预设条目。"
            } else {
                "会删除这个预设条目。"
            },
            appearance = appearance,
            onDismiss = { editorState.confirmDeleteNode = false },
            onConfirm = { editorState.confirmDeleteNode = false; editorState.deleteSelected() },
        )
    }
    invalidEnableEntry?.let { entry ->
        SettingLibraryRequiredFieldsDialog(
            triggerSelected = entry.triggerMode != null,
            positionSelected = entry.position != null,
            appearance = appearance,
            onDismiss = { invalidEnableEntry = null },
        )
    }
    conflictingOrderEntry?.let { entry ->
        SettingLibraryOrderConflictDialog(
            order = entry.order,
            appearance = appearance,
            onDismiss = { conflictingOrderEntry = null },
        )
    }
    if (hiddenTimelineDisableConfirmationOpen) {
        ConfirmDialog(
            title = "关闭隐藏工具时间线？",
            message = "关闭后 AI 回复将无法流式显示，请着重考虑。",
            appearance = appearance,
            onDismiss = { hiddenTimelineDisableConfirmationOpen = false },
            onConfirm = {
                hiddenTimelineDisableConfirmationOpen = false
                editorState.entries.firstOrNull(SettingLibraryEntry::isHiddenToolTimelineEntry)?.let { entry ->
                    editorState.updateEntry(entry.id) { it.copy(enabled = false) }
                }
            },
        )
    }
}

private fun AgentPreset.asEditorLibrary(): SettingLibrary = SettingLibrary(
    characterId = id,
    name = name,
    entries = entries,
    groups = groups,
    promptPositions = promptPositions,
    expandedGroupIds = expandedGroupIds,
)
