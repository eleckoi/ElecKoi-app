package com.eleckoi.android.engine.story.variables.config

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VariableConfigRoomCodecTest {
    @Test fun `large version collection uses separate rows and survives backup roundtrip`() {
        val versions = (0 until 300).map { index -> VariableConfigVersion(
            id = "v$index", name = "版本$index", schemaCode = "//" + "校验".repeat(4000),
            initialStateJson = "{\"progress\":$index}",
            variables = listOf(VariableItemConfig(id = "progress", title = "进度", type = "number", defaultValue = "$index")),
            createdAt = "created-$index", updatedAt = "updated-$index",
        ) }
        val config = VariableConfig(characterId = "card", activeVersionId = "v299", versions = versions)
        val record = VariableConfigJsonCodec.toRecord(config, "saved", revision = 7L)
        assertEquals(300, record.versions.size)
        val schemaRows = record.contents.filter { it.kind == "schema_code" }
        assertTrue(schemaRows.sumOf { it.content.toByteArray().size } > 2 * 1024 * 1024)
        assertTrue(schemaRows.all { it.content.toByteArray().size < 32 * 1024 })
        repeat(5) {
            val restored = VariableConfigJsonCodec.versionsFromRecord(record)
            assertEquals(versions.map { it.id }, restored.map { it.id })
            assertEquals(versions.map { it.schemaCode }, restored.map { it.schemaCode })
            assertEquals(versions.map { it.updatedAt }, restored.map { it.updatedAt })
            assertEquals(299, JSONObject(restored.last().initialStateJson).getInt("progress"))
        }
        val backup = VariableConfigJsonCodec.encode(config, "exported")
        val imported = VariableConfigJsonCodec.decodeRestore(backup)
        assertEquals("v299", imported.requestedActiveVersionId)
        assertEquals(300, imported.versions.size)
        assertEquals("updated-0", imported.versions.first().updatedAt)
    }

    @Test fun `editing one variable changes one variable row`() {
        val config = VariableConfig(
            characterId = "card",
            activeVersionId = "v1",
            versions = listOf(VariableConfigVersion(
                id = "v1",
                variables = listOf(
                    VariableItemConfig(id = "a", title = "甲"),
                    VariableItemConfig(id = "b", title = "乙"),
                ),
            )),
        )
        val before = VariableConfigJsonCodec.toRecord(config, "before", revision = 1L)
        val after = VariableConfigJsonCodec.toRecord(
            config.copy(versions = config.versions.map { version ->
                version.copy(variables = version.variables.map { item ->
                    if (item.id == "b") item.copy(title = "乙已修改") else item
                })
            }),
            "after",
            revision = 2L,
        )
        val previousRows = before.variables.associateBy { it.versionId to it.variableId }

        assertEquals(1, after.variables.count { it != previousRows[it.versionId to it.variableId] })
        assertEquals(
            previousRows["v1" to "a"],
            after.variables.single { it.variableId == "a" },
        )
    }

    @Test fun `write timestamps do not create a new variable revision by themselves`() {
        val original = VariableConfig(
            characterId = "card",
            activeVersionId = "v1",
            objects = listOf(VariableObjectConfig(id = "object", name = "状态", updatedAt = "old")),
            variables = listOf(VariableItemConfig(id = "value", title = "好感", updatedAt = "old")),
            versions = listOf(VariableConfigVersion(
                id = "v1",
                name = "主版本",
                objects = listOf(VariableObjectConfig(id = "object", name = "状态", updatedAt = "old")),
                variables = listOf(VariableItemConfig(id = "value", title = "好感", updatedAt = "old")),
                updatedAt = "old",
            )),
        )
        val rewrittenTimestamps = original.copy(
            objects = original.objects.map { it.copy(updatedAt = "new") },
            variables = original.variables.map { it.copy(updatedAt = "new") },
            versions = original.versions.map { version ->
                version.copy(
                    updatedAt = "new",
                    objects = version.objects.map { it.copy(updatedAt = "new") },
                    variables = version.variables.map { it.copy(updatedAt = "new") },
                )
            },
        )

        assertTrue(original.samePersistedContentAs(rewrittenTimestamps))
        assertFalse(original.samePersistedContentAs(
            rewrittenTimestamps.copy(name = "已修改"),
        ))
    }

    @Test fun `one edited variable keeps untouched row timestamps stable`() {
        val old = VariableConfig(
            characterId = "card",
            activeVersionId = "v1",
            versions = listOf(VariableConfigVersion(
                id = "v1",
                variables = listOf(
                    VariableItemConfig(id = "a", title = "甲", createdAt = "c-a", updatedAt = "u-a"),
                    VariableItemConfig(id = "b", title = "乙", createdAt = "c-b", updatedAt = "u-b"),
                ),
            )),
        )
        val normalized = old.copy(versions = old.versions.map { version ->
            version.copy(variables = version.variables.map { item ->
                if (item.id == "b") item.copy(title = "乙已修改", updatedAt = "normalizer-now")
                else item.copy(updatedAt = "normalizer-now")
            })
        })

        val stable = normalized.withStableItemTimestamps(old, now = "save-now")
        val rows = stable.versions.single().variables.associateBy(VariableItemConfig::id)

        assertEquals("u-a", rows.getValue("a").updatedAt)
        assertEquals("save-now", rows.getValue("b").updatedAt)
        assertEquals("c-b", rows.getValue("b").createdAt)
    }
}
