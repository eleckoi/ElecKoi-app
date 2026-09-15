package com.eleckoi.android.feature.characters.api

import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import java.io.File
import kotlinx.coroutines.flow.Flow

interface CharacterService {
    val characterCollectionFlow: Flow<CharactersPayload>

    suspend fun createCharacterDraft(group: String = ""): CharacterSlot
    suspend fun createCharacter(group: String = ""): CharacterSlot
    suspend fun createCharacter(
        draft: CharacterSlot,
        avatarFiles: Map<AvatarSlot, File> = emptyMap(),
    ): CharacterSlot
    suspend fun createCharacterGroup(name: String): CharactersPayload
    fun selectCharacter(characterId: String): CharactersPayload
    suspend fun toggleAllCharactersExpanded(): CharactersPayload
    suspend fun toggleCharacterGroupExpanded(group: String): CharactersPayload
    suspend fun saveCharacterCollection(payload: CharactersPayload): CharactersPayload
    suspend fun deleteCharacters(characterIds: List<String>): CharactersPayload
    suspend fun importCharacters(json: String): CharactersPayload
    fun exportCharacters(): String
    fun saveCharacterPersona(characterId: String, persona: CharacterCard): CharacterSlot
    fun saveCharacterAvatars(characterId: String, files: Map<AvatarSlot, File>): CharacterSlot
    fun clearCharacterAvatarSlots(characterId: String, slots: Set<AvatarSlot>): CharacterSlot
    fun saveCharacterCover(characterId: String, coverFile: File): CharacterSlot
}
