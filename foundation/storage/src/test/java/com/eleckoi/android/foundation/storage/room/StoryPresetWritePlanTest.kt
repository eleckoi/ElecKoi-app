package com.eleckoi.android.foundation.storage.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryPresetWritePlanTest {
    @Test fun `editing one preset entry does not rewrite unchanged rows`() {
        val current = presetRecord(
            entries = listOf(entry("a", "old"), entry("b", "same")),
            groups = listOf(group("g")),
        )
        val incoming = current.copy(entries = listOf(entry("a", "new"), entry("b", "same")))

        val plan = storyPresetWritePlan(current, incoming)

        assertNull(plan.preset)
        assertEquals(listOf("a"), plan.upsertEntries.map { it.entryId })
        assertEquals(emptyList<String>(), plan.deleteEntryIds)
        assertEquals(emptyList<StoryPresetGroupEntity>(), plan.upsertGroups)
    }

    @Test fun `deleting one version entry leaves every retained row untouched`() {
        val current = versionRecord(listOf(versionEntry("a", "same"), versionEntry("b", "same")))
        val incoming = current.copy(entries = listOf(versionEntry("b", "same")))

        val plan = storyPresetVersionWritePlan(current, incoming)

        assertNull(plan.version)
        assertEquals(listOf("a"), plan.deleteEntryIds)
        assertEquals(emptyList<StoryPresetVersionEntryEntity>(), plan.upsertEntries)
    }

    private fun presetRecord(
        entries: List<StoryPresetEntryEntity>,
        groups: List<StoryPresetGroupEntity> = emptyList(),
    ) = StoryPresetRecord(
        preset = StoryPresetEntity(
            id = "p", name = "Preset", modelFamily = "general", modelTagsJson = "[]",
            libraryGroupId = "", activeVersionId = "v", authorName = "", authorAvatarPath = "",
            authorTagsJson = "[]", description = "",
            sortIndex = 0, expandedGroupIdsJson = "[]",
        ),
        contents = listOf(
            StoryPresetContentEntity("p", "timeline", "[]"),
            StoryPresetContentEntity("p", "regex_rules", "[]"),
            StoryPresetContentEntity("p", "prompt_positions", "[]"),
        ),
        entries = entries,
        groups = groups,
        runtimeEntries = emptyList(),
    )

    private fun versionRecord(entries: List<StoryPresetVersionEntryEntity>) = StoryPresetVersionRecord(
        version = StoryPresetVersionEntity(
            presetId = "p", versionId = "v", versionNumber = 1, name = "Preset",
            createdAtEpochMs = 1L, expandedGroupIdsJson = "[]",
        ),
        contents = listOf(
            StoryPresetVersionContentEntity("p", "v", "regex_rules", "[]"),
        ),
        entries = entries,
        groups = emptyList(),
        runtimeEntries = emptyList(),
    )

    private fun entry(id: String, payload: String) = StoryPresetEntryEntity("p", id, 0, payload)
    private fun group(id: String) = StoryPresetGroupEntity("p", id, 0, "{}")
    private fun versionEntry(id: String, payload: String) =
        StoryPresetVersionEntryEntity("p", "v", id, 0, payload)
}
