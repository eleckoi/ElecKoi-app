package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry

internal fun selectedSettingLibraryTreeTitle(
    nodeId: String,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
): String = when {
    nodeId == RootNodeId -> "未选择"
    nodeId.startsWith("folder:") -> {
        val groupId = nodeId.removePrefix("folder:")
        groups.firstOrNull { it.id == groupId }?.name?.ifBlank { "未命名文件夹" } ?: "设定库"
    }
    nodeId.startsWith("file:") -> {
        val entryId = nodeId.removePrefix("file:")
        entries.firstOrNull { it.id == entryId }?.title?.ifBlank { "未命名设定" } ?: "未命名设定"
    }
    else -> "设定库"
}

internal fun selectedSettingLibraryTreeKindLabel(
    nodeId: String,
    entries: List<SettingLibraryEntry>,
): String = when {
    nodeId == RootNodeId || nodeId.startsWith("folder:") -> "文件夹"
    nodeId.startsWith("file:") -> when (
        entries.firstOrNull { fileNodeId(it.id) == nodeId }?.dynamicMode
    ) {
        SettingLibraryDynamicMode.EjsController -> "EJS 控制器"
        SettingLibraryDynamicMode.EjsReference -> "EJS引用设定"
        else -> "设定"
    }
    else -> "文件夹"
}

internal fun canDeleteSettingLibraryTreeNode(
    nodeId: String,
    entries: List<SettingLibraryEntry>,
): Boolean = when {
    nodeId == RootNodeId -> false
    nodeId.startsWith("file:") -> entries.firstOrNull { fileNodeId(it.id) == nodeId }
        ?.let { !it.isPinnedEntry() } == true
    else -> nodeId.startsWith("folder:")
}

internal fun settingLibraryTreePath(
    nodeId: String,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
): String {
    val groupsById = groups.associateBy { it.id }
    val selectedGroupId = when {
        nodeId.startsWith("folder:") -> nodeId.removePrefix("folder:")
        nodeId.startsWith("file:") -> entries.firstOrNull { fileNodeId(it.id) == nodeId }?.groupId.orEmpty()
        else -> ""
    }
    val chain = buildList {
        val visited = mutableSetOf<String>()
        var current = groupsById[selectedGroupId]
        while (current != null && visited.add(current.id)) {
            add(current)
            current = groupsById[current.parentId]
        }
    }.asReversed()
    val parts = chain.map { it.name.ifBlank { "未命名文件夹" } }.toMutableList()
    if (nodeId.startsWith("file:")) {
        parts += selectedSettingLibraryTreeTitle(nodeId, entries, groups)
    }
    return parts.joinToString(" / ").ifBlank { "未选择文件夹" }
}

internal fun isValidSettingLibraryTreeNode(
    nodeId: String,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
): Boolean = when {
    nodeId == RootNodeId -> true
    nodeId.startsWith("folder:") -> groups.any { folderNodeId(it.id) == nodeId }
    nodeId.startsWith("file:") -> entries.any { fileNodeId(it.id) == nodeId }
    else -> false
}
