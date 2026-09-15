package com.eleckoi.android.feature.characters.ui.list

import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload

internal const val ALL_CHARACTERS = "全部角色"
internal const val DEFAULT_GROUP = ""

internal fun characterName(character: CharacterSlot): String {
    return character.persona.assistantName.ifBlank { character.name }.ifBlank { "未命名角色" }
}

internal fun characterAvatar(character: CharacterSlot): String {
    return character.persona.assistantAvatar.ifBlank { character.avatar }
}

internal fun characterCover(character: CharacterSlot): String {
    return character.persona.assistantCover
        .ifBlank { character.coverImage }
        .ifBlank { characterAvatar(character) }
}

internal fun characterSummary(character: CharacterSlot): String {
    return "剧情小说"
}

internal fun characterGroup(character: CharacterSlot): String {
    return character.group.trim()
}

internal fun buildCharacterGroups(characters: CharactersPayload?): List<String> {
    val names = characters?.groups.orEmpty()
        .map { it.trim() }
        .filter { it.isNotBlank() && it != ALL_CHARACTERS }
        .distinct()
        .toMutableList()
    characters?.items.orEmpty().forEach { character ->
        val group = characterGroup(character)
        if (group.isNotBlank() && !names.contains(group)) names += group
    }
    return names
}

internal fun sortedAllCharacters(characters: List<CharacterSlot>): List<CharacterSlot> {
    return characters.withIndex()
        .sortedByDescending { (index, character) -> character.order.takeIf { it > 0 } ?: (index + 1) }
        .map { it.value }
}

internal fun sortedCharactersForGroup(characters: List<CharacterSlot>, group: String): List<CharacterSlot> {
    return characters.withIndex()
        .filter { (_, character) -> characterGroup(character) == group }
        .sortedByDescending { (index, character) -> character.groupViewOrder.takeIf { it > 0 } ?: (index + 1) }
        .map { it.value }
}

internal fun characterOrderMap(displayCharacters: List<CharacterSlot>): Map<String, Int> {
    val size = displayCharacters.size
    return displayCharacters.mapIndexed { index, character -> character.id to (size - index) }.toMap()
}

/** Keeps global waterfall order and per-group order independent when a sort row is dragged. */
internal fun reorderCharacterDisplay(
    characters: List<CharacterSlot>,
    group: String,
    fromId: String,
    toId: String,
): List<CharacterSlot>? {
    val display = (if (group == ALL_CHARACTERS) sortedAllCharacters(characters) else sortedCharactersForGroup(characters, group)).toMutableList()
    val fromIndex = display.indexOfFirst { it.id == fromId }
    val toIndex = display.indexOfFirst { it.id == toId }
    if (fromIndex !in display.indices || toIndex !in display.indices || fromIndex == toIndex) return null
    display.add(toIndex, display.removeAt(fromIndex))
    val nextOrders = characterOrderMap(display)
    return characters.map { character ->
        val nextOrder = nextOrders[character.id]
        when {
            nextOrder == null -> character
            group == ALL_CHARACTERS -> character.copy(order = nextOrder)
            characterGroup(character) == group -> character.copy(groupViewOrder = nextOrder)
            else -> character
        }
    }
}

internal fun filterCharacters(characters: List<CharacterSlot>, keyword: String): List<CharacterSlot> {
    val key = keyword.trim().lowercase()
    if (key.isBlank()) return characters
    return characters.filter { character ->
        listOf(
            character.name,
            character.persona.assistantName,
            character.persona.opening,
            characterGroup(character),
        ).joinToString(" ").lowercase().contains(key)
    }
}
