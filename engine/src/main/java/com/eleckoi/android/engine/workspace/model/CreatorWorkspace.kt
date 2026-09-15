package com.eleckoi.android.engine.workspace.model

import com.eleckoi.android.engine.agent.api.AgentCommandAction
import com.eleckoi.android.engine.agent.api.AgentFileChange
import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An editable, app-local authoring workspace.
 *
 * [linkedCharacterId] is only a product association. Workspace files always live in the
 * global creator workspace area, so a project is not owned by a character directory.
 */
@Serializable
data class CreatorWorkspace(
    val schemaVersion: Int = 6,
    val id: String,
    val name: String,
    /**
     * Compatibility mirror of [primaryCharacterRootId]. New creator code must resolve characters
     * through [characterRoots]; the legacy field remains because preview/publish callers still use
     * it and because character-owned workspaces have a physical character owner.
     */
    val linkedCharacterId: String? = null,
    /** True when this is the one runtime workspace physically owned by a character. */
    val characterOwned: Boolean = false,
    /** Stable root selected when a capability omits an explicit root id. */
    val primaryCharacterRootId: String? = null,
    /**
     * Character resources mounted into this creator workspace. These are references to Room-owned
     * data, never copies under /workspace. Character-owned workspaces deliberately keep this empty.
     */
    val characterRoots: List<CreatorWorkspaceCharacterRoot> = emptyList(),
    val previewEntryFile: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val files: List<String> = emptyList(),
    val totalBytes: Long = 0L,
    val latestCheckpointId: String? = null,
    /** Harness permission preset shared by every conversation in this workspace. */
    val permissionMode: AgentPermissionMode = AgentPermissionMode.AskForApproval,
    val conversations: List<CreatorConversation> = emptyList(),
    val activeConversationId: String? = null,
)

@Serializable
enum class CreatorWorkspaceRootAccess {
    ReadOnly,
    ReadWrite,
}

@Serializable
data class CreatorWorkspaceCharacterRoot(
    val id: String,
    val characterId: String,
    val alias: String = "",
    val access: CreatorWorkspaceRootAccess = CreatorWorkspaceRootAccess.ReadOnly,
)

/** Root ids are deterministic so legacy workspaces can be migrated without inventing new state. */
fun creatorCharacterRootId(characterId: String): String = "character:${characterId.trim()}"

/**
 * Normalizes the persisted multi-root contract and upgrades the old single linked character.
 * Character-owned workspaces live under one character directory and are not creator mounts.
 */
fun CreatorWorkspace.withNormalizedCharacterRoots(): CreatorWorkspace {
    if (characterOwned) {
        return copy(
            schemaVersion = maxOf(schemaVersion, 6),
            characterRoots = emptyList(),
            primaryCharacterRootId = null,
        )
    }
    val normalizedRoots = buildList {
        val seenCharacters = mutableSetOf<String>()
        characterRoots.forEach { root ->
            val characterId = root.characterId.trim()
            if (characterId.isEmpty() || !seenCharacters.add(characterId)) return@forEach
            add(
                root.copy(
                    id = creatorCharacterRootId(characterId),
                    characterId = characterId,
                    alias = root.alias.trim().take(80),
                ),
            )
        }
        linkedCharacterId?.trim()?.takeIf(String::isNotEmpty)?.let { legacyId ->
            if (seenCharacters.add(legacyId)) {
                add(
                    CreatorWorkspaceCharacterRoot(
                        id = creatorCharacterRootId(legacyId),
                        characterId = legacyId,
                        alias = legacyId,
                        access = CreatorWorkspaceRootAccess.ReadWrite,
                    ),
                )
            }
        }
    }
    val requestedPrimary = primaryCharacterRootId
        ?.let { id -> normalizedRoots.firstOrNull { it.id == id } }
        ?: linkedCharacterId?.let { id -> normalizedRoots.firstOrNull { it.characterId == id } }
    val primary = requestedPrimary?.let { selected ->
        if (selected.access == CreatorWorkspaceRootAccess.ReadWrite) selected else {
            selected.copy(access = CreatorWorkspaceRootAccess.ReadWrite)
        }
    }
    val roots = if (primary == null) normalizedRoots else normalizedRoots.map { root ->
        if (root.id == primary.id) primary else root
    }
    return copy(
        schemaVersion = maxOf(schemaVersion, 6),
        linkedCharacterId = primary?.characterId,
        primaryCharacterRootId = primary?.id,
        characterRoots = roots,
    )
}

@Serializable
data class CreatorConversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val timeline: List<CreatorConversationTimelineItem> = emptyList(),
)

@Serializable
data class CreatorConversationTimelineItem(
    val id: String,
    val kind: CreatorConversationTimelineKind,
    val text: String,
    val detail: String = "",
    val failed: Boolean = false,
    val workItemType: CreatorConversationWorkItemType? = null,
    /** Durable DSH session that owns this product turn. */
    val runtimeThreadId: String = "",
    val turnId: String? = null,
    val createdAtMillis: Long = 0L,
    val turnStartedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    val fileChanges: List<AgentFileChange> = emptyList(),
    val paths: List<String> = emptyList(),
    val diff: String = "",
    val turnDiffObserved: Boolean = false,
    val messagePhase: AgentMessagePhase? = null,
    val phaseHeader: AgentMessagePhase? = null,
    val toolName: String = "",
    val toolArguments: String = "",
    val delegatedModel: String = "",
    val childTimeline: List<CreatorConversationTimelineItem> = emptyList(),
    val delegatedSessionId: String = "",
    val rawCommand: String = "",
    val commandActions: List<AgentCommandAction> = emptyList(),
    /** App-private image metadata for the user turn; bitmap bytes remain outside Room. */
    val inputImages: List<CreatorConversationInputImage> = emptyList(),
    /** Exact native Agent history items retained for recovery if the durable DSH Session is absent. */
    val modelHistoryItems: List<String> = emptyList(),
)

@Serializable
data class CreatorConversationInputImage(
    val id: String = "",
    @SerialName("local_path") val localPath: String = "",
    @SerialName("media_type") val mediaType: String = "image/jpeg",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("creator_media_reference") val creatorMediaReference: String = "",
    val bytes: Long = 0L,
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
)

@Serializable
enum class CreatorConversationTimelineKind {
    User,
    Assistant,
    Tool,
}

@Serializable
enum class CreatorConversationWorkItemType {
    Request,
    Reasoning,
    Command,
    FileChange,
    Tool,
    Action,
    ContextCompaction,
    Unknown,
    AssistantMessage,
    UserMessage,
}

@Serializable
data class CreatorWorkspaceFile(
    val path: String,
    val sizeBytes: Long,
    val lastModifiedAt: String,
)

@Serializable
data class CreatorWorkspaceCheckpoint(
    val id: String,
    val workspaceId: String,
    val label: String? = null,
    val createdAt: String,
    val files: List<String>,
    val totalBytes: Long,
)
