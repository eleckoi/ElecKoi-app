package com.eleckoi.android.foundation.storage.room

internal data class StoryPresetWritePlan(
    val preset: StoryPresetEntity?,
    val upsertContents: List<StoryPresetContentEntity>,
    val deleteContentKinds: List<String>,
    val upsertEntries: List<StoryPresetEntryEntity>,
    val deleteEntryIds: List<String>,
    val upsertGroups: List<StoryPresetGroupEntity>,
    val deleteGroupIds: List<String>,
    val upsertRuntimeEntries: List<StoryPresetRuntimeEntryEntity>,
    val deleteRuntimeSlots: List<String>,
)

internal data class StoryPresetVersionWritePlan(
    val version: StoryPresetVersionEntity?,
    val upsertContents: List<StoryPresetVersionContentEntity>,
    val deleteContentKinds: List<String>,
    val upsertEntries: List<StoryPresetVersionEntryEntity>,
    val deleteEntryIds: List<String>,
    val upsertGroups: List<StoryPresetVersionGroupEntity>,
    val deleteGroupIds: List<String>,
    val upsertRuntimeEntries: List<StoryPresetVersionRuntimeEntryEntity>,
    val deleteRuntimeSlots: List<String>,
)

internal fun storyPresetWritePlan(
    current: StoryPresetRecord?,
    incoming: StoryPresetRecord,
): StoryPresetWritePlan {
    val currentEntries = current?.entries.orEmpty().associateBy(StoryPresetEntryEntity::entryId)
    val incomingEntries = incoming.entries.associateBy(StoryPresetEntryEntity::entryId)
    val currentGroups = current?.groups.orEmpty().associateBy(StoryPresetGroupEntity::groupId)
    val incomingGroups = incoming.groups.associateBy(StoryPresetGroupEntity::groupId)
    val currentRuntime = current?.runtimeEntries.orEmpty().associateBy(StoryPresetRuntimeEntryEntity::slot)
    val incomingRuntime = incoming.runtimeEntries.associateBy(StoryPresetRuntimeEntryEntity::slot)
    val currentContents = current?.contents.orEmpty().associateBy(StoryPresetContentEntity::kind)
    val incomingContents = incoming.contents.associateBy(StoryPresetContentEntity::kind)
    return StoryPresetWritePlan(
        preset = incoming.preset.takeIf { it != current?.preset },
        upsertContents = incoming.contents.filter { it != currentContents[it.kind] },
        deleteContentKinds = currentContents.keys.filterNot(incomingContents::containsKey),
        upsertEntries = incoming.entries.filter { it != currentEntries[it.entryId] },
        deleteEntryIds = currentEntries.keys.filterNot(incomingEntries::containsKey),
        upsertGroups = incoming.groups.filter { it != currentGroups[it.groupId] },
        deleteGroupIds = currentGroups.keys.filterNot(incomingGroups::containsKey),
        upsertRuntimeEntries = incoming.runtimeEntries.filter { it != currentRuntime[it.slot] },
        deleteRuntimeSlots = currentRuntime.keys.filterNot(incomingRuntime::containsKey),
    )
}

internal fun storyPresetVersionWritePlan(
    current: StoryPresetVersionRecord?,
    incoming: StoryPresetVersionRecord,
): StoryPresetVersionWritePlan {
    val currentEntries = current?.entries.orEmpty().associateBy(StoryPresetVersionEntryEntity::entryId)
    val incomingEntries = incoming.entries.associateBy(StoryPresetVersionEntryEntity::entryId)
    val currentGroups = current?.groups.orEmpty().associateBy(StoryPresetVersionGroupEntity::groupId)
    val incomingGroups = incoming.groups.associateBy(StoryPresetVersionGroupEntity::groupId)
    val currentRuntime = current?.runtimeEntries.orEmpty().associateBy(StoryPresetVersionRuntimeEntryEntity::slot)
    val incomingRuntime = incoming.runtimeEntries.associateBy(StoryPresetVersionRuntimeEntryEntity::slot)
    val currentContents = current?.contents.orEmpty().associateBy(StoryPresetVersionContentEntity::kind)
    val incomingContents = incoming.contents.associateBy(StoryPresetVersionContentEntity::kind)
    return StoryPresetVersionWritePlan(
        version = incoming.version.takeIf { it != current?.version },
        upsertContents = incoming.contents.filter { it != currentContents[it.kind] },
        deleteContentKinds = currentContents.keys.filterNot(incomingContents::containsKey),
        upsertEntries = incoming.entries.filter { it != currentEntries[it.entryId] },
        deleteEntryIds = currentEntries.keys.filterNot(incomingEntries::containsKey),
        upsertGroups = incoming.groups.filter { it != currentGroups[it.groupId] },
        deleteGroupIds = currentGroups.keys.filterNot(incomingGroups::containsKey),
        upsertRuntimeEntries = incoming.runtimeEntries.filter { it != currentRuntime[it.slot] },
        deleteRuntimeSlots = currentRuntime.keys.filterNot(incomingRuntime::containsKey),
    )
}
