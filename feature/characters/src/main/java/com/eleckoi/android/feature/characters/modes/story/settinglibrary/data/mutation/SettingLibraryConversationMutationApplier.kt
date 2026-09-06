package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeEntity
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry

/** Applies request-scoped conversation changes without making the Room repository a mutation DSL. */
internal fun applySettingLibrarySessionMutations(
    sessionId: String,
    mutations: List<SettingLibrarySessionMutation>,
    base: SettingLibrary,
    persisted: List<ConversationSettingChangeEntity>,
    upsertChanges: (List<ConversationSettingChangeEntity>) -> Unit,
): SettingLibrarySessionMutationResult {
    if (sessionId.isBlank()) throw ElecKoiDataException("当前对话不存在，不能修改动态设定")
    if (mutations.isEmpty()) throw ElecKoiDataException("至少提供一项设定变更")
    if (mutations.size > MaxSessionMutationsPerCall) {
        throw ElecKoiDataException("一次最多修改 $MaxSessionMutationsPerCall 项设定")
    }

    val changesByTarget = persisted.associateByTo(linkedMapOf()) { change ->
        change.targetType to change.targetId
    }
    val staged = linkedMapOf<Pair<String, String>, ConversationSettingChangeEntity>()
    val applied = mutableListOf<SettingLibraryAppliedMutation>()
    var effective = SettingLibraryConversationOverlay.merge(base, changesByTarget.values.toList())

    fun stage(
        targetType: String,
        targetId: String,
        operation: String,
        payloadJson: String,
    ) {
        val entity = ConversationSettingChangeEntity(
            sessionId = sessionId,
            targetType = targetType,
            targetId = targetId,
            operation = operation,
            payloadJson = payloadJson,
            updatedAt = nowIso(),
        )
        val key = targetType to targetId
        changesByTarget[key] = entity
        staged[key] = entity
        effective = SettingLibraryConversationOverlay.merge(base, changesByTarget.values.toList())
        SettingLibraryNormalizer.requireUniqueLogicalNames(
            entries = effective.entries.filterNot(SettingLibraryEntry::isFixedEntry),
            groups = effective.groups,
        )
    }

    mutations.forEach { mutation ->
        when (mutation) {
            is SettingLibrarySessionMutation.CreateEntry -> {
                requireExistingGroup(mutation.groupId, effective.groups)
                val entry = SettingLibraryEntry(
                    id = mutation.entryId,
                    title = requiredEntryTitle(mutation.title),
                    groupId = mutation.groupId,
                    content = requiredEntryContent(mutation.content),
                    agentSelectionHint = normalizedSelectionHint(mutation.selectionHint),
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                    enabled = true,
                    viewOrder = (effective.entries.maxOfOrNull(SettingLibraryEntry::viewOrder) ?: 0) + 1,
                    groupViewOrder = effective.entries.count { it.groupId == mutation.groupId } + 1,
                    treeViewOrder = nextTreeViewOrder(mutation.groupId, effective),
                    createdAt = nowIso(),
                    updatedAt = nowIso(),
                )
                stage(EntryTarget, entry.id, UpsertOperation, SettingLibraryJsonCodec.entryToJson(entry).toString())
                applied += SettingLibraryAppliedMutation(
                    operation = "create_entry",
                    targetType = EntryTarget,
                    targetId = entry.id,
                    title = entry.title,
                )
            }

            is SettingLibrarySessionMutation.UpdateEntry -> {
                val current = requireMutableAgentEntry(mutation.entryId, effective.entries)
                val nextGroupId = mutation.groupId ?: current.groupId
                requireExistingGroup(nextGroupId, effective.groups)
                if (
                    mutation.groupId == null && mutation.title == null && mutation.content == null &&
                    mutation.selectionHint == null
                ) {
                    throw ElecKoiDataException("update_entry 没有提供需要修改的字段")
                }
                val updated = current.copy(
                    title = mutation.title?.let(::requiredEntryTitle) ?: current.title,
                    groupId = nextGroupId,
                    content = mutation.content?.let(::requiredEntryContent) ?: current.content,
                    agentSelectionHint = mutation.selectionHint?.let(::normalizedSelectionHint)
                        ?: current.agentSelectionHint,
                    updatedAt = nowIso(),
                )
                if (updated.copy(updatedAt = current.updatedAt) != current) {
                    stage(
                        EntryTarget,
                        updated.id,
                        UpsertOperation,
                        SettingLibraryJsonCodec.entryToJson(updated).toString(),
                    )
                }
                applied += SettingLibraryAppliedMutation(
                    operation = "update_entry",
                    targetType = EntryTarget,
                    targetId = updated.id,
                    title = updated.title,
                )
            }

            is SettingLibrarySessionMutation.DeleteEntry -> {
                val current = requireMutableAgentEntry(mutation.entryId, effective.entries)
                stage(EntryTarget, current.id, DeleteOperation, "")
                applied += SettingLibraryAppliedMutation(
                    operation = "delete_entry",
                    targetType = EntryTarget,
                    targetId = current.id,
                    title = current.title,
                )
            }

            is SettingLibrarySessionMutation.CreateGroup -> {
                requireExistingGroup(mutation.parentId, effective.groups)
                val group = SettingLibraryGroup(
                    id = mutation.groupId,
                    name = requiredGroupName(mutation.name),
                    parentId = mutation.parentId,
                    order = effective.groups.size + 1,
                    treeViewOrder = nextTreeViewOrder(mutation.parentId, effective),
                    createdAt = nowIso(),
                    updatedAt = nowIso(),
                )
                stage(GroupTarget, group.id, UpsertOperation, SettingLibraryJsonCodec.groupToJson(group).toString())
                applied += SettingLibraryAppliedMutation(
                    operation = "create_group",
                    targetType = GroupTarget,
                    targetId = group.id,
                    title = group.name,
                )
            }

            is SettingLibrarySessionMutation.UpdateGroup -> {
                val current = effective.groups.firstOrNull { it.id == mutation.groupId }
                    ?: throw ElecKoiDataException("找不到 group_id：${mutation.groupId}")
                if (mutation.parentId == null && mutation.name == null) {
                    throw ElecKoiDataException("update_group 没有提供需要修改的字段")
                }
                val nextParentId = mutation.parentId ?: current.parentId
                requireValidGroupParent(current.id, nextParentId, effective.groups)
                val updated = current.copy(
                    name = mutation.name?.let(::requiredGroupName) ?: current.name,
                    parentId = nextParentId,
                    updatedAt = nowIso(),
                )
                if (updated.copy(updatedAt = current.updatedAt) != current) {
                    stage(
                        GroupTarget,
                        updated.id,
                        UpsertOperation,
                        SettingLibraryJsonCodec.groupToJson(updated).toString(),
                    )
                }
                applied += SettingLibraryAppliedMutation(
                    operation = "update_group",
                    targetType = GroupTarget,
                    targetId = updated.id,
                    title = updated.name,
                )
            }

            is SettingLibrarySessionMutation.DeleteGroup -> {
                val current = effective.groups.firstOrNull { it.id == mutation.groupId }
                    ?: throw ElecKoiDataException("找不到 group_id：${mutation.groupId}")
                stage(GroupTarget, current.id, DeleteOperation, "")
                applied += SettingLibraryAppliedMutation(
                    operation = "delete_group",
                    targetType = GroupTarget,
                    targetId = current.id,
                    title = current.name,
                )
            }
        }
    }

    upsertChanges(staged.values.toList())
    return SettingLibrarySessionMutationResult(applied = applied, effectiveLibrary = effective)
}

