package com.eleckoi.android.feature.settings.ui.personalization.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Owns persistence for the chat-display editor while the Composable keeps only draft UI state. */
class ChatDisplaySettingsViewModel(
    private val repository: UiPreferencesRepository,
) : ViewModel() {
    val preferences: StateFlow<UiPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UiPreferences(),
    )

    suspend fun selectLayoutMode(mode: ChatLayoutMode) = withContext(Dispatchers.IO) {
        repository.setChatLayoutMode(mode)
    }

    suspend fun setGenerationStatsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        repository.setChatGenerationStatsEnabled(enabled)
    }

    suspend fun resetLayout(mode: ChatLayoutMode) = withContext(Dispatchers.IO) {
        repository.resetChatLayoutPreferences(mode)
    }

    internal suspend fun commitChangedLayout(
        draft: ChatLayoutDraft,
        stored: ChatLayoutDraft,
    ) = withContext(Dispatchers.IO) {
        if (draft.layoutMode != stored.layoutMode) repository.setChatLayoutMode(draft.layoutMode)
        if (draft.assistantBubbleEnabled != stored.assistantBubbleEnabled) {
            repository.setAssistantBubbleEnabled(draft.assistantBubbleEnabled)
        }
        if (draft.avatarShape != stored.avatarShape) repository.setChatAvatarShape(draft.avatarShape)
        if (draft.roleplayCardPanel != stored.roleplayCardPanel) {
            repository.setChatRoleplayCardPanel(draft.roleplayCardPanel)
        }
        if (draft.roleplayTimestampsEnabled != stored.roleplayTimestampsEnabled) {
            repository.setChatRoleplayTimestampsEnabled(draft.roleplayTimestampsEnabled)
        }
        if (draft.roleplayMessageFloorsEnabled != stored.roleplayMessageFloorsEnabled) {
            repository.setChatRoleplayMessageFloorsEnabled(draft.roleplayMessageFloorsEnabled)
        }
        if (draft.cornerRadius != stored.cornerRadius) repository.setChatBubbleCornerRadius(draft.cornerRadius)
        if (draft.avatarSize != stored.avatarSize) repository.setChatAvatarSize(draft.avatarSize)
        if (draft.nameFontSize != stored.nameFontSize) repository.setChatNameFontSize(draft.nameFontSize)
        if (draft.nameSpacing != stored.nameSpacing) repository.setChatNameAvatarSpacing(draft.nameSpacing)
        if (draft.horizontalPadding != stored.horizontalPadding) {
            repository.setChatAreaHorizontalPadding(draft.horizontalPadding)
        }
        if (draft.replySpacing != stored.replySpacing) repository.setChatReplySpacing(draft.replySpacing)
        if (draft.turnSpacing != stored.turnSpacing) repository.setChatTurnSpacing(draft.turnSpacing)
        if (draft.fontSize != stored.fontSize) repository.setChatMessageFontSize(draft.fontSize)
        if (draft.lineHeight != stored.lineHeight) repository.setChatLineHeightMultiplier(draft.lineHeight)
        if (draft.letterSpacing != stored.letterSpacing) repository.setChatLetterSpacing(draft.letterSpacing)
        if (draft.paragraphSpacing != stored.paragraphSpacing) {
            repository.setChatParagraphSpacing(draft.paragraphSpacing)
        }
        if (draft.timelineThinkingAnimation != stored.timelineThinkingAnimation) {
            repository.setChatTimelineThinkingAnimation(draft.timelineThinkingAnimation)
        }
        if (draft.reasoningDisplayMode != stored.reasoningDisplayMode) {
            repository.setChatReasoningDisplayMode(draft.reasoningDisplayMode)
        }
        if (draft.toolTimelineStyle != stored.toolTimelineStyle) {
            repository.setChatToolTimelineStyle(draft.toolTimelineStyle)
        }
        if (draft.codeBlockStyle != stored.codeBlockStyle) repository.setChatCodeBlockStyle(draft.codeBlockStyle)
        if (draft.codeBlockWrapEnabled != stored.codeBlockWrapEnabled) {
            repository.setChatCodeBlockWrapEnabled(draft.codeBlockWrapEnabled)
        }
        if (draft.codeBlockShowAllEnabled != stored.codeBlockShowAllEnabled) {
            repository.setChatCodeBlockShowAllEnabled(draft.codeBlockShowAllEnabled)
        }
    }

    companion object {
        fun factory(repository: UiPreferencesRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatDisplaySettingsViewModel::class.java)) {
                        return ChatDisplaySettingsViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
