package com.eleckoi.android.feature.chat.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.ui.ChatIntent
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.composer.ChatComposer
import com.eleckoi.android.feature.chat.ui.loading.ChatWaitingReply
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.ContextWindowUsage

@Composable
internal fun ChatScreenComposer(
    state: ChatUiState,
    draft: ChatDraft?,
    webWaitingSlotReserved: Boolean,
    waitingIndicatorVisible: Boolean,
    replyPresentationActive: Boolean,
    appearance: AppearanceTheme,
    generationMetrics: ChatGenerationMetrics,
    contextWindowUsage: ContextWindowUsage?,
    dynamicSettingsAvailable: Boolean,
    canDeleteMessages: Boolean,
    canRegenerateLatest: Boolean,
    onIntent: (ChatIntent) -> Unit,
    onSubmit: () -> Unit,
    onPickImages: () -> Unit,
    onStop: () -> Unit,
    onOpenVariableViewer: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenDynamicSettings: () -> Unit,
    onDeleteMessages: () -> Unit,
    onRegenerateLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(
        enabled = state.deleteMessagesOpen &&
            !state.deleteMessagesConfirmationOpen &&
            !state.isDeletingMessages,
    ) {
        onIntent(ChatIntent.CloseDeleteMessages)
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        if (!state.deleteMessagesOpen && webWaitingSlotReserved) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatWaitingReplySlotHeight)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (waitingIndicatorVisible) {
                    ChatWaitingReply(
                        appearance = appearance,
                    )
                }
            }
        }
        if (state.deleteMessagesOpen) {
            ChatMessageDeleteBar(
                selected = state.deleteFromMessageId != null,
                deleting = state.isDeletingMessages,
                appearance = appearance,
                onDelete = { onIntent(ChatIntent.RequestDeleteMessages) },
                onCancel = { onIntent(ChatIntent.CloseDeleteMessages) },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        } else ChatComposer(
            input = state.input,
            inputImages = state.inputImages,
            onInputChange = { onIntent(ChatIntent.InputChanged(it)) },
            isSending = replyPresentationActive,
            stopEnabled = state.isSending,
            modelLabel = draft?.selectedModel.orEmpty(),
            modelProviderId = draft?.selectedModelConfig?.provider.orEmpty(),
            permissionMode = draft?.session?.permissionMode
                ?: com.eleckoi.android.engine.agent.api.AgentPermissionMode.AskForApproval,
            moreToolsOpen = state.moreToolsOpen,
            appearance = appearance,
            generationMetrics = generationMetrics,
            contextWindowUsage = contextWindowUsage,
            showGenerationStats = state.chatGenerationStatsEnabled,
            onSubmit = onSubmit,
            canAttachImages = draft?.let { current ->
                current.selectedModelConfig.supportsImageInput(current.selectedModel)
            } == true,
            isPreparingImages = state.isPreparingInputImages,
            onPickImages = onPickImages,
            onRemoveImage = { onIntent(ChatIntent.RemoveInputImage(it)) },
            onStop = onStop,
            onOpenHistory = { onIntent(ChatIntent.SetHistoryOpen(true)) },
            onPermissionModeChange = { onIntent(ChatIntent.ChangePermissionMode(it)) },
            onOpenVariableViewer = onOpenVariableViewer,
            onOpenTools = onOpenTools,
            onOpenDynamicSettings = onOpenDynamicSettings.takeIf { dynamicSettingsAvailable },
            canDeleteMessages = canDeleteMessages,
            onDeleteMessages = onDeleteMessages,
            canRegenerateLatest = canRegenerateLatest,
            onRegenerateLatest = onRegenerateLatest,
            onOpenModelPicker = { onIntent(ChatIntent.SetModelPickerOpen(true)) },
            onToggleMore = { onIntent(ChatIntent.ToggleMoreTools) },
            onDismissMore = { onIntent(ChatIntent.DismissMoreTools) },
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ChatMessageDeleteBar(
    selected: Boolean,
    deleting: Boolean,
    appearance: AppearanceTheme,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDelete,
            enabled = selected && !deleting,
            modifier = Modifier
                .widthIn(min = 96.dp)
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (selected) ElecKoiDanger.copy(alpha = 0.42f) else appearance.mobileLine,
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ElecKoiDanger,
                disabledContentColor = appearance.mobileMuted.copy(alpha = 0.5f),
            ),
        ) {
            Text(if (deleting) "删除中…" else "删除")
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !deleting,
            modifier = Modifier
                .widthIn(min = 96.dp)
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, appearance.mobileLine),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = appearance.mobileText,
                disabledContentColor = appearance.mobileMuted.copy(alpha = 0.5f),
            ),
        ) {
            Text("取消")
        }
    }
}
