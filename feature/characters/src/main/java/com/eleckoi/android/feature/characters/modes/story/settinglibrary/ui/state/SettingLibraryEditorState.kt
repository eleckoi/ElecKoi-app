package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.defaultSettingLibraryFixedEntries
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.normalizeSettingLibraryFixedEntry

internal const val InitialAllEntriesVisibleCount = 20
private const val AllEntriesLoadMoreCount = 20

internal class SettingLibraryEditorState(library: SettingLibrary?) {
    var libraryName by mutableStateOf(library?.name.orEmpty())
    var entries by mutableStateOf(library?.entries.orEmpty())
    var groups by mutableStateOf(library?.groups.orEmpty())
    var promptPositions by mutableStateOf(library?.promptPositions.orEmpty())
    var versions by mutableStateOf(library?.versions.orEmpty())
    var activeVersionId by mutableStateOf(library?.activeVersionId.orEmpty())
    var editorEntryId by mutableStateOf<String?>(null)
    var libraryManagerOpen by mutableStateOf(false)
    var importPageOpen by mutableStateOf(false)
    var createLibraryVersionDialogOpen by mutableStateOf(false)
    var search by mutableStateOf("")
    var allEntriesExpanded by mutableStateOf(initialAllEntriesExpanded(library))
    var allEntriesVisibleLimit by mutableStateOf(InitialAllEntriesVisibleCount)
    var expandedGroupIds by mutableStateOf(library?.expandedGroupIds?.toSet().orEmpty())
    var selectedTreeNodeId by mutableStateOf(RootNodeId)
    var createSettingKindDialogOpen by mutableStateOf(false)
    var createEntryGroupPickerOpen by mutableStateOf(false)
    var selectedCreateEntryGroupId by mutableStateOf("")
    var pendingCreateEntryTriggerMode by mutableStateOf<SettingLibraryTriggerMode?>(null)
    var createGroupNameDialogOpen by mutableStateOf(false)
    var createGroupName by mutableStateOf("")
    var renameNodeDialogOpen by mutableStateOf(false)
    var renameNodeName by mutableStateOf("")
    var confirmDeleteNode by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    var confirmDeleteLibrary by mutableStateOf(false)
    private val treeClipboardController = SettingLibraryTreeClipboardController(this)

    fun syncFrom(library: SettingLibrary) {
        libraryName = library.name
        entries = library.entries
        groups = library.groups
        promptPositions = library.promptPositions
        versions = library.versions
        activeVersionId = library.activeVersionId
        allEntriesExpanded = library.listAllExpanded
        allEntriesVisibleLimit = InitialAllEntriesVisibleCount
        expandedGroupIds = library.expandedGroupIds.toSet()
        if (editorEntryId != null && entries.none { it.id == editorEntryId }) {
            editorEntryId = null
        }
        ensureSelectedTreeNode()
    }

    fun editedLibrary(source: SettingLibrary): SettingLibrary {
        return source.copy(
            name = libraryName,
            entries = entries,
            groups = groups,
            promptPositions = promptPositions,
            activeVersionId = activeVersionId,
            versions = versions,
            listAllExpanded = allEntriesExpanded,
            expandedGroupIds = expandedGroupIds.toList(),
        )
    }

    fun syncActiveVersion(
        nextEntries: List<SettingLibraryEntry> = entries,
        nextGroups: List<SettingLibraryGroup> = groups,
        nextPromptPositions: List<SettingLibraryPromptPosition> = promptPositions,
    ) {
        versions = versions.map { version ->
            if (version.id == activeVersionId) {
                version.copy(
                    name = libraryName,
                    entries = nextEntries,
                    groups = nextGroups,
                    promptPositions = nextPromptPositions,
                    listAllExpanded = allEntriesExpanded,
                    expandedGroupIds = expandedGroupIds.toList(),
                )
            } else {
                version
            }
        }
    }

    fun update(next: List<SettingLibraryEntry>) {
        entries = next
        ensureSelectedTreeNode(nextEntries = next)
        syncActiveVersion(next)
        dirty = true
    }

    fun updateGroups(next: List<SettingLibraryGroup>) {
        val normalized = normalizeSettingLibraryTree(entries = entries, groups = next)
        groups = normalized.groups
        entries = normalized.entries
        expandedGroupIds = expandedGroupIds.intersect(normalized.groups.mapTo(mutableSetOf()) { it.id })
        ensureSelectedTreeNode(nextEntries = normalized.entries, nextGroups = normalized.groups)
        syncActiveVersion(normalized.entries, normalized.groups)
        dirty = true
    }

