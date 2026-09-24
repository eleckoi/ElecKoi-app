package com.eleckoi.android.feature.chat.ui.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.ui.composer.menu.ChatComposerMenu
import com.eleckoi.android.feature.chat.ui.composer.shared.UnifiedChatComposerBody
import com.eleckoi.android.feature.chat.ui.composer.shared.UnifiedChatComposerSurface
import com.eleckoi.android.feature.chat.ui.composer.shared.unifiedChatComposerPlacement
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ContextWindowUsage

@Composable
fun ChatComposer(
    input: String,
    inputImages: List<ChatUserImageAttachment> = emptyList(),
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    stopEnabled: Boolean = isSending,
    modelLabel: String,
    modelProviderId: String,
    permissionMode: AgentPermissionMode,
    moreToolsOpen: Boolean,
    appearance: AppearanceTheme,
    generationMetrics: ChatGenerationMetrics = ChatGenerationMetrics(),
    contextWindowUsage: ContextWindowUsage? = null,
    showGenerationStats: Boolean = true,
    onSubmit: () -> Unit,
    canAttachImages: Boolean = false,
    isPreparingImages: Boolean = false,
    onPickImages: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
    onStop: () -> Unit,
    onVoiceInput: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onOpenVariableViewer: () -> Unit,
    onOpenDynamicSettings: (() -> Unit)?,
    canDeleteMessages: Boolean,
    onDeleteMessages: () -> Unit,
    canRegenerateLatest: Boolean,
    onRegenerateLatest: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onToggleMore: () -> Unit,
    onDismissMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        UnifiedChatComposerSurface(
            appearance = appearance,
            modifier = Modifier.unifiedChatComposerPlacement(),
            menuContent = {
                ChatComposerMenu(
                    expanded = moreToolsOpen,
                    appearance = appearance,
                    permissionMode = permissionMode,
                    permissionEnabled = !isSending,
                    canRegenerateLatest = canRegenerateLatest,
                    canDeleteMessages = canDeleteMessages,
                    canAttachImages = canAttachImages && !isPreparingImages,
                    onDismiss = onDismissMore,
                    onPickImages = onPickImages,
                    onOpenHistory = onOpenHistory,
                    onPermissionModeChange = onPermissionModeChange,
                    onOpenVariableViewer = onOpenVariableViewer,
                    onOpenDynamicSettings = onOpenDynamicSettings,
                    onDeleteMessages = onDeleteMessages,
                    onRegenerateLatest = onRegenerateLatest,
                )
            },
        ) {
            UnifiedChatComposerBody(
                input = input,
                inputImages = inputImages,
                onInputChange = onInputChange,
                inputEnabled = true,
                isSending = isSending,
                stopEnabled = stopEnabled,
                submitEnabled = true,
                modelLabel = modelLabel,
                modelSelectorEnabled = true,
                moreToolsOpen = moreToolsOpen,
                appearance = appearance,
                contextWindowUsage = contextWindowUsage,
                onSubmit = onSubmit,
                onRemoveImage = onRemoveImage,
                onStop = onStop,
                onVoiceInput = onVoiceInput,
                onOpenModelPicker = onOpenModelPicker,
                onToggleMore = onToggleMore,
                onDismissMore = onDismissMore,
            )
        }
        ChatGenerationStatsLine(
            metrics = generationMetrics,
            appearance = appearance,
            enabled = showGenerationStats,
        )
    }
}
