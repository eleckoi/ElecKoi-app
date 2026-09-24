package com.eleckoi.android.feature.characters.modes.story.variables.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableInitializationObjectId
import com.eleckoi.android.engine.story.variables.model.VariableInitializationObjectName
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.model.generatedInitialStatePreviewJson
import com.eleckoi.android.engine.story.variables.model.generatedObjectStateJson
import com.eleckoi.android.engine.story.variables.model.isInitializationObject

internal const val VariableRootNodeId = "root"

internal class VariableConfigEditorState(config: VariableConfig?) {
    private var document by mutableStateOf(VariableConfigDocument.from(config))
    val configName: String get() = document.name
    val schemaCode: String get() = document.schemaCode
    val objects: List<VariableObjectConfig> get() = document.objects
    val variables: List<VariableItemConfig> get() = document.variables
    val expandedObjectIds: Set<String> get() = document.expandedObjectIds
    val versions: List<VariableConfigVersion> get() = document.versions
    val activeVersionId: String get() = document.activeVersionId
    var configManagerOpen by mutableStateOf(false)
    var createConfigVersionDialogOpen by mutableStateOf(false)
    var confirmDeleteConfig by mutableStateOf(false)
    var selectedTreeNodeId by mutableStateOf(VariableRootNodeId)
    var editorObjectId by mutableStateOf<String?>(null)
    var editorVariableId by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
    var createMenuOpen by mutableStateOf(false)
    var createObjectNameDialogOpen by mutableStateOf(false)
    var createObjectName by mutableStateOf("")
    var renameNodeDialogOpen by mutableStateOf(false)
    var renameNodeName by mutableStateOf("")
    var confirmDeleteNode by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    private var treeClipboard by mutableStateOf<VariableTreeClipboard?>(null)

    fun syncFrom(config: VariableConfig) {
        dispatch(VariableConfigDocumentAction.Sync(config))
        if (editorObjectId != null && objects.none { it.id == editorObjectId }) {
            editorObjectId = null
        }
        if (editorVariableId != null && variables.none { it.id == editorVariableId }) {
            editorVariableId = null
        }
        ensureSelectedTreeNode()
    }

    fun editedConfig(source: VariableConfig): VariableConfig = document.editedConfig(source)

    fun updateConfigName(value: String) {
        dispatch(VariableConfigDocumentAction.Rename(value))
        dirty = true
    }

    fun switchVersion(version: VariableConfigVersion) {
        dispatch(VariableConfigDocumentAction.SwitchVersion(version))
        selectedTreeNodeId = VariableRootNodeId
        editorObjectId = null
        editorVariableId = null
        dirty = true
    }

    fun createConfigVersion(name: String, sourceVersionId: String?) {
        dispatch(
            VariableConfigDocumentAction.CreateVersion(
                id = "draft-variable-config-${System.currentTimeMillis()}",
                name = name,
                sourceVersionId = sourceVersionId,
            ),
        )
        selectedTreeNodeId = VariableRootNodeId
        editorObjectId = null
        editorVariableId = null
        dirty = true
    }

    fun deleteActiveVersion() {
        dispatch(
            VariableConfigDocumentAction.DeleteActiveVersion(
                fallbackId = "draft-variable-config-${System.currentTimeMillis()}",
            ),
        )
        selectedTreeNodeId = VariableRootNodeId
        editorObjectId = null
        editorVariableId = null
        dirty = true
        configManagerOpen = false
        confirmDeleteConfig = false
    }

    fun hasUserTreeNodes(): Boolean {
        return objects.any { !it.isInitializationObject() } || variables.isNotEmpty()
    }

    fun currentInitialStateJson(): String {
        return generatedInitialStatePreviewJson(objects, variables)
    }

    fun currentObjectStateJson(objectId: String): String {
        return generatedObjectStateJson(objectId, objects, variables)
    }

    fun requestAddObject() {
        createObjectName = if (objects.none { !it.isInitializationObject() }) "" else nextObjectName()
        createObjectNameDialogOpen = true
        createMenuOpen = false
    }

    fun requestAddVariable() {
        val targetObjectId = selectedTargetObjectId()
        addVariable(nextVariableName(targetObjectId))
        createMenuOpen = false
    }

