package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.AppDefaultChatBackground
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CustomChatBackground
import com.eleckoi.android.feature.characters.model.GlobalChatBackground
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterChatBackgroundPolicyTest {
    @Test
    fun applyGlobalChangesOnlySourceAndAutomaticBlankChoices() {
        val source = slot("source", CustomChatBackground)
        val automatic = slot("automatic", "")
        val appDefault = slot("app-default", AppDefaultChatBackground)
        val custom = slot("custom", "/custom/background.png")

        val updated = CharacterChatBackgroundPolicy.applyGlobal(
            items = listOf(source, automatic, appDefault, custom),
            sourceCharacterId = source.id,
        ).associateBy(CharacterSlot::id)

        assertEquals(GlobalChatBackground, updated.getValue(source.id).persona.chatBackground)
        assertEquals(GlobalChatBackground, updated.getValue(automatic.id).persona.chatBackground)
        assertEquals(AppDefaultChatBackground, updated.getValue(appDefault.id).persona.chatBackground)
        assertEquals("/custom/background.png", updated.getValue(custom.id).persona.chatBackground)
    }

    @Test
    fun explicitSelectionResetsBackgroundTuning() {
        val selected = CharacterChatBackgroundPolicy.withSelection(
            target = slot("character", "").copy(
                persona = CharacterCard(
                    chatBackgroundOpacity = 0.1f,
                    chatBackgroundBlur = 12f,
                    chatBackgroundScrim = 0.9f,
                ),
            ),
            selection = CustomChatBackground,
        )

        assertEquals(CustomChatBackground, selected.persona.chatBackground)
        assertEquals(0.72f, selected.persona.chatBackgroundOpacity)
        assertEquals(0f, selected.persona.chatBackgroundBlur)
        assertEquals(0.22f, selected.persona.chatBackgroundScrim)
    }

    private fun slot(id: String, background: String): CharacterSlot {
        return CharacterSlot(
            id = id,
            name = id,
            avatar = "",
            group = "",
            folder = id,
            persona = CharacterCard(characterId = id, chatBackground = background),
        )
    }
}
