package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup

internal const val RootNodeId = "root"

internal sealed interface SettingTreeNode {
    val id: String
    val title: String
    val depth: Int

    data class Folder(
        val group: SettingLibraryGroup,
        override val depth: Int,
        val count: Int,
    ) : SettingTreeNode {
        override val id: String = folderNodeId(group.id)
        override val title: String = group.name.ifBlank { "未命名文件夹" }
    }

    data class File(
        val entry: SettingLibraryEntry,
        override val depth: Int,
    ) : SettingTreeNode {
        override val id: String = fileNodeId(entry.id)
        override val title: String = entry.title.ifBlank { "未命名设定" }
    }
}

internal val SettingTreeNode.File.isEjsReference: Boolean
    get() = entry.dynamicMode == SettingLibraryDynamicMode.EjsReference

internal fun folderNodeId(groupId: String): String = "folder:$groupId"

internal fun fileNodeId(entryId: String): String = "file:$entryId"

internal fun selectedFolderIdFromNodeId(nodeId: String, entries: List<SettingLibraryEntry>): String {
    return when {
        nodeId.startsWith("folder:") -> nodeId.removePrefix("folder:")
        nodeId.startsWith("file:") -> entries.firstOrNull { fileNodeId(it.id) == nodeId }?.groupId.orEmpty()
        else -> ""
    }
}

internal fun descendantGroupIds(groupId: String, groups: List<SettingLibraryGroup>): Set<String> {
    val childrenByParent = groups.groupBy { it.parentId }
    val result = mutableSetOf<String>()
    fun collect(parentId: String) {
        childrenByParent[parentId].orEmpty().forEach { child ->
            if (result.add(child.id)) {
                collect(child.id)
            }
        }
    }
    collect(groupId)
    return result
}
