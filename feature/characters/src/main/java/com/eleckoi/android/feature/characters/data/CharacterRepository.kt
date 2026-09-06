package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.JsonFileStore
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CharacterRepository(
    store: JsonFileStore,
    private val database: ElecKoiDatabase,
) {
    private val dao = database.characterDao()
    private val userProfileDao = database.userProfileDao()
    private val defaultGroup = ""
    private val mediaStore = CharacterMediaStore(store)
    private val payloadJsonCodec = CharacterPayloadJsonCodec(::loadUserProfile)
    private val characterMedia = CharacterMediaCoordinator(
        mediaStore = mediaStore,
        loadCharacters = ::loadCharacters,
        saveCharacters = ::saveCharacters,
    )
    private val chatBackgrounds = CharacterChatBackgroundCoordinator(
        mediaStore = mediaStore,
        loadCharacters = ::loadCharacters,
        saveCharacters = ::saveCharacters,
    )

    private fun loadUserProfile(): UserProfile {
        return userProfileDao.profile().toUserProfile()
    }

    fun charactersFlow(): Flow<CharactersPayload> {
        return combine(
            dao.charactersFlow(),
            dao.metaFlow(),
            userProfileDao.profileFlow(),
        ) { characters, meta, user ->
            val profile = user.toUserProfile()
            payloadFromRoom(characters.map { it.toSlot(profile) }, meta)
        }
    }

    fun loadCharacters(): CharactersPayload {
        return payloadFromRoom(
            dao.characters().map { it.toSlot(loadUserProfile()) },
            dao.meta(),
        )
    }

    fun characterById(characterId: String): CharacterSlot? {
        return dao.characterById(characterId)?.toSlot(loadUserProfile())
    }

    fun characterPage(
        query: String,
        afterOrder: Int,
        afterId: String,
        limit: Int,
    ): List<CharacterSlot> {
        val profile = loadUserProfile()
        return dao.characterPage(
            query = query.toSqlLikePattern(),
            afterOrder = afterOrder,
            afterId = afterId,
            limit = limit.coerceIn(1, 51),
        ).map { entity -> entity.toSlot(profile) }
    }

    fun prepareCharacters(payload: CharactersPayload): CharactersPayload {
        val normalizedItems = payload.items.mapIndexed { index, slot ->
            normalizeCharacter(slot).let { character ->
                character.copy(order = character.order.takeIf { it > 0 } ?: (index + 1))
            }
        }
        val groups = buildGroups(payload.groups, normalizedItems)
        val expandedGroupNames = payload.expandedGroupNames
            .map(::normalizeGroupName)
            .filter { it in groups }
            .distinct()
        val activeId = payload.activeCharacterId
            .takeIf { id -> normalizedItems.any { it.id == id } }
            ?: normalizedItems.firstOrNull()?.id
            ?: ""
        return CharactersPayload(
            activeCharacterId = activeId,
            groups = groups,
            items = normalizedItems,
            listAllExpanded = payload.listAllExpanded,
            expandedGroupNames = expandedGroupNames,
        )
    }

    fun saveCharacters(payload: CharactersPayload): CharactersPayload {
        val previous = loadCharacters()
        val saved = prepareCharacters(payload)
        val normalizedItems = saved.items
        val retainedFolders = normalizedItems.map { it.folder }.toSet()
        previous.items.map { it.folder }.distinct()
            .filterNot { it in retainedFolders }
            .forEach(characterMedia::deleteCharacterFolder)
        val previousRows = previous.items.associate { it.id to it.toEntity() }
        val incomingRows = normalizedItems.map { it.toEntity() }
        val previousContents = previous.items
            .flatMap { it.toTextContentEntities() }
            .associateBy { it.characterId to it.kind }
        val incomingContents = normalizedItems.flatMap { it.toTextContentEntities() }
        val incomingIds = incomingRows.mapTo(mutableSetOf()) { it.id }
        val changedRows = incomingRows.filter { it != previousRows[it.id] }
        val changedContents = incomingContents.filter {
            it != previousContents[it.characterId to it.kind]
        }
        val removedIds = previousRows.keys.filterNot(incomingIds::contains)
        val metadata = saved.toMetaEntity()
        database.runInTransaction {
            removedIds.chunked(900).forEach(dao::deleteCharacters)
            if (changedRows.isNotEmpty()) dao.upsertCharacters(changedRows)
            if (changedContents.isNotEmpty()) dao.upsertTextContents(changedContents)
            if (dao.meta() != metadata) dao.upsertMeta(metadata)
        }
        return saved
    }

    fun createCharacter(group: String = defaultGroup): CharacterSlot {
        val current = loadCharacters()
        val user = loadUserProfile()
        val cleanGroup = normalizeGroupName(group)
        val id = "character-${newId(16)}"
        val slot = CharacterSlot(
            id = id,
            name = "未命名角色",
            avatar = "",
            coverImage = "",
            group = cleanGroup,
            order = current.items.size + 1,
            groupViewOrder = 0,
            folder = folderNameForCharacter(id),
            characterMode = CharacterMode.Story.storageValue,
            persona = CharacterCard(
                characterId = id,
                characterName = "未命名角色",
                assistantName = "",
                assistantAvatar = "",
                assistantPrompt = "",
                opening = "",
                showOpening = false,
            ).withUser(user),
        )
        saveCharacters(
            CharactersPayload(
                activeCharacterId = current.activeCharacterId.ifBlank { id },
                groups = if (cleanGroup.isBlank() || current.groups.contains(cleanGroup)) {
                    current.groups
                } else {
                    current.groups + cleanGroup
                },
                items = current.items + slot,
                listAllExpanded = true,
                expandedGroupNames = if (cleanGroup.isBlank()) {
                    current.expandedGroupNames
                } else {
                    (current.expandedGroupNames + cleanGroup).distinct()
                },
            ),
        )
        return slot
    }

    fun createCharacterGroup(name: String): CharactersPayload {
        val current = loadCharacters()
        val cleanName = normalizeGroupName(name)
        if (cleanName.isBlank() || cleanName in current.groups) return current
        return saveCharacters(current.copy(groups = current.groups + cleanName))
    }

    fun toggleAllCharactersExpanded(): CharactersPayload {
        val current = loadCharacters()
        return saveCharacters(current.copy(listAllExpanded = !current.listAllExpanded))
    }

    fun toggleCharacterGroupExpanded(group: String): CharactersPayload {
        val current = loadCharacters()
        val cleanGroup = normalizeGroupName(group)
        if (cleanGroup.isBlank() || cleanGroup !in current.groups) return current
        val nextExpandedGroups = if (cleanGroup in current.expandedGroupNames) {
            current.expandedGroupNames - cleanGroup
        } else {
            current.expandedGroupNames + cleanGroup
        }
        return saveCharacters(current.copy(expandedGroupNames = nextExpandedGroups))
    }

    fun selectCharacter(characterId: String): CharactersPayload {
        val current = loadCharacters()
        if (current.items.none { it.id == characterId }) return current
        return saveCharacters(current.copy(activeCharacterId = characterId))
    }

    fun deleteCharacters(characterIds: List<String>): CharactersPayload {
        val ids = characterIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return loadCharacters()
        val current = loadCharacters()
        val remaining = current.items.filterNot { it.id in ids }
        val activeId = current.activeCharacterId.takeIf { id -> remaining.any { it.id == id } }
            ?: remaining.firstOrNull()?.id
            ?: ""
        return saveCharacters(current.copy(activeCharacterId = activeId, items = remaining))
    }

    fun importCharacters(json: String): CharactersPayload {
        return saveCharacters(decodeCharacters(json))
    }

    fun decodeCharacters(json: String): CharactersPayload = prepareCharacters(payloadJsonCodec.decode(json))

    fun exportCharacters(): String = payloadJsonCodec.encode(loadCharacters())

    fun saveCharacterAvatars(
        characterId: String,
        files: Map<AvatarSlot, File>,
    ): CharacterSlot = characterMedia.saveAvatars(characterId, files)

    fun clearCharacterAvatarSlots(
        characterId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = characterMedia.clearAvatarSlots(characterId, slots)

    fun saveCharacterCover(characterId: String, coverFile: File): CharacterSlot {
        return characterMedia.saveCover(characterId, coverFile)
    }

    fun saveCharacterPersona(characterId: String, persona: CharacterCard): CharacterSlot {
        val current = loadCharacters()
        val target = current.items.firstOrNull { it.id == characterId }
            ?: throw ElecKoiDataException("角色不存在")
        val name = persona.assistantName.trim().ifBlank { "未命名角色" }
        val updated = target.copy(
            name = name,
            avatar = persona.assistantAvatar.ifBlank { target.avatar },
            persona = target.persona.copy(
                assistantName = persona.assistantName,
                assistantAvatar = persona.assistantAvatar,
                assistantPrompt = persona.assistantPrompt,
                profileAge = persona.profileAge.trim().take(16),
                profileSex = persona.profileSex.trim().take(16),
                profileHeight = persona.profileHeight.trim().take(16),
                profileBirthday = persona.profileBirthday.trim().take(24),
                profileLike = persona.profileLike.trim().take(80),
                imagePrompt = persona.imagePrompt.trim().take(4_000),
                opening = persona.opening,
                showOpening = persona.opening.isNotBlank(),
            ),
        )
        saveCharacters(
            current.copy(items = current.items.map { if (it.id == characterId) updated else it }),
        )
        return updated
    }

    fun saveCharacterChatBackground(
        characterId: String,
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): CharacterSlot {
        return chatBackgrounds.saveCustomImage(characterId, backgroundFile, opacity, blur, scrim)
    }

    fun restoreCharacterChatBackgroundDefault(characterId: String): CharacterSlot {
        return chatBackgrounds.useAppDefault(characterId)
    }

    fun useCharacterCardChatBackground(characterId: String): CharacterSlot {
        return chatBackgrounds.useCharacterCard(characterId)
    }

    fun useCustomChatBackground(characterId: String): CharacterSlot {
        return chatBackgrounds.useCustomChoice(characterId)
    }

    fun useGlobalChatBackground(characterId: String): CharacterSlot {
        return chatBackgrounds.useGlobalChoice(characterId)
    }

    fun applyGlobalChatBackground(sourceCharacterId: String): CharacterSlot {
        return chatBackgrounds.applyGlobal(sourceCharacterId)
    }

    fun saveCharacterMode(characterId: String, characterMode: String): CharactersPayload {
        val current = loadCharacters()
        val mode = normalizeCharacterMode(characterMode)
        val updated = current.items.map { slot ->
            if (slot.id == characterId) slot.copy(characterMode = mode) else slot
        }
        return saveCharacters(current.copy(items = updated))
    }
}
