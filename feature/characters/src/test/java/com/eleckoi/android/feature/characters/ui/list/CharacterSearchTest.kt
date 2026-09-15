package com.eleckoi.android.feature.characters.ui.list

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSearchTest {
    @Test
    fun `character search matches display name and group`() {
        val characters = listOf(
            character("one", "测试角色甲", "分组甲", assistantName = "显示名称"),
            character("two", "测试角色乙", "分组乙"),
        )

        assertEquals(listOf("one"), filterCharacters(characters, "显示").map { it.id })
        assertEquals(listOf("two"), filterCharacters(characters, "分组乙").map { it.id })
    }

    @Test
    fun `list portrait prefers persona cover and falls back to avatar`() {
        val character = character("one", "角色", "")
            .copy(
                avatar = "content://slot-avatar",
                coverImage = "content://slot-cover",
                persona = CharacterCard(
                    assistantAvatar = "content://persona-avatar",
                    assistantCover = "content://persona-cover",
                ),
            )

        assertEquals("content://persona-cover", characterCover(character))
        assertEquals(
            "content://persona-avatar",
            characterCover(character.copy(coverImage = "", persona = character.persona.copy(assistantCover = ""))),
        )
    }

    private fun character(
        id: String,
        name: String,
        group: String,
        assistantName: String = "",
    ) = CharacterSlot(
        id = id,
        name = name,
        avatar = "",
        group = group,
        folder = "",
        persona = CharacterCard(assistantName = assistantName),
    )
}
