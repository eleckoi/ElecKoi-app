package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.eleckoi.android.foundation.design.components.ContextWindowUsage

@Composable
internal fun ChatScreenComposer(
    state: ChatUiState,
    draft: ChatDraft?,
    roleplay: Boolean,
    roleplayWebActive: Boolean,
    roleplayWaitingSlotReserved: Boolean,
    nativeWaitingSlotReserved: Boolean,
    waitingIndicatorVisible: Boolean,
    replyPresentationActive: Boolean,
    appearance: AppearanceTheme,
    generationMetrics: ChatGenerationMetrics,
    contextWindowUsage: ContextWindowUsage?,
    dynamicSettingsAvailable: Boolean,
    canRegenerateLatest: Boolean,
    onIntent: (ChatIntent) -> Unit,
    onSubmit: () -> Unit,
    onPickImages: () -> Unit,
    onStop: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenRequestViewer: () -> Unit,
    onOpenVariableViewer: () -> Unit,
    onOpenDynamicSettings: () -> Unit,
    onRegenerateLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        if (roleplayWebActive && roleplayWaitingSlotReserved) {
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
                        animation = state.chatWaitingAnimation,
                    )
                }
            }
        } else if (nativeWaitingSlotReserved) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatWaitingReplySlotHeight)
                    .padding(start = if (roleplay) 16.dp else 22.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (waitingIndicatorVisible) {
                    ChatWaitingReply(
                        appearance = appearance,
                        animation = state.chatWaitingAnimation,
                    )
                }
            }
        }
        ChatComposer(
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
            onOpenPresets = onOpenPresets,
            onPermissionModeChange = { onIntent(ChatIntent.ChangePermissionMode(it)) },
            onOpenTools = onOpenTools,
            onOpenRequestViewer = onOpenRequestViewer,
            onOpenVariableViewer = onOpenVariableViewer,
            onOpenDynamicSettings = onOpenDynamicSettings.takeIf { dynamicSettingsAvailable },
            canRegenerateLatest = canRegenerateLatest,
            onRegenerateLatest = onRegenerateLatest,
            onOpenModelPicker = { onIntent(ChatIntent.SetModelPickerOpen(true)) },
            onToggleMore = { onIntent(ChatIntent.ToggleMoreTools) },
            onDismissMore = { onIntent(ChatIntent.DismissMoreTools) },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (roleplayWebActive) Modifier else Modifier.navigationBarsPadding()),
        )
    }
}
