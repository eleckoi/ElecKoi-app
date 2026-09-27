package com.eleckoi.android.feature.settings.ui.personalization.chat

import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.RoleplayLayoutDefaults
import com.eleckoi.android.feature.preferences.layoutDefaults

/** The editor owns drafts for each visited layout; none are persisted until Save. */
internal data class ChatLayoutEditorState(
    val selectedMode: ChatLayoutMode,
    val storedMode: ChatLayoutMode,
    val drafts: Map<ChatLayoutMode, ChatLayoutDraft>,
    val baselines: Map<ChatLayoutMode, ChatLayoutDraft>,
    val generationStatsEnabled: Boolean,
    val storedGenerationStatsEnabled: Boolean,
) {
    constructor(stored: ChatLayoutDraft, generationStatsEnabled: Boolean) : this(
        selectedMode = stored.layoutMode,
        storedMode = stored.layoutMode,
        drafts = mapOf(stored.layoutMode to stored),
        baselines = mapOf(stored.layoutMode to stored),
        generationStatsEnabled = generationStatsEnabled,
        storedGenerationStatsEnabled = generationStatsEnabled,
    )

    val draft: ChatLayoutDraft get() = drafts.getValue(selectedMode)

    val hasUnsavedChanges: Boolean
        get() = selectedMode != storedMode ||
            generationStatsEnabled != storedGenerationStatsEnabled ||
            drafts.any { (mode, value) -> value != baselines[mode] }

    val changedLayouts: List<ChatLayoutDraft>
        get() = drafts.filter { (mode, value) -> value != baselines[mode] }.values.toList()

    fun edited(next: ChatLayoutDraft): ChatLayoutEditorState {
        require(next.layoutMode == selectedMode)
        // These controls are global; a switch to another layout must keep their unsaved values.
        val updated = drafts.mapValues { (mode, value) ->
            if (mode == selectedMode) next else value.withGlobalControlsFrom(next)
        }
        return copy(drafts = updated)
    }

    fun updated(change: ChatLayoutDraft.() -> ChatLayoutDraft): ChatLayoutEditorState =
        edited(draft.change())

    fun switched(mode: ChatLayoutMode, loaded: ChatLayoutDraft): ChatLayoutEditorState {
        if (mode == selectedMode) return this
        require(loaded.layoutMode == mode)
        val current = draft
        return copy(
            selectedMode = mode,
            drafts = drafts + (mode to (drafts[mode] ?: loaded.withGlobalControlsFrom(current))),
            baselines = baselines + (mode to (baselines[mode] ?: loaded)),
        )
    }

    fun storedChanged(stored: ChatLayoutDraft, statsEnabled: Boolean): ChatLayoutEditorState =
        if (hasUnsavedChanges || (stored == draft && statsEnabled == generationStatsEnabled)) {
            this
        } else {
            ChatLayoutEditorState(stored, statsEnabled)
        }

    fun resetCurrentLayout(): ChatLayoutEditorState {
        val defaults = selectedMode.layoutDefaults
        val current = draft
        return edited(current.copy(
            assistantBubbleEnabled = defaults.assistantBubbleEnabled,
            avatarShape = defaults.avatarShape,
            roleplayCardPanel = if (selectedMode == ChatLayoutMode.Roleplay) {
                RoleplayLayoutDefaults.CardPanel
            } else current.roleplayCardPanel,
            roleplayTimestampsEnabled = if (selectedMode == ChatLayoutMode.Roleplay) {
                RoleplayLayoutDefaults.TimestampsEnabled
            } else current.roleplayTimestampsEnabled,
            roleplayMessageFloorsEnabled = if (selectedMode == ChatLayoutMode.Roleplay) {
                RoleplayLayoutDefaults.MessageFloorsEnabled
            } else current.roleplayMessageFloorsEnabled,
            cornerRadius = defaults.bubbleCornerRadius,
            avatarSize = defaults.avatarSize,
            nameFontSize = defaults.nameFontSize,
            nameSpacing = defaults.nameAvatarSpacing,
            horizontalPadding = defaults.horizontalPadding,
            replySpacing = defaults.replySpacing,
            turnSpacing = defaults.turnSpacing,
            fontSize = defaults.messageFontSize,
            lineHeight = defaults.lineHeightMultiplier,
            letterSpacing = defaults.letterSpacing,
            paragraphSpacing = defaults.paragraphSpacing,
            timelineThinkingAnimation = defaults.timelineThinkingAnimation,
        ))
    }
}

private fun ChatLayoutDraft.withGlobalControlsFrom(other: ChatLayoutDraft): ChatLayoutDraft = copy(
    reasoningDisplayMode = other.reasoningDisplayMode,
    toolTimelineStyle = other.toolTimelineStyle,
    codeBlockStyle = other.codeBlockStyle,
    codeBlockWrapEnabled = other.codeBlockWrapEnabled,
    codeBlockShowAllEnabled = other.codeBlockShowAllEnabled,
)
