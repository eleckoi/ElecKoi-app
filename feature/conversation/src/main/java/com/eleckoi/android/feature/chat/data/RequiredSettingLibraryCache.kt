package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy

/** Frozen required bodies for one turn. The read tool may cite only this exact snapshot. */
internal class RequiredSettingLibraryCache(
    entries: List<SettingLibraryAgentEntry>,
    renderContent: (String) -> String = { it },
) {
    val entries: List<RequiredSettingLibraryCacheEntry> = entries.asSequence()
        .filter { it.readStrategy == SettingLibraryAgentReadStrategy.Required && it.content.isNotBlank() }
        .distinctBy(SettingLibraryAgentEntry::id)
        .sortedWith(Comparator { left, right ->
            val orders = left.treeOrderPath.zip(right.treeOrderPath)
                .firstOrNull { (a, b) -> a != b }
                ?.let { (a, b) -> a.compareTo(b) }
                ?: left.treeOrderPath.size.compareTo(right.treeOrderPath.size)
            if (orders != 0) orders else left.id.compareTo(right.id)
        })
        .mapIndexed { index, entry ->
            RequiredSettingLibraryCacheEntry(
                id = entry.id,
                path = entry.path,
                title = entry.title.trim().ifBlank { "未命名设定" },
                content = entry.content,
                renderedContent = renderContent(entry.content),
                reference = "#S${(index + 1).toString().padStart(2, '0')}",
            )
        }
        .toList()

    private val byId = this.entries.associateBy(RequiredSettingLibraryCacheEntry::id)
    private val injectionIds = this.entries.mapTo(hashSetOf(), RequiredSettingLibraryCacheEntry::injectionId)

    fun ownsInjection(id: String): Boolean = id in injectionIds

    fun referenceFor(entry: SettingLibraryAgentEntry): RequiredSettingLibraryCacheEntry? =
        byId[entry.id]?.takeIf { frozen ->
            entry.readStrategy == SettingLibraryAgentReadStrategy.Required &&
                entry.path == frozen.path &&
                entry.title.trim().ifBlank { "未命名设定" } == frozen.title &&
                entry.content == frozen.content
        }
}

internal data class RequiredSettingLibraryCacheEntry(
    val id: String,
    val path: String,
    val title: String,
    val content: String,
    val renderedContent: String,
    val reference: String,
) {
    val injectionId: String get() = "required-setting-${reference.drop(1)}"
    val prompt: String get() = "[Setting $reference: $title]\n$renderedContent"
    val readReceipt: String get() = "已读取：$reference「$title」；正文见本轮前置的 [Setting $reference: $title]。"
}