    fun updatePromptPositions(next: List<SettingLibraryPromptPosition>) {
        val normalized = next.distinctBy { it.id }.mapIndexed { index, position ->
            position.copy(order = index + 1)
        }
        val anchors = normalized.associate { it.id to it.anchor }
        val nextEntries = entries.map { entry ->
            when {
                entry.promptPositionId.isBlank() -> entry
                entry.promptPositionId in anchors -> entry.copy(position = anchors.getValue(entry.promptPositionId))
                else -> entry.copy(promptPositionId = "")
            }
        }
        promptPositions = normalized
        entries = nextEntries
        syncActiveVersion(nextEntries, groups, normalized)
        dirty = true
    }

    fun updateTree(nextEntries: List<SettingLibraryEntry>, nextGroups: List<SettingLibraryGroup>) {
        val normalized = normalizeSettingLibraryTree(entries = nextEntries, groups = nextGroups)
        groups = normalized.groups
        entries = normalized.entries
        expandedGroupIds = expandedGroupIds.intersect(normalized.groups.mapTo(mutableSetOf()) { it.id })
        ensureSelectedTreeNode(nextEntries = normalized.entries, nextGroups = normalized.groups)
        syncActiveVersion(normalized.entries, normalized.groups)
        dirty = true
    }

    fun updateListExpansion(
        nextAllExpanded: Boolean = allEntriesExpanded,
        nextExpandedGroupIds: Set<String> = expandedGroupIds,
    ) {
        allEntriesExpanded = nextAllExpanded
        if (!nextAllExpanded) {
            allEntriesVisibleLimit = InitialAllEntriesVisibleCount
        }
        expandedGroupIds = nextExpandedGroupIds
        syncActiveVersion()
        dirty = true
    }

    fun showMoreAllEntries() {
        allEntriesVisibleLimit += AllEntriesLoadMoreCount
    }

    fun updateLibraryName(value: String) {
        libraryName = value.take(60)
        syncActiveVersion()
        dirty = true
    }

    fun switchVersion(version: SettingLibraryVersion) {
        activeVersionId = version.id
        libraryName = version.name
        entries = version.entries
        groups = version.groups
        promptPositions = version.promptPositions
        allEntriesExpanded = version.listAllExpanded
        allEntriesVisibleLimit = InitialAllEntriesVisibleCount
        expandedGroupIds = version.expandedGroupIds.toSet()
        dirty = true
    }

    fun createLibraryVersion(name: String, sourceVersionId: String?) {
        syncActiveVersion()
        val id = "draft-library-${System.currentTimeMillis()}"
        val sourceVersion = sourceVersionId?.let { sourceId ->
            versions.firstOrNull { it.id == sourceId }
        }
        val version = sourceVersion?.copy(
            id = id,
            name = name.trim().take(60),
            createdAt = "",
            updatedAt = "",
        ) ?: SettingLibraryVersion(
            id = id,
            name = name.trim().take(60),
            entries = defaultSettingLibraryFixedEntries(),
            groups = emptyList(),
            promptPositions = emptyList(),
        )
        activeVersionId = id
        libraryName = version.name
        entries = version.entries
        groups = version.groups
        promptPositions = version.promptPositions
        allEntriesExpanded = version.listAllExpanded
        allEntriesVisibleLimit = InitialAllEntriesVisibleCount
        expandedGroupIds = version.expandedGroupIds.toSet()
        versions = versions + version
        editorEntryId = null
        selectedTreeNodeId = RootNodeId
        search = ""
        dirty = true
    }

    fun deleteActiveVersion() {
        val remaining = versions.filterNot { it.id == activeVersionId }.ifEmpty {
            listOf(
                SettingLibraryVersion(
                    id = "draft-library-${System.currentTimeMillis()}",
                    name = "新版本",
                    entries = defaultSettingLibraryFixedEntries(),
                ),
            )
        }
        versions = remaining
        editorEntryId = null
        selectedTreeNodeId = RootNodeId
        search = ""
        switchVersion(remaining.first())
    }

    fun updateEntry(entryId: String, transform: (SettingLibraryEntry) -> SettingLibraryEntry) {
        val nextEntries = entries.map { entry ->
            if (entry.id != entryId) {
                entry
            } else {
                transform(entry).let { updated ->
                    when {
                        entry.isFixedEntry() -> normalizeSettingLibraryFixedEntry(updated)
                        else -> updated.copy(
                            enabled = updated.enabled && updated.hasRequiredActivationFields(),
                        )
                    }
                }
            }
        }
        update(nextEntries.map { entry ->
            if (entry.id == entryId && entry.hasOrderConflictIn(nextEntries)) {
                entry.copy(enabled = false)
            } else {
                entry
            }
        })
    }

