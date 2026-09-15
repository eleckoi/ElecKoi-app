package com.eleckoi.android.feature.characters.ui.list

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterReorderPolicyTest {
    @Test
    fun `reorder all characters updates only global order`() {
        val source = listOf(
            character("a", group = "A", order = 3, groupOrder = 2),
            character("b", group = "A", order = 2, groupOrder = 1),
            character("c", group = "B", order = 1, groupOrder = 1),
        )

        val reordered = reorderAllCharacters(source, fromId = "a", toId = "c")!!

        assertEquals(listOf("b", "c", "a"), sortedAllCharacters(reordered).map { it.id })
        assertEquals(source.map { it.groupViewOrder }, reordered.map { it.groupViewOrder })
    }

    @Test
    fun `move character to another group normalizes source and target orders`() {
        val source = listOf(
            character("a", group = "A", order = 4, groupOrder = 2),
            character("b", group = "A", order = 3, groupOrder = 1),
            character("c", group = "B", order = 2, groupOrder = 2),
            character("d", group = "B", order = 1, groupOrder = 1),
        )

        val moved = moveCharacterToGroup(
            characters = source,
            groups = listOf("A", "B"),
            characterId = "a",
            targetGroup = "B",
            targetCharacterId = "d",
        )!!

        assertEquals(listOf("c", "a", "d"), sortedCharactersForGroup(moved, "B").map { it.id })
        assertEquals(listOf("b"), sortedCharactersForGroup(moved, "A").map { it.id })
        assertEquals("B", moved.first { it.id == "a" }.group)
    }

    @Test
    fun `move rejects unknown group without changing data`() {
        val source = listOf(character("a", group = "A", order = 1, groupOrder = 1))

        assertNull(
            moveCharacterToGroup(
                characters = source,
                groups = listOf("A"),
                characterId = "a",
                targetGroup = "missing",
            ),
        )
    }

    private fun character(
        id: String,
        group: String,
        order: Int,
        groupOrder: Int,
    ) = CharacterSlot(
        id = id,
        name = id,
        avatar = "",
        group = group,
        order = order,
        groupViewOrder = groupOrder,
        folder = "",
        persona = CharacterCard(characterId = id, characterName = id),
    )
}
