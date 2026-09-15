package com.eleckoi.android.feature.characters.presets.ui.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetCatalog
import com.eleckoi.android.feature.characters.presets.model.DefaultAgentPresetId
import com.eleckoi.android.feature.characters.presets.model.AgentPresetLibraryGroup
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetSummary
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryEditorHeader
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StorySearchHeader
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppFloatingBottomAction
import com.eleckoi.android.foundation.design.components.AppFloatingBottomActionBar
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.MobileRootActionHeader
import com.eleckoi.android.foundation.design.components.MobileRootTopBar
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.mobileRootContentColor

internal enum class PresetBatchMode {
    Delete,
    Export,
}

internal const val AllPresetGroupId = "__all_presets__"

@Composable
internal fun AgentPresetLibrary(
    catalog: AgentPresetCatalog,
    appearance: AppearanceTheme,
    onBack: (() -> Unit)?,
    onOpenSidebar: (() -> Unit)?,
    onOpenPreset: (String) -> Unit,
    onOpenOverview: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onCreate: (String, List<AgentPresetModelTag>, String) -> Unit,
    onImport: () -> Unit,
    exporting: Boolean,
    onExport: (Set<String>) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveToGroup: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var headerMenuOpen by rememberSaveable { mutableStateOf(false) }
    var selectedGroupId by rememberSaveable { mutableStateOf(AllPresetGroupId) }
    var createDialogOpen by remember { mutableStateOf(false) }
    var createGroupDialogOpen by remember { mutableStateOf(false) }
    var actionPreset by remember { mutableStateOf<AgentPresetSummary?>(null) }
    var renamePreset by remember { mutableStateOf<AgentPresetSummary?>(null) }
    var deletePreset by remember { mutableStateOf<AgentPresetSummary?>(null) }
    var deleteGroup by remember { mutableStateOf<AgentPresetLibraryGroup?>(null) }
    var manageGroup by remember { mutableStateOf<AgentPresetLibraryGroup?>(null) }
    var renameGroup by remember { mutableStateOf<AgentPresetLibraryGroup?>(null) }
    var batchMode by remember { mutableStateOf<PresetBatchMode?>(null) }
    var selectedPresetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteSelectionConfirmationOpen by remember { mutableStateOf(false) }
    val libraryGroups = remember(catalog.groups) { catalog.groups.filter { it.id.isNotBlank() } }
    val context = LocalContext.current
    val leaveBatchMode = {
        batchMode = null
        selectedPresetIds = emptySet()
        deleteSelectionConfirmationOpen = false
    }
    BackHandler(enabled = batchMode != null) { leaveBatchMode() }
    BackHandler(enabled = searchOpen && batchMode == null) {
        searchOpen = false
        search = ""
    }

    LaunchedEffect(libraryGroups, catalog.activePreset?.libraryGroupId) {
        if (selectedGroupId != AllPresetGroupId && libraryGroups.none { it.id == selectedGroupId }) {
            selectedGroupId = catalog.activePreset?.libraryGroupId
                ?.takeIf { activeGroupId -> libraryGroups.any { it.id == activeGroupId } }
                ?: AllPresetGroupId
        }
    }
    LaunchedEffect(catalog.presets) {
        val storedIds = catalog.presets.mapTo(mutableSetOf()) { it.id }
        selectedPresetIds = selectedPresetIds.intersect(storedIds)
    }
    val modalOpen = actionPreset != null ||
        manageGroup != null ||
        createDialogOpen ||
        createGroupDialogOpen ||
        renamePreset != null ||
        renameGroup != null ||
        deletePreset != null ||
        deleteGroup != null ||
        deleteSelectionConfirmationOpen
    val modalBackdropBlur by animateDpAsState(
        targetValue = if (modalOpen) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "presetModalBackdropBlur",
    )
    val query = search.trim()
    val groupPresets = remember(catalog.presets, selectedGroupId) {
        if (selectedGroupId == AllPresetGroupId) {
            catalog.presets
        } else {
            catalog.presets.filter { preset -> preset.libraryGroupId == selectedGroupId }
        }
    }
    val searchResults = remember(catalog.presets, query) {
        catalog.presets.filter { preset -> query.isBlank() || preset.name.contains(query, ignoreCase = true) }
    }
    val visiblePresets = if (searchOpen) searchResults else groupPresets
    val togglePresetSelection: (AgentPresetSummary) -> Unit = { preset ->
        selectedPresetIds = if (preset.id in selectedPresetIds) {
            selectedPresetIds - preset.id
        } else if (batchMode == PresetBatchMode.Delete && preset.id == DefaultAgentPresetId) {
            Toast.makeText(context, "默认预设不可删除", Toast.LENGTH_SHORT).show()
            selectedPresetIds
        } else if (
            batchMode == PresetBatchMode.Delete &&
            selectedPresetIds.size >= catalog.presets.size - 1
        ) {
            Toast.makeText(context, "至少保留一个预设", Toast.LENGTH_SHORT).show()
            selectedPresetIds
        } else {
            selectedPresetIds + preset.id
        }
    }

    PinnedStatusScaffold(
        appearance = appearance,
        modifier = if (modalBackdropBlur > 0.dp) {
            Modifier.blur(modalBackdropBlur, BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier
        },
        backgroundColor = mobileRootContentColor(appearance),
        includeStatusBarPadding = false,
    ) {
        MobileRootTopBar(appearance = appearance) {
            if (searchOpen) {
                StorySearchHeader(
                    query = search,
                    placeholder = "搜索预设",
                    appearance = appearance,
                    onQueryChange = { search = it },
                    onClose = {
                        searchOpen = false
                        search = ""
                    },
                )
            } else if (onBack == null && onOpenSidebar != null && batchMode == null) {
                val enterDeleteMode = {
                    if (catalog.presets.size <= 1) {
                        Toast.makeText(context, "至少保留一个预设", Toast.LENGTH_SHORT).show()
                    } else {
                        batchMode = PresetBatchMode.Delete
                    }
                }
                MobileRootActionHeader(
                    title = "预设",
                    appearance = appearance,
                    onOpenSidebar = onOpenSidebar,
                    onSearch = { searchOpen = true },
                    onAdd = { createDialogOpen = true },
                    addMenuActions = presetLibraryMenuActions(
                        onCreate = { createDialogOpen = true },
                        onImport = onImport,
                        onExport = { batchMode = PresetBatchMode.Export },
                        onDelete = enterDeleteMode,
                    ),
                    addMenuExpanded = headerMenuOpen,
                    onAddMenuExpandedChange = { headerMenuOpen = it },
                    useOverflowAction = true,
                )
            } else {
                StoryEditorHeader(
                    title = "预设",
                    appearance = appearance,
                    onBack = onBack,
                    onOpenSidebar = onOpenSidebar,
                    actionWidth = if (batchMode == null) 96.dp else 48.dp,
                    action = {
                        if (batchMode == null) {
                            PresetLibraryHeaderActions(
                                appearance = appearance,
                                onSearch = { searchOpen = true },
                                onCreate = { createDialogOpen = true },
                                onImport = onImport,
                                onExport = { batchMode = PresetBatchMode.Export },
                                onDelete = {
                                    if (catalog.presets.size <= 1) {
                                        Toast.makeText(context, "至少保留一个预设", Toast.LENGTH_SHORT).show()
                                    } else {
                                        batchMode = PresetBatchMode.Delete
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).background(mobileRootContentColor(appearance))) {
            if (searchOpen) {
                PresetCardsList(
                    presets = visiblePresets,
                    activePresetId = catalog.activePresetId,
                    selectedPresetIds = selectedPresetIds,
                    selectionMode = false,
                    appearance = appearance,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 18.dp),
                    onOpenPreset = onOpenPreset,
                    onOpenActions = { actionPreset = it },
                    onToggleSelected = togglePresetSelection,
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val railWidth = if (maxWidth < 390.dp) 96.dp else 112.dp
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PresetGroupRail(
                            groups = libraryGroups,
                            presetCounts = catalog.presets.groupingBy { it.libraryGroupId }.eachCount(),
                            allPresetCount = catalog.presets.size,
                            selectedGroupId = selectedGroupId,
                            managementEnabled = batchMode == null,
                            appearance = appearance,
                            modifier = Modifier.width(railWidth).fillMaxHeight(),
                            onSelect = { group ->
                                if (selectedGroupId != group.id) selectedPresetIds = emptySet()
                                selectedGroupId = group.id
                            },
                            onManage = { manageGroup = it },
                            onCreate = { createGroupDialogOpen = true },
                        )
                        PresetCardsList(
                            presets = visiblePresets,
                            activePresetId = catalog.activePresetId,
                            selectedPresetIds = selectedPresetIds,
                            selectionMode = batchMode != null,
                            appearance = appearance,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = if (batchMode == null) 18.dp else 112.dp,
                            ),
                            onOpenPreset = onOpenPreset,
                            onOpenActions = { actionPreset = it },
                            onToggleSelected = togglePresetSelection,
                        )
                    }
                }
            }
            if (!searchOpen && batchMode != null) {
                AppFloatingBottomActionBar(
                    appearance = appearance,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val visibleIds = visiblePresets
                        .filterNot { batchMode == PresetBatchMode.Delete && it.id == DefaultAgentPresetId }
                        .mapTo(linkedSetOf()) { it.id }
                    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all(selectedPresetIds::contains)
                    AppFloatingBottomAction(
                        label = "取消",
                        icon = AppIconPaths.X,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        onClick = leaveBatchMode,
                    )
                    AppFloatingBottomAction(
                        label = if (allVisibleSelected) "取消全选" else "全选",
                        icon = AppIconPaths.Check,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPresetIds = if (allVisibleSelected) {
                                selectedPresetIds - visibleIds
                            } else {
                                val merged = selectedPresetIds + visibleIds
                                if (batchMode == PresetBatchMode.Delete && merged.size >= catalog.presets.size) {
                                    merged - catalog.activePresetId
                                } else {
                                    merged
                                }
                            }
                        },
                    )
                    val mode = batchMode
                    AppFloatingBottomAction(
                        label = when (mode) {
                            PresetBatchMode.Delete -> "删除 ${selectedPresetIds.size}"
                            PresetBatchMode.Export -> "导出 ${selectedPresetIds.size}"
                            null -> ""
                        },
                        icon = if (mode == PresetBatchMode.Delete) AppIconPaths.Trash else AppIconPaths.Export,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        danger = mode == PresetBatchMode.Delete,
                        enabled = selectedPresetIds.isNotEmpty() && !exporting,
                        onClick = {
                            when (mode) {
                                PresetBatchMode.Delete -> deleteSelectionConfirmationOpen = true
                                PresetBatchMode.Export -> {
                                    onExport(selectedPresetIds)
                                    leaveBatchMode()
                                }
                                null -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
    manageGroup?.let { group ->
        PresetGroupActionSheet(
            group = group,
            appearance = appearance,
            onDismiss = { manageGroup = null },
            onRename = {
                manageGroup = null
                renameGroup = group
            },
            onDelete = {
                manageGroup = null
                deleteGroup = group
            },
        )
    }
    actionPreset?.let { preset ->
        PresetActionSheet(
            preset = preset,
            groups = libraryGroups,
            active = preset.id == catalog.activePresetId,
            canDelete = preset.id != DefaultAgentPresetId,
            appearance = appearance,
            onDismiss = { actionPreset = null },
            onSetActive = {
                actionPreset = null
                onSetActive(preset.id)
            },
            onEditProfile = {
                actionPreset = null
                onOpenOverview(preset.id)
            },
            onRename = {
                actionPreset = null
                renamePreset = preset
            },
            onDuplicate = {
                actionPreset = null
                onDuplicate(preset.id)
            },
            onMove = { targetGroupId ->
                actionPreset = null
                selectedGroupId = targetGroupId
                onMoveToGroup(preset.id, targetGroupId)
            },
            onDelete = {
                actionPreset = null
                deletePreset = preset
            },
        )
    }
    deleteGroup?.let { group ->
        ConfirmDialog(
            title = "删除分组？",
            message = "“${group.name}”中的预设不会删除，之后仍可在“全部预设”中找到。",
            appearance = appearance,
            onDismiss = { deleteGroup = null },
            onConfirm = {
                selectedGroupId = AllPresetGroupId
                deleteGroup = null
                onDeleteGroup(group.id)
            },
        )
    }
    if (createGroupDialogOpen) {
        PresetNameDialog(
            title = "新建分组",
            placeholder = "分组名称",
            appearance = appearance,
            onDismiss = { createGroupDialogOpen = false },
            onConfirm = { name ->
                createGroupDialogOpen = false
                onCreateGroup(name)
            },
        )
    }

    renameGroup?.let { group ->
        PresetNameDialog(
            title = "重命名分组",
            placeholder = "分组名称",
            initialValue = group.name,
            confirmLabel = "重命名",
            appearance = appearance,
            onDismiss = { renameGroup = null },
            onConfirm = { name ->
                renameGroup = null
                onRenameGroup(group.id, name)
            },
        )
    }

    renamePreset?.let { preset ->
        PresetNameDialog(
            title = "重命名预设",
            placeholder = "预设名称",
            initialValue = preset.name,
            confirmLabel = "重命名",
            appearance = appearance,
            onDismiss = { renamePreset = null },
            onConfirm = { name ->
                renamePreset = null
                onRename(preset.id, name)
            },
        )
    }

    if (createDialogOpen) {
        CreatePresetDialog(
            appearance = appearance,
            onDismiss = { createDialogOpen = false },
            onConfirm = { name, tags ->
                createDialogOpen = false
                onCreate(
                    name,
                    tags,
                    selectedGroupId.takeUnless { it == AllPresetGroupId }.orEmpty(),
                )
            },
        )
    }
    deletePreset?.let { preset ->
        ConfirmDialog(
            title = "删除预设？",
            message = "将永久删除“${preset.name}”及其中的全部条目。",
            appearance = appearance,
            onDismiss = { deletePreset = null },
            onConfirm = {
                deletePreset = null
                onDelete(preset.id)
            },
        )
    }
    if (deleteSelectionConfirmationOpen) {
        ConfirmDialog(
            title = "删除 ${selectedPresetIds.size} 个预设？",
            message = "将永久删除所选预设及其中的全部条目。",
            appearance = appearance,
            onDismiss = { deleteSelectionConfirmationOpen = false },
            onConfirm = {
                deleteSelectionConfirmationOpen = false
                selectedPresetIds.forEach(onDelete)
                leaveBatchMode()
            },
        )
    }
}