private fun requireMutableAgentEntry(
    entryId: String,
    entries: List<SettingLibraryEntry>,
): SettingLibraryEntry {
    val entry = entries.firstOrNull { it.id == entryId }
        ?: throw ElecKoiDataException("找不到 entry_id：$entryId")
    if (entry.isFixedEntry() || entry.triggerMode != SettingLibraryTriggerMode.AgentTool) {
        throw ElecKoiDataException("这个条目不允许由设定库工具修改：$entryId")
    }
    return entry
}

private fun requireExistingGroup(groupId: String, groups: List<SettingLibraryGroup>) {
    if (groupId.isBlank()) return
    if (groups.none { it.id == groupId }) throw ElecKoiDataException("找不到 group_id：$groupId")
}

private fun requireValidGroupParent(
    groupId: String,
    parentId: String,
    groups: List<SettingLibraryGroup>,
) {
    requireExistingGroup(parentId, groups)
    if (parentId.isBlank()) return
    val byId = groups.associateBy(SettingLibraryGroup::id)
    val visited = mutableSetOf<String>()
    var current = parentId
    while (current.isNotBlank() && visited.add(current)) {
        if (current == groupId) throw ElecKoiDataException("文件夹不能移动到自己或自己的子文件夹中")
        current = byId[current]?.parentId.orEmpty()
    }
}

private fun requiredEntryTitle(value: String): String = value.trim().take(MaxSessionEntryTitleCharacters)
    .takeIf(String::isNotBlank)
    ?: throw ElecKoiDataException("设定标题不能为空")

private fun requiredEntryContent(value: String): String = value.trim()
    .takeIf(String::isNotBlank)
    ?: throw ElecKoiDataException("设定正文不能为空")

private fun normalizedSelectionHint(value: String): String = value
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(MaxSettingLibrarySelectionHintCharacters)

private fun requiredGroupName(value: String): String = value.trim().take(MaxSessionGroupNameCharacters)
    .takeIf(String::isNotBlank)
    ?: throw ElecKoiDataException("文件夹名称不能为空")

private fun nextTreeViewOrder(parentId: String, library: SettingLibrary): Int {
    val groupMax = library.groups
        .filter { it.parentId == parentId }
        .maxOfOrNull(SettingLibraryGroup::treeViewOrder)
        ?: 0
    val entryMax = library.entries
        .filter { it.groupId == parentId }
        .maxOfOrNull(SettingLibraryEntry::treeViewOrder)
        ?: 0
    return maxOf(groupMax, entryMax) + 1
}

private const val EntryTarget = "entry"
private const val GroupTarget = "group"
private const val UpsertOperation = "upsert"
private const val DeleteOperation = "delete"
private const val MaxSessionMutationsPerCall = 24
private const val MaxSessionEntryTitleCharacters = 120
private const val MaxSessionGroupNameCharacters = 80
