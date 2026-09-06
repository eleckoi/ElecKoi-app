package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.foundation.storage.room.CharacterEntity
import com.eleckoi.android.foundation.storage.room.CharacterMetaEntity
import com.eleckoi.android.foundation.storage.room.CharacterRecord
import com.eleckoi.android.foundation.storage.room.CharacterTextContentEntity
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.characters.modes.story.model.StoryToolSettings
import org.json.JSONArray

internal fun CharacterSlot.toEntity(): CharacterEntity {
    return CharacterEntity(
        id = id,
        name = name,
        avatar = avatar,
        squareImage = squareImage,
        coverImage = coverImage,
        groupName = group,
        orderIndex = order,
        groupViewOrder = groupViewOrder,
        folder = folder,
        characterMode = CharacterMode.fromStorage(characterMode).storageValue,
        frontendBeautyEnabled = storyTools.frontendBeautyEnabled,
        assistantName = persona.assistantName,
        assistantAvatar = persona.assistantAvatar,
        profileAge = persona.profileAge,
        profileSex = persona.profileSex,
        profileHeight = persona.profileHeight,
        profileBirthday = persona.profileBirthday,
        profileLike = persona.profileLike,
        showOpening = persona.showOpening,
        chatBackground = persona.chatBackground,
        chatBackgroundOpacity = persona.chatBackgroundOpacity,
        chatBackgroundBlur = persona.chatBackgroundBlur,
        chatBackgroundScrim = persona.chatBackgroundScrim,
    )
}

internal fun CharacterSlot.toTextContentEntities(): List<CharacterTextContentEntity> = listOf(
    CharacterTextContentEntity(id, AssistantPromptContentKind, persona.assistantPrompt),
    CharacterTextContentEntity(id, ImagePromptContentKind, persona.imagePrompt),
    CharacterTextContentEntity(id, OpeningContentKind, persona.opening),
)

internal fun CharacterRecord.toSlot(user: UserProfile): CharacterSlot {
    val entity = character
    val contentByKind = textContents.associate { it.kind to it.content }
    return entity.toSlot(
        user = user,
        assistantPrompt = contentByKind[AssistantPromptContentKind].orEmpty(),
        imagePrompt = contentByKind[ImagePromptContentKind].orEmpty(),
        opening = contentByKind[OpeningContentKind].orEmpty(),
    )
}

private fun CharacterEntity.toSlot(
    user: UserProfile,
    assistantPrompt: String,
    imagePrompt: String,
    opening: String,
): CharacterSlot {
    val characterName = name.ifBlank { assistantName }.ifBlank { "未命名角色" }
    val characterAvatar = avatar.ifBlank { assistantAvatar }
    return CharacterSlot(
        id = id,
        name = characterName,
        avatar = characterAvatar,
        squareImage = squareImage,
        coverImage = coverImage,
        group = groupName,
        order = orderIndex,
        groupViewOrder = groupViewOrder,
        folder = folder,
        characterMode = CharacterMode.fromStorage(characterMode).storageValue,
        storyTools = StoryToolSettings(
            frontendBeautyEnabled = frontendBeautyEnabled,
        ),
        persona = CharacterCard(
            characterId = id,
            characterName = characterName,
            characterAvatar = characterAvatar,
            assistantName = assistantName,
            assistantAvatar = assistantAvatar.ifBlank { characterAvatar },
            assistantSquare = squareImage,
            assistantCover = coverImage,
            assistantPrompt = assistantPrompt,
            profileAge = profileAge,
            profileSex = profileSex,
            profileHeight = profileHeight,
            profileBirthday = profileBirthday,
            profileLike = profileLike,
            imagePrompt = imagePrompt,
            opening = opening,
            showOpening = showOpening,
            chatBackground = chatBackground,
            chatBackgroundOpacity = chatBackgroundOpacity.coerceIn(0f, 1f),
            chatBackgroundBlur = chatBackgroundBlur.coerceIn(0f, 24f),
            chatBackgroundScrim = chatBackgroundScrim.coerceIn(0f, 1f),
        ).withUser(user),
    )
}

private const val AssistantPromptContentKind = "assistant_prompt"
private const val ImagePromptContentKind = "image_prompt"
private const val OpeningContentKind = "opening"

internal fun payloadFromRoom(
    items: List<CharacterSlot>,
    meta: CharacterMetaEntity?,
): CharactersPayload {
    val groups = buildGroups(meta?.groupsJson?.decodeStringList().orEmpty(), items)
    val active = meta?.activeCharacterId
        ?.takeIf { id -> items.any { it.id == id } }
        ?: items.firstOrNull()?.id
        ?: ""
    val expanded = meta?.expandedGroupNamesJson
        ?.decodeStringList()
        .orEmpty()
        .map(::normalizeGroupName)
        .filter { it in groups }
        .distinct()
    return CharactersPayload(
        activeCharacterId = active,
        groups = groups,
        items = items,
        listAllExpanded = meta?.listAllExpanded ?: true,
        expandedGroupNames = expanded,
    )
}

internal fun CharactersPayload.toMetaEntity(): CharacterMetaEntity {
    return CharacterMetaEntity(
        activeCharacterId = activeCharacterId,
        groupsJson = JSONArray(groups).toString(),
        listAllExpanded = listAllExpanded,
        expandedGroupNamesJson = JSONArray(expandedGroupNames).toString(),
    )
}
