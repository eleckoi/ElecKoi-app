package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArray
import com.eleckoi.android.feature.studio.authoring.creatorBoolean
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorString
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class ApplyResult(
    val library: SettingLibrary,
    val descriptions: List<String>,
    val createdEntries: Int,
    val updatedEntries: Int,
    val deletedEntries: Int,
    val createdGroups: Int,
    val updatedGroups: Int,
    val deletedGroups: Int,
    val createdPromptPositions: Int,
    val updatedPromptPositions: Int,
    val deletedPromptPositions: Int,
)

internal fun applyOperations(source: SettingLibrary, operations: List<JsonObject>): ApplyResult {
    val groups = source.groups.associateByTo(linkedMapOf(), SettingLibraryGroup::id)
    val entries = source.entries.associateByTo(linkedMapOf(), SettingLibraryEntry::id)
    val promptPositions = source.promptPositions.associateByTo(
        linkedMapOf(),
        SettingLibraryPromptPosition::id,
    )
    val descriptions = mutableListOf<String>()
    var createdEntries = 0
    var updatedEntries = 0
    var deletedEntries = 0
    var createdGroups = 0
    var updatedGroups = 0
    var deletedGroups = 0
    var createdPromptPositions = 0
    var updatedPromptPositions = 0
    var deletedPromptPositions = 0

    operations.forEachIndexed { index, operation ->
        when (operation.creatorString("op")) {
            "create_group" -> {
                val id = operation.creatorString("id").ifBlank { "creator-group-${UUID.randomUUID()}" }
                if (groups.containsKey(id)) invalid(index, "分组 id 已存在：$id")
                val name = operation.requiredText("name", index, 80)
                groups[id] = SettingLibraryGroup(
                    id = id,
                    name = name,
                    parentId = operation.creatorString("parent_id"),
                    order = operation.creatorInt("order", groups.size + 1).coerceAtLeast(1),
                    treeViewOrder = operation.creatorInt("tree_view_order", groups.size + 1).coerceAtLeast(1),
                )
                createdGroups++
                descriptions += "创建分组：$name"
            }
            "patch_group" -> {
                val id = operation.requiredId(index)
                val current = groups[id] ?: invalid(index, "找不到分组：$id")
                val name = operation.creatorString("name").takeIf(String::isNotBlank)?.take(80) ?: current.name
                val parentId = if (operation.containsKey("parent_id")) operation.creatorString("parent_id") else current.parentId
                groups[id] = current.copy(
                    name = name,
                    parentId = parentId,
                    order = if (operation.containsKey("order")) operation.creatorInt("order", current.order).coerceAtLeast(1) else current.order,
                    treeViewOrder = if (operation.containsKey("tree_view_order")) {
                        operation.creatorInt("tree_view_order", current.treeViewOrder).coerceAtLeast(1)
                    } else current.treeViewOrder,
                )
                updatedGroups++
                descriptions += "修改分组：${current.name} → $name"
            }
            "delete_group" -> {
                val id = operation.requiredId(index)
                val target = groups[id] ?: invalid(index, "找不到分组：$id")
                val descendants = groupDescendants(id, groups.values)
                val removedIds = descendants + id
                val pinnedInside = entries.values.firstOrNull { it.groupId in removedIds && it.isPinnedEntry() }
                if (pinnedInside != null) invalid(index, "固定条目不能随分组删除：${pinnedInside.title}")
                removedIds.forEach(groups::remove)
                val removedEntryIds = entries.values.filter { it.groupId in removedIds }.map { it.id }
                removedEntryIds.forEach(entries::remove)
                deletedGroups += removedIds.size
                deletedEntries += removedEntryIds.size
                descriptions += "删除分组：${target.name}（含 ${removedIds.size - 1} 个子分组、${removedEntryIds.size} 条设定）"
            }
            "create_prompt_position" -> {
                val id = operation.creatorString("id").ifBlank { "creator-position-${UUID.randomUUID()}" }
                if (promptPositions.containsKey(id)) invalid(index, "提示词位置 id 已存在：$id")
                val name = operation.requiredText("name", index, 60)
                val anchor = operation.positionValue("anchor", index, SettingLibraryPosition.InsertPoint1)
                promptPositions[id] = SettingLibraryPromptPosition(
                    id = id,
                    name = name,
                    anchor = anchor,
                    side = operation.promptPositionSideValue(
                        "side",
                        index,
                        SettingLibraryPromptPositionSide.BeforeSettingPosition,
                    ),
                    order = operation.creatorInt("order", promptPositions.size + 1).coerceAtLeast(1),
                )
                createdPromptPositions++
                descriptions += "创建提示词位置：$name"
            }
            "patch_prompt_position" -> {
                val id = operation.requiredId(index)
                val current = promptPositions[id] ?: invalid(index, "找不到提示词位置：$id")
                val name = operation.creatorString("name").takeIf(String::isNotBlank)?.take(60) ?: current.name
                val anchor = if (operation.containsKey("anchor")) {
                    operation.positionValue("anchor", index, current.anchor)
                } else current.anchor
                val side = if (operation.containsKey("side")) {
                    operation.promptPositionSideValue("side", index, current.side)
                } else current.side
                promptPositions[id] = current.copy(
                    name = name,
                    anchor = anchor,
                    side = side,
                    order = if (operation.containsKey("order")) operation.creatorInt("order", current.order).coerceAtLeast(1) else current.order,
                )
                entries.keys.toList().forEach { entryId ->
                    val entry = entries.getValue(entryId)
                    if (entry.promptPositionId == id) entries[entryId] = entry.copy(position = anchor)
                }
                updatedPromptPositions++
                descriptions += "修改提示词位置：${current.name} → $name"
            }
            "delete_prompt_position" -> {
                val id = operation.requiredId(index)
                val current = promptPositions.remove(id) ?: invalid(index, "找不到提示词位置：$id")
                entries.keys.toList().forEach { entryId ->
                    val entry = entries.getValue(entryId)
                    if (entry.promptPositionId == id) entries[entryId] = entry.copy(promptPositionId = "")
                }
                deletedPromptPositions++
                descriptions += "删除提示词位置：${current.name}（EJS引用设定回退到对应内置锚点）"
            }
            "create_entry" -> {
                val id = operation.creatorString("id").ifBlank { "creator-entry-${UUID.randomUUID()}" }
                if (entries.containsKey(id)) invalid(index, "条目 id 已存在：$id")
                val title = operation.requiredText("title", index, 120)
                val content = operation.requiredText("content", index, 200_000)
                entries[id] = operation.patchEntry(
                    current = SettingLibraryEntry(
                        id = id,
                        title = title,
                        content = content,
                        groupId = operation.creatorString("group_id"),
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                        order = entries.size + 1,
                        treeViewOrder = entries.size + 1,
                    ),
                    index = index,
                )
                createdEntries++
                descriptions += "创建设定：$title"
            }
            "patch_entry" -> {
                val id = operation.requiredId(index)
                val current = entries[id] ?: invalid(index, "找不到条目：$id")
                if (current.isPinnedEntry() && !current.isOpeningEntry()) {
                    invalid(index, "这个系统条目不允许由创作助手修改：${current.title}")
                }
                val updated = operation.patchEntry(current, index)
                entries[id] = updated
                updatedEntries++
                descriptions += "修改设定：${current.title}"
            }
            "delete_entry" -> {
                val id = operation.requiredId(index)
                val current = entries[id] ?: invalid(index, "找不到条目：$id")
                if (current.isPinnedEntry()) invalid(index, "固定条目不能删除：${current.title}")
                entries.remove(id)
                deletedEntries++
                descriptions += "删除设定：${current.title}"
            }
            else -> invalid(index, "未知 op：${operation.creatorString("op")}")
        }
    }
    val next = source.copy(
        entries = entries.values.toList(),
        groups = groups.values.toList(),
        promptPositions = promptPositions.values.toList(),
    )
    return ApplyResult(
        library = next,
        descriptions = descriptions,
        createdEntries = createdEntries,
        updatedEntries = updatedEntries,
        deletedEntries = deletedEntries,
        createdGroups = createdGroups,
        updatedGroups = updatedGroups,
        deletedGroups = deletedGroups,
        createdPromptPositions = createdPromptPositions,
        updatedPromptPositions = updatedPromptPositions,
        deletedPromptPositions = deletedPromptPositions,
    )
}

