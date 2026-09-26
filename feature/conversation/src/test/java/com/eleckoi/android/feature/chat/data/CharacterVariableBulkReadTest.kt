package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterVariableBulkReadTest {
    @Test
    fun `read returns all requested variables beyond sixteen paths`() = runBlocking {
        val names = (1..20).map { "item$it" }
        val initialState = JSONObject().apply {
            names.forEachIndexed { index, name -> put(name, index + 1) }
        }.toString()
        val config = VariableConfig(
            characterId = "example-character",
            initialStateJson = initialState,
            variables = names.mapIndexed { index, name ->
                VariableItemConfig(id = "variable-$index", title = name, type = "number")
            },
        )
        val readTool = characterVariableTools(
            config = config,
            turnState = CharacterVariableTurnState(initialState),
            validateState = { _, _ -> VariableRuntimeCheckResult(ok = true) },
            virtualFileSearch = FakeAgentVirtualFileSearch(),
        ).single { it.definition.name == AgentReadVariablesTool }

        assertFalse(
            readTool.definition.parameters.getValue("properties").jsonObject
                .getValue("paths").jsonObject.containsKey("maxItems"),
        )
        val result = readTool.handler.execute(buildJsonObject {
            put("paths", JsonArray(names.map { JsonPrimitive("/$it") }))
        })

        assertTrue(result.success)
        val variables = Json.parseToJsonElement(result.content).jsonObject
            .getValue("variables").jsonArray
        assertEquals(20, variables.size)
        assertEquals("/item20", variables.last().jsonObject.getValue("path").jsonPrimitive.content)
    }
}
