package com.eleckoi.android.app.service

import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.api.SettingLibraryService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibrarySessionMutation
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryConversation
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrarySource
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

internal class SettingLibraryServiceImpl(
    private val settingLibrary: SettingLibraryRepository,
    private val sessions: ChatSessionStore,
    private val characters: CharacterRepository,
) : SettingLibraryService {
    /**
     * Reads every other character's library so entries can be imported across cards. A card with
     * nothing in it is still listed — the picker greys it out rather than pretending it is missing,
     * which is less confusing than a name that silently disappears.
     */
    override fun parseSettingLibraryFile(json: String): SettingLibraryVersion = settingLibrary.parseJson(json)

    override fun settingLibrarySources(excludeCharacterId: String): List<SettingLibrarySource> {
        return characters.loadCharacters().items
            .filterNot { it.id == excludeCharacterId }
            .map { slot ->
                val versions = runCatching { settingLibrary.load(slot.id).versions }.getOrDefault(emptyList())
                SettingLibrarySource(
                    characterId = slot.id,
                    characterName = slot.name,
                    avatar = slot.avatar,
                    versions = versions,
                )
            }
    }

    override fun settingLibraryFlow(characterId: String): Flow<SettingLibrary> {
        return settingLibrary.libraryFlow(characterId)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun saveSettingLibrary(characterId: String, library: SettingLibrary): SettingLibrary {
        return settingLibrary.save(characterId, library).also(::syncUnstartedStoryOpening)
    }

    override fun exportSettingLibrary(characterId: String): String = settingLibrary.exportJson(characterId)

    override fun importSettingLibrary(characterId: String, json: String): SettingLibrary {
        return settingLibrary.importJson(characterId, json).also(::syncUnstartedStoryOpening)
    }

    override fun conversationSettingLibraries(characterId: String): List<SettingLibraryConversation> {
        val libraries = settingLibrary.conversationLibraries(characterId)
        return sessions.chatList()
            .asSequence()
            .filter { session -> session.characterId == characterId }
            .mapNotNull { session ->
                libraries[session.id]?.let { library ->
                    SettingLibraryConversation(
                        sessionId = session.id,
                        title = session.title.ifBlank { "新对话" },
                        characterName = session.characterName,
                        characterAvatar = session.characterAvatar,
                        summary = session.summary,
                        updatedAt = session.updatedAt,
                        library = library,
                    )
                }
            }
            .sortedByDescending(SettingLibraryConversation::updatedAt)
            .toList()
    }

    override fun saveConversationSettingVersion(
        characterId: String,
        sessionId: String,
        name: String,
    ): SettingLibrary = settingLibrary.saveConversationAsVersion(characterId, sessionId, name)

    override fun updateConversationSettingEntry(
        characterId: String,
        sessionId: String,
        entryId: String,
        title: String,
        content: String,
    ): SettingLibrary = settingLibrary.applySessionMutations(
        characterId = characterId,
        sessionId = sessionId,
        mutations = listOf(
            SettingLibrarySessionMutation.UpdateEntry(
                entryId = entryId,
                groupId = null,
                title = title,
                content = content,
                selectionHint = null,
            ),
        ),
    ).effectiveLibrary

    override fun replaceConversationSettingLibrary(
        characterId: String,
        sessionId: String,
        library: SettingLibrary,
    ): SettingLibrary {
        val current = settingLibrary.loadEffective(characterId, sessionId)
        val currentGroups = current.groups.associateBy { it.id }
        val nextGroups = library.groups.associateBy { it.id }
        val currentEntries = current.entries.associateBy { it.id }
        val nextEntries = library.entries.associateBy { it.id }
        val removedGroupIds = currentGroups.keys - nextGroups.keys
        val mutations = mutableListOf<SettingLibrarySessionMutation>()

        library.groups
            .filter { it.id !in currentGroups }
            .forEach { group ->
                mutations += SettingLibrarySessionMutation.CreateGroup(
                    parentId = group.parentId,
                    name = group.name,
                    groupId = group.id,
                )
            }
        library.groups.forEach { group ->
            val previous = currentGroups[group.id] ?: return@forEach
            val parentChanged = previous.parentId != group.parentId
            val nameChanged = previous.name != group.name
            if (parentChanged || nameChanged) {
                mutations += SettingLibrarySessionMutation.UpdateGroup(
                    groupId = group.id,
                    parentId = group.parentId.takeIf { parentChanged },
                    name = group.name.takeIf { nameChanged },
                )
            }
        }
        library.entries
            .filter { entry ->
                entry.id !in currentEntries && !entry.isFixedEntry() &&
                    entry.triggerMode == SettingLibraryTriggerMode.AgentTool
            }
            .forEach { entry ->
                mutations += SettingLibrarySessionMutation.CreateEntry(
                    groupId = entry.groupId,
                    title = entry.title,
                    content = entry.content,
                    selectionHint = entry.agentSelectionHint,
                    entryId = entry.id,
                )
            }
        library.entries.forEach { entry ->
            val previous = currentEntries[entry.id] ?: return@forEach
            if (previous.isFixedEntry() || previous.triggerMode != SettingLibraryTriggerMode.AgentTool) {
                return@forEach
            }
            val groupChanged = previous.groupId != entry.groupId
            val titleChanged = previous.title != entry.title
            val contentChanged = previous.content != entry.content
            val hintChanged = previous.agentSelectionHint != entry.agentSelectionHint
            if (groupChanged || titleChanged || contentChanged || hintChanged) {
                mutations += SettingLibrarySessionMutation.UpdateEntry(
                    entryId = entry.id,
                    groupId = entry.groupId.takeIf { groupChanged },
                    title = entry.title.takeIf { titleChanged },
                    content = entry.content.takeIf { contentChanged },
                    selectionHint = entry.agentSelectionHint.takeIf { hintChanged },
                )
            }
        }
        current.entries
            .filter { entry ->
                entry.id !in nextEntries && entry.groupId !in removedGroupIds &&
                    !entry.isFixedEntry() && entry.triggerMode == SettingLibraryTriggerMode.AgentTool
            }
            .forEach { entry ->
                mutations += SettingLibrarySessionMutation.DeleteEntry(entry.id)
            }
        current.groups
            .filter { group -> group.id in removedGroupIds && group.parentId !in removedGroupIds }
            .forEach { group ->
                mutations += SettingLibrarySessionMutation.DeleteGroup(group.id)
            }

        return if (mutations.isEmpty()) {
            current
        } else {
            settingLibrary.applySessionMutations(characterId, sessionId, mutations).effectiveLibrary
        }
    }

    override fun deleteConversationSettingEntry(
        characterId: String,
        sessionId: String,
        entryId: String,
    ): SettingLibrary = settingLibrary.applySessionMutations(
        characterId = characterId,
        sessionId = sessionId,
        mutations = listOf(SettingLibrarySessionMutation.DeleteEntry(entryId)),
    ).effectiveLibrary

    override fun deleteConversationSettingGroup(
        characterId: String,
        sessionId: String,
        groupId: String,
    ): SettingLibrary = settingLibrary.applySessionMutations(
        characterId = characterId,
        sessionId = sessionId,
        mutations = listOf(SettingLibrarySessionMutation.DeleteGroup(groupId)),
    ).effectiveLibrary

    override fun deleteConversationSettings(characterId: String, sessionId: String) {
        settingLibrary.deleteConversationChanges(characterId, sessionId)
    }

    private fun syncUnstartedStoryOpening(library: SettingLibrary) {
        val opening = library.entries
            .firstOrNull { it.isOpeningEntry() && it.enabled }
            ?.content
            ?.trim()
            .orEmpty()
        sessions.replaceUnstartedOpening(
            characterId = library.characterId,
            content = opening,
        )
    }
}