private fun JsonObject.patchEntry(current: SettingLibraryEntry, index: Int): SettingLibraryEntry {
    fun enumValue(name: String): String? = if (containsKey(name)) creatorString(name) else null
    val triggerMode = enumValue("trigger_mode")?.let { raw ->
        SettingLibraryTriggerMode.entries.firstOrNull { it.storageValue == raw }
            ?: invalid(index, "trigger_mode 无效：$raw")
    } ?: current.triggerMode
    val strategy = enumValue("agent_read_strategy")?.let { raw ->
        SettingLibraryAgentReadStrategy.entries.firstOrNull { it.storageValue == raw }
            ?: invalid(index, "agent_read_strategy 无效：$raw")
    } ?: current.agentReadStrategy
    val keywordCondition = enumValue("keyword_condition")?.let { raw ->
        SettingLibraryKeywordCondition.entries.firstOrNull { it.storageValue == raw }
            ?: invalid(index, "keyword_condition 无效：$raw")
    } ?: current.keywordCondition
    val dynamicMode = enumValue("dynamic_mode")?.let { raw ->
        SettingLibraryDynamicMode.entries.firstOrNull { it.storageValue == raw }
            ?: invalid(index, "dynamic_mode 无效：$raw")
    } ?: current.dynamicMode
    val position = if (containsKey("position")) {
        creatorString("position").takeIf(String::isNotBlank)?.let { raw ->
            SettingLibraryPosition.entries.firstOrNull { candidate -> candidate.storageValue == raw }
                ?: invalid(index, "position 无效：$raw")
        }
    } else current.position
    val insertRole = enumValue("insert_role")?.let { raw ->
        SettingLibraryInsertRole.entries.firstOrNull { it.storageValue == raw }
            ?: invalid(index, "insert_role 无效：$raw")
    } ?: current.insertRole
    val keywords = if (containsKey("keywords")) stringArray("keywords", index) else current.keywords
    val conditionKeywords = if (containsKey("condition_keywords")) {
        stringArray("condition_keywords", index)
    } else current.conditionKeywords
    val updated = current.copy(
        title = creatorString("title").takeIf(String::isNotBlank)?.take(120) ?: current.title,
        iconId = if (containsKey("icon_id")) creatorString("icon_id").take(80) else current.iconId,
        content = if (containsKey("content")) requiredText("content", index, 200_000) else current.content,
        groupId = if (containsKey("group_id")) creatorString("group_id") else current.groupId,
        enabled = if (containsKey("enabled")) creatorBoolean("enabled") else current.enabled,
        triggerMode = triggerMode,
        agentReadStrategy = strategy,
        agentSelectionHint = if (containsKey("agent_selection_hint")) creatorString("agent_selection_hint").take(1_000) else current.agentSelectionHint,
        agentReadCondition = if (containsKey("agent_read_condition")) creatorString("agent_read_condition").take(10_000) else current.agentReadCondition,
        dynamicMode = dynamicMode,
        keywords = keywords,
        keywordScanDepth = if (containsKey("keyword_scan_depth")) {
            creatorInt("keyword_scan_depth", current.keywordScanDepth).coerceIn(1, 1000)
        } else current.keywordScanDepth,
        conditionKeywords = conditionKeywords,
        keywordCondition = keywordCondition,
        keywordUseRegex = if (containsKey("keyword_use_regex")) creatorBoolean("keyword_use_regex") else current.keywordUseRegex,
        keywordIgnoreCase = if (containsKey("keyword_ignore_case")) creatorBoolean("keyword_ignore_case") else current.keywordIgnoreCase,
        keywordWholeWord = if (containsKey("keyword_whole_word")) creatorBoolean("keyword_whole_word") else current.keywordWholeWord,
        keywordRecursionDepth = if (containsKey("keyword_recursion_depth")) {
            creatorInt("keyword_recursion_depth", current.keywordRecursionDepth).coerceIn(0, 10)
        } else current.keywordRecursionDepth,
        position = position,
        promptPositionId = if (containsKey("prompt_position_id")) creatorString("prompt_position_id") else current.promptPositionId,
        insertRole = insertRole,
        order = if (containsKey("order")) creatorInt("order", current.order).coerceAtLeast(1) else current.order,
        treeViewOrder = if (containsKey("tree_view_order")) {
            creatorInt("tree_view_order", current.treeViewOrder).coerceAtLeast(1)
        } else current.treeViewOrder,
    )
    return when {
        current.isOpeningEntry() -> {
            val normalized = settingLibraryOpeningEntry(current)
            val messages = when {
                containsKey("opening_messages") -> openingMessages(index)
                containsKey("content") -> normalized.openingMessages.map { message ->
                    if (message.id == normalized.defaultOpeningMessageId) {
                        message.copy(content = updated.content)
                    } else message
                }
                else -> normalized.openingMessages
            }
            val requestedDefaultId = creatorString("default_opening_message_id")
            if (
                containsKey("default_opening_message_id") &&
                (requestedDefaultId.isBlank() || messages.none { it.id == requestedDefaultId })
            ) {
                invalid(index, "default_opening_message_id 不属于 opening_messages：$requestedDefaultId")
            }
            val defaultId = requestedDefaultId.takeIf(String::isNotBlank)
                ?: normalized.defaultOpeningMessageId.takeIf { candidate -> messages.any { it.id == candidate } }
                .orEmpty()
            settingLibraryOpeningEntry(
                updated.copy(
                    openingMessages = messages,
                    defaultOpeningMessageId = defaultId,
                ),
            )
        }
        updated.triggerMode == SettingLibraryTriggerMode.Cache -> updated.copy(
            position = SettingLibraryPosition.InsertPoint1,
            promptPositionId = "",
            insertRole = SettingLibraryInsertRole.User,
        )
        else -> updated
    }
}

