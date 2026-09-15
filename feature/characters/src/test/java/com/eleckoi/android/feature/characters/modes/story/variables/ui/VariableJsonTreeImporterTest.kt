package com.eleckoi.android.feature.characters.modes.story.variables.ui

import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.model.VariableReadMode
import com.eleckoi.android.engine.story.variables.model.VariableValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableJsonTreeImporterTest {
    private val importer = VariableJsonTreeImporter(
        objectId = { offset -> "object-$offset" },
        variableId = { offset -> "variable-$offset" },
    )

    @Test
    fun `nested json becomes a deterministic object and variable tree`() {
        val result = importer.replaceChildren(
            targetObjectId = "target",
            rawJson = """{"profile":{"name":"sample","age":2},"tags":["friend"],"ready":true}""",
            objects = listOf(VariableObjectConfig(id = "target", name = "目标")),
            variables = emptyList(),
            expandedObjectIds = emptySet(),
        ) as VariableJsonTreeImportResult.Success

        val profile = result.objects.single { it.name == "profile" }
        assertEquals("target", profile.parentId)
        assertEquals(VariableValueType.String.raw, result.variables.single { it.title == "name" }.type)
        assertEquals(VariableValueType.Number.raw, result.variables.single { it.title == "age" }.type)
        assertEquals(VariableValueType.Array.raw, result.variables.single { it.title == "tags" }.type)
        assertEquals(VariableValueType.Boolean.raw, result.variables.single { it.title == "ready" }.type)
        assertTrue("target" in result.expandedObjectIds)
        assertTrue(profile.id in result.expandedObjectIds)
    }

    @Test
    fun `null is rejected without changing the tree`() {
        val result = importer.replaceChildren(
            targetObjectId = "target",
            rawJson = """{"unsupported":null}""",
            objects = listOf(VariableObjectConfig(id = "target", name = "目标")),
            variables = emptyList(),
            expandedObjectIds = emptySet(),
        )

        assertEquals(
            "暂不支持 null：unsupported",
            (result as VariableJsonTreeImportResult.Failure).message,
        )
    }

    @Test
    fun `editing object json preserves variable descriptions and update rules at the same path`() {
        val result = importer.replaceChildren(
            targetObjectId = "target",
            rawJson = """{"profile":{"affinity":20}}""",
            objects = listOf(
                VariableObjectConfig(id = "target", name = "目标"),
                VariableObjectConfig(id = "profile", name = "profile", parentId = "target"),
            ),
            variables = listOf(
                VariableItemConfig(
                    id = "affinity",
                    title = "affinity",
                    objectId = "profile",
                    type = VariableValueType.Number.raw,
                    defaultValue = "10",
                    description = "角色对用户的好感程度",
                    updateRule = "根据互动结果小幅增减",
                    readMode = VariableReadMode.Required,
                ),
            ),
            expandedObjectIds = emptySet(),
        ) as VariableJsonTreeImportResult.Success

        val affinity = result.variables.single { it.title == "affinity" }
        assertEquals("角色对用户的好感程度", affinity.description)
        assertEquals("根据互动结果小幅增减", affinity.updateRule)
        assertEquals(VariableReadMode.Required, affinity.readMode)
        assertEquals("20", affinity.defaultValue)
    }

    @Test
    fun `editing object json preserves nested object descriptions and update rules at the same path`() {
        val result = importer.replaceChildren(
            targetObjectId = "target",
            rawJson = """{"profile":{"affinity":20}}""",
            objects = listOf(
                VariableObjectConfig(id = "target", name = "目标"),
                VariableObjectConfig(
                    id = "profile",
                    name = "profile",
                    parentId = "target",
                    description = "角色关系状态",
                    updateRule = "关系发生明确变化时更新其子变量",
                ),
            ),
            variables = listOf(
                VariableItemConfig(
                    id = "affinity",
                    title = "affinity",
                    objectId = "profile",
                    type = VariableValueType.Number.raw,
                    defaultValue = "10",
                ),
            ),
            expandedObjectIds = emptySet(),
        ) as VariableJsonTreeImportResult.Success

        val profile = result.objects.single { it.name == "profile" }
        assertEquals("角色关系状态", profile.description)
        assertEquals("关系发生明确变化时更新其子变量", profile.updateRule)
    }
}
