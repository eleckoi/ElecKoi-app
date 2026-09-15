package com.eleckoi.android.feature.chat.roleplay.protocol

import com.eleckoi.android.engine.agent.protocol.AssistantActionCallCloseTag
import com.eleckoi.android.engine.agent.protocol.AssistantFinalCloseTag
import com.eleckoi.android.engine.agent.protocol.AssistantFinalOpenTag
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.DefaultHiddenToolTimelineContent
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan

internal object RoleplayPhaseMarker {
    const val Final = AssistantFinalOpenTag
    const val FinalClose = AssistantFinalCloseTag
}

internal fun roleplayOutputProtocolInstructions(actionCallEnabled: Boolean = false): String {
    return if (actionCallEnabled) {
        augmentRoleplayOutputProtocolForImage(DefaultHiddenToolTimelineContent)
    } else {
        DefaultHiddenToolTimelineContent
    }
}

internal fun augmentRoleplayOutputProtocolForImage(content: String): String {
    val imageInstructions = """
    当且仅当其他指令明确提供了可用的单向动作名称及参数格式时，你可以在助手普通文本流中
    原样写入以下 XML 风格动作边界和 JSON 参数：
    <ACTION_CALL name="动作名称">
    {"参数名":"参数值"}
    $AssistantActionCallCloseTag

    ACTION_CALL 元素和其中的 JSON 是普通 assistant 文本，不属于 API 提供的 tools，绝对不是原生
    Tool Call 或 function_call。必须把开始标签、JSON 和结束标签逐字写进当前助手响应；
    禁止把开始标签中的动作名称提交给原生工具系统，也禁止使用 Markdown 代码块包裹它们。

    ACTION_CALL 只能在所有原生工具调用完成后写入，并且必须紧接在 ${RoleplayPhaseMarker.Final}
    之前；它不是扮演回复，前后不得夹杂过程文字。应用程序会直接从文本流读取并处理它及其 JSON，
    不会返回执行结果，也不会因此再次请求你。写完结束标签后必须在同一次响应中立即输出
    ${RoleplayPhaseMarker.Final} 和完整正文，不要等待结果、不要重试，也不要声称该动作不受支持或被禁用。
    禁止使用未被其他指令明确提供的动作名称。
    """.trimIndent()
    val closeTag = "</roleplay_output_protocol>"
    val closeIndex = content.lastIndexOf(closeTag)
    return if (closeIndex >= 0) {
        buildString(content.length + imageInstructions.length + 2) {
            append(content.substring(0, closeIndex).trimEnd())
            append("\n\n")
            append(imageInstructions)
            append('\n')
            append(content.substring(closeIndex))
        }
    } else {
        content.trimEnd() + "\n\n" + imageInstructions
    }
}

private const val ImageEnabledDefaultRoleplayPlanFinalTask: String =
    "等前置任务都完成，先按 <generate_image_action> 约定输出完整的 generate_image ACTION_CALL，" +
        "再直接输出 <FINAL> 正文，并在正文对应位置写入 [[IMAGE:n]]；" +
        "不要再次调用 update_roleplay_plan，应用检测到正文后会自动完成最终项的标记。"

internal fun effectiveRoleplayPlanItems(
    items: List<String>,
    imageActionEnabled: Boolean = false,
): List<String> {
    if (items.isEmpty()) return emptyList()
    val normalized = AgentPresetRoleplayPlan(items).normalized().steps
    if (normalized.isEmpty() || !imageActionEnabled) return normalized
    return normalized.toMutableList().apply {
        this[lastIndex] = ImageEnabledDefaultRoleplayPlanFinalTask
    }
}

internal fun roleplayPlanFixedItemsInstructions(
    items: List<String>,
    imageActionEnabled: Boolean = false,
): String = buildString {
    val fixedItems = effectiveRoleplayPlanItems(items, imageActionEnabled)
    if (fixedItems.isEmpty()) return ""
    appendLine("作者为 update_roleplay_plan 指定了以下固定任务项：")
    fixedItems.forEachIndexed { index, item ->
        appendLine("${index + 1}. $item")
    }
    append("调用 update_roleplay_plan 时，只能使用以上任务项，必须原样保留每一项及其顺序，并按实际进度填写状态；不得添加、删除或改写任务项。")
}
