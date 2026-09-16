package com.eleckoi.android.feature.preferences

import androidx.datastore.preferences.core.Preferences

internal fun Preferences.toUiPreferences(): UiPreferences {
    val preferences = this
    val resolvedMode = ChatLayoutMode.fromStorageKey(preferences[ChatLayoutModeKey])
    val defaults = resolvedMode.layoutDefaults
    return UiPreferences(
        pinnedChatIds = preferences[PinnedChatIdsJson]
            ?.let(::decodeStringList)
            ?: emptyList(),
        hiddenChatIds = preferences[HiddenChatIdsJson]
            ?.let(::decodeStringList)
            ?: emptyList(),
        searchHistory = preferences[SearchHistoryJson]
            ?.let(::decodeStringList)
            ?: emptyList(),
        lastActiveChatSessionId = preferences[LastActiveChatSessionId].orEmpty(),
        activeChatSessionIds = preferences[ActiveChatSessionIdsJson]
            ?.let(::decodeStringMap)
            ?: emptyMap(),
        pinnedCreatorWorkspaceIds = preferences[PinnedCreatorWorkspaceIdsJson]
            ?.let(::decodeStringList)
            ?: emptyList(),
        creatorWorkspaceExpansionOverrides = preferences[CreatorWorkspaceExpansionOverridesJson]
            ?.let(::decodeBooleanMap)
            ?: emptyMap(),
        lastCreatorWorkspaceId = preferences[LastCreatorWorkspaceId].orEmpty(),
        creatorAssistantEnabledToolGroupIds =
            preferences[CreatorAssistantEnabledToolGroupIds],
        creatorAssistantImageModelConfigId =
            preferences[CreatorAssistantImageModelConfigId].orEmpty(),
        historySaveMode = normalizeHistoryMode(
            preferences[HistorySaveMode].orEmpty(),
        ),
        appearanceMode = AppearanceMode.fromStorageKey(preferences[AppearanceModeKey]),
        newCharacterBackground = NewCharacterBackground.fromStorageKey(
            preferences[NewCharacterBackgroundKey],
        ),
        listCharacterArtwork = ListCharacterArtwork.fromStorageKey(
            preferences[SidebarCharacterArtwork],
        ),
        defaultChatConfigId = preferences[DefaultChatConfigId].orEmpty(),
        defaultChatModel = preferences[DefaultChatModel].orEmpty(),
        assistantBubbleEnabled = resolveAssistantBubbleEnabled(
            mode = resolvedMode,
            storedValue = preferences[
                profileKey(
                    AssistantBubbleEnabledAgent,
                    AssistantBubbleEnabledSocial,
                    AssistantBubbleEnabledRoleplay,
                    resolvedMode,
                ),
            ],
        ),
        chatLayoutMode = resolvedMode,
        chatReasoningDisplayMode = ChatReasoningDisplayMode.fromStorageKey(
            preferences[ChatReasoningDisplayModeKey],
        ),
        chatToolTimelineStyle = ChatToolTimelineStyle.fromStorageKey(
            preferences[ChatToolTimelineStyleKey],
        ),
        chatGenerationStatsEnabled = preferences[ChatGenerationStatsEnabled] ?: true,
        chatCodeBlockStyle = ChatCodeBlockStyle.fromStorageKey(
            preferences[ChatCodeBlockStyleKey],
        ),
        chatCodeBlockWrapEnabled = preferences[ChatCodeBlockWrapEnabled]
            ?: ChatCodeBlockDefaults.WrapEnabled,
        chatCodeBlockShowAllEnabled = preferences[ChatCodeBlockShowAllEnabled]
            ?: ChatCodeBlockDefaults.ShowAllEnabled,
        chatAvatarShape = preferences[
            profileKey(
                ChatAvatarShapeAgent,
                ChatAvatarShapeSocial,
                ChatAvatarShapeRoleplay,
                resolvedMode,
            ),
        ]?.let(ChatAvatarShape::fromStorageKey) ?: defaults.avatarShape,
        chatRoleplayCardPanel = preferences[ChatRoleplayCardPanel]
            ?: RoleplayLayoutDefaults.CardPanel,
        chatBubbleCornerRadius = (preferences[
                profileKey(
                    ChatBubbleCornerRadiusAgent,
                    ChatBubbleCornerRadiusSocial,
                    ChatBubbleCornerRadiusRoleplay,
                    resolvedMode,
                ),
            ] ?: defaults.bubbleCornerRadius).coerceIn(0f, 24f),
        chatAvatarSize = (preferences[
                profileKey(ChatAvatarSizeAgent, ChatAvatarSizeSocial, ChatAvatarSizeRoleplay, resolvedMode),
            ] ?: defaults.avatarSize).coerceIn(
            ChatLayoutDefaults.AvatarSizeMin,
            ChatLayoutDefaults.AvatarSizeMax,
        ),
        chatNameFontSize = (preferences[
            profileKey(
                ChatNameFontSizeAgent,
                ChatNameFontSizeSocial,
                ChatNameFontSizeRoleplay,
                resolvedMode,
            ),
        ] ?: defaults.nameFontSize)
            .coerceIn(ChatLayoutDefaults.NameFontSizeMin, ChatLayoutDefaults.NameFontSizeMax),
        chatNameAvatarSpacing = (preferences[
            profileKey(
                ChatNameAvatarSpacingAgent,
                ChatNameAvatarSpacingSocial,
                ChatNameAvatarSpacingRoleplay,
                resolvedMode,
            ),
        ] ?: defaults.nameAvatarSpacing).coerceIn(0f, 20f),
        chatAreaHorizontalPadding = (preferences[
                profileKey(
                    ChatAreaHorizontalPaddingAgent,
                    ChatAreaHorizontalPaddingSocial,
                    ChatAreaHorizontalPaddingRoleplay,
                    resolvedMode,
                ),
            ] ?: defaults.horizontalPadding).coerceIn(0f, 32f),
        chatReplySpacing = (preferences[
                profileKey(
                    ChatReplySpacingAgent,
                    ChatReplySpacingSocial,
                    ChatReplySpacingRoleplay,
                    resolvedMode,
                ),
            ] ?: defaults.replySpacing).coerceIn(0f, 32f),
        chatTurnSpacing = (preferences[
                profileKey(ChatTurnSpacingAgent, ChatTurnSpacingSocial, ChatTurnSpacingRoleplay, resolvedMode),
            ] ?: defaults.turnSpacing).coerceIn(0f, 32f),
        chatMessageFontSize = (preferences[
                profileKey(
                    ChatMessageFontSizeAgent,
                    ChatMessageFontSizeSocial,
                    ChatMessageFontSizeRoleplay,
                    resolvedMode,
                ),
            ] ?: defaults.messageFontSize).coerceIn(
            ChatLayoutDefaults.MessageFontSizeMin,
            ChatLayoutDefaults.MessageFontSizeMax,
        ),
        chatLineHeightMultiplier = (preferences[
            profileKey(
                ChatLineHeightMultiplierAgent,
                ChatLineHeightMultiplierSocial,
                ChatLineHeightMultiplierRoleplay,
                resolvedMode,
            ),
        ] ?: defaults.lineHeightMultiplier).coerceIn(0.8f, 1.6f),
        chatLetterSpacing = (preferences[
            profileKey(
                ChatLetterSpacingAgent,
                ChatLetterSpacingSocial,
                ChatLetterSpacingRoleplay,
                resolvedMode,
            ),
        ] ?: defaults.letterSpacing).coerceIn(-1f, 4f),
        chatParagraphSpacing = (preferences[
            profileKey(
                ChatParagraphSpacingAgent,
                ChatParagraphSpacingSocial,
                ChatParagraphSpacingRoleplay,
                resolvedMode,
            ),
        ] ?: defaults.paragraphSpacing).coerceIn(0f, 24f),
        chatTimelineThinkingAnimation = preferences[
            profileKey(
                ChatTimelineThinkingAnimationAgent,
                ChatTimelineThinkingAnimationSocial,
                ChatTimelineThinkingAnimationRoleplay,
                resolvedMode,
            ),
        ]?.let(ChatTimelineThinkingAnimation::fromStorageKey)
            ?: defaults.timelineThinkingAnimation,
        appearanceTheme = appearanceThemeFromPreferences(preferences),
    )
}
 
internal fun <T> profileKey(
    agent: Preferences.Key<T>,
    social: Preferences.Key<T>,
    roleplay: Preferences.Key<T>,
    mode: ChatLayoutMode,
): Preferences.Key<T> = when (mode) {
    ChatLayoutMode.Social -> social
    ChatLayoutMode.Agent -> agent
    ChatLayoutMode.Roleplay -> roleplay
}
