package com.eleckoi.android.app

import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.agent.tools.AgentToolScopes
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterToolDefaultsTest {
    @Test
    fun `new character enables its setting library and variables only`() {
        val calls = mutableListOf<Triple<String, String, Boolean>>()

        initializeCharacterToolDefaults("character-new") { scopeId, groupId, enabled ->
            calls += Triple(scopeId, groupId, enabled)
        }

        assertEquals(
            listOf(
                Triple(
                    AgentToolScopes.character("character-new"),
                    AgentToolRequestPolicy.BuiltInSettingLibrary,
                    true,
                ),
                Triple(
                    AgentToolScopes.character("character-new"),
                    AgentToolRequestPolicy.BuiltInVariables,
                    true,
                ),
            ),
            calls,
        )
    }

    @Test
    fun `backup import initializes tools only for characters absent before restore`() {
        val initialized = mutableListOf<String>()

        initializeMissingCharacterToolDefaults(
            existingCharacterIds = listOf("character-existing"),
            savedCharacterIds = listOf("character-existing", "character-imported", "character-imported"),
            initializeCharacterTools = initialized::add,
        )

        assertEquals(listOf("character-imported"), initialized)
    }
}
