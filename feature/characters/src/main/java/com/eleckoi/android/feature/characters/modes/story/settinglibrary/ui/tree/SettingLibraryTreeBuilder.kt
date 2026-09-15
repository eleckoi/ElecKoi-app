package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningEntryId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup

internal fun settingTreeNodes(
    groups: List<SettingLibraryGroup>,
    entries: List<SettingLibraryEntry>,
    expandedGroupIds: Set<String>,
    forceCollapsedGroupIds: Set<String> = emptySet(),
    search: String,
): List<SettingTreeNode> {
    val query = search.trim()
    val visibleGroups = groups.sortedBy { it.order }
    val visibleEntries = entries
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SettingLibraryEntry>> { (index, entry) -> groupViewOrder(entry, index) }
                .thenByDescending { (index, entry) -> allViewOrder(entry, index) },
        )
        .map { it.value }
    val childrenByParent = visibleGroups.groupBy { it.parentId }
    val entriesByGroup = visibleEntries.groupBy { it.groupId }
    val nodes = mutableListOf<SettingTreeNode>()

    fun groupMatches(group: SettingLibraryGroup): Boolean {
        if (query.isBlank()) return true
        if (group.name.contains(query, ignoreCase = true)) return true
        if (entriesByGroup[group.id].orEmpty().any { it.matchesSettingLibrarySearch(query) }) return true
        return childrenByParent[group.id].orEmpty().any(::groupMatches)
    }

    fun visibleEntry(entry: SettingLibraryEntry): Boolean {
        return query.isBlank() || entry.matchesSettingLibrarySearch(query)
    }

    fun directCount(groupId: String): Int {
        return childrenByParent[groupId].orEmpty().size + entriesByGroup[groupId].orEmpty().size
    }

    fun childNodes(parentId: String, depth: Int): List<SettingTreeNode> {
        val folders = childrenByParent[parentId].orEmpty()
            .filter(::groupMatches)
            .map { group -> SettingTreeNode.Folder(group = group, depth = depth, count = directCount(group.id)) }
        val files = entriesByGroup[parentId].orEmpty()
            .filter(::visibleEntry)
            .map { entry -> SettingTreeNode.File(entry = entry, depth = depth) }
        val children = folders + files
        return children.sortedWith(
            compareBy<SettingTreeNode> { node -> node.treeViewOrder }
                .thenBy { node -> if (node is SettingTreeNode.Folder) 0 else 1 },
        )
    }

    fun addFolder(group: SettingLibraryGroup, depth: Int) {
        if (!groupMatches(group)) return
        val expanded = (query.isNotBlank() || group.id in expandedGroupIds) && group.id !in forceCollapsedGroupIds
        nodes += SettingTreeNode.Folder(group = group, depth = depth, count = directCount(group.id))
        if (!expanded) return
        childNodes(group.id, depth + 1).forEach { child ->
            when (child) {
                is SettingTreeNode.Folder -> addFolder(child.group, depth + 1)
                is SettingTreeNode.File -> nodes += child
            }
        }
    }

    childNodes("", 0).forEach { child ->
        when (child) {
            is SettingTreeNode.Folder -> addFolder(child.group, 0)
            is SettingTreeNode.File -> nodes += child
        }
    }
    return nodes
}

private val SettingTreeNode.treeViewOrder: Int
    get() = when (this) {
        is SettingTreeNode.Folder -> group.treeViewOrder
        is SettingTreeNode.File -> when (entry.id) {
            SettingLibraryOpeningEntryId -> Int.MIN_VALUE
            else -> entry.treeViewOrder
        }
    }
