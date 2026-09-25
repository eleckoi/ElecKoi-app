package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibrarySessionMutation
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibrarySessionMutationResult

/**
 * Independent setting-library tools backed by the current conversation overlay.
 *
 * The setting library is not a shell workspace: discovery, reading, and mutations use the
 * request-scoped tools that already enforce the virtual-library boundary. The ordinary local
 * workspace keeps its own native DSH shell tools.
 */
internal fun characterSettingLibraryTools(
    contextProvider: suspend () -> SettingLibraryAgentTurnContext,
    virtualFileSearch: AgentVirtualFileSearch,
    requiredCache: RequiredSettingLibraryCache = RequiredSettingLibraryCache(emptyList()),
    applyChanges: suspend (List<SettingLibrarySessionMutation>) -> SettingLibrarySessionMutationResult,
): List<AgentDynamicTool> {
    return buildList {
        add(characterSettingLibraryGlobTool(contextProvider, virtualFileSearch, requiredCache))
        add(characterSettingLibraryGrepTool(contextProvider, virtualFileSearch, requiredCache))
        add(characterSettingLibraryReadTool(contextProvider, requiredCache))
        add(characterSettingLibraryPatchTool(contextProvider, applyChanges))
    }
}
