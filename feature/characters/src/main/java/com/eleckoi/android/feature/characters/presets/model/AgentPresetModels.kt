package com.eleckoi.android.feature.characters.presets.model

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHistoryCompactionEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryHistoryCompactionEntry
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy

enum class AgentPresetModelFamily(
    val storageValue: String,
    val label: String,
) {
    General("general", "通用"),
    Claude("claude", "Claude"),
    OpenAI("openai", "OpenAI"),
    Gemini("gemini", "Gemini"),
    DeepSeek("deepseek", "DeepSeek"),
    Other("other", "其他"),
    ;

    companion object {
        fun fromStorage(value: String): AgentPresetModelFamily = entries
            .firstOrNull { it.storageValue == value.trim().lowercase() }
            ?: General
    }
}

data class AgentPresetModelTag(
    val id: String,
    val label: String,
    val providerId: String = "",
)

fun AgentPresetModelFamily.toTag(): AgentPresetModelTag = AgentPresetModelTag(
    id = storageValue,
    label = label,
    providerId = when (this) {
        AgentPresetModelFamily.General -> ""
        AgentPresetModelFamily.Claude -> "claude"
        AgentPresetModelFamily.OpenAI -> "openai"
        AgentPresetModelFamily.Gemini -> "gemini"
        AgentPresetModelFamily.DeepSeek -> "deepseek"
        AgentPresetModelFamily.Other -> ""
    },
)

data class AgentPresetLibraryGroup(
    val id: String,
    val name: String,
    val sortIndex: Int,
)

/** Author-written metadata shown on the preset overview. This is not an executable preset version. */
data class AgentPresetTimelineItem(
    val id: String,
    val title: String,
    val dateLabel: String = "",
    val note: String = "",
)

data class AgentPresetProfile(
    val authorName: String = "",
    val authorAvatarPath: String = "",
    val usageInstructions: String = "",
    val timeline: List<AgentPresetTimelineItem> = emptyList(),
)

data class AgentPresetToolConfiguration(
    val includedGroupIds: List<String> = DefaultAgentPresetToolGroupIds,
    val enabledGroupIds: Set<String> = DefaultAgentPresetToolGroupIds.toSet(),
    val subagentModelConfigId: String = "",
    val subagentModel: String = "",
    val toolModelConfigIds: Map<String, String> = emptyMap(),
) {
    fun normalized(): AgentPresetToolConfiguration {
        val included = includedGroupIds.map(String::trim).filter(String::isNotBlank).distinct()
        return copy(
            includedGroupIds = included,
            enabledGroupIds = enabledGroupIds.map(String::trim).filter { it in included }.toSet(),
            subagentModelConfigId = subagentModelConfigId.trim(),
            subagentModel = subagentModel.trim(),
            toolModelConfigIds = toolModelConfigIds.mapNotNull { (groupId, configId) ->
                val group = groupId.trim()
                val config = configId.trim()
                if (group.isBlank() || config.isBlank()) null else group to config
            }.toMap(),
        )
    }
}

val DefaultAgentPresetRoleplayPlanSteps: List<String> = listOf(
    "必须先并行调用工具调研阅读设定，这里不扮演回复，禁止未阅读设定直接回复",
    "等前置任务都完成，直接输出 <FINAL> 正文，不要再次调用 update_roleplay_plan；" +
        "应用检测到正文后会自动完成最终项的标记。",
)

data class AgentPresetRoleplayPlan(
    val steps: List<String> = DefaultAgentPresetRoleplayPlanSteps,
) {
    fun normalized(): AgentPresetRoleplayPlan {
        val normalizedSteps = steps
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.take(2_000) }
            .take(20)
        return copy(steps = normalizedSteps.ifEmpty { DefaultAgentPresetRoleplayPlanSteps })
    }
}

val DefaultAgentPresetToolGroupIds: List<String> = listOf(
    AgentToolRequestPolicy.BuiltInVariables,
    AgentToolRequestPolicy.BuiltInSettingLibrary,
)

/**
 * One globally selectable preset. Its ordinary prompt entries deliberately reuse the setting
 * library model.
 */
