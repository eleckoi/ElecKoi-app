package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService

internal fun characterVariableTools(
    config: VariableConfig,
    turnState: CharacterVariableTurnState,
    runtime: VariableRuntimeService,
    virtualFileSearch: AgentVirtualFileSearch,
    settingChangesForPatch: (suspend (String, String) -> SettingLibraryEjsChanges)? = null,
): List<AgentDynamicTool> = characterVariableTools(
    config = config,
    turnState = turnState,
    validateState = runtime::validateState,
    virtualFileSearch = virtualFileSearch,
    settingChangesForPatch = settingChangesForPatch,
)

internal fun characterVariableTools(
    config: VariableConfig,
    turnState: CharacterVariableTurnState,
    validateState: suspend (schemaCode: String, stateJson: String) -> VariableRuntimeCheckResult,
    virtualFileSearch: AgentVirtualFileSearch,
    settingChangesForPatch: (suspend (String, String) -> SettingLibraryEjsChanges)? = null,
): List<AgentDynamicTool> {
    val catalogProvider = { characterVariableCatalog(config, turnState.stateJson) }
    return listOf(
        characterVariableGlobTool(catalogProvider, virtualFileSearch),
        characterVariableGrepTool(config, catalogProvider, turnState, virtualFileSearch),
        characterVariableReadTool(config, catalogProvider, turnState),
        characterVariablePatchTool(config, turnState, validateState, settingChangesForPatch),
    )
}
