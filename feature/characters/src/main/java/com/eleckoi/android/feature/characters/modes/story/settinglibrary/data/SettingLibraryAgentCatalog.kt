package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry

internal data class SettingLibraryAgentCatalogItem(
    val id: String,
    /** Logical group/title breadcrumb for author preview only; never a filesystem path. */
    val path: String,
    val selectionHint: String = "",
    val readStrategy: SettingLibraryAgentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
    val contentMode: SettingLibraryContentMode = SettingLibraryContentMode.PlainText,
    /** Author-defined order from the root group down to this entry. */
    val treeOrderPath: List<Int> = emptyList(),
)

internal fun settingLibraryAgentCatalogPreview(
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
): List<SettingLibraryAgentCatalogItem> {
    val groupsById = groups.associateBy(SettingLibraryGroup::id)
    return entries.asSequence()
        .filterNot(SettingLibraryEntry::isFixedEntry)
        .filter { entry ->
                entry.enabled &&
                entry.triggerMode == SettingLibraryTriggerMode.AgentTool &&
                entry.dynamicMode != SettingLibraryDynamicMode.EjsReference
        }
        .map { entry ->
            val folder = settingLibraryGroupPath(groupsById[entry.groupId], groupsById)
            val entryName = settingLibrarySafePathSegment(entry.title.ifBlank { "未命名设定" })
            SettingLibraryAgentCatalogItem(
                id = entry.id,
                path = listOf(folder, entryName).filter(String::isNotBlank).joinToString("/"),
                selectionHint = if (entry.agentReadStrategy == SettingLibraryAgentReadStrategy.Normal &&
                    entry.contentMode == SettingLibraryContentMode.PlainText
                ) entry.agentSelectionHint else "",
                readStrategy = entry.agentReadStrategy,
                contentMode = entry.contentMode,
                treeOrderPath = settingLibraryTreeOrderPath(entry, groupsById),
            )
        }
        .distinctBy(SettingLibraryAgentCatalogItem::id)
        .sortedWith(settingLibraryTreeOrderComparator(SettingLibraryAgentCatalogItem::treeOrderPath))
        .toList()
}

internal fun settingLibraryTreeOrderPath(
    entry: SettingLibraryEntry,
    groups: Map<String, SettingLibraryGroup>,
): List<Int> {
    val orders = ArrayDeque<Int>()
    val visited = mutableSetOf<String>()
    var current = groups[entry.groupId]
    while (current != null && current.id !in visited && orders.size < 12) {
        visited += current.id
        orders.addFirst(current.treeViewOrder)
        current = groups[current.parentId]
    }
    orders.addLast(entry.treeViewOrder)
    return orders.toList()
}

internal fun <T> settingLibraryTreeOrderComparator(
    orderPath: (T) -> List<Int>,
): Comparator<T> = Comparator { left, right ->
    compareSettingLibraryTreeOrderPaths(orderPath(left), orderPath(right))
}