private fun JsonObject.openingMessages(operationIndex: Int): List<SettingLibraryOpeningMessage> {
    val values = creatorArray("opening_messages")
        ?: invalid(operationIndex, "opening_messages 必须是 array")
    if (values.isEmpty()) invalid(operationIndex, "opening_messages 至少保留一条开场白")
    return values.mapIndexed { messageIndex, element ->
        val message = element as? JsonObject
            ?: invalid(operationIndex, "opening_messages[$messageIndex] 必须是 object")
        if (!message.containsKey("content")) {
            invalid(operationIndex, "opening_messages[$messageIndex].content 缺失")
        }
        SettingLibraryOpeningMessage(
            id = message.rawString("id").trim().take(120),
            title = message.rawString("title").trim().take(120),
            content = message.rawString("content").take(200_000),
            initialVariableStateJson = message.rawString("initial_variable_state_json").take(200_000),
        )
    }
}

private fun JsonObject.rawString(name: String): String =
    (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.positionValue(
    name: String,
    operationIndex: Int,
    default: SettingLibraryPosition,
): SettingLibraryPosition {
    val raw = creatorString(name)
    if (raw.isBlank()) return default
    return SettingLibraryPosition.entries.firstOrNull { it.storageValue == raw }
        ?: invalid(operationIndex, "$name 无效：$raw")
}

private fun JsonObject.promptPositionSideValue(
    name: String,
    operationIndex: Int,
    default: SettingLibraryPromptPositionSide,
): SettingLibraryPromptPositionSide {
    val raw = creatorString(name)
    if (raw.isBlank()) return default
    return SettingLibraryPromptPositionSide.entries.firstOrNull { it.storageValue == raw }
        ?: invalid(operationIndex, "$name 无效：$raw")
}

internal fun validateLibrary(library: SettingLibrary) {
    val promptPositionIds = library.promptPositions.map { it.id }
    if (
        promptPositionIds.any(String::isBlank) ||
        promptPositionIds.distinct().size != promptPositionIds.size
    ) {
        throw CreatorAuthoringException("INVALID_CHANGE_SET", "提示词位置 id 不能为空或重复")
    }
    library.promptPositions.forEach { position ->
        if (position.name.isBlank()) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "提示词位置名称不能为空")
        }
    }
    val promptPositionIdSet = promptPositionIds.toSet()
    val groupIds = library.groups.map { it.id }
    if (groupIds.any(String::isBlank) || groupIds.distinct().size != groupIds.size) {
        throw CreatorAuthoringException("INVALID_CHANGE_SET", "分组 id 不能为空或重复")
    }
    val entryIds = library.entries.map { it.id }
    if (entryIds.any(String::isBlank) || entryIds.distinct().size != entryIds.size) {
        throw CreatorAuthoringException("INVALID_CHANGE_SET", "条目 id 不能为空或重复")
    }
    val groupsById = library.groups.associateBy { it.id }
    library.groups.forEach { group ->
        if (group.name.isBlank()) throw CreatorAuthoringException("INVALID_CHANGE_SET", "分组名称不能为空")
        if (group.parentId.isNotBlank() && group.parentId !in groupsById) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "分组父级不存在：${group.name}")
        }
        val visited = mutableSetOf(group.id)
        var parentId = group.parentId
        while (parentId.isNotBlank()) {
            if (!visited.add(parentId)) throw CreatorAuthoringException("INVALID_CHANGE_SET", "分组出现循环：${group.name}")
            parentId = groupsById[parentId]?.parentId.orEmpty()
        }
    }
    library.entries.forEach { entry ->
        if (entry.title.isBlank()) throw CreatorAuthoringException("INVALID_CHANGE_SET", "设定标题不能为空")
        if (entry.content.isBlank() && !entry.isPinnedEntry()) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "设定正文不能为空：${entry.title}")
        }
        if (entry.groupId.isNotBlank() && entry.groupId !in groupsById) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "条目分组不存在：${entry.title}")
        }
        if (entry.promptPositionId.isNotBlank() && entry.promptPositionId !in promptPositionIdSet) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "条目引用的提示词位置不存在：${entry.title}")
        }
        if (
            !entry.isPinnedEntry() &&
            entry.triggerMode == SettingLibraryTriggerMode.Always &&
            entry.position == null
        ) {
            throw CreatorAuthoringException("INVALID_CHANGE_SET", "提示词常驻条目必须选择插入位置：${entry.title}")
        }
    }
}

private fun JsonObject.requiredId(index: Int): String = creatorString("id")
    .takeIf(String::isNotBlank)
    ?: invalid(index, "id 不能为空")

private fun JsonObject.requiredText(name: String, index: Int, maxLength: Int): String =
    creatorString(name).takeIf(String::isNotBlank)?.take(maxLength)
        ?: invalid(index, "$name 不能为空")

private fun JsonObject.stringArray(name: String, index: Int): List<String> =
    creatorArray(name)?.mapIndexed { itemIndex, element ->
        (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            ?: invalid(index, "$name[$itemIndex] 必须是非空字符串")
    }?.distinct().orEmpty()

private fun groupDescendants(id: String, groups: Collection<SettingLibraryGroup>): Set<String> {
    val descendants = mutableSetOf<String>()
    var changed = true
    while (changed) {
        val before = descendants.size
        groups.forEach { group ->
            if (group.parentId == id || group.parentId in descendants) descendants += group.id
        }
        changed = descendants.size != before
    }
    return descendants
}

private fun invalid(index: Int, message: String): Nothing =
    throw CreatorAuthoringException("INVALID_CHANGE_SET", "operations[$index]：$message")
