package com.eleckoi.android.feature.chat.data.session

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot

internal fun characterPersonaSnapshot(character: CharacterSlot): CharacterCard = character.persona.copy(
    characterId = character.id,
    characterName = character.name,
    characterAvatar = character.avatar,
    assistantName = character.persona.assistantName.ifBlank { character.name },
    assistantAvatar = character.persona.assistantAvatar.ifBlank { character.avatar },
    assistantCover = character.coverImage,
    opening = "",
    showOpening = false,
)
