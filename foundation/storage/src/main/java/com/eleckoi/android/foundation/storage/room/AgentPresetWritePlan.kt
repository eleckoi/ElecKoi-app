package com.eleckoi.android.foundation.storage.room

internal data class AgentPresetWritePlan(
    val preset: AgentPresetEntity?,
    val upsertContents: List<AgentPresetContentEntity>,
    val deleteContentKinds: List<String>,
    val upsertEntries: List<AgentPresetEntryEntity>,
    val deleteEntryIds: List<String>,
    val upsertGroups: List<AgentPresetGroupEntity>,
    val deleteGroupIds: List<String>,
)

internal data class AgentPresetVersionWritePlan(
    val version: AgentPresetVersionEntity?,
    val upsertContents: List<AgentPresetVersionContentEntity>,
    val deleteContentKinds: List<String>,
    val upsertEntries: List<AgentPresetVersionEntryEntity>,
    val deleteEntryIds: List<String>,
    val upsertGroups: List<AgentPresetVersionGroupEntity>,
    val deleteGroupIds: List<String>,
)

internal fun agentPresetWritePlan(
    current: AgentPresetRecord?,
    incoming: AgentPresetRecord,
): AgentPresetWritePlan {
    val currentEntries = current?.entries.orEmpty().associateBy(AgentPresetEntryEntity::entryId)
    val incomingEntries = incoming.entries.associateBy(AgentPresetEntryEntity::entryId)
    val currentGroups = current?.groups.orEmpty().associateBy(AgentPresetGroupEntity::groupId)
    val incomingGroups = incoming.groups.associateBy(AgentPresetGroupEntity::groupId)
    val currentContents = current?.contents.orEmpty().associateBy(AgentPresetContentEntity::kind)
    val incomingContents = incoming.contents.associateBy(AgentPresetContentEntity::kind)
    return AgentPresetWritePlan(
        preset = incoming.preset.takeIf { it != current?.preset },
        upsertContents = incoming.contents.filter { it != currentContents[it.kind] },
        deleteContentKinds = currentContents.keys.filterNot(incomingContents::containsKey),
        upsertEntries = incoming.entries.filter { it != currentEntries[it.entryId] },
        deleteEntryIds = currentEntries.keys.filterNot(incomingEntries::containsKey),
        upsertGroups = incoming.groups.filter { it != currentGroups[it.groupId] },
        deleteGroupIds = currentGroups.keys.filterNot(incomingGroups::containsKey),
    )
}

internal fun agentPresetVersionWritePlan(
    current: AgentPresetVersionRecord?,
    incoming: AgentPresetVersionRecord,
): AgentPresetVersionWritePlan {
    val currentEntries = current?.entries.orEmpty().associateBy(AgentPresetVersionEntryEntity::entryId)
    val incomingEntries = incoming.entries.associateBy(AgentPresetVersionEntryEntity::entryId)
    val currentGroups = current?.groups.orEmpty().associateBy(AgentPresetVersionGroupEntity::groupId)
    val incomingGroups = incoming.groups.associateBy(AgentPresetVersionGroupEntity::groupId)
    val currentContents = current?.contents.orEmpty().associateBy(AgentPresetVersionContentEntity::kind)
    val incomingContents = incoming.contents.associateBy(AgentPresetVersionContentEntity::kind)
    return AgentPresetVersionWritePlan(
        version = incoming.version.takeIf { it != current?.version },
        upsertContents = incoming.contents.filter { it != currentContents[it.kind] },
        deleteContentKinds = currentContents.keys.filterNot(incomingContents::containsKey),
        upsertEntries = incoming.entries.filter { it != currentEntries[it.entryId] },
        deleteEntryIds = currentEntries.keys.filterNot(incomingEntries::containsKey),
        upsertGroups = incoming.groups.filter { it != currentGroups[it.groupId] },
        deleteGroupIds = currentGroups.keys.filterNot(incomingGroups::containsKey),
    )
}
