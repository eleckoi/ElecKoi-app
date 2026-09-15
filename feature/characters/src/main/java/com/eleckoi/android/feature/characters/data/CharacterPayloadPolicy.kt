package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.safeId
import org.json.JSONArray

internal fun normalizeCharacter(slot: CharacterSlot): CharacterSlot {
    val id = slot.id.ifBlank { "character-${newId(16)}" }
    val name = slot.name.ifBlank { slot.persona.assistantName }.ifBlank { "未命名角色" }
    val avatar = slot.avatar.ifBlank { slot.persona.assistantAvatar }
    val folder = folderNameForCharacter(id)
    return slot.copy(
        id = id,
        name = name,
        avatar = avatar,
        coverImage = slot.coverImage,
        group = normalizeGroupName(slot.group),
        order = slot.order,
        groupViewOrder = slot.groupViewOrder,
        folder = folder,
        persona = slot.persona.copy(
            characterId = id,
            characterName = name,
            characterAvatar = avatar,
            assistantName = slot.persona.assistantName,
            assistantAvatar = slot.persona.assistantAvatar.ifBlank { avatar },
        ),
    )
}

internal fun buildGroups(groups: List<String>, items: List<CharacterSlot>): List<String> {
    val names = groups.map(::normalizeGroupName)
        .filter { it.isNotBlank() }
        .distinct()
        .toMutableList()
    items.forEach { slot ->
        val group = normalizeGroupName(slot.group)
        if (group.isNotBlank() && !names.contains(group)) names += group
    }
    return names
}

internal fun normalizeGroupName(value: String): String = value.trim().take(30)

internal fun folderNameForCharacter(characterId: String): String {
    val suffix = safeId(characterId)
        .removePrefix("character-")
        .takeLast(8)
        .ifBlank { newId(8) }
    return "character_$suffix"
}

internal fun String.toSqlLikePattern(): String {
    val value = trim().take(80)
    if (value.isBlank()) return ""
    val escaped = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

internal fun String.decodeStringList(): List<String> {
    return runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty() && !contains(value)) add(value)
            }
        }
    }.getOrDefault(emptyList())
}
