package com.eleckoi.android.app.service

import com.eleckoi.android.app.initializeMissingCharacterToolDefaults
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.api.CharacterService
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.foundation.storage.PersistentCleanupRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

internal class CharacterServiceImpl(
    private val characters: CharacterRepository,
    private val sessions: ChatSessionStore,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val frontendProjects: FrontendProjectRepository,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val initializeCharacterTools: (characterId: String) -> Unit,
    private val regexRules: RegexRuleRepository,
    private val deleteWorkspace: suspend (String) -> Unit,
    private val deleteCharacterTools: (Collection<String>) -> Unit,
    private val beforeDeleteCharacters: suspend (Collection<String>) -> Unit,
    private val cleanupRunner: PersistentCleanupRunner,
) : CharacterService {
    private val collectionChanges = Mutex()
    override val characterCollectionFlow: Flow<CharactersPayload> = characters.charactersFlow()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override suspend fun saveCharacterCollection(payload: CharactersPayload): CharactersPayload = collectionChanges.withLock {
        val prepared = characters.prepareCharacters(payload)
        val retainedIds = prepared.items.map { it.id }
        val retained = retainedIds.toSet()
        val existingIds = characters.loadCharacters().items.map { it.id }
        removeCharacterData(existingIds.filterNot { it in retained })
        // Reconcile current stores too, including residual character files from earlier deletions.
        sessions.deleteExceptCharacters(retainedIds)
        settingLibrary.deleteExceptCharacters(retainedIds)
        variableConfig.deleteExceptCharacters(retainedIds)
        frontendProjects.deleteExceptCharacters(retainedIds)
        regexRules.deleteExceptCharacters(retainedIds)
        deleteCharacterWorkspaces(retained, deleteMatching = false)
        creatorWorkspaces.deleteCharacterContainersExcept(retained)
        ensureCharacterContainers(retainedIds)
        val saved = characters.saveCharacters(prepared)
        initializeMissingCharacterToolDefaults(existingIds, retainedIds, initializeCharacterTools)
        saved
    }

    override suspend fun createCharacter(group: String): CharacterSlot {
        val character = characters.createCharacter(group)
        creatorWorkspaces.ensureCharacterContainer(character.id)
        initializeCharacterTools(character.id)
        return character
    }

    override suspend fun createCharacterGroup(name: String): CharactersPayload {
        return characters.createCharacterGroup(name)
    }

    override fun selectCharacter(characterId: String): CharactersPayload {
        return characters.selectCharacter(characterId)
    }

    override suspend fun toggleAllCharactersExpanded(): CharactersPayload {
        return characters.toggleAllCharactersExpanded()
    }

    override suspend fun toggleCharacterGroupExpanded(group: String): CharactersPayload {
        return characters.toggleCharacterGroupExpanded(group)
    }

    override suspend fun deleteCharacters(characterIds: List<String>): CharactersPayload = collectionChanges.withLock {
        val ids = characterIds.filter(String::isNotBlank).distinct()
        removeCharacterData(ids)
        characters.loadCharacters()
    }

    private suspend fun removeCharacterData(characterIds: List<String>) {
        characterIds.filter(String::isNotBlank).distinct().forEach { characterId ->
            cleanupRunner.run(CharacterCleanupKind, characterId) { deleteCharacterNow(characterId) }
        }
    }

    internal suspend fun deleteCharacterNow(characterId: String) {
        val ids = listOf(characterId)
        beforeDeleteCharacters(ids)
        creatorWorkspaces.detachCharacterRootsFor(setOf(characterId))
        sessions.deleteForCharacters(ids)
        settingLibrary.deleteForCharacters(ids)
        variableConfig.deleteForCharacters(ids)
        frontendProjects.deleteForCharacters(ids)
        regexRules.deleteForCharacters(ids)
        deleteCharacterTools(ids)
        deleteCharacterWorkspaces(setOf(characterId), deleteMatching = true)
        creatorWorkspaces.deleteCharacterContainer(characterId)
        characters.deleteCharacters(ids)
    }

    override suspend fun importCharacters(json: String): CharactersPayload {
        return saveCharacterCollection(characters.decodeCharacters(json))
    }

    override fun exportCharacters(): String = characters.exportCharacters()

    override fun saveCharacterAvatars(
        characterId: String,
        files: Map<AvatarSlot, File>,
    ): CharacterSlot = characters.saveCharacterAvatars(characterId, files)

    override fun clearCharacterAvatarSlots(
        characterId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = characters.clearCharacterAvatarSlots(characterId, slots)

    override fun saveCharacterCover(
        characterId: String,
        coverFile: File,
    ): CharacterSlot = characters.saveCharacterCover(characterId, coverFile)

    override fun saveCharacterPersona(characterId: String, persona: CharacterCard): CharacterSlot {
        return characters.saveCharacterPersona(characterId, persona)
    }

    override fun saveCharacterMode(characterId: String, characterMode: String): CharactersPayload {
        return characters.saveCharacterMode(characterId, characterMode)
    }

    private suspend fun ensureCharacterContainers(characterIds: Collection<String>) {
        characterIds.distinct().forEach { characterId ->
            creatorWorkspaces.ensureCharacterContainer(characterId)
        }
    }

    private suspend fun deleteCharacterWorkspaces(
        characterIds: Set<String>,
        deleteMatching: Boolean,
    ) {
        if (characterIds.isEmpty() && deleteMatching) return
        creatorWorkspaces.list()
            .filter { workspace ->
                val characterId = workspace.linkedCharacterId ?: return@filter false
                workspace.linkedCharacterMode != null &&
                    ((characterId in characterIds) == deleteMatching)
            }
            .forEach { workspace -> deleteWorkspace(workspace.id) }
    }

    private companion object {
        const val CharacterCleanupKind = "character"
    }
}
