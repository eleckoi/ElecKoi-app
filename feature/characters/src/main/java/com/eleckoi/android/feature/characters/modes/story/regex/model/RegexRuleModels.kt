package com.eleckoi.android.feature.characters.modes.story.regex.model

import com.eleckoi.android.foundation.storage.newId

enum class RegexRuleScope(val label: String) {
    Global("全局"),
    PromptPreset("提示词预设"),
    Character("角色"),
}

enum class RegexRuleTarget(val label: String) {
    UserInput("用户发送"),
    AiOutput("AI 回复"),
    SlashCommand("快捷命令"),
    SettingContent("设定内容"),
    Reasoning("推理内容"),
}

data class RegexRule(
    val id: String = "regex-${newId(10)}",
    val name: String = "",
    val pattern: String = "",
    val replacement: String = "",
    val targets: Set<RegexRuleTarget> = setOf(RegexRuleTarget.AiOutput),
    val enabled: Boolean = true,
    val displayOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = false,
    val order: Int = 0,
)

data class RegexRuleVersion(
    val id: String = "regex-version-${newId(10)}",
    val name: String = "未命名版本",
    val globalEnabledIds: Set<String> = emptySet(),
    val promptPresetEnabledIds: Set<String> = emptySet(),
    val characterEnabledIds: Set<String> = emptySet(),
)

data class RegexRuleCollection(
    val globalRules: List<RegexRule> = emptyList(),
    val promptPresetRules: List<RegexRule> = emptyList(),
    val characterRules: List<RegexRule> = emptyList(),
    val versions: List<RegexRuleVersion> = emptyList(),
    val activeVersionId: String = "",
)

data class RegexRuleImportDocument(
    val displayName: String,
    val json: String,
)

data class RegexRuleImportResult(
    val collection: RegexRuleCollection,
    val importedFileCount: Int,
    val importedRuleCount: Int,
    val failedFileNames: List<String> = emptyList(),
)