    fun nextSettingGroupName(): String {
        val parentId = selectedFolderIdFromNodeId(selectedTreeNodeId, entries)
        val names = groups.filter { it.parentId == parentId }.map { it.name.trim() }.toSet()
        if ("新文件夹" !in names) return "新文件夹"
        var index = 2
        while ("新文件夹$index" in names) index += 1
        return "新文件夹$index"
    }

    fun addGroup(name: String) {
        val normalizedName = name.trim().take(40)
        val parentId = selectedFolderIdFromNodeId(selectedTreeNodeId, entries)
        if (normalizedName.isBlank() || groups.any { it.parentId == parentId && it.name.trim() == normalizedName }) return
        val id = "group-${System.currentTimeMillis()}"
        val treeViewOrder = nextTreeViewOrder(parentId)
        val nextGroup = SettingLibraryGroup(
            id = id,
            name = normalizedName,
            parentId = parentId,
            order = groups.size + 1,
            treeViewOrder = treeViewOrder,
        )
        if (parentId.isNotBlank()) {
            expandedGroupIds = expandedGroupIds + parentId
        }
        val nextGroups = groups.sortedBy { it.order } + nextGroup
        updateGroups(nextGroups)
        syncActiveVersion()
        dirty = true
    }

    fun requestAddGroup() {
        createGroupName = if (groups.isEmpty() && entries.none { !it.isFixedEntry() }) {
            ""
        } else {
            nextSettingGroupName()
        }
        createGroupNameDialogOpen = true
    }

    fun addEntry(
        targetGroupId: String = "",
        triggerMode: SettingLibraryTriggerMode = SettingLibraryTriggerMode.AgentTool,
    ) {
        val id = "draft-${System.currentTimeMillis()}"
        val normalizedGroupId = targetGroupId.takeIf { groupId -> groups.any { it.id == groupId } }.orEmpty()
        val treeViewOrder = nextTreeViewOrder(normalizedGroupId)
        val next = createSettingLibraryEntryDraft(
            id = id,
            groupId = normalizedGroupId,
            triggerMode = triggerMode,
            existingEntries = entries,
            treeViewOrder = treeViewOrder,
        )
        search = ""
        if (normalizedGroupId.isNotBlank()) {
            expandedGroupIds = expandedGroupIds + normalizedGroupId
        }
        update(entries + next)
        selectedTreeNodeId = fileNodeId(id)
        editorEntryId = id
    }

    fun addEjsReference(targetGroupId: String = selectedFolderIdFromNodeId(selectedTreeNodeId, entries)) {
        val normalizedGroupId = targetGroupId.takeIf { groupId -> groups.any { it.id == groupId } }.orEmpty()
        val id = "reference-${System.currentTimeMillis()}"
        val treeViewOrder = nextTreeViewOrder(normalizedGroupId)
        val next = createSettingLibraryEntryDraft(
            id = id,
            groupId = normalizedGroupId,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            existingEntries = entries,
            treeViewOrder = treeViewOrder,
            kind = SettingLibraryEntryDraftKind.EjsReference,
        )
        search = ""
        if (normalizedGroupId.isNotBlank()) {
            expandedGroupIds = expandedGroupIds + normalizedGroupId
        }
        update(entries + next)
        selectedTreeNodeId = fileNodeId(id)
        editorEntryId = id
    }

    internal fun nextTreeViewOrder(parentId: String): Int {
        val folderMax = groups.filter { it.parentId == parentId }.maxOfOrNull { it.treeViewOrder } ?: 0
        val entryMax = entries.filter { it.groupId == parentId }.maxOfOrNull { it.treeViewOrder } ?: 0
        return maxOf(folderMax, entryMax) + 1
    }

    fun requestAddSetting() {
        createSettingKindDialogOpen = !createSettingKindDialogOpen
    }

    fun requestAddEntry(triggerMode: SettingLibraryTriggerMode = SettingLibraryTriggerMode.AgentTool) {
        pendingCreateEntryTriggerMode = triggerMode
        val targetGroupId = selectedFolderIdFromNodeId(selectedTreeNodeId, entries)
        pendingCreateEntryTriggerMode = null
        addEntry(targetGroupId = targetGroupId, triggerMode = triggerMode)
    }

