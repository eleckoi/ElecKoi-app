package com.eleckoi.android.feature.characters.modes.story.variables.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableInitializationObjectId
import com.eleckoi.android.engine.story.variables.model.isInitializationObject
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StorySearchHeader
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun VariableConfigPage(
    config: VariableConfig?,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onSave: (VariableConfig) -> Unit,
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    val context = LocalContext.current
    val editorState = rememberVariableConfigEditorState(config)
    val horizontalTreeScroll = rememberScrollState()
    val listState = rememberLazyListState()
    var searchOpen by remember { mutableStateOf(false) }

    with(editorState) {
        BackHandler(enabled = searchOpen && editorObjectId == null && editorVariableId == null && !configManagerOpen) {
            searchOpen = false
            search = ""
        }
        LaunchedEffect(
            config?.characterId,
            config?.name,
            config?.initialStateJson,
            config?.schemaCode,
            config?.objects,
            config?.variables,
            config?.expandedObjectIds,
            config?.activeVersionId,
            config?.versions,
        ) {
            if (config == null || dirty) return@LaunchedEffect
            syncFrom(config)
        }

        LaunchedEffect(configName, schemaCode, objects, variables, expandedObjectIds, activeVersionId, versions, dirty) {
            val source = config ?: return@LaunchedEffect
            if (!dirty) return@LaunchedEffect
            delay(800)
            onSave(editedConfig(source))
            dirty = false
        }

        val treeDragUiState = rememberVariableTreeDragUiState()
        val treeNodes = remember(objects, variables, expandedObjectIds, treeDragUiState.forceCollapsedObjectIds, search) {
            variableTreeNodes(
                objects = objects,
                variables = variables,
                expandedObjectIds = expandedObjectIds,
                search = search,
                forceCollapsedObjectIds = treeDragUiState.forceCollapsedObjectIds,
            )
        }
        val hasUserTreeNodes = remember(objects, variables) { hasUserTreeNodes() }
        val treeInternalReorder = rememberVariableTreeInternalReorderState(
            listState = listState,
            nodes = treeNodes,
            variables = variables,
            objects = objects,
            enabled = !searchOpen && search.trim().isBlank(),
            onTreeChange = ::updateTree,
            onObjectDragStarted = treeDragUiState::collapseDraggedObject,
            onDragStopped = treeDragUiState::clearDragCollapse,
        )
        val displayedTreeNodes = treeInternalReorder.displayNodes()
        val selectedTreeNode = treeNodes.firstOrNull { it.id == selectedTreeNodeId }

        if (config != null && editorObjectId != null) {
            val variableObject = objects.firstOrNull { it.id == editorObjectId }
            if (variableObject != null) {
                VariableObjectEditorPage(
                    variableObject = variableObject,
                    variablePath = objectPath(variableObject.id),
                    objectStateJson = currentObjectStateJson(variableObject.id),
                    initialStateJson = currentInitialStateJson(),
                    schemaCode = schemaCode,
                    appearance = appearance,
                    onBack = { editorObjectId = null },
                    onObjectChange = { transform -> updateObject(variableObject.id, transform) },
                    onObjectJsonChange = { value -> replaceObjectChildrenFromJson(variableObject.id, value) },
                    onSchemaCodeChange = ::updateSchemaCode,
                )
                return
            }
            editorObjectId = null
        }

        if (config != null && editorVariableId != null) {
            val variable = variables.firstOrNull { it.id == editorVariableId }
            if (variable != null) {
                VariableItemEditorPage(
                    variable = variable,
                    variablePath = variablePath(variable.id),
                    appearance = appearance,
                    onBack = { editorVariableId = null },
                    onConvertToObject = { convertVariableToObject(variable.id) },
                    onVariableChange = { transform -> updateVariable(variable.id, transform) },
                )
                return
            }
            editorVariableId = null
        }

        PinnedStatusScaffold(appearance = appearance, imeAware = false, backgroundColor = appearance.mobileBg) {
            if (searchOpen) {
                StorySearchHeader(
                    query = search,
                    placeholder = "搜索变量",
                    appearance = appearance,
                    onQueryChange = { search = it },
                    onClose = {
                        searchOpen = false
                        search = ""
                    },
                )
            } else {
                VariableConfigHeader(
                    title = configName.trim().ifBlank { "变量配置" },
                    managerOpen = configManagerOpen,
                    appearance = appearance,
                    onBack = {
                        if (dirty && config != null) {
                            onSave(editedConfig(config))
                        }
                        onBack()
                    },
                    onSearch = { searchOpen = true },
                    onOpenManager = { configManagerOpen = true },
                )
            }

            if (config == null) {
                Box(modifier = Modifier.fillMaxSize().background(appearance.mobileBg), contentAlignment = Alignment.Center) {
                    Text("正在读取变量配置", color = appearance.mobileMuted, fontSize = 15.sp)
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
                        bottom = if (hasUserTreeNodes && !searchOpen) 112.dp else 18.dp,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .clearVariableTreeSelectionOnBlankTap(
                            enabled = !searchOpen && selectedTreeNodeId != VariableRootNodeId,
                            onClear = { focusTreeNode(VariableRootNodeId) },
                        ),
                ) {
                    if (!searchOpen) {
                        item(key = "selection-context") {
                            Text(
                                text = if (selectedTreeNodeId == VariableRootNodeId) {
                                    "点选变量组或变量后，可在底部编辑它。"
                                } else {
                                    selectedTreePath()
                                },
                                color = appearance.mobileMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    when {
                        !hasUserTreeNodes && !searchOpen -> item(key = "empty") {
                            EmptyVariableRootGuide(
                                appearance = appearance,
                                onCreateRoot = ::requestAddObject,
                            )
                        }
                        !hasUserTreeNodes || treeNodes.isEmpty() -> item(key = "empty_search") {
                            EmptyVariableSearchResult(appearance)
                        }
                        else -> items(displayedTreeNodes, key = { it.id }) { node ->
                            val dragging = treeInternalReorder.isDragging(node)
                            VariableTreeNodeRow(
                                node = node,
                                selected = selectedTreeNodeId == node.id,
                                dragging = dragging,
                                modifier = Modifier
                                    .zIndex(if (dragging) 2f else 0f)
                                    .offset { IntOffset(0, treeInternalReorder.dragOffsetY(node).roundToInt()) },
                                reorderModifier = treeInternalReorder.dragModifier(node),
                                expanded = when (node) {
                                    is VariableTreeNode.ObjectNode -> !node.variableObject.isInitializationObject() && (search.isNotBlank() || node.variableObject.id in expandedObjectIds)
                                    is VariableTreeNode.VariableNode -> false
                                },
                                horizontalScrollState = horizontalTreeScroll,
                                appearance = appearance,
                                onSelect = {
                                    when (node) {
                                        is VariableTreeNode.ObjectNode -> selectTreeNode(node.id)
                                        is VariableTreeNode.VariableNode -> focusTreeNode(node.id)
                                    }
                                },
                                onOpen = {
                                    openTreeNode(node)
                                },
                                onObjectEnabledChange = { item, enabled ->
                                    updateObject(item.id) { it.copy(enabled = enabled) }
                                },
                                onVariableEnabledChange = { item, enabled ->
                                    updateVariable(item.id) { it.copy(enabled = enabled) }
                                },
                                onToggle = {
                                    if (node is VariableTreeNode.ObjectNode) {
                                        toggleObject(node.variableObject.id)
                                    }
                                },
                            )
                        }
                    }
                }

                if (hasUserTreeNodes && !searchOpen) {
                    VariableTreeBottomPanel(
                        hasSelection = selectedTreeNodeId != VariableRootNodeId && selectedTreeNodeId != variableObjectNodeId(VariableInitializationObjectId),
                        canEdit = selectedTreeNode != null,
                        canCopy = canUseTreeClipboardSource(),
                        canDelete = canDeleteSelected(),
                        hasClipboard = hasTreeClipboard(),
                        createMenuOpen = createMenuOpen,
                        appearance = appearance,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onCreate = ::requestAddFromToolbar,
                        onDismissCreateMenu = { createMenuOpen = false },
                        onCreateObject = ::requestAddObject,
                        onCreateVariable = ::requestAddVariable,
                        onEdit = { selectedTreeNode?.let(::openTreeNode) },
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

        if (renameNodeDialogOpen) {
            VariableNameDialog(
                title = "重命名",
                value = renameNodeName,
                duplicate = false,
                duplicateText = "",
                confirmText = "保存",
                appearance = appearance,
                onValueChange = { renameNodeName = it },
                onDismiss = { renameNodeDialogOpen = false },
                onConfirm = {
                    renameNodeDialogOpen = false
                    renameSelected(renameNodeName)
                },
            )
        }

        if (createObjectNameDialogOpen) {
            val parentId = selectedObjectIdFromNodeId(selectedTreeNodeId, variables)
                .takeUnless { it == VariableInitializationObjectId }
                .orEmpty()
            val name = createObjectName.trim()
            VariableNameDialog(
                title = "新建变量组",
                value = createObjectName,
                duplicate = name.isNotBlank() && objects.any { !it.isInitializationObject() && it.parentId == parentId && it.name.trim() == name },
                duplicateText = "变量组名已存在",
                confirmText = "创建",
                appearance = appearance,
                onValueChange = { createObjectName = it },
                onDismiss = { createObjectNameDialogOpen = false },
                onConfirm = {
                    createObjectNameDialogOpen = false
                    addObject(createObjectName)
                },
            )
        }

        if (configManagerOpen) {
            VariableConfigManagerSheet(
                name = configName,
                versions = versions,
                activeVersionId = activeVersionId,
                appearance = appearance,
                onNameChange = ::updateConfigName,
                onDismiss = { configManagerOpen = false },
                onCreateConfig = ::createConfigVersion,
                onSelectVersion = ::switchVersion,
                onImport = onImport,
                onExport = onExport,
                onDeleteConfig = { confirmDeleteConfig = true },
            )
        }

        if (confirmDeleteNode) {
            VariableDeleteConfirmDialog(
                kindLabel = selectedTreeKindLabel(),
                appearance = appearance,
                onDismiss = { confirmDeleteNode = false },
                onConfirm = {
                    confirmDeleteNode = false
                    deleteSelected()
                },
            )
        }


        if (confirmDeleteConfig) {
            ConfirmDialog(
                title = "删除当前变量配置？",
                message = "将删除当前变量配置版本，并自动切换到另一个版本。",
                appearance = appearance,
                onDismiss = { confirmDeleteConfig = false },
                onConfirm = ::deleteActiveVersion,
            )
        }

    }
}

