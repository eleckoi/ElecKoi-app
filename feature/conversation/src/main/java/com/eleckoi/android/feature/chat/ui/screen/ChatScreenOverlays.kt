package com.eleckoi.android.feature.chat.ui.screen

import com.eleckoi.android.feature.chat.ui.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryPage
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryReadOptions
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.trajectory.DshTrajectoryDialog
import com.eleckoi.android.feature.chat.ui.sheets.ChatHistorySheet
import com.eleckoi.android.feature.chat.ui.sheets.EditMessageSheet
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ModelPickerSheet
import com.eleckoi.android.feature.chat.ui.sheets.SelectMessageTextSheet
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.feature.chat.ui.message.ChatAgentProcessSheet
import com.eleckoi.android.feature.chat.ui.roleplay.dialog.RoleplayOpeningJumpDialog
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.DialogConfirmButton
import com.eleckoi.android.foundation.design.components.DialogDismissButton
import com.eleckoi.android.foundation.design.components.ErrorDialog

/**
 * Collects modal chat surfaces in one layer. The ViewModel remains the sole business-state owner;
 * local selection/diagnostic visibility is returned through narrow dismissal callbacks.
 */
@Composable
internal fun ChatScreenOverlays(
    state: ChatUiState,
    draft: ChatDraft?,
    onIntent: (ChatIntent) -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onSaveCharacterImagePrompt: (String, (Result<String>) -> Unit) -> Unit,
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    selectedUserMessageText: String?,
    onDismissSelectedText: () -> Unit,
    showTrajectory: Boolean,
    trajectoryRuntimeThreadId: String,
    trajectoryIsSending: Boolean,
    loadTrajectory: suspend (DshTrajectoryReadOptions) -> DshTrajectoryPage,
    onDismissTrajectory: () -> Unit,
    onImportHistory: () -> Unit,
    onResumeToEnd: () -> Unit,
) {
    if (state.editingMessage != null) {
        EditMessageSheet(
            editorKey = state.editingMessage.id,
            value = state.editInput,
            isAssistant = state.editingMessage.role == MessageRole.Assistant,
            saving = state.isSavingEditedMessage,
            appearance = state.appearance,
            onValueChange = { onIntent(ChatIntent.EditInputChanged(it)) },
            onDismiss = { onIntent(ChatIntent.CloseEditMessage) },
            onSave = if (state.editingMessage.role == MessageRole.Assistant) {
                { editedText ->
                    onIntent(ChatIntent.EditInputChanged(editedText))
                    onIntent(ChatIntent.SaveEditedMessage)
                }
            } else {
                null
            },
            onRegenerate = if (state.editingMessage.role == MessageRole.User) {
                { editedText ->
                    onResumeToEnd()
                    onIntent(ChatIntent.EditInputChanged(editedText))
                    onIntent(ChatIntent.SubmitEditedMessage)
                }
            } else {
                null
            },
        )
    }

    if (state.deleteMessagesConfirmationOpen) {
        ConfirmDialog(
            title = "删除这些消息？",
            message = "所选消息以及它之后的全部消息、图片和工具调用记录都会被永久删除。",
            appearance = state.appearance,
            confirmText = "确认删除",
            destructive = true,
            onDismiss = { onIntent(ChatIntent.DismissDeleteMessagesConfirmation) },
            onConfirm = {
                onResumeToEnd()
                onIntent(ChatIntent.ConfirmDeleteMessages)
            },
        )
    }

    selectedUserMessageText?.let { text ->
        SelectMessageTextSheet(
            text = text,
            appearance = state.appearance,
            onDismiss = onDismissSelectedText,
        )
    }

    ChatHistorySheet(
            visible = state.historyOpen,
            sessions = state.sessions,
            currentSessionId = draft?.session?.id.orEmpty(),
            currentCharacterId = draft?.session?.characterId ?: state.chatCharacterId,
            characterName = draft?.session?.characterName ?: state.chatCharacterName,
            saveMode = state.historySaveMode,
            appearance = state.appearance,
            onDismiss = { onIntent(ChatIntent.SetHistoryOpen(false)) },
            onLoadChat = { sessionId ->
                onIntent(ChatIntent.LoadDraft(sessionId))
                onIntent(ChatIntent.SetHistoryOpen(false))
            },
            onSaveMode = { onIntent(ChatIntent.ChangeHistorySaveMode(it)) },
            onDelete = { sessionId ->
                onIntent(ChatIntent.DeleteHistoryChat(sessionId))
                if (sessionId == draft?.session?.id) {
                    onIntent(ChatIntent.SetHistoryOpen(false))
                }
            },
            onExport = { onIntent(ChatIntent.ExportHistoryChats(it)) },
            onImport = onImportHistory,
        )

    ModelPickerSheet(
            visible = state.modelPickerOpen,
            configs = state.modelConfigs + state.imageModelConfigs,
            selectedConfigId = draft?.selectedModelConfig?.id.orEmpty(),
            selectedModel = draft?.selectedModel.orEmpty(),
            characterImagePrompt = draft?.session?.characterPersona?.imagePrompt.orEmpty(),
            appearance = state.appearance,
            onDismiss = { onIntent(ChatIntent.SetModelPickerOpen(false)) },
            onSelect = { configId, model ->
                onIntent(
                    ChatIntent.SelectModel(
                        configId,
                        model,
                    ),
                )
            },
            onSaveConfig = onSaveModelConfig,
            onCharacterImagePromptChange = onSaveCharacterImagePrompt,
            onRefreshModels = onRefreshModels,
        )

    if (state.errorMessage.isNotBlank()) {
        ErrorDialog(
            message = state.errorMessage,
            appearance = state.appearance,
            onDismiss = { onIntent(ChatIntent.DismissError) },
        )
    }

    state.requiredToolPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = {
                if (!prompt.enabling) onIntent(ChatIntent.DismissRequiredToolPrompt)
            },
            title = { Text("需要开启角色设定库工具", color = state.appearance.mobileText) },
            text = {
                Text(
                    "模型尝试读取角色设定，但“角色设定库”工具当前处于关闭状态，所以工具调用被当成了普通文字。开启后，请重新生成这条回复。",
                    color = state.appearance.mobileMuted,
                )
            },
            confirmButton = {
                DialogConfirmButton(
                    text = if (prompt.enabling) "正在开启…" else "立即开启",
                    appearance = state.appearance,
                    enabled = !prompt.enabling,
                    onClick = { onIntent(ChatIntent.EnableRequiredSettingLibraryTool) },
                )
            },
            dismissButton = {
                if (!prompt.enabling) {
                    DialogDismissButton(
                        text = "稍后",
                        appearance = state.appearance,
                        onClick = { onIntent(ChatIntent.DismissRequiredToolPrompt) },
                    )
                }
            },
            containerColor = state.appearance.mobileSurface,
        )
    }

    if (showTrajectory) {
        DshTrajectoryDialog(
            runtimeThreadId = trajectoryRuntimeThreadId,
            isSending = trajectoryIsSending,
            appearance = state.appearance,
            load = loadTrajectory,
            onDismiss = onDismissTrajectory,
        )
    }

}