    fun selectTreeNode(nodeId: String) {
        selectedTreeNodeId = if (selectedTreeNodeId == nodeId) RootNodeId else nodeId
        createSettingKindDialogOpen = false
    }

    fun focusTreeNode(nodeId: String) {
        selectedTreeNodeId = nodeId
        createSettingKindDialogOpen = false
    }

    fun openTreeNode(node: SettingTreeNode) {
        when (node) {
            is SettingTreeNode.File -> {
                selectedTreeNodeId = node.id
                createSettingKindDialogOpen = false
                editorEntryId = node.entry.id
            }
            is SettingTreeNode.Folder -> selectTreeNode(node.id)
        }
    }

    fun selectedTreeTitle(): String = selectedSettingLibraryTreeTitle(selectedTreeNodeId, entries, groups)

    fun selectedTreeKindLabel(): String = selectedSettingLibraryTreeKindLabel(selectedTreeNodeId, entries)

    fun canDeleteSelected(): Boolean = canDeleteSettingLibraryTreeNode(selectedTreeNodeId, entries)

    fun hasTreeClipboard(): Boolean = treeClipboardController.hasClipboard()

    fun canUseTreeClipboardSource(): Boolean {
        return canDeleteSelected()
    }

    fun copySelectedTreeNode(): String? {
        return treeClipboardController.copySelected()
    }

    fun cutSelectedTreeNode(): String? {
        return treeClipboardController.cutSelected()
    }

    fun cancelTreeClipboard(): String? {
        return treeClipboardController.cancel()
    }

    fun pasteTreeClipboard(): String? {
        return treeClipboardController.paste()
    }

    fun selectedTreePath(): String = settingLibraryTreePath(selectedTreeNodeId, entries, groups)

    fun requestRenameSelected() {
        if (entries.firstOrNull { fileNodeId(it.id) == selectedTreeNodeId }?.isPinnedEntry() == true) return
        renameNodeName = selectedTreeTitle()
        renameNodeDialogOpen = true
    }

    fun renameSelected(value: String) {
        val name = value.trim().take(60)
        if (name.isBlank()) return
        when {
            selectedTreeNodeId == RootNodeId -> Unit
            selectedTreeNodeId.startsWith("folder:") -> {
                val groupId = selectedTreeNodeId.removePrefix("folder:")
                updateGroups(
                    groups.map { group ->
                        if (group.id == groupId) {
                            group.copy(name = name)
                        } else {
                            group
                        }
                    },
                )
            }
            selectedTreeNodeId.startsWith("file:") -> {
                val entryId = selectedTreeNodeId.removePrefix("file:")
                updateEntry(entryId) { it.copy(title = name) }
            }
        }
    }

    fun deleteSelected() {
        when {
            selectedTreeNodeId.startsWith("file:") -> {
                val entryId = selectedTreeNodeId.removePrefix("file:")
                deleteEntry(entryId)
            }
            selectedTreeNodeId.startsWith("folder:") -> {
                val groupId = selectedTreeNodeId.removePrefix("folder:")
                val descendantIds = descendantGroupIds(groupId, groups)
                val deleteIds = descendantIds + groupId
                update(entries.filterNot { it.groupId in deleteIds })
                updateGroups(
                    groups
                        .filterNot { it.id in deleteIds },
                )
            }
        }
        selectedTreeNodeId = RootNodeId
    }

    fun deleteEntry(entryId: String) {
        val target = entries.firstOrNull { it.id == entryId } ?: return
        if (target.isPinnedEntry()) return
        update(entries.filterNot { it.id == target.id })
    }

    fun ensureSelectedTreeNode(
        nextEntries: List<SettingLibraryEntry> = entries,
        nextGroups: List<SettingLibraryGroup> = groups,
    ) {
        if (!isValidSettingLibraryTreeNode(selectedTreeNodeId, nextEntries, nextGroups)) {
            selectedTreeNodeId = RootNodeId
        }
    }

    fun moveEntryToGroup(
        entryId: String,
        targetGroupId: String,
        targetEntryId: String? = null,
        insertAfterTarget: Boolean = false,
    ) {
        val nextEntries = moveSettingEntryToGroup(
            entries = entries,
            groups = groups,
            entryId = entryId,
            targetGroupId = targetGroupId,
            targetEntryId = targetEntryId,
            insertAfterTarget = insertAfterTarget,
        ) ?: return
        update(nextEntries)
    }

}

@Composable
internal fun rememberSettingLibraryEditorState(library: SettingLibrary?): SettingLibraryEditorState {
    return remember(library?.characterId) { SettingLibraryEditorState(library) }
}