    fun addObject(name: String) {
        val normalizedName = name.trim().take(40)
        if (normalizedName.isBlank()) return
        val parentId = selectedTargetObjectId()
        if (objects.any { it.parentId == parentId && it.name.trim() == normalizedName }) return
        val id = uniqueObjectId()
        val next = VariableObjectConfig(
            id = id,
            name = normalizedName,
            parentId = parentId,
            order = objects.size + 1,
            treeViewOrder = nextTreeViewOrder(parentId),
        )
        if (parentId.isNotBlank()) {
            dispatch(VariableConfigDocumentAction.SetExpanded(expandedObjectIds + parentId))
        }
        updateObjects(ensureInitializationObject(objects + next))
    }

    fun addVariable(name: String) {
        val normalizedName = name.trim().take(60)
        if (normalizedName.isBlank()) return
        val targetObjectId = selectedTargetObjectId()
        if (targetObjectId.isNotBlank() && objects.none { it.id == targetObjectId }) return
        val id = uniqueVariableId()
        val next = VariableItemConfig(
            id = id,
            title = normalizedName,
            objectId = targetObjectId,
            order = variables.size + 1,
            treeViewOrder = nextTreeViewOrder(targetObjectId),
        )
        if (targetObjectId.isNotBlank()) {
            dispatch(VariableConfigDocumentAction.SetExpanded(expandedObjectIds + targetObjectId))
        }
        updateVariables(variables + next)
    }

    fun updateVariable(variableId: String, transform: (VariableItemConfig) -> VariableItemConfig) {
        updateVariables(variables.map { item -> if (item.id == variableId) transform(item) else item })
    }

    fun convertVariableToObject(variableId: String) {
        val source = variables.firstOrNull { it.id == variableId } ?: return
        val objectId = uniqueObjectId()
        val variableObject = VariableObjectConfig(
            id = objectId,
            name = source.title.ifBlank { "未命名变量组" }.take(40),
            parentId = source.objectId,
            enabled = source.enabled,
            description = source.description,
            updateRule = source.updateRule,
            order = source.order,
            treeViewOrder = source.treeViewOrder,
            createdAt = source.createdAt,
            updatedAt = source.updatedAt,
        )
        dispatch(
            VariableConfigDocumentAction.ReplaceTree(
                objects = ensureInitializationObject(objects + variableObject),
                variables = variables.filterNot { it.id == variableId },
                expandedObjectIds =
                    expandedObjectIds + objectId + listOf(source.objectId).filter { it.isNotBlank() },
            ),
        )
        selectedTreeNodeId = variableObjectNodeId(objectId)
        editorVariableId = null
        editorObjectId = objectId
        dirty = true
    }

    fun replaceObjectChildrenFromJson(objectId: String, rawJson: String): String? {
        val result = VariableJsonTreeImporter(
            objectId = ::uniqueObjectId,
            variableId = ::uniqueVariableId,
        ).replaceChildren(
            targetObjectId = objectId,
            rawJson = rawJson,
            objects = objects,
            variables = variables,
            expandedObjectIds = expandedObjectIds,
        )
        return when (result) {
            is VariableJsonTreeImportResult.Failure -> result.message
            is VariableJsonTreeImportResult.Success -> {
                dispatch(
                    VariableConfigDocumentAction.ReplaceTree(
                        objects = result.objects,
                        variables = result.variables,
                        expandedObjectIds = result.expandedObjectIds,
                    ),
                )
                selectedTreeNodeId = variableObjectNodeId(objectId)
                dirty = true
                null
            }
        }
    }

    fun updateObject(objectId: String, transform: (VariableObjectConfig) -> VariableObjectConfig) {
        updateObjects(objects.map { item -> if (item.id == objectId) transform(item) else item })
    }

    fun updateSchemaCode(value: String) {
        dispatch(VariableConfigDocumentAction.UpdateSchema(value))
        dirty = true
    }

    fun toggleObject(objectId: String) {
        if (objectId == VariableInitializationObjectId) return
        dispatch(
            VariableConfigDocumentAction.SetExpanded(
                if (objectId in expandedObjectIds) {
                    expandedObjectIds - objectId
                } else {
                    expandedObjectIds + objectId
                },
            ),
        )
        dirty = true
    }

