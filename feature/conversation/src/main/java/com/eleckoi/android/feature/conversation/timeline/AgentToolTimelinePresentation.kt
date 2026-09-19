package com.eleckoi.android.feature.conversation.timeline

import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentApplySettingPatchTool
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGlobVariablesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.agent.api.AgentRemoteDshTaskTool
import com.eleckoi.android.engine.agent.api.AgentSettingFileMutationTools
import com.eleckoi.android.engine.agent.api.AgentSendMessageTool
import com.eleckoi.android.engine.agent.api.AgentSubagentTool
import com.eleckoi.android.engine.agent.api.AgentTodoWriteTool
import com.eleckoi.android.engine.agent.api.AgentUpdatePlanTool
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

private val SubagentWhitespace = Regex("\\s+")

fun CreationTimelineItem.agentToolTimelinePresentation():
    AgentToolTimelinePresentation? {
    if (toolName == AgentSubagentTool) {
        val delegation = subagentToolPresentation() ?: return null
        val title = when {
            failed -> "子 Agent 运行失败"
            running -> "子 Agent 正在处理"
            else -> "子 Agent 已完成"
        }
        val target = buildList {
            add(delegation.description)
            delegatedModel.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" · ")
        return AgentToolTimelinePresentation(title = title, target = target)
    }
    if (toolName == AgentRemoteDshTaskTool) {
        val task = toolArguments.jsonObjectOrNull()?.string("task").orEmpty().trim()
        return AgentToolTimelinePresentation(
            title = when {
                failed -> "电脑 DSH 执行失败"
                running -> "电脑 DSH 正在处理"
                else -> "电脑 DSH 已完成"
            },
            target = task.take(96).ifBlank { "远端电脑任务" },
        )
    }
    val action = when (toolName) {
        AgentGlobSettingFilesTool -> "查找设定文件"
        AgentGrepSettingFilesTool -> "搜索设定内容"
        AgentReadSettingFilesTool -> "读取设定正文"
        AgentGlobVariablesTool -> "查找变量"
        AgentGrepVariablesTool -> "搜索变量内容"
        AgentReadVariablesTool -> "读取变量"
        AgentApplyVariablePatchTool -> "修改变量"
        AgentApplySettingPatchTool -> "修改对话设定"
        in AgentSettingFileMutationTools -> "修改对话设定"
        AgentUpdatePlanTool -> "更新任务计划"
        AgentUpdateRoleplayPlanTool -> "更新角色扮演计划"
        AgentTodoWriteTool -> "更新任务计划"
        else -> return null
    }
    val title = when {
        failed -> "${action}失败"
        running -> "正在$action"
        else -> "已$action"
    }
    val argumentPayload = toolArguments.jsonObjectOrNull()
    val resultPayload = detail.jsonObjectOrNull()
    val payload = argumentPayload ?: resultPayload
    val target = when (toolName) {
        AgentGlobSettingFilesTool -> argumentPayload.patternTargetFromArguments()
            ?: resultPayload.patternTarget("设定库")
        AgentReadSettingFilesTool -> parseSettingEntryToolResult(detail)
            .map(SettingEntryToolResult::title)
            .filter(String::isNotBlank)
            .ifEmpty { argumentPayload.pathsFromStrings("paths") }
            .ifEmpty { payload.pathsFromStrings("paths") }
            .summarizedPaths("角色设定")
        AgentGrepSettingFilesTool -> argumentPayload.settingSearchTargetFromArguments()
            ?: resultPayload.patternTarget("设定库")
        AgentGlobVariablesTool -> argumentPayload.patternTargetFromArguments()
            ?: resultPayload.patternTarget("剧情变量")
        AgentGrepVariablesTool -> argumentPayload.patternTargetFromArguments()
            ?: resultPayload.patternTarget("剧情变量")
        AgentReadVariablesTool -> payload
            .let {
                argumentPayload.pathsFromStrings("paths")
                    .ifEmpty { resultPayload.pathsFromObjects("variables") }
            }
            .ifEmpty { payload.pathsFromStrings("paths") }
            .summarizedPaths("剧情变量")
        AgentApplyVariablePatchTool -> argumentPayload
            .pathsFromOperations()
            .ifEmpty { resultPayload.pathsFromStrings("paths") }
            .summarizedPaths(
                fallback = resultPayload
                    ?.primitive("applied_operations")
                    ?.contentOrNull
                    ?.let { "$it 项变更" }
                    ?: "剧情变量",
            )
        AgentApplySettingPatchTool -> buildList {
            payload?.string("path")?.takeIf(String::isNotBlank)?.let(::add)
            payload?.string("destination")?.takeIf(String::isNotBlank)?.let(::add)
        }.summarizedPaths("当前对话设定")
        in AgentSettingFileMutationTools -> payload?.string("destination")
            ?: payload?.string("path")
            ?: "当前对话设定"
        AgentUpdatePlanTool,
        AgentUpdateRoleplayPlanTool,
        AgentTodoWriteTool,
        -> itemPlanTarget()
        else -> ""
    }
    return AgentToolTimelinePresentation(title = title, target = target)
}

/**
 * A subagent tool completes as soon as the delegated session starts, so its direct tool result is
 * only a launch receipt. The child reply is already visible in the nested timeline; a separate
 * return block is needed only when the text relayed to the parent differs from that visible reply.
 */
fun CreationTimelineItem.subagentReturnResult(): String {
    if (toolName != AgentSubagentTool) return ""
    val relayedResult = childTimeline.asReversed().firstNotNullOfOrNull { child ->
        child.takeIf { it.toolName == AgentSendMessageTool }
            ?.toolArguments
            ?.jsonObjectOrNull()
            ?.string("message")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }.orEmpty()
    val childIsRunning = childTimeline.any(CreationTimelineItem::running)
    val finalReply = childTimeline
        .toCreationTurns(isRunning = childIsRunning)
        .lastOrNull()
        ?.finalAnswer
        ?.text
        .orEmpty()
        .trim()
    if (
        relayedResult.isNotBlank() &&
        relayedResult.normalizedSubagentResult() != finalReply.normalizedSubagentResult()
    ) {
        return relayedResult
    }
    return detail.trim().takeIf { failed }.orEmpty()
}

private fun String.normalizedSubagentResult(): String = trim().replace(SubagentWhitespace, " ")

private fun CreationTimelineItem.itemPlanTarget(): String {
    val plan = agentPlanUpdatePresentation() ?: return "任务计划"
    if (plan.steps.isEmpty()) return "计划已清空"
    val completed = plan.steps.count { it.status == AgentPlanStepStatus.Completed }
    return "$completed/${plan.steps.size} 已完成"
}


private fun JsonObject?.patternTargetFromArguments(): String? {
    val pattern = this?.string("pattern")?.takeIf(String::isNotBlank) ?: return null
    val scope = string("path")?.takeIf(String::isNotBlank)
    return if (scope == null) "“$pattern”" else "“$pattern” · $scope"
}

private fun JsonObject?.settingSearchTargetFromArguments(): String? {
    val pattern = this?.string("pattern")?.takeIf(String::isNotBlank) ?: return null
    val group = string("path")?.takeIf(String::isNotBlank)
    return if (group == null) "“$pattern”" else "“$pattern” · $group"
}
