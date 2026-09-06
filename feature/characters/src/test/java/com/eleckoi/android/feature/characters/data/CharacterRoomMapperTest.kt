package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.storage.room.CharacterRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CharacterRoomMapperTest {
    @Test fun `large character text is stored outside the directory row and roundtrips`() {
        val prompt = "系统提示".repeat(50_000)
        val opening = "开场白".repeat(20_000)
        val slot = CharacterSlot(
            id = "card-a",
            name = "角色甲",
            avatar = "avatar.png",
            group = "主角",
            folder = "card-a",
            persona = CharacterCard(
                assistantName = "角色甲",
                assistantPrompt = prompt,
                imagePrompt = "blue hair",
                opening = opening,
                showOpening = true,
            ),
        )
        val core = slot.toEntity()
        val record = CharacterRecord(core, slot.toTextContentEntities())

        assertFalse(core.toString().contains(prompt.take(100)))
        assertEquals(prompt, record.toSlot(UserProfile()).persona.assistantPrompt)
        assertEquals(opening, record.toSlot(UserProfile()).persona.opening)
    }
}