    fun selectTreeNode(nodeId: String) {
        selectedTreeNodeId = if (selectedTreeNodeId == nodeId) VariableRootNodeId else nodeId
        createMenuOpen = false
    }

    fun focusTreeNode(nodeId: String) {
        selectedTreeNodeId = nodeId
        createMenuOpen = false
    }

    fun openTreeNode(node: VariableTreeNode) {
        selectedTreeNodeId = node.id
        createMenuOpen = false
        when (node) {
            is VariableTreeNode.ObjectNode -> {
                editorObjectId = node.variableObject.id
                editorVariableId = null
            }
            is VariableTreeNode.VariableNode -> {
                editorVariableId = node.variable.id
                editorObjectId = null
            }
        }
    }

    fun requestAddFromToolbar() {
        createMenuOpen = !createMenuOpen
    }

    fun selectedTreeTitle(): String = variableTreeTitle(selectedTreeNodeId, objects, variables)

    fun selectedTreeKindLabel(): String = variableTreeKindLabel(selectedTreeNodeId)

    fun canDeleteSelected(): Boolean = canDeleteVariableTreeNode(selectedTreeNodeId)

    fun canRenameSelected(): Boolean {
        return canDeleteSelected()
    }

    fun hasTreeClipboard(): Boolean = treeClipboard != null

    fun canUseTreeClipboardSource(): Boolean = canDeleteSelected()

    fun copySelectedTreeNode(): String? {
        if (!canUseTreeClipboardSource()) return null
        treeClipboard = VariableTreeClipboard(selectedTreeNodeId, VariableTreeClipboardMode.Copy)
        return "已复制"
    }

    fun cutSelectedTreeNode(): String? {
        if (!canDeleteSelected()) return null
        treeClipboard = VariableTreeClipboard(selectedTreeNodeId, VariableTreeClipboardMode.Cut)
        return "已剪切"
    }

    fun cancelTreeClipboard(): String? {
        if (treeClipboard == null) return null
        treeClipboard = null
        return "已取消"
    }

    fun pasteTreeClipboard(): String? {
        val clipboard = treeClipboard ?: return null
        val targetObjectId = selectedObjectIdFromNodeId(selectedTreeNodeId, variables)
        val result = planVariableTreePaste(
            clipboard = clipboard,
            targetObjectId = targetObjectId,
            objects = objects,
            variables = variables,
            uniqueObjectId = ::uniqueObjectId,
            uniqueVariableId = ::uniqueVariableId,
        ) ?: return null
        result.objects?.let(::updateObjects)
        result.variables?.let(::updateVariables)
        treeClipboard = null
        selectedTreeNodeId = result.selectedNodeId
        if (targetObjectId.isNotBlank()) {
            dispatch(
                VariableConfigDocumentAction.SetExpanded(expandedObjectIds + targetObjectId),
            )
        }
        return "已粘贴"
    }

    fun requestRenameSelected() {
        if (!canRenameSelected()) return
        renameNodeName = selectedTreeTitle()
        renameNodeDialogOpen = true
    }

    fun renameSelected(value: String) {
        val name = value.trim().take(60)
        if (name.isBlank()) return
        when {
            selectedTreeNodeId.startsWith("object:") -> {
                val objectId = selectedTreeNodeId.removePrefix("object:")
                updateObjects(objects.map { variableObject ->
                    if (variableObject.id == objectId && !variableObject.isInitializationObject()) {
                        variableObject.copy(name = name)
                    } else {
                        variableObject
                    }
                })
            }
            selectedTreeNodeId.startsWith("variable:") -> {
                val variableId = selectedTreeNodeId.removePrefix("variable:")
                updateVariable(variableId) { it.copy(title = name) }
            }
        }
    }

    fun deleteSelected() {
        when {
            selectedTreeNodeId.startsWith("variable:") -> {
                val variableId = selectedTreeNodeId.removePrefix("variable:")
                updateVariables(variables.filterNot { it.id == variableId })
            }
            selectedTreeNodeId.startsWith("object:") -> {
                val objectId = selectedTreeNodeId.removePrefix("object:")
                if (objectId == VariableInitializationObjectId) return
                val descendantIds = descendantVariableObjectIds(objectId, objects)
                val deleteIds = descendantIds + objectId
                updateVariables(variables.filterNot { it.objectId in deleteIds })
                updateObjects(objects.filterNot { it.id in deleteIds })
            }
        }
        selectedTreeNodeId = VariableRootNodeId
    }

