package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import java.util.concurrent.ConcurrentHashMap

internal data class SettingLibraryEjsChanges(
    val newlyAvailablePaths: List<String> = emptyList(),
    val staleReadPaths: List<String> = emptyList(),
    val unavailableReadPaths: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = newlyAvailablePaths.isEmpty() &&
        staleReadPaths.isEmpty() && unavailableReadPaths.isEmpty()
}

/** Tracks only EJS bodies delivered by the read tool during this turn. */
internal class SettingLibraryEjsReadTracker {
    private val readBodies = ConcurrentHashMap<String, String>()

    fun record(entries: List<SettingLibraryAgentEntry>) {
        entries.filter { it.contentMode == SettingLibraryContentMode.Ejs }
            .forEach { readBodies[it.id] = it.content }
    }

    fun changesAfterVariablePatch(
        before: SettingLibraryAgentTurnContext,
        after: SettingLibraryAgentTurnContext,
    ): SettingLibraryEjsChanges {
        fun visible(context: SettingLibraryAgentTurnContext) = context.readableEntries
            .filter { it.contentMode == SettingLibraryContentMode.Ejs }
            .associateBy(SettingLibraryAgentEntry::id)

        val old = visible(before)
        val current = visible(after)
        return SettingLibraryEjsChanges(
            newlyAvailablePaths = current.values.filter { it.id !in old }.map { it.path },
            staleReadPaths = current.values.filter { entry ->
                readBodies[entry.id]?.let { it != entry.content } == true
            }.map { it.path },
            unavailableReadPaths = old.values.filter { entry ->
                entry.id !in current && readBodies.containsKey(entry.id)
            }.map { it.path },
        )
    }
}
