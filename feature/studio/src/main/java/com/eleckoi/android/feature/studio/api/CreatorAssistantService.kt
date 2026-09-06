package com.eleckoi.android.feature.studio.api

import androidx.paging.PagingData
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.eleckoi.conversation.PagedConversationTurn
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ImageGenerationProvider
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpoint
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.config.VersionedVariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.data.VersionedRegexRuleCollection
import java.io.File
import kotlinx.coroutines.flow.Flow

interface CreatorAssistantService {
    suspend fun creatorCharacter(characterId: String): CharacterSlot?
    suspend fun searchCreatorCharacters(
        query: String = "",
        cursor: String = "",
        limit: Int = 20,
    ): CreatorCharacterPage

    suspend fun listCreatorWorkspaces(): List<CreatorWorkspace>
    suspend fun createCreatorWorkspace(name: String, linkedCharacterId: String?): CreatorWorkspace
    suspend fun renameCreatorWorkspace(workspaceId: String, name: String): CreatorWorkspace
    suspend fun creatorWorkspace(workspaceId: String): CreatorWorkspace?
    suspend fun attachCreatorCharacter(
        workspaceId: String,
        characterId: String,
        access: CreatorWorkspaceRootAccess = CreatorWorkspaceRootAccess.ReadOnly,
        makePrimary: Boolean = false,
    ): CreatorWorkspace
    suspend fun detachCreatorCharacter(workspaceId: String, rootId: String): CreatorWorkspace
    suspend fun setPrimaryCreatorCharacter(workspaceId: String, rootId: String): CreatorWorkspace
    suspend fun setCreatorCharacterAccess(
        workspaceId: String,
        rootId: String,
        access: CreatorWorkspaceRootAccess,
    ): CreatorWorkspace
    suspend fun createAndAttachCreatorCharacter(
        workspaceId: String,
        name: String,
        group: String = "",
    ): Pair<CreatorWorkspace, CharacterSlot>
    suspend fun creatorCharacterMedia(
        workspaceId: String,
        rootId: String = "",
    ): CreatorCharacterMediaState
    suspend fun registerCreatorMediaAsset(
        workspaceId: String,
        sourceFile: File,
        displayName: String = "",
        source: CreatorMediaAssetSource = CreatorMediaAssetSource.Upload,
    ): CreatorMediaAsset
    suspend fun creatorImageGenerationProvider(): ImageGenerationProvider?
    suspend fun generateCreatorMediaAsset(
        workspaceId: String,
        prompt: String,
        negativePrompt: String = "",
        displayName: String = "",
    ): CreatorMediaAsset
    suspend fun prepareCreatorInputImages(uriValues: List<String>): List<ChatUserImageAttachment>
    fun discardCreatorInputImage(image: ChatUserImageAttachment)
    suspend fun searchCreatorMediaAssets(
        workspaceId: String,
        cursor: String = "",
        limit: Int = 20,
    ): CreatorMediaAssetPage
    suspend fun creatorMediaAsset(workspaceId: String, assetId: String): CreatorMediaAsset?
    suspend fun applyCreatorMediaAsset(
        workspaceId: String,
        rootId: String = "",
        assetId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot
    suspend fun clearCreatorCharacterMedia(
        workspaceId: String,
        rootId: String = "",
        slots: Set<AvatarSlot>,
    ): CharacterSlot
    suspend fun loadCreatorSettingLibrary(workspaceId: String, rootId: String = ""): SettingLibrary
    suspend fun creatorSettingLibraryMetadata(
        workspaceId: String,
        rootId: String = "",
    ): CreatorSettingLibraryMetadata
    suspend fun searchCreatorSettingEntries(
        workspaceId: String,
        rootId: String = "",
        query: String = "",
        cursor: String = "",
        limit: Int = 20,
    ): CreatorSettingEntryPage
    suspend fun searchCreatorSettingGroups(
        workspaceId: String,
        rootId: String = "",
        query: String = "",
        cursor: String = "",
        limit: Int = 20,
    ): CreatorSettingGroupPage
    suspend fun creatorSettingEntry(
        workspaceId: String,
        rootId: String = "",
        entryId: String,
    ): SettingLibraryEntry?
    suspend fun saveCreatorSettingLibrary(
        workspaceId: String,
        rootId: String,
        library: SettingLibrary,
    ): SettingLibrary
    suspend fun loadCreatorVariableConfig(workspaceId: String, rootId: String = ""): VariableConfig
    suspend fun loadVersionedCreatorVariableConfig(
        workspaceId: String,
        rootId: String = "",
    ): VersionedVariableConfig
    suspend fun saveCreatorVariableConfig(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
    ): VariableConfig
    suspend fun saveCreatorVariableConfigIfRevision(
        workspaceId: String,
        rootId: String,
        config: VariableConfig,
        expectedRevision: Long,
    ): VersionedVariableConfig?
    suspend fun validateCreatorVariableSchema(schemaCode: String): VariableRuntimeCheckResult
    suspend fun validateCreatorVariableState(
        schemaCode: String,
        stateJson: String,
    ): VariableRuntimeCheckResult
    suspend fun loadCreatorRegexRules(workspaceId: String, rootId: String = ""): RegexRuleCollection
    suspend fun loadVersionedCreatorRegexRules(
        workspaceId: String,
        rootId: String = "",
    ): VersionedRegexRuleCollection
    suspend fun saveCreatorRegexRules(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
    ): RegexRuleCollection
    suspend fun saveCreatorRegexRulesIfRevision(
        workspaceId: String,
        rootId: String,
        collection: RegexRuleCollection,
        expectedRevision: Long,
    ): VersionedRegexRuleCollection?
    suspend fun deleteCreatorWorkspace(workspaceId: String)
    suspend fun ensureCreatorConversation(workspaceId: String): CreatorWorkspace
    suspend fun createCreatorConversation(workspaceId: String, title: String = "新对话"): CreatorWorkspace
    suspend fun selectCreatorConversation(workspaceId: String, conversationId: String): CreatorWorkspace
    suspend fun renameCreatorConversation(workspaceId: String, conversationId: String, title: String): CreatorWorkspace
    suspend fun saveCreatorWorkspacePermissionMode(
        workspaceId: String,
        permissionMode: AgentPermissionMode,
    ): CreatorWorkspace
    suspend fun saveCreatorConversationTimeline(
        workspaceId: String,
        conversationId: String,
        timeline: List<CreatorConversationTimelineItem>,
    ): CreatorWorkspace
    /** Keep the selected user turn, delete its reply and every later turn, in one Room transaction. */
    suspend fun truncateCreatorConversationForRegeneration(
        workspaceId: String,
        conversationId: String,
        retainedUser: CreatorConversationTimelineItem,
    ): CreatorWorkspace
    suspend fun checkpointCreatorConversationTurn(
        workspaceId: String,
        conversationId: String,
        turnTimeline: List<CreatorConversationTimelineItem>,
    )
    fun creatorConversationPaging(conversationId: String): Flow<PagingData<PagedConversationTurn>>
    suspend fun loadCreatorConversationAgentHistory(
        workspaceId: String,
        conversationId: String,
        excludeTrailingUser: Boolean = false,
    ): List<AgentHistoryItem>
    suspend fun deleteCreatorConversation(workspaceId: String, conversationId: String): CreatorWorkspace
    suspend fun pinnedCreatorWorkspaceIds(): List<String>
    suspend fun setPinnedCreatorWorkspaceIds(workspaceIds: List<String>)
    suspend fun creatorWorkspaceExpansionOverrides(): Map<String, Boolean>
    suspend fun setCreatorWorkspaceExpansionOverrides(overrides: Map<String, Boolean>)
    suspend fun lastCreatorWorkspaceId(): String
    suspend fun setLastCreatorWorkspaceId(workspaceId: String)
    suspend fun listCreatorWorkspaceFiles(workspaceId: String): List<CreatorWorkspaceFile>
    suspend fun readCreatorWorkspaceText(workspaceId: String, path: String): String
    suspend fun writeCreatorWorkspaceText(workspaceId: String, path: String, content: String): CreatorWorkspace
    suspend fun checkpointCreatorWorkspace(workspaceId: String, label: String? = null): CreatorWorkspaceCheckpoint
    suspend fun listCreatorWorkspaceCheckpoints(workspaceId: String): List<CreatorWorkspaceCheckpoint>
    suspend fun restoreCreatorWorkspaceCheckpoint(
        workspaceId: String,
        checkpointId: String,
    ): CreatorWorkspace
    fun creatorWorkspaceProjectDirectory(workspaceId: String): File?
    suspend fun defaultCreatorModelConfig(): ModelConfig
}

private const val CreatorConversationAttachmentPrefix = "conversation-attachment:"

fun creatorConversationAttachmentAssetId(attachmentId: String): String =
    "$CreatorConversationAttachmentPrefix$attachmentId"

fun creatorConversationAttachmentId(assetId: String): String? = assetId
    .takeIf { it.startsWith(CreatorConversationAttachmentPrefix) }
    ?.removePrefix(CreatorConversationAttachmentPrefix)
    ?.takeIf(String::isNotBlank)

data class CreatorCharacterPage(
    val items: List<CharacterSlot>,
    val nextCursor: String = "",
)

enum class CreatorMediaAssetSource(val storageValue: String) {
    Upload("upload"),
    Generated("generated"),
    ConversationAttachment("conversation_attachment"),
    ;

    companion object {
        fun fromStorage(value: String): CreatorMediaAssetSource =
            entries.firstOrNull { it.storageValue == value } ?: Upload
    }
}

data class CreatorMediaAsset(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val source: CreatorMediaAssetSource,
    val createdAt: String,
)

data class CreatorMediaAssetPage(
    val items: List<CreatorMediaAsset>,
    val nextCursor: String = "",
)

data class CreatorCharacterMediaState(
    val rootId: String,
    val characterId: String,
    val characterName: String,
    val revision: String,
    val configuredSlots: Set<AvatarSlot>,
)

data class CreatorSettingLibraryMetadata(
    val rootId: String,
    val characterId: String,
    val name: String,
    val updatedAt: String,
    val entryCount: Int,
    val groupCount: Int,
    val promptPositions: List<SettingLibraryPromptPosition>,
)

data class CreatorSettingEntryPage(
    val items: List<SettingLibraryEntry>,
    val nextCursor: String = "",
)

data class CreatorSettingGroupPage(
    val items: List<SettingLibraryGroup>,
    val nextCursor: String = "",
)
