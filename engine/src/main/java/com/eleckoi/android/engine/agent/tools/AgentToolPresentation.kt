package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentApplySettingPatchTool
import com.eleckoi.android.engine.agent.api.AgentSettingFileMutationTools
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGlobVariablesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.agent.api.AgentRemoteDshTaskTool
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWebSearchTool
import com.eleckoi.android.engine.agent.api.AgentListCreatorToolsetsTool
import com.eleckoi.android.engine.agent.api.AgentDescribeCreatorToolsetTool
import com.eleckoi.android.engine.agent.api.AgentCallCreatorCapabilityTool

/** Builds localized metadata independently from request filtering and wire classification. */
internal fun agentToolMember(name: String, description: String = ""): AgentToolMember =
    AgentToolMember(
        name = name,
        displayName = displayName(name),
        description = displayDescription(name)
            .ifBlank { description.normalizedDescription() },
    )

private fun displayName(name: String): String = when (name) {
    "shell_command", "exec_command" -> "执行命令"
    "bash" -> "Bash"
    "read" -> "读取文件"
    "edit" -> "编辑文件"
    "write" -> "写入文件"
    "write_stdin" -> "继续输入命令"
    "apply_patch" -> "修改文件"
    "read_image" -> "读取图片"
    "update_plan" -> "更新任务计划"
    "todo_write" -> "更新任务清单"
    AgentUpdateRoleplayPlanTool -> "更新角色扮演计划"
    "request_user_input" -> "询问用户"
    "spawn_agent" -> "创建子任务"
    "subagent" -> "创建子 Agent"
    "send_input" -> "发送子任务消息"
    "resume_agent" -> "恢复子任务"
    "wait_agent" -> "等待子任务"
    "close_agent" -> "关闭子任务"
    "list_mcp_resources" -> "列出 MCP 资源"
    "list_mcp_resource_templates" -> "列出 MCP 资源模板"
    "read_mcp_resource" -> "读取 MCP 资源"
    "request_plugin_install" -> "请求安装插件"
    AgentWebSearchTool -> "联网搜索"
    AgentRemoteDshTaskTool -> "交给电脑 DSH"
    "web_search" -> "网页搜索"
    "web_search_exa" -> "网页搜索"
    "web_fetch_exa" -> "网页抓取"
    "web_search_advanced_exa" -> "高级网页搜索"
    AgentReadSettingFilesTool -> "读取设定正文"
    AgentGlobSettingFilesTool -> "查找设定文件"
    AgentGrepSettingFilesTool -> "搜索设定内容"
    in AgentSettingFileMutationTools -> "管理对话设定文件"
    AgentApplySettingPatchTool -> "管理对话设定文件"
    AgentGlobVariablesTool -> "查找变量"
    AgentGrepVariablesTool -> "搜索变量内容"
    AgentReadVariablesTool -> "读取变量"
    AgentApplyVariablePatchTool -> "修改变量"
    AgentListCreatorToolsetsTool -> "列出创作能力组"
    AgentDescribeCreatorToolsetTool -> "查看创作能力组"
    AgentCallCreatorCapabilityTool -> "调用创作能力"
    else -> name
}

private fun displayDescription(name: String): String = when (name) {
    AgentGlobSettingFilesTool ->
        "使用 Glob 路径模式查找虚拟设定文件，不读取正文。"
    AgentReadSettingFilesTool ->
        "读取 Glob 或 Grep 返回的虚拟设定文件正文，不会修改设定。"
    AgentGrepSettingFilesTool ->
        "使用 ripgrep 正则搜索虚拟设定文件的标题、作者注释和正文。"
    in AgentSettingFileMutationTools ->
        "用 write、move、delete 等文件操作管理当前对话设定；写入时自动创建父目录。"
    AgentApplySettingPatchTool ->
        "用一个结构化操作管理当前对话设定；支持写入、编辑、建目录、移动和删除。"
    AgentGlobVariablesTool ->
        "使用 Glob 路径模式查找变量，不读取完整值和规则。"
    AgentGrepVariablesTool ->
        "使用 ripgrep 正则搜索变量路径、值、说明和作者更新规则。"
    AgentReadVariablesTool ->
        "读取指定变量的当前值、默认值、说明和作者更新规则。"
    AgentApplyVariablePatchTool ->
        "修改已读取的变量；补丁通过校验后才会暂存并随本回合提交。"
    AgentListCreatorToolsetsTool ->
        "列出当前创作工作区可以按需使用的能力组。"
    AgentDescribeCreatorToolsetTool ->
        "查看一个能力组中的操作，并只加载选中操作的输入结构。"
    AgentCallCreatorCapabilityTool ->
        "调用一个已发现的创作操作；宿主继续检查角色根、权限和数据版本。"
    AgentRemoteDshTaskTool ->
        "让电脑上的 DSH 执行一项任务；结果会返回当前角色，由当前角色继续回复。"
    else -> ""
}

internal fun String.normalizedDescription(): String =
    replace(Regex("\\s+"), " ")
        .trim()
        .take(MaxDescriptionChars)

private const val MaxDescriptionChars = 240
