package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.ui.ChatIntent
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.background.CharacterChatBackgroundPage
import com.eleckoi.android.feature.chat.ui.variables.VariableViewerPage
import com.eleckoi.android.feature.preferences.NewCharacterBackground

@Composable
internal fun CharacterChatBackgroundDestination(
    state: ChatUiState,
    draft: ChatDraft,
    newCharacterBackground: NewCharacterBackground,
    onNewCharacterBackgroundChange: (NewCharacterBackground) -> Unit,
    onIntent: (ChatIntent) -> Unit,
    onBack: () -> Unit,
) {
    val persona = draft.session.characterPersona
    CharacterChatBackgroundPage(
        characterName = persona.assistantName.ifBlank { draft.session.characterName },
        defaultBackgroundPath = persona.defaultChatBackground,
        backgroundPath = persona.chatBackground,
        backgroundOpacity = persona.chatBackgroundOpacity,
        backgroundBlur = persona.chatBackgroundBlur,
        backgroundScrim = persona.chatBackgroundScrim,
        newCharacterBackground = newCharacterBackground,
        appearance = state.appearance,
        errorMessage = state.chatBackgroundErrorMessage,
        // The page autosaves, so committing must not also navigate away.
        onSave = { file, opacity, blur, scrim, global ->
            onIntent(ChatIntent.SaveChatBackground(file, opacity, blur, scrim, global))
        },
        onSetGlobal = { file, opacity, blur, scrim ->
            onIntent(ChatIntent.SetGlobalChatBackground(file, opacity, blur, scrim))
        },
        onUseAppDefault = { onIntent(ChatIntent.UseAppDefaultChatBackground) },
        onUseCharacterCard = { onIntent(ChatIntent.UseCharacterCardChatBackground) },
        onUseCustom = { onIntent(ChatIntent.UseCustomChatBackground) },
        onUseGlobal = { onIntent(ChatIntent.UseExistingGlobalChatBackground) },
        onNewCharacterBackgroundChange = onNewCharacterBackgroundChange,
        onDismissError = { onIntent(ChatIntent.DismissChatBackgroundError) },
        onBack = {
            onIntent(ChatIntent.DismissChatBackgroundError)
            onBack()
        },
    )
}

@Composable
internal fun ChatVariableViewerDestination(
    state: ChatUiState,
    draft: ChatDraft,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(state.historyHasMore, state.historyPageLoading) {
        if (state.historyHasMore && !state.historyPageLoading) {
            onLoadOlder()
        }
    }
    VariableViewerPage(
        messages = draft.session.messages,
        initialStateJson = draft.session.initialVariableStateJson,
        currentStateJson = draft.session.variableStateJson,
        historyLoading = state.historyHasMore || state.historyPageLoading,
        appearance = state.appearance,
        onBack = onBack,
    )
}
