package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningEntryId

internal fun SettingLibraryEntry.matchesSettingLibrarySearch(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        content.contains(query, ignoreCase = true) ||
        keywords.any { it.contains(query, ignoreCase = true) }
}

internal fun allViewOrder(entry: SettingLibraryEntry, rawIndex: Int): Int {
    return entry.viewOrder.takeIf { it > 0 } ?: (rawIndex + 1)
}

internal fun groupViewOrder(entry: SettingLibraryEntry, rawIndex: Int): Int {
    return entry.groupViewOrder.takeIf { it > 0 } ?: (rawIndex + 1)
}

internal fun allVisibleEntries(entries: List<SettingLibraryEntry>, search: String): List<SettingLibraryEntry> {
    val query = search.trim()
    return entries
        .withIndex()
        .filter { (_, entry) -> entry.matchesSettingLibrarySearch(query) }
        .sortedWith(
            compareBy<IndexedValue<SettingLibraryEntry>> { (_, entry) -> fixedEntryRank(entry) }
                .thenByDescending { (index, entry) -> allViewOrder(entry, index) },
        )
        .map { it.value }
}

internal fun groupVisibleEntries(entries: List<SettingLibraryEntry>, groupId: String, search: String): List<SettingLibraryEntry> {
    val query = search.trim()
    return entries
        .withIndex()
        .filter { (_, entry) -> entry.groupId == groupId && entry.matchesSettingLibrarySearch(query) }
        .sortedWith(
            compareBy<IndexedValue<SettingLibraryEntry>> { (_, entry) -> fixedEntryRank(entry) }
                .thenByDescending { (index, entry) -> groupViewOrder(entry, index) },
        )
        .map { it.value }
}

internal fun viewOrderMap(displayEntries: List<SettingLibraryEntry>): Map<String, Int> {
    val size = displayEntries.size
    return displayEntries.mapIndexed { index, entry -> entry.id to (size - index) }.toMap()
}

internal fun initialAllEntriesExpanded(library: SettingLibrary?): Boolean {
    if (library == null) return true
    return library.listAllExpanded
}

private fun fixedEntryRank(entry: SettingLibraryEntry): Int = when (entry.id) {
    SettingLibraryOpeningEntryId -> 0
    else -> 1
}