private fun compareSettingLibraryTreeOrderPaths(left: List<Int>, right: List<Int>): Int {
    val sharedSize = minOf(left.size, right.size)
    repeat(sharedSize) { index ->
        val comparison = left[index].compareTo(right[index])
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

internal fun renderSettingLibraryAgentCatalogPreviewTree(
    items: List<SettingLibraryAgentCatalogItem>,
): String {
    if (items.isEmpty()) return "(暂无已启用的 Agent 读取条目)"
    val root = settingDirectoryTree(items)
    val lines = mutableListOf<String>()
    appendDirectoryPreviewTree(root, prefix = "", output = lines)
    return lines.joinToString("\n").take(MaxSettingLibraryCatalogCharacters)
}

internal fun settingLibraryGroupPath(
    group: SettingLibraryGroup?,
    groups: Map<String, SettingLibraryGroup>,
): String {
    val names = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    var current = group
    while (current != null && current.id !in visited && names.size < 12) {
        val node = current
        visited += node.id
        val segment = settingLibrarySafePathSegment(node.name.ifBlank { "未命名分组" })
        val duplicate = groups.values.count { sibling ->
            sibling.parentId == node.parentId &&
                settingLibrarySafePathSegment(sibling.name) == segment
        } > 1
        names.addFirst(if (duplicate) "$segment-${stableSettingLibraryCatalogId(node.id).take(8)}" else segment)
        current = groups[node.parentId]
    }
    return names.joinToString("/")
}

fun settingLibrarySafePathSegment(value: String): String {
    return value.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\u0000-\\u001F]"), "")
        .trim('.', ' ')
        .take(72)
        .ifBlank { "未命名" }
}

private fun appendDirectoryPreviewTree(
    node: SettingDirectoryNode,
    prefix: String,
    output: MutableList<String>,
) {
    val children = node.children()
    children.forEachIndexed { index, child ->
        val last = index == children.lastIndex
        val connector = if (last) "└─ " else "├─ "
        when (child) {
            is SettingTreeChild.Directory -> {
                output += "$prefix$connector${child.name}/"
                appendDirectoryPreviewTree(
                    node = child.node,
                    prefix = prefix + if (last) "   " else "│  ",
                    output = output,
                )
            }

            is SettingTreeChild.File -> {
                val strategy = when (child.item.readStrategy) {
                    SettingLibraryAgentReadStrategy.Required -> " [必读]"
                    SettingLibraryAgentReadStrategy.Keyword -> if (child.item.contentMode == SettingLibraryContentMode.Ejs) {
                        " [关键词 + EJS]"
                    } else " [关键词]"
                    SettingLibraryAgentReadStrategy.Normal -> if (child.item.contentMode == SettingLibraryContentMode.Ejs) {
                        " [EJS 条件]"
                    } else " [选读]"
                }
                val hint = child.item.normalizedSelectionHint()
                    .takeIf(String::isNotBlank)
                    ?.let { " # \"" + it + "\"" }
                    .orEmpty()
                output += "$prefix$connector${child.item.displayName()}$strategy$hint"
            }
        }
    }
}

private fun SettingDirectoryNode.children(): List<SettingTreeChild> = buildList {
    directories.forEach { (name, directory) ->
        add(SettingTreeChild.Directory(name, directory))
    }
    files.forEach { item ->
        add(SettingTreeChild.File(item))
    }
}.sortedWith(
    compareBy<SettingTreeChild> { child -> child.treeViewOrder }
        .thenBy { child -> if (child is SettingTreeChild.Directory) 0 else 1 },
)

private fun SettingLibraryAgentCatalogItem.normalizedSelectionHint(): String {
    return selectionHint
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MaxSettingLibrarySelectionHintCharacters)
}

private fun SettingLibraryAgentCatalogItem.displayName(): String = path.substringAfterLast('/')

private fun settingDirectoryTree(
    items: List<SettingLibraryAgentCatalogItem>,
): SettingDirectoryNode {
    val root = SettingDirectoryNode()
    items.forEach { item ->
        val segments = item.path.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return@forEach
        var directory = root
        segments.dropLast(1).forEachIndexed { index, name ->
            directory = directory.directories.getOrPut(name) {
                SettingDirectoryNode(
                    treeViewOrder = item.treeOrderPath.getOrNull(index) ?: Int.MAX_VALUE,
                )
            }
        }
        directory.files += item
    }
    return root
}

private class SettingDirectoryNode(
    val treeViewOrder: Int = Int.MAX_VALUE,
) {
    val directories = linkedMapOf<String, SettingDirectoryNode>()
    val files = mutableListOf<SettingLibraryAgentCatalogItem>()
}

private sealed interface SettingTreeChild {
    data class Directory(
        val name: String,
        val node: SettingDirectoryNode,
    ) : SettingTreeChild

    data class File(val item: SettingLibraryAgentCatalogItem) : SettingTreeChild
}

private val SettingTreeChild.treeViewOrder: Int
    get() = when (this) {
        is SettingTreeChild.Directory -> node.treeViewOrder
        is SettingTreeChild.File -> item.treeOrderPath.lastOrNull() ?: Int.MAX_VALUE
    }

private fun stableSettingLibraryCatalogId(value: String): String {
    return java.util.UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
}

internal const val MaxSettingLibrarySelectionHintCharacters = 200
private const val MaxSettingLibraryCatalogCharacters = 24_000
