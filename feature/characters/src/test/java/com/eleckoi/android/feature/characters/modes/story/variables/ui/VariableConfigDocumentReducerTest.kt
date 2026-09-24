package com.eleckoi.android.feature.characters.modes.story.variables.ui

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableConfigDocumentReducerTest {
    @Test
    fun `editor exposes the json pointer accepted by variable tools`() {
        val state = VariableConfigEditorState(
            VariableConfig(
                characterId = "character-1",
                objects = listOf(
                    VariableObjectConfig(id = "character", name = "星见/绫音"),
                    VariableObjectConfig(id = "relationship", name = "关系~状态", parentId = "character"),
                ),
                variables = listOf(
                    VariableItemConfig(id = "affection", title = "好感度", objectId = "relationship"),
                ),
            ),
        )

        assertEquals("/星见~1绫音/关系~0状态/好感度", state.variablePath("affection"))
        assertEquals("/星见~1绫音/关系~0状态", state.objectPath("relationship"))
    }

    @Test
    fun `switching version snapshots the current immutable document first`() {
        val first = VariableConfigVersion(id = "first", name = "旧名称")
        val second = VariableConfigVersion(id = "second", name = "第二版")
        val document = VariableConfigDocument(
            name = "尚未保存的新名称",
            variables = listOf(VariableItemConfig(id = "new-variable", title = "新变量")),
            versions = listOf(first, second),
            activeVersionId = first.id,
        )

        val switched = VariableConfigDocumentReducer.reduce(
            document,
            VariableConfigDocumentAction.SwitchVersion(second),
        )

        assertEquals("second", switched.activeVersionId)
        assertEquals("第二版", switched.name)
        val savedFirst = switched.versions.single { it.id == "first" }
        assertEquals("尚未保存的新名称", savedFirst.name)
        assertEquals("new-variable", savedFirst.variables.single().id)
    }

    @Test
    fun `creating from current version includes unsaved edits and keeps original`() {
        val document = VariableConfigDocument(
            name = "当前未保存的名称",
            initialStateJson = "{\"score\":1}",
            schemaCode = "return true",
            variables = listOf(VariableItemConfig(id = "score", title = "分数")),
            versions = listOf(VariableConfigVersion(id = "current", name = "旧名称")),
            activeVersionId = "current",
        )

        val created = VariableConfigDocumentReducer.reduce(
            document,
            VariableConfigDocumentAction.CreateVersion("new", "当前副本", "current"),
        )

        assertEquals("new", created.activeVersionId)
        assertEquals("当前副本", created.name)
        assertEquals("{\"score\":1}", created.initialStateJson)
        assertEquals("return true", created.schemaCode)
        assertEquals("score", created.variables.single().id)
        assertEquals("当前未保存的名称", created.versions.single { it.id == "current" }.name)
    }

    @Test
    fun `creating from historical version or blank uses the selected source`() {
        val document = VariableConfigDocument(
            name = "当前",
            schemaCode = "current code",
            versions = listOf(
                VariableConfigVersion(id = "current", name = "当前"),
                VariableConfigVersion(id = "old", name = "旧版", schemaCode = "old code"),
            ),
            activeVersionId = "current",
        )

        val copied = VariableConfigDocumentReducer.reduce(
            document,
            VariableConfigDocumentAction.CreateVersion("copy", "旧版副本", "old"),
        )
        assertEquals("old code", copied.schemaCode)
        assertEquals("旧版副本", copied.name)

        val blank = VariableConfigDocumentReducer.reduce(
            document,
            VariableConfigDocumentAction.CreateVersion("blank", "空白版", null),
        )
        assertEquals("空白版", blank.name)
        assertTrue(blank.schemaCode.isEmpty())
        assertTrue(blank.objects.isEmpty())
        assertTrue(blank.variables.isEmpty())
    }

    @Test
    fun `selecting current version does not discard pending edits`() {
        val oldSnapshot = VariableConfigVersion(id = "current", name = "旧名称")
        val document = VariableConfigDocument(
            name = "新名称",
            initialStateJson = "{\"ready\":true}",
            versions = listOf(oldSnapshot),
            activeVersionId = "current",
        )

        val selected = VariableConfigDocumentReducer.reduce(
            document,
            VariableConfigDocumentAction.SwitchVersion(oldSnapshot),
        )

        assertEquals("新名称", selected.name)
        assertEquals("{\"ready\":true}", selected.initialStateJson)
    }
}
