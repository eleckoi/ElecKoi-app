package com.eleckoi.android.feature.characters.modes.story.settinglibrary.model

enum class SettingLibraryPosition(val storageValue: String, val label: String) {
    Instructions("instructions", "系统指令"),
    InsertPoint1("insert_point_1", "设定插入点 1"),
    InsertPoint2("insert_point_2", "设定插入点 2"),
    InsertPoint3("insert_point_3", "设定插入点 3"),
    InsertPoint4("insert_point_4", "设定插入点 4"),
    InsertPoint5("insert_point_5", "设定插入点 5");
}

enum class SettingLibraryPromptPositionSide(val storageValue: String) {
    BeforeSettingPosition("before_setting_position"),
    AfterSettingPosition("after_setting_position"),
}

enum class SettingLibraryInsertRole(val storageValue: String, val label: String, val apiRole: String) {
    System("system", "系统", "system"),
    User("user", "用户", "user"),
    Assistant("assistant", "AI", "assistant");
}

enum class SettingLibraryTriggerMode(val storageValue: String, val label: String) {
    Always("always", "提示词常驻"),
    AgentTool("agent_tool", "Agent 读取"),
    Cache("cache", "缓存设定");
}

enum class SettingLibraryAgentReadStrategy(val storageValue: String, val label: String) {
    Required("required", "必读"),
    Keyword("keyword", "关键词"),
    Normal("normal", "按需"),
    VariableCondition("variable_condition", "变量条件");
}

enum class SettingLibraryDynamicMode(val storageValue: String, val label: String) {
    SingleCondition("single_condition", "单条条件"),
    EjsController("ejs_controller", "EJS 控制器"),
    EjsReference("ejs_reference", "EJS引用设定");
}

enum class SettingLibraryKeywordCondition(val storageValue: String, val label: String) {
    None("none", "无需"),
    Any("any", "任意命中"),
    All("all", "全部命中"),
    NotAny("not_any", "排除命中");
}

enum class SettingLibraryEntryKind(val storageValue: String) {
    Normal("normal"),
    Opening("opening"),
    HistoryCompaction("history_compaction"),
    HiddenToolTimeline("hidden_tool_timeline");
}

const val SettingLibraryOpeningEntryId: String = "fixed-opening-assistant"
const val SettingLibraryOpeningEntryTitle: String = "AI角色开场白"
const val DefaultOpeningMessageId: String = "opening-default"
const val DefaultOpeningMessageTitle: String = "默认开场"
const val HiddenToolTimelineEntryTitle: String = "隐藏工具时间线"
const val HiddenToolTimelineEntryId: String = "built-in-hidden-tool-timeline"
const val HiddenToolTimelinePromptPositionId: String = "hidden-tool-timeline"
const val HiddenToolTimelinePromptPositionTitle: String = "隐藏工具时间线"
private const val LegacyDefaultHiddenToolTimelineContent: String = """<roleplay_output_protocol>
tool_phase:
  setting_library:
    preflight: "若设定库工具可用，最终回复前先用 eleckoi_glob_setting_files 浏览设定文件，并用 eleckoi_read_setting_files 读取结果中的 required_entries"
    search: "按本轮扮演需要使用 eleckoi_grep_setting_files 检索角色与世界设定；允许按需继续搜索"
    empty_result: "没有可用设定时停止查询，直接进入最终回复"
    no_repeat: "不得用相同条件重复无结果的查询"
  plot_variables:
    empty_result: "未发现变量时忽略并继续；不得反复查询"
  visible_output: "仅允许原生 Tool Call"
  forbidden:
    - "角色对白"
    - "叙事"
    - "动作描写"
    - "过程说明"
    - "其他可见文字"
final_phase:
  format: "<FINAL>本轮完整的最终扮演回复</FINAL>"
  before_final: "禁止输出任何可见文字"
  after_final: "禁止再调用原生工具"
</roleplay_output_protocol>"""
const val DefaultHiddenToolTimelineContent: String = """<roleplay_output_protocol>
tool_phase:
  setting_library:
    preflight: "若设定库工具可用，最终回复前先用 eleckoi_glob_setting_files 浏览设定文件，并用 eleckoi_read_setting_files 读取结果中的 required_entries"
    search: "按本轮扮演需要使用 eleckoi_grep_setting_files 检索角色与世界设定；允许按需继续搜索"
    empty_result: "没有可用设定时停止查询，直接进入最终回复"
    no_repeat: "不得用相同条件重复无结果的查询"
  plot_variables:
    empty_result: "未发现变量时忽略并继续；不得反复查询"
  visible_output: "仅允许原生 Tool Call"
  forbidden:
    - "角色对白"
    - "叙事"
    - "动作描写"
    - "过程说明"
    - "其他可见文字"
final_phase:
  mandatory: "最终可见回复必须且只能使用一对 <FINAL> 与 </FINAL> 标签完整包裹；缺少任一标签、使用多对标签或把任何正文写在标签外，都不符合本协议"
  format: "<FINAL>本轮完整的最终扮演回复</FINAL>"
  before_final: "禁止输出任何可见文字"
  after_final: "禁止再调用原生工具"
</roleplay_output_protocol>"""