@Composable
internal fun ChatRoleplayOverlays(
    state: ChatUiState,
    draft: ChatDraft?,
    transcript: RoleplayTranscriptModel?,
    presentedMessages: List<ChatMessage>,
    processMessageId: String?,
    openingJumpOpen: Boolean,
    onDismissProcess: () -> Unit,
    onSelectOpeningOption: (String) -> Unit,
    onDismissOpeningJump: () -> Unit,
) {
    processMessageId?.let { messageId ->
        val message = transcript
            ?.messages
            ?.firstOrNull { it.source.id == messageId }
            ?.source
            ?: presentedMessages.firstOrNull { it.id == messageId }
        if (message == null) {
            LaunchedEffect(messageId) { onDismissProcess() }
        } else {
            ChatAgentProcessSheet(
                message = message,
                appearance = state.appearance.asRoleplayReadingTheme(),
                onDismiss = onDismissProcess,
            )
        }
    }
    if (openingJumpOpen && draft != null) {
        RoleplayOpeningJumpDialog(
            options = draft.openingOptions,
            selectedIndex = draft.openingOptions.indexOfFirst {
                it.id == draft.selectedOpeningOptionId
            },
            appearance = state.appearance.asRoleplayReadingTheme(),
            onSelect = onSelectOpeningOption,
            onDismiss = onDismissOpeningJump,
        )
    }
}