    fun selectedTreePath(): String = variableTreeBreadcrumb(selectedTreeNodeId, objects, variables)

    fun objectPath(objectId: String): String = variableObjectJsonPointer(objectId, objects)

    fun variablePath(variableId: String): String = variableItemJsonPointer(variableId, objects, variables)

    fun ensureSelectedTreeNode() {
        val valid = when {
            selectedTreeNodeId == VariableRootNodeId -> true
            selectedTreeNodeId.startsWith("object:") -> objects.any { variableObjectNodeId(it.id) == selectedTreeNodeId }
            selectedTreeNodeId.startsWith("variable:") -> variables.any { variableItemNodeId(it.id) == selectedTreeNodeId }
            else -> false
        }
        if (!valid) selectedTreeNodeId = VariableRootNodeId
    }

    fun updateTree(nextVariables: List<VariableItemConfig>, nextObjects: List<VariableObjectConfig>) {
        updateObjects(nextObjects)
        updateVariables(nextVariables)
    }

    private fun updateObjects(next: List<VariableObjectConfig>) {
        dispatch(VariableConfigDocumentAction.ReplaceObjects(next))
        dirty = true
    }

    private fun updateVariables(next: List<VariableItemConfig>) {
        dispatch(VariableConfigDocumentAction.ReplaceVariables(next))
        dirty = true
    }

    private fun nextTreeViewOrder(parentId: String): Int {
        val objectMax = objects.filter { it.parentId == parentId }.maxOfOrNull { it.treeViewOrder } ?: 0
        val variableMax = variables.filter { it.objectId == parentId }.maxOfOrNull { it.treeViewOrder } ?: 0
        return maxOf(objectMax, variableMax) + 1
    }

    private fun nextObjectName(): String {
        val parentId = selectedTargetObjectId()
        val names = objects.filter { it.parentId == parentId }.map { it.name.trim() }.toSet()
        if ("新变量组" !in names) return "新变量组"
        var index = 2
        while ("新变量组$index" in names) index += 1
        return "新变量组$index"
    }

    private fun nextVariableName(objectId: String): String {
        val names = variables.filter { it.objectId == objectId }.map { it.title.trim() }.toSet()
        if ("新变量" !in names) return "新变量"
        var index = 2
        while ("新变量$index" in names) index += 1
        return "新变量$index"
    }

    private fun selectedTargetObjectId(): String {
        return selectedObjectIdFromNodeId(selectedTreeNodeId, variables)
            .takeUnless { it == VariableInitializationObjectId }
            .orEmpty()
    }

    private fun ensureInitializationObject(source: List<VariableObjectConfig>): List<VariableObjectConfig> {
        if (source.any { it.isInitializationObject() }) return source
        return listOf(
            VariableObjectConfig(
                id = VariableInitializationObjectId,
                name = VariableInitializationObjectName,
                parentId = "",
                order = 0,
                treeViewOrder = 0,
            ),
        ) + source
    }

    private fun dispatch(action: VariableConfigDocumentAction) {
        document = VariableConfigDocumentReducer.reduce(document, action)
    }

    private fun uniqueObjectId(offset: Int = 0): String {
        val existing = objects.map { it.id }.toSet()
        var index = offset
        while (true) {
            val id = "variable-object-${System.currentTimeMillis()}-$index"
            if (id !in existing) return id
            index += 1
        }
    }

    private fun uniqueVariableId(offset: Int = 0): String {
        val existing = variables.map { it.id }.toSet()
        var index = offset
        while (true) {
            val id = "variable-${System.currentTimeMillis()}-$index"
            if (id !in existing) return id
            index += 1
        }
    }
}

@Composable
internal fun rememberVariableConfigEditorState(config: VariableConfig?): VariableConfigEditorState {
    return remember(config?.characterId) { VariableConfigEditorState(config) }
}
