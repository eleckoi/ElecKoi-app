package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.engine.agent.api.AgentApprovalDecision
import com.eleckoi.android.engine.agent.api.AgentApprovalKind
import com.eleckoi.android.engine.agent.api.AgentCommandAction
import com.eleckoi.android.engine.agent.api.AgentFileChange
import com.eleckoi.android.engine.agent.api.AgentMessagePhase
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.workspace.model.CreatorConversation
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpoint
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeCapabilities
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeHealth
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeState
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeInstallationState
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.conversation.timeline.model.CreationPendingSteerInput
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.studio.ui.assistant.runtime.creationCapabilitiesOrNull
import java.io.File
import java.util.UUID

data class CreationApprovalRequest(
    val requestId: Long,
    val kind: AgentApprovalKind,
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val title: String,
    val detail: String,
    val reviewContent: String = "",
    val availableDecisions: List<AgentApprovalDecision>,
    val rawCommand: String = "",
    val commandActions: List<AgentCommandAction> = emptyList(),
)

data class CreationModelChoice(
    val id: String,
    val label: String,
)

data class CreationContextWindowUsage(
    val threadId: String,
    val turnId: String?,
    val latestTokens: Long,
    val totalTokens: Long,
    val modelContextWindow: Long?,
    val systemTokens: Long? = null,
    val toolsTokens: Long? = null,
    val messageTokens: Long? = null,
)

data class AiCreationAssistantUiState(
    val characterId: String = "",
    val characterName: String = "",
    val workspaces: List<CreatorWorkspace> = emptyList(),
    val pinnedWorkspaceIds: List<String> = emptyList(),
    val workspaceExpansionOverrides: Map<String, Boolean> = emptyMap(),
    val workspace: CreatorWorkspace? = null,
    val conversation: CreatorConversation? = null,
    val projectDirectory: File? = null,
    val files: List<CreatorWorkspaceFile> = emptyList(),
    val previewEntryFile: String? = null,
    val timeline: List<CreationTimelineItem> = emptyList(),
    val historyHasMore: Boolean = false,
    val historyPageLoading: Boolean = false,
    val input: String = "",
    val inputImages: List<ChatUserImageAttachment> = emptyList(),
    val isPreparingInputImages: Boolean = false,
    val editingUserMessage: CreationTimelineItem? = null,
    val editInput: String = "",
    val modelLabel: String = "",
    val selectedModelConfigId: String = "",
    val selectedModelId: String = "",
    val modelChoices: List<CreationModelChoice> = emptyList(),
    val modelConfigs: List<ModelConfig> = emptyList(),
    val creatorImageModelConfigId: String = "",
    val toolGroups: List<AgentToolGroupSnapshot> = CreationAssistantToolPolicy.defaultGroups,
    val enabledToolGroupIds: Set<String> = CreationAssistantToolPolicy.defaultEnabledGroupIds,
    val characterDirectory: List<CharacterSlot> = emptyList(),
    val creatorRootCharacters: List<CharacterSlot> = emptyList(),
    val characterDirectoryQuery: String = "",
    val characterDirectoryNextCursor: String = "",
    val isCharacterDirectoryLoading: Boolean = false,
    val isCharacterRootsUpdating: Boolean = false,
    val contextWindowUsage: CreationContextWindowUsage? = null,
    val pendingSteerInputs: List<CreationPendingSteerInput> = emptyList(),
    val permissionMode: AgentPermissionMode = AgentPermissionMode.AskForApproval,
    val isLoading: Boolean = false,
    val isRunning: Boolean = false,
    val isPublishing: Boolean = false,
    val isRestoringCheckpoint: Boolean = false,
    val undoCheckpoint: CreatorWorkspaceCheckpoint? = null,
    val selectedFilePath: String? = null,
    val fileDraft: String = "",
    val fileDraftDirty: Boolean = false,
    val reloadRevision: Int = 0,
    val runtimeState: LocalRuntimeState = LocalRuntimeState.Disconnected,
    val runtimeInstallationState: RuntimeInstallationState = RuntimeInstallationState.Idle,
    val pendingApprovals: List<CreationApprovalRequest> = emptyList(),
    val notice: String = "",
    val errorMessage: String = "",
) {
    val runtimeCapabilities: LocalRuntimeCapabilities?
        get() = runtimeState.creationCapabilitiesOrNull()

    val isRuntimeInstalled: Boolean
        get() = runtimeCapabilities?.let { capabilities ->
            capabilities.supportsArm64Runtime &&
                capabilities.health in RuntimeConversationReadyHealth
        } == true

    /**
     * A normal process reconnect briefly reports Disconnected/Connecting, and a structurally
     * complete bundled runtime reports Checking while its deeper health probe runs. Neither is an
     * installation screen. Block the studio only when the runtime is genuinely unavailable.
     */
    val shouldShowRuntimeBootstrap: Boolean
        get() {
            if (isRuntimeInstalled) return false
            val health = runtimeCapabilities?.health
            return runtimeInstallationState is RuntimeInstallationState.Installing ||
                runtimeInstallationState is RuntimeInstallationState.Failed ||
                runtimeState is LocalRuntimeState.Failed ||
                health == LocalRuntimeHealth.Unsupported ||
                health == LocalRuntimeHealth.NotInstalled ||
                health == LocalRuntimeHealth.NeedsRepair
        }

    val pendingApproval: CreationApprovalRequest?
        get() = pendingApprovals.firstOrNull()
}