data class AgentPreset(
    val id: String,
    val name: String,
    val modelFamily: AgentPresetModelFamily = AgentPresetModelFamily.General,
    val modelTags: List<AgentPresetModelTag> = listOf(modelFamily.toTag()),
    val libraryGroupId: String = "",
    val activeVersionId: String = "",
    val activeVersionNumber: Int = 1,
    val profile: AgentPresetProfile = AgentPresetProfile(),
    val entries: List<SettingLibraryEntry> = emptyList(),
    val groups: List<SettingLibraryGroup> = emptyList(),
    val promptPositions: List<SettingLibraryPromptPosition> = emptyList(),
    val toolConfiguration: AgentPresetToolConfiguration = AgentPresetToolConfiguration(),
    val roleplayPlan: AgentPresetRoleplayPlan = AgentPresetRoleplayPlan(),
    val regexRules: List<RegexRule> = emptyList(),
    val expandedGroupIds: List<String> = emptyList(),
) {
    /**
     * Namespaces the selected preset before it joins a character's effective setting library.
     * Namespaced ids prevent imported ids from colliding with a character card or conversation
     * delta, while blank parent/group ids stay blank so the author's root layout is preserved.
     */
    fun asRuntimeSettingLibrary(): SettingLibrary {
        val namespace = "agent-preset:$id:"
        fun groupId(sourceId: String): String = "$namespace${sourceId.ifBlank { "ungrouped" }}"
        fun promptPositionId(sourceId: String): String = "$namespace${sourceId.ifBlank { "position" }}"

        val runtimeGroups = groups.mapIndexed { index, group ->
            group.copy(
                id = groupId(group.id),
                parentId = group.parentId.takeIf(String::isNotBlank)?.let(::groupId).orEmpty(),
                order = index + 1,
            )
        }
        val runtimeEntries = entries
            .filterNot(SettingLibraryEntry::isHistoryCompactionEntry)
            .mapIndexed { index, entry ->
            entry.copy(
                id = "$namespace${entry.id.ifBlank { "entry-$index" }}",
                groupId = entry.groupId.takeIf(String::isNotBlank)?.let(::groupId).orEmpty(),
                promptPositionId = entry.promptPositionId.takeIf(String::isNotBlank)?.let(::promptPositionId).orEmpty(),
            )
        }
        val runtimePromptPositions = promptPositions.mapIndexed { index, position ->
            position.copy(
                id = promptPositionId(position.id.ifBlank { "position-$index" }),
            )
        }
        return SettingLibrary(
            characterId = namespace,
            name = name,
            entries = runtimeEntries,
            groups = runtimeGroups,
            promptPositions = runtimePromptPositions,
            expandedGroupIds = runtimeGroups.map(SettingLibraryGroup::id),
        )
    }
}

data class AgentPresetSummary(
    val id: String,
    val name: String,
    val modelFamily: AgentPresetModelFamily,
    val modelTags: List<AgentPresetModelTag>,
    val libraryGroupId: String,
    val activeVersionId: String,
    val activeVersionNumber: Int,
    val entryCount: Int,
    val profile: AgentPresetProfile = AgentPresetProfile(),
)

data class AgentPresetCatalog(
    val activePresetId: String,
    val groups: List<AgentPresetLibraryGroup>,
    val presets: List<AgentPresetSummary>,
) {
    val activePreset: AgentPresetSummary?
        get() = presets.firstOrNull { it.id == activePresetId } ?: presets.firstOrNull()
}

const val DefaultAgentPresetId: String = "agent-preset-default"

fun AgentPreset.withRequiredBuiltIns(): AgentPreset {
    val compactionEntry = settingLibraryHistoryCompactionEntry(
        entries.firstOrNull(SettingLibraryEntry::isHistoryCompactionEntry),
    )
    val hiddenTimelineEntry = settingLibraryHiddenToolTimelineEntry(
        entries.firstOrNull(SettingLibraryEntry::isHiddenToolTimelineEntry),
    )
    val normalizedPromptPositions = promptPositions
        .distinctBy(SettingLibraryPromptPosition::id)
        .sortedWith(
            compareBy<SettingLibraryPromptPosition> { it.anchor.ordinal }
                .thenBy { it.side.ordinal }
                .thenBy(SettingLibraryPromptPosition::order)
                .thenBy(SettingLibraryPromptPosition::id),
        )
        .groupBy { it.anchor to it.side }
        .flatMap { (_, positions) ->
            positions.mapIndexed { index, position -> position.copy(order = index + 1) }
        }
    val positionsById = normalizedPromptPositions.associateBy(SettingLibraryPromptPosition::id)
    val ordinaryEntries = entries.filterNot { candidate ->
        candidate.isHistoryCompactionEntry() || candidate.isHiddenToolTimelineEntry()
    }.map { entry ->
        if (entry.triggerMode != SettingLibraryTriggerMode.Always) return@map entry
        val customPosition = positionsById[entry.promptPositionId]
        when {
            customPosition != null -> entry.copy(position = customPosition.anchor)
            entry.position == SettingLibraryPosition.Instructions -> entry.copy(promptPositionId = "")
            else -> entry.copy(position = null, promptPositionId = "", enabled = false)
        }
    }
    return copy(
        entries = listOf(compactionEntry, hiddenTimelineEntry) + ordinaryEntries,
        promptPositions = normalizedPromptPositions,
        toolConfiguration = toolConfiguration.normalized(),
        roleplayPlan = roleplayPlan.normalized(),
    )
}

fun AgentPreset.historyCompactionInstructions(): String? = entries
    .firstOrNull(SettingLibraryEntry::isHistoryCompactionEntry)
    ?.takeIf(SettingLibraryEntry::enabled)
    ?.content
    ?.trim()
    ?.takeIf(String::isNotBlank)

fun defaultAgentPreset(): AgentPreset = AgentPreset(
    id = DefaultAgentPresetId,
    name = "默认 Agent 预设",
    modelFamily = AgentPresetModelFamily.General,
).withRequiredBuiltIns()

fun defaultAgentPresetCatalog(): AgentPresetCatalog = AgentPresetCatalog(
    activePresetId = DefaultAgentPresetId,
    groups = emptyList(),
    presets = listOf(
        AgentPresetSummary(
            id = DefaultAgentPresetId,
            name = "默认 Agent 预设",
            modelFamily = AgentPresetModelFamily.General,
            modelTags = listOf(AgentPresetModelFamily.General.toTag()),
            libraryGroupId = "",
            activeVersionId = "$DefaultAgentPresetId:v1",
            activeVersionNumber = 1,
            entryCount = 2,
        ),
    ),
)
