package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.ChatWaitingAnimation
import com.eleckoi.android.feature.preferences.RoleplayLayoutDefaults
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.io.File

data class ChatUiState(
    val draft: ChatDraft? = null,
    val isDraftLoading: Boolean = false,
    val chatCharacterId: String = "",
    val chatCharacterName: String = "",
    val input: String = "",
    val inputImages: List<ChatUserImageAttachment> = emptyList(),
    val isPreparingInputImages: Boolean = false,
    val isSending: Boolean = false,
    val generationPresentation: ChatGenerationPresentation? = null,
    val errorMessage: String = "",
    val requiredToolPrompt: RequiredToolPrompt? = null,
    val chatBackgroundErrorMessage: String = "",
    val editingMessage: ChatMessage? = null,
    val editInput: String = "",
    val isSavingEditedMessage: Boolean = false,
    val deleteMessagesOpen: Boolean = false,
    val deleteFromMessageId: String? = null,
    val deleteMessagesConfirmationOpen: Boolean = false,
    val isDeletingMessages: Boolean = false,
    val assistantBubbleEnabled: Boolean = RoleplayLayoutDefaults.AssistantBubbleEnabled,
    val chatLayoutMode: ChatLayoutMode = ChatLayoutMode.Default,
    val chatRoleplayCardPanel: Boolean = RoleplayLayoutDefaults.CardPanel,
    val chatBubbleWideLayout: Boolean = true,
    val chatBubbleCornerRadius: Float = RoleplayLayoutDefaults.BubbleCornerRadius,
    val chatAvatarSize: Float = RoleplayLayoutDefaults.AvatarSize,
    val chatAvatarShape: ChatAvatarShape = RoleplayLayoutDefaults.AvatarShape,
    val chatNameFontSize: Float = RoleplayLayoutDefaults.NameFontSize,
    val chatNameAvatarSpacing: Float = RoleplayLayoutDefaults.NameAvatarSpacing,
    val chatAreaHorizontalPadding: Float = RoleplayLayoutDefaults.HorizontalPadding,
    val chatReplySpacing: Float = RoleplayLayoutDefaults.ReplySpacing,
    val chatTurnSpacing: Float = RoleplayLayoutDefaults.TurnSpacing,
    val chatMessageFontSize: Float = RoleplayLayoutDefaults.MessageFontSize,
    val chatLineHeightMultiplier: Float = RoleplayLayoutDefaults.LineHeightMultiplier,
    val chatLetterSpacing: Float = RoleplayLayoutDefaults.LetterSpacing,
    val chatParagraphSpacing: Float = RoleplayLayoutDefaults.ParagraphSpacing,
    val chatWaitingAnimation: ChatWaitingAnimation = RoleplayLayoutDefaults.WaitingAnimation,
    val chatGenerationStatsEnabled: Boolean = true,
    val moreToolsOpen: Boolean = false,
    val historyOpen: Boolean = false,
    val historyHasMore: Boolean = false,
    val historyPageLoading: Boolean = false,
    /** True only after Paging has presented the first authoritative Room window. */
    val historyInitialPageReady: Boolean = false,
    val modelPickerOpen: Boolean = false,
    val sessions: List<ChatListItem> = emptyList(),
    val modelConfigs: List<ModelConfig> = emptyList(),
    /** Kept separate so the Author chat API can never select an image provider as its reply model. */
    val imageModelConfigs: List<ModelConfig> = emptyList(),
    val historySaveMode: String = "all",
    val appearance: AppearanceTheme = AppearanceTheme(),
)

/**
 * Stable identity for the latest generation whose reply may still need a visual hand-off.
 *
 * This lives with the ViewModel state instead of being inferred from Compose observing a pending
 * row. Lifecycle-aware collectors may legitimately skip every intermediate stream value while the
 * app is backgrounded, but the completed reply must still retain the generation that owns it.
 */
data class ChatGenerationPresentation(
    val generation: Int,
    val sessionId: String,
    val assistantMessageId: String? = null,
)

data class RequiredToolPrompt(
    val characterId: String,
    val assistantMessageId: String,
    val enabling: Boolean = false,
)

sealed interface ChatIntent {
    data object LoadInitialDraft : ChatIntent
    data class LoadDraft(val sessionId: String) : ChatIntent
    data object LoadOlderMessages : ChatIntent
    data class OpenCharacterChat(val characterId: String) : ChatIntent
    data class ApplyAppearanceTheme(val theme: AppearanceTheme) : ChatIntent
    data class InputChanged(val value: String) : ChatIntent
    data class AddInputImages(val uriValues: List<String>) : ChatIntent
    data class RemoveInputImage(val imageId: String) : ChatIntent
    data object SendMessage : ChatIntent
    data object StopSending : ChatIntent
    data class AcknowledgeGenerationPresentation(val generation: Int) : ChatIntent
    data object CreateChat : ChatIntent
    data class CreateChatForCharacter(val characterId: String) : ChatIntent
    data class OpenEditMessage(val message: ChatMessage) : ChatIntent
    data object CloseEditMessage : ChatIntent
    data class EditInputChanged(val value: String) : ChatIntent
    data object SubmitEditedMessage : ChatIntent
    data object SaveEditedMessage : ChatIntent
    data object OpenDeleteMessages : ChatIntent
    data object CloseDeleteMessages : ChatIntent
    data class SelectDeleteFromMessage(val messageId: String) : ChatIntent
    data object RequestDeleteMessages : ChatIntent
    data object DismissDeleteMessagesConfirmation : ChatIntent
    data object ConfirmDeleteMessages : ChatIntent
    data class RegenerateFrom(val message: ChatMessage) : ChatIntent
    data class RegenerateImage(val messageId: String, val attachmentId: String) : ChatIntent
    data class SelectOpeningOption(val openingOptionId: String) : ChatIntent
    data class ChangePermissionMode(val mode: AgentPermissionMode) : ChatIntent
    data class SelectModel(
        val configId: String,
        val model: String,
    ) : ChatIntent
    data class ChangeHistorySaveMode(val mode: String) : ChatIntent
    data class SaveChatBackground(
        val backgroundFile: File?,
        val opacity: Float,
        val blur: Float,
        val scrim: Float,
        val global: Boolean,
    ) : ChatIntent
    data class SetGlobalChatBackground(
        val backgroundFile: File,
        val opacity: Float,
        val blur: Float,
        val scrim: Float,
    ) : ChatIntent
    data object UseAppDefaultChatBackground : ChatIntent
    data object UseCharacterCardChatBackground : ChatIntent
    data object UseCustomChatBackground : ChatIntent
    data object UseExistingGlobalChatBackground : ChatIntent
    data class DeleteHistoryChat(val sessionId: String) : ChatIntent
    data class ExportHistoryChats(val sessionIds: List<String>) : ChatIntent
    data class ImportHistoryChats(val json: String) : ChatIntent
    data class SetHistoryOpen(val open: Boolean) : ChatIntent
    data class SetModelPickerOpen(val open: Boolean) : ChatIntent
    data object DismissError : ChatIntent
    data object DismissRequiredToolPrompt : ChatIntent
    data object EnableRequiredSettingLibraryTool : ChatIntent
    data object DismissChatBackgroundError : ChatIntent
    data class ReportError(val message: String) : ChatIntent
    data object ToggleMoreTools : ChatIntent
    data object DismissMoreTools : ChatIntent
}

sealed interface ChatEffect {
    data class ExportHistoryReady(val json: String, val fileName: String) : ChatEffect
}
