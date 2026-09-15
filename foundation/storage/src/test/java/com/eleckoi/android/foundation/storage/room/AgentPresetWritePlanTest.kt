package com.eleckoi.android.foundation.storage.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentPresetWritePlanTest {
    @Test fun `editing one preset entry does not rewrite unchanged rows`() {
        val current = presetRecord(
            entries = listOf(entry("a", "old"), entry("b", "same")),
            groups = listOf(group("g")),
        )
        val incoming = current.copy(entries = listOf(entry("a", "new"), entry("b", "same")))

        val plan = agentPresetWritePlan(current, incoming)

        assertNull(plan.preset)
        assertEquals(listOf("a"), plan.upsertEntries.map { it.entryId })
        assertEquals(emptyList<String>(), plan.deleteEntryIds)
        assertEquals(emptyList<AgentPresetGroupEntity>(), plan.upsertGroups)
    }

    @Test fun `deleting one version entry leaves every retained row untouched`() {
        val current = versionRecord(listOf(versionEntry("a", "same"), versionEntry("b", "same")))
        val incoming = current.copy(entries = listOf(versionEntry("b", "same")))

        val plan = agentPresetVersionWritePlan(current, incoming)

        assertNull(plan.version)
        assertEquals(listOf("a"), plan.deleteEntryIds)
        assertEquals(emptyList<AgentPresetVersionEntryEntity>(), plan.upsertEntries)
    }

    private fun presetRecord(
        entries: List<AgentPresetEntryEntity>,
        groups: List<AgentPresetGroupEntity> = emptyList(),
    ) = AgentPresetRecord(
        preset = AgentPresetEntity(
            id = "p", name = "Preset", modelFamily = "general", modelTagsJson = "[]",
            libraryGroupId = "", activeVersionId = "v", authorName = "", authorAvatarPath = "",
            sortIndex = 0, expandedGroupIdsJson = "[]",
        ),
        contents = listOf(
            AgentPresetContentEntity("p", "usage_instructions", ""),
            AgentPresetContentEntity("p", "timeline", "[]"),
            AgentPresetContentEntity("p", "regex_rules", "[]"),
            AgentPresetContentEntity("p", "prompt_positions", "[]"),
        ),
        entries = entries,
        groups = groups,
    )

    private fun versionRecord(entries: List<AgentPresetVersionEntryEntity>) = AgentPresetVersionRecord(
        version = AgentPresetVersionEntity(
            presetId = "p", versionId = "v", versionNumber = 1, name = "Preset",
            createdAtEpochMs = 1L, expandedGroupIdsJson = "[]",
        ),
        contents = listOf(
            AgentPresetVersionContentEntity("p", "v", "regex_rules", "[]"),
        ),
        entries = entries,
        groups = emptyList(),
    )

    private fun entry(id: String, payload: String) = AgentPresetEntryEntity("p", id, 0, payload)
    private fun group(id: String) = AgentPresetGroupEntity("p", id, 0, "{}")
    private fun versionEntry(id: String, payload: String) =
        AgentPresetVersionEntryEntity("p", "v", id, 0, payload)
}