private val RuntimeConversationReadyHealth = setOf(
    LocalRuntimeHealth.Checking,
    LocalRuntimeHealth.Healthy,
    LocalRuntimeHealth.UpdateAvailable,
)

sealed interface AiCreationAssistantIntent {
    data object Load : AiCreationAssistantIntent
    data class CreateWorkspace(val name: String) : AiCreationAssistantIntent
    data class RenameWorkspace(val workspaceId: String, val name: String) : AiCreationAssistantIntent
    data class TogglePinnedWorkspace(val workspaceId: String) : AiCreationAssistantIntent
    data class ToggleWorkspaceExpanded(val workspaceId: String) : AiCreationAssistantIntent
    data class CreateConversation(val workspaceId: String) : AiCreationAssistantIntent
    data class SelectConversation(val workspaceId: String, val conversationId: String) : AiCreationAssistantIntent
    data object LoadOlderTimeline : AiCreationAssistantIntent
    data class RenameConversation(
        val workspaceId: String,
        val conversationId: String,
        val title: String,
    ) : AiCreationAssistantIntent

    data class DeleteConversation(val workspaceId: String, val conversationId: String) : AiCreationAssistantIntent
    data class DeleteWorkspace(val workspaceId: String) : AiCreationAssistantIntent
    data class ChangePermissionMode(val value: AgentPermissionMode) : AiCreationAssistantIntent
    data class ChangeModel(val configId: String, val modelId: String) : AiCreationAssistantIntent
    data class ChangeCreatorImageModelConfig(val configId: String) : AiCreationAssistantIntent
    data class ChangeToolGroupEnabled(
        val groupId: String,
        val enabled: Boolean,
    ) : AiCreationAssistantIntent
    data class ChangeInput(val value: String) : AiCreationAssistantIntent
    data class AddInputImages(val uriValues: List<String>) : AiCreationAssistantIntent
    data class RemoveInputImage(val imageId: String) : AiCreationAssistantIntent
    data class OpenUserMessageEditor(val message: CreationTimelineItem) : AiCreationAssistantIntent
    data class ChangeEditInput(val value: String) : AiCreationAssistantIntent
    data object CloseUserMessageEditor : AiCreationAssistantIntent
    data object SubmitEditedUserMessage : AiCreationAssistantIntent
    data object LoadCharacterDirectory : AiCreationAssistantIntent
    data class ChangeCharacterDirectoryQuery(val value: String) : AiCreationAssistantIntent
    data object LoadMoreCharacters : AiCreationAssistantIntent
    data class AttachCharacter(val characterId: String) : AiCreationAssistantIntent
    data class DetachCharacterRoot(val rootId: String) : AiCreationAssistantIntent
    data class SetPrimaryCharacterRoot(val rootId: String) : AiCreationAssistantIntent
    data class SetCharacterRootAccess(
        val rootId: String,
        val access: CreatorWorkspaceRootAccess,
    ) : AiCreationAssistantIntent
    data class CreateAndAttachCharacter(val name: String, val group: String = "") : AiCreationAssistantIntent
    data object Send : AiCreationAssistantIntent
    data object RegenerateLatest : AiCreationAssistantIntent
    data object Stop : AiCreationAssistantIntent
    data object InstallRuntime : AiCreationAssistantIntent
    data object UpdateRuntime : AiCreationAssistantIntent
    data object RepairRuntime : AiCreationAssistantIntent
    data object UninstallRuntime : AiCreationAssistantIntent
    data object RefreshRuntime : AiCreationAssistantIntent
    data object CancelInstall : AiCreationAssistantIntent
    data class ResolveApproval(
        val requestId: Long,
        val decision: AgentApprovalDecision,
    ) : AiCreationAssistantIntent

    data object Publish : AiCreationAssistantIntent
    data class OpenFile(val path: String) : AiCreationAssistantIntent
    data object CloseFile : AiCreationAssistantIntent
    data class ChangeFileDraft(val value: String) : AiCreationAssistantIntent
    data object SaveFile : AiCreationAssistantIntent
    data object UndoLastAiChanges : AiCreationAssistantIntent
    data object DismissMessage : AiCreationAssistantIntent
}
