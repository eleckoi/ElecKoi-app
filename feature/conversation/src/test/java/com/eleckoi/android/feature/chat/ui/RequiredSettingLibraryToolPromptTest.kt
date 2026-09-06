package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequiredSettingLibraryToolPromptTest {
    @Test
    fun `disabled setting library is reported when assistant prints raw DSML invocation`() {
        val draft = draftWithAssistant(
            """
            I'll inspect the settings first.
            <|DSML|tool_calls><|DSML|invoke name="eleckoi_glob_setting_files">
            <|DSML|parameter name="pattern" string="true">*
            """.trimIndent(),
        )

        val prompt = requiredSettingLibraryToolPrompt(
            draft = draft,
            assistantMessageId = "assistant-1",
            settingLibraryToolEnabled = false,
        )

        assertEquals("character-1", prompt?.characterId)
        assertEquals("assistant-1", prompt?.assistantMessageId)
    }

    @Test
    fun `enabled setting library does not show the prompt`() {
        val draft = draftWithAssistant(
            "<|DSML|tool_calls><|DSML|invoke name=\"eleckoi_read_setting_files\">",
        )

        assertNull(
            requiredSettingLibraryToolPrompt(
                draft = draft,
                assistantMessageId = "assistant-1",
                settingLibraryToolEnabled = true,
            ),
        )
    }

    @Test
    fun `ordinary mention of setting tool is not treated as a failed invocation`() {
        val draft = draftWithAssistant("You can use eleckoi_glob_setting_files when needed.")

        assertNull(
            requiredSettingLibraryToolPrompt(
                draft = draft,
                assistantMessageId = "assistant-1",
                settingLibraryToolEnabled = false,
            ),
        )
    }

    private fun draftWithAssistant(content: String) = ChatDraft(
        session = ChatSession(
            id = "session-1",
            title = "chat",
            characterId = "character-1",
            characterName = "角色",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = listOf(ChatMessage("assistant-1", MessageRole.Assistant, content)),
            updatedAt = "now",
        ),
        selectedModelConfig = ModelConfig(),
        selectedModel = "model",
    )
}
