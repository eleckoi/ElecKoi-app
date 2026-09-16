package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry

internal fun SettingLibraryEntry.triggerPreviewLabel(): String {
    if (isFixedEntry()) return "固定"
    if (dynamicMode == SettingLibraryDynamicMode.EjsReference) return "EJS引用设定"
    if (dynamicMode == SettingLibraryDynamicMode.EjsController) return "EJS 控制器"
    return when (triggerMode) {
        SettingLibraryTriggerMode.Always -> "常驻"
        SettingLibraryTriggerMode.AgentTool -> "Agent 读取"
        SettingLibraryTriggerMode.Cache -> "缓存设定"
        null -> "需选触发"
    }
}

internal fun SettingLibraryEntry.hasRequiredActivationFields(): Boolean {
    return isFixedEntry() ||
        (triggerMode == SettingLibraryTriggerMode.AgentTool &&
            (agentReadStrategy != SettingLibraryAgentReadStrategy.VariableCondition ||
                dynamicMode != SettingLibraryDynamicMode.SingleCondition ||
                agentReadCondition.isNotBlank())) ||
        (triggerMode == SettingLibraryTriggerMode.Always && position != null) ||
        triggerMode == SettingLibraryTriggerMode.Cache
}

internal fun List<SettingLibraryEntry>.sortedForSettingLibraryDisplay(): List<SettingLibraryEntry> {
    return sortedWith(
        compareBy<SettingLibraryEntry> { entry -> entry.position?.displayBucketIndex() ?: Int.MAX_VALUE }
            .thenBy { entry -> entry.order }
            .thenBy { entry -> entry.title }
            .thenBy { entry -> entry.id },
    )
}

internal fun List<SettingLibraryEntry>.sortedForPositionPreview(position: SettingLibraryPosition): List<SettingLibraryEntry> {
    return filter { it.position == position && (!it.isFixedEntry() || it.enabled) }
        .sortedWith(
            compareBy<SettingLibraryEntry> { entry -> entry.order }
                .thenBy { entry -> entry.title }
                .thenBy { entry -> entry.id },
        )
}

internal fun SettingLibraryEntry.hasOrderConflictIn(entries: List<SettingLibraryEntry>): Boolean {
    if (triggerMode !in setOf(SettingLibraryTriggerMode.Always, SettingLibraryTriggerMode.Cache)) return false
    val targetPosition = if (triggerMode == SettingLibraryTriggerMode.Cache) {
        SettingLibraryPosition.InsertPoint1
    } else {
        position ?: return false
    }
    val targetScope = if (triggerMode == SettingLibraryTriggerMode.Cache) {
        SettingLibraryTriggerMode.Cache.storageValue
    } else {
        promptPositionId.ifBlank { targetPosition.storageValue }
    }
    return entries.any { entry ->
        entry.id != id &&
            entry.enabled &&
            entry.triggerMode == triggerMode &&
            !entry.isFixedEntry() &&
            (if (entry.triggerMode == SettingLibraryTriggerMode.Cache) {
                SettingLibraryTriggerMode.Cache.storageValue
            } else {
                entry.promptPositionId.ifBlank { entry.position?.storageValue.orEmpty() }
            }) == targetScope &&
            entry.order == order
    }
}

internal fun List<SettingLibraryEntry>.duplicateOrderGroupCount(position: SettingLibraryPosition): Int {
    return filter { it.position == position && !it.isFixedEntry() }
        .groupBy { entry ->
            Triple(
                entry.triggerMode,
                entry.promptPositionId.ifBlank { entry.position?.storageValue.orEmpty() },
                entry.order,
            )
        }
        .count { (_, grouped) -> grouped.size > 1 }
}

private fun SettingLibraryPosition.displayBucketIndex(): Int {
    return when (this) {
        SettingLibraryPosition.Instructions -> 0
        SettingLibraryPosition.InsertPoint1 -> 1
        SettingLibraryPosition.InsertPoint2 -> 2
        SettingLibraryPosition.InsertPoint3 -> 3
        SettingLibraryPosition.InsertPoint4 -> 4
        SettingLibraryPosition.InsertPoint5 -> 5
    }
}

internal fun SettingLibraryEntry.positionOrderPreviewLabel(): String {
    return if (isFixedEntry()) "固定" else order.toString()
}

internal fun SettingLibraryEntry.insertionPositionLabel(
    promptPositions: List<SettingLibraryPromptPosition>,
): String {
    if (promptPositionId.isBlank()) return position?.label ?: "请选择"
    return promptPositions
        .firstOrNull { candidate -> candidate.id == promptPositionId }
        ?.name
        ?.trim()
        ?.ifBlank { "未命名位置" }
        ?: "自定义位置"
}

internal fun triggerDescription(mode: SettingLibraryTriggerMode): String {
    return when (mode) {
        SettingLibraryTriggerMode.Always -> "每回合写入提示词"
        SettingLibraryTriggerMode.AgentTool -> "由 Agent 搜索并读取"
        SettingLibraryTriggerMode.Cache -> "固定写入缓存设定区"
    }
}

internal fun keywordConditionDescription(condition: SettingLibraryKeywordCondition): String {
    return when (condition) {
        SettingLibraryKeywordCondition.None -> "不使用附加关键词"
        SettingLibraryKeywordCondition.Any -> "任意一个附加关键词出现即可"
        SettingLibraryKeywordCondition.All -> "附加关键词必须全部出现"
        SettingLibraryKeywordCondition.NotAny -> "出现任意附加关键词则不触发"
    }
}
