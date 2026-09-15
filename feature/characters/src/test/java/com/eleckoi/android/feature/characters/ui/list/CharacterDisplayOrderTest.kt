package com.eleckoi.android.feature.characters.ui.list

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterDisplayOrderTest {
    @Test
    fun waterfallArtworkRatios_repeatWithoutProducingExtremeCards() {
        val ratios = (0 until 12).map(::waterfallArtworkAspectRatio)

        assertEquals(ratios.take(4), ratios.drop(4).take(4))
        assertTrue(ratios.all { ratio -> ratio in 0.65f..0.9f })
        assertTrue(ratios.distinct().size > 1)
    }

    @Test
    fun `sorting all characters updates waterfall order without changing group order`() {
        val source = listOf(
            character("a", "A", order = 3, groupOrder = 2),
            character("b", "A", order = 2, groupOrder = 1),
            character("c", "B", order = 1, groupOrder = 1),
        )

        val reordered = reorderCharacterDisplay(source, ALL_CHARACTERS, "a", "c")!!

        assertEquals(listOf("b", "c", "a"), sortedAllCharacters(reordered).map(CharacterSlot::id))
        assertEquals(source.map(CharacterSlot::groupViewOrder), reordered.map(CharacterSlot::groupViewOrder))
    }

    @Test
    fun `sorting one group leaves global and other group order unchanged`() {
        val source = listOf(
            character("a", "A", order = 4, groupOrder = 2),
            character("b", "A", order = 3, groupOrder = 1),
            character("c", "B", order = 2, groupOrder = 2),
            character("d", "B", order = 1, groupOrder = 1),
        )

        val reordered = reorderCharacterDisplay(source, "A", "a", "b")!!

        assertEquals(listOf("b", "a"), sortedCharactersForGroup(reordered, "A").map(CharacterSlot::id))
        assertEquals(source.map(CharacterSlot::order), reordered.map(CharacterSlot::order))
        assertEquals(source.filter { it.group == "B" }.map(CharacterSlot::groupViewOrder), reordered.filter { it.group == "B" }.map(CharacterSlot::groupViewOrder))
        assertNull(reorderCharacterDisplay(source, "A", "a", "missing"))
    }

    private fun character(id: String, group: String, order: Int, groupOrder: Int) = CharacterSlot(
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