data class SettingLibraryEntry(
    val id: String = "",
    val title: String = "",
    val iconId: String = "",
    val kind: SettingLibraryEntryKind = SettingLibraryEntryKind.Normal,
    val groupId: String = "",
    val content: String = "",
    /** Ordered alternatives for the fixed assistant opening entry. */
    val openingMessages: List<SettingLibraryOpeningMessage> = emptyList(),
    val defaultOpeningMessageId: String = "",
    val agentSelectionHint: String = "",
    val agentReadStrategy: SettingLibraryAgentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
    /** JavaScript expression evaluated against the current variable state for VariableCondition. */
    val agentReadCondition: String = "",
    val dynamicMode: SettingLibraryDynamicMode = SettingLibraryDynamicMode.SingleCondition,
    val keywords: List<String> = emptyList(),
    val keywordScanDepth: Int = 1,
    val conditionKeywords: List<String> = emptyList(),
    val keywordCondition: SettingLibraryKeywordCondition = SettingLibraryKeywordCondition.None,
    /** Treat primary and secondary keyword strings as SillyTavern-compatible regular expressions. */
    val keywordUseRegex: Boolean = false,
    val keywordIgnoreCase: Boolean = true,
    val keywordWholeWord: Boolean = false,
    /** Number of association rounds after this entry matches a conversation keyword. Zero disables it. */
    val keywordRecursionDepth: Int = 0,
    val triggerMode: SettingLibraryTriggerMode? = null,
    val enabled: Boolean = true,
    val position: SettingLibraryPosition? = null,
    /** Optional user-defined position. [position] remains its runtime anchor for compatibility. */
    val promptPositionId: String = "",
    val insertRole: SettingLibraryInsertRole = SettingLibraryInsertRole.User,
    val order: Int = 1,
    val viewOrder: Int = 0,
    val groupViewOrder: Int = 0,
    val treeViewOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** A named insertion position. It is placement metadata, never a setting entry by itself. */
data class SettingLibraryPromptPosition(
    val id: String = "",
    val name: String = "",
    val anchor: SettingLibraryPosition = SettingLibraryPosition.InsertPoint1,
    val side: SettingLibraryPromptPositionSide = SettingLibraryPromptPositionSide.BeforeSettingPosition,
    val order: Int = 1,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class SettingLibraryOpeningMessage(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val initialVariableStateJson: String = "",
)

fun SettingLibraryEntry.isOpeningEntry(): Boolean {
    return kind == SettingLibraryEntryKind.Opening || id == SettingLibraryOpeningEntryId
}

fun SettingLibraryEntry.isFixedEntry(): Boolean {
    return isOpeningEntry() ||
        isHistoryCompactionEntry()
}

fun SettingLibraryEntry.isHiddenToolTimelineEntry(): Boolean {
    return kind == SettingLibraryEntryKind.HiddenToolTimeline || id == HiddenToolTimelineEntryId
}

fun SettingLibraryEntry.isPinnedEntry(): Boolean {
    return isFixedEntry() || isHiddenToolTimelineEntry()
}

fun settingLibraryOpeningEntry(existing: SettingLibraryEntry? = null): SettingLibraryEntry {
    val source = existing ?: SettingLibraryEntry()
    val messages = normalizeOpeningMessages(source.openingMessages, source.content)
    val defaultId = source.defaultOpeningMessageId
        .takeIf { candidate -> messages.any { it.id == candidate } }
        ?: messages.first().id
    val defaultMessage = messages.first { it.id == defaultId }
    return source.copy(
        id = SettingLibraryOpeningEntryId,
        title = SettingLibraryOpeningEntryTitle,
        iconId = "chat",
        kind = SettingLibraryEntryKind.Opening,
        groupId = "",
        // Keep the legacy field mirrored so old snapshots and the existing chat-start pipeline
        // continue to see the selected opening without needing a Room schema migration.
        content = defaultMessage.content,
        openingMessages = messages,
        defaultOpeningMessageId = defaultId,
        agentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
        agentReadCondition = "",
        dynamicMode = SettingLibraryDynamicMode.SingleCondition,
        keywords = emptyList(),
        conditionKeywords = emptyList(),
        keywordCondition = SettingLibraryKeywordCondition.None,
        keywordUseRegex = false,
        keywordIgnoreCase = true,
        keywordWholeWord = false,
        keywordRecursionDepth = 0,
        triggerMode = SettingLibraryTriggerMode.Always,
        position = null,
        promptPositionId = "",
        insertRole = SettingLibraryInsertRole.Assistant,
        order = 1,
        viewOrder = 0,
        groupViewOrder = 0,
        treeViewOrder = 0,
    )
}

fun SettingLibraryEntry.defaultOpeningMessage(): SettingLibraryOpeningMessage {
    val normalized = settingLibraryOpeningEntry(this)
    return normalized.openingMessages.first { it.id == normalized.defaultOpeningMessageId }
}

fun SettingLibraryEntry.withOpeningMessages(
    messages: List<SettingLibraryOpeningMessage>,
    defaultMessageId: String = defaultOpeningMessageId,
): SettingLibraryEntry {
    return settingLibraryOpeningEntry(
        copy(
            openingMessages = messages,
            defaultOpeningMessageId = defaultMessageId,
        ),
    )
}

private fun normalizeOpeningMessages(
    source: List<SettingLibraryOpeningMessage>,
    legacyContent: String,
): List<SettingLibraryOpeningMessage> {
    val candidates = source.ifEmpty {
        listOf(
            SettingLibraryOpeningMessage(
                id = DefaultOpeningMessageId,
                title = DefaultOpeningMessageTitle,
                content = legacyContent,
            ),
        )
    }
    val usedIds = mutableSetOf<String>()
    return candidates.mapIndexed { index, message ->
        val requestedId = message.id.trim()
        val id = requestedId
            .takeIf { it.isNotBlank() && usedIds.add(it) }
            ?: generateSequence(index + 1) { it + 1 }
                .map { "opening-$it" }
                .first { usedIds.add(it) }
        message.copy(
            id = id,
            title = message.title.takeUnless { title ->
                val trimmed = title.trim()
                trimmed == "开场白 ${index + 1}" ||
                    trimmed == "开场白${index + 1}" ||
                    (index > 0 && trimmed == "备用开场 $index") ||
                    (index > 0 && trimmed == "备用开场白 $index")
            }.orEmpty(),
        )
    }
}

fun settingLibraryHiddenToolTimelineEntry(existing: SettingLibraryEntry? = null): SettingLibraryEntry {
    val source = existing ?: SettingLibraryEntry(
        id = HiddenToolTimelineEntryId,
        title = HiddenToolTimelineEntryTitle,
        content = DefaultHiddenToolTimelineContent,
        kind = SettingLibraryEntryKind.HiddenToolTimeline,
        triggerMode = SettingLibraryTriggerMode.Always,
        position = SettingLibraryPosition.InsertPoint5,
        promptPositionId = HiddenToolTimelinePromptPositionId,
        insertRole = SettingLibraryInsertRole.User,
        order = 1,
    )
    return source.copy(
        id = HiddenToolTimelineEntryId,
        kind = SettingLibraryEntryKind.HiddenToolTimeline,
        groupId = "",
        content = if (source.content.trim() == LegacyDefaultHiddenToolTimelineContent.trim()) {
            DefaultHiddenToolTimelineContent
        } else {
            source.content
        },
        treeViewOrder = Int.MIN_VALUE + 1,
    )
}

fun hiddenToolTimelinePromptPosition(): SettingLibraryPromptPosition = SettingLibraryPromptPosition(
    id = HiddenToolTimelinePromptPositionId,
    name = HiddenToolTimelinePromptPositionTitle,
    anchor = SettingLibraryPosition.InsertPoint5,
    side = SettingLibraryPromptPositionSide.AfterSettingPosition,
    order = 1,
)

fun normalizeSettingLibraryFixedEntry(entry: SettingLibraryEntry): SettingLibraryEntry {
    return when {
        entry.isOpeningEntry() -> settingLibraryOpeningEntry(entry)
        entry.isHistoryCompactionEntry() -> settingLibraryHistoryCompactionEntry(entry)
        else -> entry
    }
}

fun defaultSettingLibraryFixedEntries(): List<SettingLibraryEntry> {
    return listOf(settingLibraryOpeningEntry())
}

data class SettingLibraryGroup(
    val id: String = "",
    val name: String = "",
    val parentId: String = "",
    val order: Int = 1,
    val treeViewOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class SettingLibraryVersion(
    val id: String = "",
    val name: String = "",
    val entries: List<SettingLibraryEntry> = emptyList(),
    val groups: List<SettingLibraryGroup> = emptyList(),
    val promptPositions: List<SettingLibraryPromptPosition> = emptyList(),
    val listAllExpanded: Boolean = true,
    val expandedGroupIds: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class SettingLibrary(
    val characterId: String,
    val name: String = "",
    val entries: List<SettingLibraryEntry> = emptyList(),
    val groups: List<SettingLibraryGroup> = emptyList(),
    val promptPositions: List<SettingLibraryPromptPosition> = emptyList(),
    val activeVersionId: String = "",
    val versions: List<SettingLibraryVersion> = emptyList(),
    val listAllExpanded: Boolean = true,
    val expandedGroupIds: List<String> = emptyList(),
)

data class SettingLibraryConversation(
    val sessionId: String,
    val title: String,
    val characterName: String,
    val characterAvatar: String,
    val summary: String,
    val updatedAt: String,
    val library: SettingLibrary,
)
