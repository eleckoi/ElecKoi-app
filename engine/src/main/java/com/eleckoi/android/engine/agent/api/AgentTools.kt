package com.eleckoi.android.engine.agent.api

import kotlinx.serialization.json.JsonObject

const val AgentReadSettingFilesTool = "eleckoi_read_setting_files"
const val AgentGlobSettingFilesTool = "eleckoi_glob_setting_files"
const val AgentGrepSettingFilesTool = "eleckoi_grep_setting_files"
const val AgentWriteSettingFileTool = "eleckoi_write_setting_file"
const val AgentEditSettingFileTool = "eleckoi_edit_setting_file"
const val AgentMakeSettingDirectoryTool = "eleckoi_make_setting_directory"
const val AgentMoveSettingFileTool = "eleckoi_move_setting_file"
const val AgentMoveSettingDirectoryTool = "eleckoi_move_setting_directory"
const val AgentDeleteSettingFileTool = "eleckoi_delete_setting_file"
const val AgentDeleteSettingDirectoryTool = "eleckoi_delete_setting_directory"
const val AgentApplySettingPatchTool = "eleckoi_apply_setting_patch"
val AgentSettingLibraryTools = setOf(
    AgentGlobSettingFilesTool,
    AgentGrepSettingFilesTool,
    AgentReadSettingFilesTool,
    AgentApplySettingPatchTool,
)
val AgentSettingFileMutationTools = setOf(
    AgentWriteSettingFileTool,
    AgentEditSettingFileTool,
    AgentMakeSettingDirectoryTool,
    AgentMoveSettingFileTool,
    AgentMoveSettingDirectoryTool,
    AgentDeleteSettingFileTool,
    AgentDeleteSettingDirectoryTool,
)
const val AgentGlobVariablesTool = "eleckoi_glob_variables"
const val AgentReadVariablesTool = "eleckoi_read_variables"
const val AgentGrepVariablesTool = "eleckoi_grep_variables"
const val AgentApplyVariablePatchTool = "eleckoi_apply_variable_patch"
const val AgentWebSearchTool = "eleckoi_web_search"
/**
 * Request-only bridge marker. DSH/pi-ai can describe function tools but not provider-native
 * server tools, so Android replaces this declaration at the final provider wire boundary.
 */
const val AgentNativeWebSearchBridgeTool = "eleckoi_native_web_search_bridge"
const val AgentRemoteDshTaskTool = "eleckoi_remote_dsh_task"
const val AgentUpdatePlanTool = "update_plan"
const val AgentUpdateRoleplayPlanTool = "update_roleplay_plan"
const val AgentTodoWriteTool = "todo_write"
const val AgentSubagentTool = "subagent"
const val AgentSendMessageTool = "send_message"
const val AgentListCreatorToolsetsTool = "eleckoi_list_toolsets"
const val AgentDescribeCreatorToolsetTool = "eleckoi_describe_toolset"
const val AgentCallCreatorCapabilityTool = "eleckoi_call_capability"
val AgentCreatorMetaTools = setOf(
    AgentListCreatorToolsetsTool,
    AgentDescribeCreatorToolsetTool,
    AgentCallCreatorCapabilityTool,
)

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * A request-scoped tool executed by the Android host rather than by the guest Harness runtime.
 *
 * The model only receives [definition]. [handler] remains inside the app process and is therefore
 * the authorization boundary for product data such as a character's setting library.
 */
data class AgentDynamicTool(
    val definition: AgentToolDefinition,
    val handler: AgentDynamicToolHandler,
)

fun interface AgentDynamicToolHandler {
    suspend fun execute(arguments: JsonObject): AgentDynamicToolResult
}

data class AgentDynamicToolResult(
    val content: String,
    val success: Boolean = true,
)
