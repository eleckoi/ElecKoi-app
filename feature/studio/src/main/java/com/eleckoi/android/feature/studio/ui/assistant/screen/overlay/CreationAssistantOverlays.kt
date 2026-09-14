package com.eleckoi.android.feature.studio.ui.assistant.screen.overlay

import androidx.compose.runtime.Composable
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.ui.sheets.EditMessageSheet
import com.eleckoi.android.feature.chat.ui.sheets.ModelPickerSheet
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantIntent
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantViewModel
import com.eleckoi.android.feature.studio.ui.assistant.composer.CreatorCharacterRootsSheet

/** Hosts modal surfaces so the main screen remains focused on navigation and content layout. */
@Composable
internal fun CreationAssistantOverlays(
    state: AiCreationAssistantUiState,
    viewModel: AiCreationAssistantViewModel,
    appearance: AppearanceTheme,
    showModelPicker: Boolean,
    onDismissModelPicker: () -> Unit,
    showCharacterRoots: Boolean,
    onDismissCharacterRoots: () -> Unit,
) {
    if (showModelPicker) {
        ModelPickerSheet(
            configs = state.modelConfigs,
            selectedConfigId = state.selectedModelConfigId,
            selectedModel = state.selectedModelId,
            streamEnabled = null,
            appearance = appearance,
            onDismiss = onDismissModelPicker,
            onSelect = { configId, modelId ->
                viewModel.onIntent(AiCreationAssistantIntent.ChangeModel(configId, modelId))
            },
            onStreamChange = {},
            onSaveConfig = viewModel::saveModelConfig,
            onRefreshModels = viewModel::refreshModels,
        )
    }
    if (showCharacterRoots) {
        state.workspace?.let { workspace ->
            CreatorCharacterRootsSheet(
                workspace = workspace,
                rootCharacters = state.creatorRootCharacters,
                directoryCharacters = state.characterDirectory,
                query = state.characterDirectoryQuery,
                nextCursor = state.characterDirectoryNextCursor,
                loading = state.isCharacterDirectoryLoading,
                updating = state.isCharacterRootsUpdating,
                interactionEnabled = !state.isRunning,
                appearance = appearance,
                onDismiss = onDismissCharacterRoots,
                onQueryChange = {
                    viewModel.onIntent(AiCreationAssistantIntent.ChangeCharacterDirectoryQuery(it))
                },
                onLoadMore = {
                    viewModel.onIntent(AiCreationAssistantIntent.LoadMoreCharacters)
                },
                onAttach = {
                    viewModel.onIntent(AiCreationAssistantIntent.AttachCharacter(it))
                },
                onDetach = {
                    viewModel.onIntent(AiCreationAssistantIntent.DetachCharacterRoot(it))
                },
                onSetPrimary = {
                    viewModel.onIntent(AiCreationAssistantIntent.SetPrimaryCharacterRoot(it))
                },
                onAccessChange = { rootId, access ->
                    viewModel.onIntent(
                        AiCreationAssistantIntent.SetCharacterRootAccess(rootId, access),
                    )
                },
                onCreate = {
                    viewModel.onIntent(AiCreationAssistantIntent.CreateAndAttachCharacter(it))
                },
            )
        }
    }
    state.editingUserMessage?.let { message ->
        EditMessageSheet(
            editorKey = message.id,
            value = state.editInput,
            isAssistant = false,
            appearance = appearance,
            onValueChange = {
                viewModel.onIntent(AiCreationAssistantIntent.ChangeEditInput(it))
            },
            onDismiss = {
                viewModel.onIntent(AiCreationAssistantIntent.CloseUserMessageEditor)
            },
            onSave = { editedText ->
                viewModel.onIntent(AiCreationAssistantIntent.ChangeEditInput(editedText))
                viewModel.onIntent(AiCreationAssistantIntent.SubmitEditedUserMessage)
            },
            onRegenerate = null,
        )
    }
}
