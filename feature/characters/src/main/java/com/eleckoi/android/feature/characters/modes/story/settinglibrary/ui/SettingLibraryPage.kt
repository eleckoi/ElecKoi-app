package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrarySource
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.noRippleClickable
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val LargeAllEntriesThreshold = 50
private const val LibraryTreeItemKeyPrefix = "library:"

@Composable
fun SettingLibraryPage(
    characterName: String,
    characterAvatar: String = "",
    library: SettingLibrary?,
    appearance: AppearanceTheme,
    toolContextNames: List<String> = emptyList(),
    importSources: List<SettingLibrarySource> = emptyList(),
    loadingImportSources: Boolean = false,
    onBack: () -> Unit,
    onSave: (SettingLibrary) -> Unit,
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    onRequestImportSources: () -> Unit = {},
    onParseImportFile: (String) -> SettingLibraryVersion? = { null },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottom = with(density) { imeBottomPx.toDp() }
    val listState = rememberLazyListState()
    val editorState = rememberSettingLibraryEditorState(library)
    var searchOpen by remember { mutableStateOf(false) }

    with(editorState) {

    fun savePendingEdits() {
        val current = library ?: return
        if (!dirty) return
        onSave(editedLibrary(current))
        dirty = false
    }

    BackHandler(enabled = editorEntryId != null && !libraryManagerOpen) {
        savePendingEdits()
        editorEntryId = null
    }
    BackHandler(enabled = searchOpen && editorEntryId == null && !libraryManagerOpen) {
        searchOpen = false
        search = ""
    }

    LaunchedEffect(
        library?.name,
        library?.entries,
        library?.groups,
        library?.promptPositions,
        library?.versions,
        library?.activeVersionId,
        library?.listAllExpanded,
        library?.expandedGroupIds,
    ) {
        if (library == null || dirty) return@LaunchedEffect
        syncFrom(library)
    }

    LaunchedEffect(
        libraryName,
        entries,
        groups,
        promptPositions,
        versions,
        activeVersionId,
        allEntriesExpanded,
        expandedGroupIds,
        dirty,
    ) {
        val current = library ?: return@LaunchedEffect
        if (!dirty) return@LaunchedEffect
        delay(800)
        onSave(editedLibrary(current))
        dirty = false
    }

    val treeDragUiState = rememberSettingTreeDragUiState()
    val treeNodes = remember(groups, entries, expandedGroupIds, treeDragUiState.forceCollapsedGroupIds, search) {
        settingTreeNodes(
            groups = groups,
            entries = entries,
            expandedGroupIds = expandedGroupIds,
            forceCollapsedGroupIds = treeDragUiState.forceCollapsedGroupIds,
            search = search,
        )
    }
    val hasUserTreeNodes = remember(groups, entries) {
        groups.isNotEmpty() || entries.isNotEmpty()
    }
    val horizontalTreeScroll = rememberScrollState()
    var invalidEnableEntry by remember { mutableStateOf<SettingLibraryEntry?>(null) }
    var conflictingOrderEntry by remember { mutableStateOf<SettingLibraryEntry?>(null) }
    val treeInternalReorder = rememberSettingTreeInternalReorderState(
        listState = listState,
        nodes = treeNodes,
        entries = entries,
        groups = groups,
        enabled = !searchOpen && search.trim().isBlank(),
        onTreeChange = ::updateTree,
        onFolderDragStarted = treeDragUiState::collapseDraggedFolder,
        onDragStopped = treeDragUiState::clearDragCollapse,
        itemKeyPrefix = LibraryTreeItemKeyPrefix,
    )
    val displayedTreeNodes = treeInternalReorder.displayNodes()
    val selectedLocalTreeNode = treeNodes.firstOrNull { it.id == selectedTreeNodeId }
    if (library != null && editorEntryId != null) {
        val entry = entries.firstOrNull { it.id == editorEntryId }
        if (entry != null) {
            EntryEditorPage(
                entry = entry,
                entries = entries,
                groups = groups,
                promptPositions = promptPositions,
                allowCustomPromptPositions = false,
                appearance = appearance,
                onBack = {
                    savePendingEdits()
                    editorEntryId = null
                },
                onEntryChange = { transform -> updateEntry(entry.id, transform) },
                onEntriesChange = ::update,
                onPromptPositionsChange = ::updatePromptPositions,
                onOpenEntry = { targetEntryId -> editorEntryId = targetEntryId },
                onDeleteConfirmed = {
                    deleteEntry(entry.id)
                    editorEntryId = null
                },
            )
            return
        }
        editorEntryId = null
    }

    PinnedStatusScaffold(appearance = appearance, imeAware = false, backgroundColor = appearance.mobileBg) {
        if (searchOpen) {
            StorySearchHeader(
                query = search,
                placeholder = "搜索设定条目",
                appearance = appearance,
                onQueryChange = {
                    search = it
                    allEntriesVisibleLimit = InitialAllEntriesVisibleCount
                },
                onClose = {
                    searchOpen = false
                    search = ""
                },
                modifier = Modifier.zIndex(2f),
            )
        } else {
            SettingLibraryHeader(
                managerOpen = libraryManagerOpen,
                appearance = appearance,
                onBack = {
                    savePendingEdits()
                    onBack()
                },
                onSearch = { searchOpen = true },
                onOpenManager = {
                    libraryManagerOpen = true
                },
                modifier = Modifier.zIndex(2f),
            )
        }

        if (library == null) {
            Box(modifier = Modifier.fillMaxSize().background(appearance.mobileBg), contentAlignment = Alignment.Center) {
                Text("正在读取设定库", color = appearance.mobileMuted, fontSize = 15.sp)
            }
            return@PinnedStatusScaffold
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(appearance.mobileBg),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = (if (hasUserTreeNodes && !searchOpen) 112.dp else 18.dp) + imeBottom,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .clearSettingTreeSelectionOnBlankTap(
                        enabled = !searchOpen && selectedTreeNodeId != RootNodeId,
                        onClear = { focusTreeNode(RootNodeId) },
                    ),
            ) {
                if (!searchOpen) {
                    item(key = "selection-context") {
                        Text(
                            text = if (selectedTreeNodeId == RootNodeId) {
                                "点选分组或设定条目后，可在底部编辑它。"
                            } else {
                                selectedTreePath()
                            },
                            color = appearance.mobileMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
                if (!hasUserTreeNodes && !searchOpen) {
                    item(key = "empty") {
                        EmptySettingRootGuide(
                            appearance = appearance,
                            onCreateRoot = ::requestAddGroup,
                        )
                    }
                } else {
                    if (displayedTreeNodes.isNotEmpty()) {
                        items(
                            items = displayedTreeNodes,
                            key = { settingTreeLazyItemKey(it.id, LibraryTreeItemKeyPrefix) },
                        ) { node ->
                            val dragging = treeInternalReorder.isDragging(node)
                            SettingTreeNodeRow(
                                node = node,
                                selected = selectedTreeNodeId == node.id,
                                dropTarget = false,
                                dragging = dragging,
                                modifier = Modifier
                                    .zIndex(if (dragging) 2f else 0f)
                                    .offset { IntOffset(0, treeInternalReorder.dragOffsetY(node).roundToInt()) },
                                reorderModifier = treeInternalReorder.dragModifier(node),
                                expanded = when (node) {
                                    is SettingTreeNode.Folder -> search.isNotBlank() || node.group.id in expandedGroupIds
                                    is SettingTreeNode.File -> false
                                },
                                horizontalScrollState = horizontalTreeScroll,
                                appearance = appearance,
                                onSelect = {
                                    when (node) {
                                        is SettingTreeNode.File -> focusTreeNode(node.id)
                                        is SettingTreeNode.Folder -> selectTreeNode(node.id)
                                    }
                                },
                                onOpen = {
                                    openTreeNode(node)
                                },
                                onFileEnabledChange = { entry, enabled ->
                                    if (enabled && !entry.hasRequiredActivationFields()) {
                                        updateEntry(entry.id) { it.copy(enabled = false) }
                                        invalidEnableEntry = entry
                                    } else if (enabled && entry.hasOrderConflictIn(entries)) {
                                        updateEntry(entry.id) { it.copy(enabled = false) }
                                        conflictingOrderEntry = entry
                                    } else {
                                        updateEntry(entry.id) { it.copy(enabled = enabled) }
                                    }
                                },
                                onToggle = {
                                    val expandableId = when (node) {
                                        is SettingTreeNode.Folder -> node.group.id
                                        is SettingTreeNode.File -> null
                                    }
                                    if (expandableId != null) {
                                        updateListExpansion(
                                            nextExpandedGroupIds = if (expandableId in expandedGroupIds) {
                                                expandedGroupIds - expandableId
                                            } else {
                                                expandedGroupIds + expandableId
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (searchOpen && displayedTreeNodes.isEmpty()) {
                        item(key = "empty_search") { EmptySearchResult(appearance) }
                    }
                }
            }
            if (hasUserTreeNodes && !searchOpen) {
                SettingTreeBottomPanel(
                    hasSelection = selectedTreeNodeId != RootNodeId && canDeleteSelected(),
                    canEdit = selectedLocalTreeNode is SettingTreeNode.File,
                    canCopy = canUseTreeClipboardSource(),
                    canDelete = canDeleteSelected(),
                    hasClipboard = hasTreeClipboard(),
                    createMenuOpen = createSettingKindDialogOpen,
                    appearance = appearance,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onCreate = ::requestAddSetting,
                    onDismissCreateMenu = { createSettingKindDialogOpen = false },
                    onCreateFolder = {
                        createSettingKindDialogOpen = false
                        requestAddGroup()
                    },
                    onCreateStatic = {
                        createSettingKindDialogOpen = false
                        requestAddEntry()
                    },
                    onCreateCache = {
                        createSettingKindDialogOpen = false
                        requestAddEntry(SettingLibraryTriggerMode.Cache)
                    },
                    onCreateReference = {
                        createSettingKindDialogOpen = false
                        addEjsReference()
                    },
                    onEdit = {
                        if (selectedLocalTreeNode is SettingTreeNode.File) {
                            openTreeNode(selectedLocalTreeNode)
                        }
                    },
                    onCopyOrPaste = {
                        val message = if (hasTreeClipboard()) pasteTreeClipboard() else copySelectedTreeNode()
                        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onCutOrCancel = {
                        val message = if (hasTreeClipboard()) cancelTreeClipboard() else cutSelectedTreeNode()
                        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onRename = ::requestRenameSelected,
                    onDelete = { confirmDeleteNode = true },
                )
            }
        }
    }

    SettingLibraryPageOverlays(
        editorState = editorState,
        library = library,
        characterName = characterName,
        characterAvatar = characterAvatar,
        importSources = importSources,
        loadingImportSources = loadingImportSources,
        appearance = appearance,
        invalidEnableEntry = invalidEnableEntry,
        conflictingOrderEntry = conflictingOrderEntry,
        onDismissInvalidEnableEntry = { invalidEnableEntry = null },
        onDismissConflictingOrderEntry = { conflictingOrderEntry = null },
        onRequestImportSources = onRequestImportSources,
        onImport = onImport,
        onExport = onExport,
        onParseImportFile = onParseImportFile,
    )
    }
}

internal fun Modifier.clearSettingTreeSelectionOnBlankTap(
    enabled: Boolean,
    onClear: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onClear) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null && !down.isConsumed && !up.isConsumed) {
                onClear()
            }
        }
    }
}
