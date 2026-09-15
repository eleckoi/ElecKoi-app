package com.eleckoi.android.feature.preferences

import com.eleckoi.android.foundation.design.AppearanceTheme

object SocialLayoutDefaults {
    // Social layout always renders a conversation bubble. Only Agent exposes an on/off choice.
    const val AssistantBubbleEnabled = true
    val AvatarShape = ChatAvatarShape.Circle
    val WaitingAnimation = ChatWaitingAnimation.Dots
    val TimelineThinkingAnimation = ChatTimelineThinkingAnimation.Default
    const val BubbleCornerRadius = 10f
    const val AvatarSize = 40f
    const val NameFontSize = 13f
    const val NameAvatarSpacing = 8f
    const val HorizontalPadding = 10f
    const val ReplySpacing = 16f
    const val TurnSpacing = 16f
    const val MessageFontSize = 16f
    const val LineHeightMultiplier = 1f
    const val LetterSpacing = 0f
    const val ParagraphSpacing = 6f
}

object AgentLayoutDefaults {
    const val AssistantBubbleEnabled = false
    val AvatarShape = ChatAvatarShape.Circle
    val WaitingAnimation = ChatWaitingAnimation.Dots
    val TimelineThinkingAnimation = ChatTimelineThinkingAnimation.Default
    const val BubbleCornerRadius = 12f
    const val AvatarSize = 34.5f
    const val NameFontSize = 13f
    const val NameAvatarSpacing = 8f
    const val HorizontalPadding = 16f
    const val ReplySpacing = 15f
    const val TurnSpacing = 15f
    const val MessageFontSize = 14f
    const val LineHeightMultiplier = 1f
    const val LetterSpacing = 0f
    const val ParagraphSpacing = 6f
}

/** Defaults owned only by the roleplay profile. Equal numbers in two profiles are still separate. */
object RoleplayLayoutDefaults {
    const val AssistantBubbleEnabled = false
    val AvatarShape = ChatAvatarShape.Portrait
    val WaitingAnimation = ChatWaitingAnimation.Dots
    val TimelineThinkingAnimation = ChatTimelineThinkingAnimation.Default
    const val BubbleCornerRadius = 10f
    const val AvatarSize = 55f
    const val PortraitAvatarSize = 55f
    const val NameFontSize = 15f
    const val NameAvatarSpacing = 10f
    const val HorizontalPadding = 10f
    const val ReplySpacing = 4f
    const val TurnSpacing = 5f
    const val MessageFontSize = 14f
    const val LineHeightMultiplier = 1f
    const val LetterSpacing = 0f
    const val ParagraphSpacing = 10f
    const val CardPanel = false
}

/** Limits and formulas shared by all three independently stored layout profiles. */
object ChatLayoutDefaults {
    const val BodyLineHeightBaseMultiplier = 1.4f
    const val AvatarSizeMin = 24f
    const val AvatarSizeMax = 96f
    const val NameFontSizeMin = 10f
    const val NameFontSizeMax = 18f
    // 12 was too high a floor for the roleplay layout, where the panel already carries the text and
    // long scenes read better set small.
    const val MessageFontSizeMin = 9f
    const val MessageFontSizeMax = 20f
    fun nameLineHeight(fontSize: Float): Float {
        return maxOf(16f, fontSize.coerceIn(NameFontSizeMin, NameFontSizeMax) * 1.35f)
    }
}

data class ChatLayoutProfileDefaults(
    val assistantBubbleEnabled: Boolean,
    val bubbleCornerRadius: Float,
    val avatarSize: Float,
    val avatarShape: ChatAvatarShape,
    val nameFontSize: Float,
    val nameAvatarSpacing: Float,
    val horizontalPadding: Float,
    val replySpacing: Float,
    val turnSpacing: Float,
    val messageFontSize: Float,
    val lineHeightMultiplier: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Float,
    val waitingAnimation: ChatWaitingAnimation,
    val timelineThinkingAnimation: ChatTimelineThinkingAnimation,
)

private val SocialProfileDefaults = ChatLayoutProfileDefaults(
    assistantBubbleEnabled = SocialLayoutDefaults.AssistantBubbleEnabled,
    bubbleCornerRadius = SocialLayoutDefaults.BubbleCornerRadius,
    avatarSize = SocialLayoutDefaults.AvatarSize,
    avatarShape = SocialLayoutDefaults.AvatarShape,
    nameFontSize = SocialLayoutDefaults.NameFontSize,
    nameAvatarSpacing = SocialLayoutDefaults.NameAvatarSpacing,
    horizontalPadding = SocialLayoutDefaults.HorizontalPadding,
    replySpacing = SocialLayoutDefaults.ReplySpacing,
    turnSpacing = SocialLayoutDefaults.TurnSpacing,
    messageFontSize = SocialLayoutDefaults.MessageFontSize,
    lineHeightMultiplier = SocialLayoutDefaults.LineHeightMultiplier,
    letterSpacing = SocialLayoutDefaults.LetterSpacing,
    paragraphSpacing = SocialLayoutDefaults.ParagraphSpacing,
    waitingAnimation = SocialLayoutDefaults.WaitingAnimation,
    timelineThinkingAnimation = SocialLayoutDefaults.TimelineThinkingAnimation,
)

private val AgentProfileDefaults = ChatLayoutProfileDefaults(
    assistantBubbleEnabled = AgentLayoutDefaults.AssistantBubbleEnabled,
    bubbleCornerRadius = AgentLayoutDefaults.BubbleCornerRadius,
    avatarSize = AgentLayoutDefaults.AvatarSize,
    avatarShape = AgentLayoutDefaults.AvatarShape,
    nameFontSize = AgentLayoutDefaults.NameFontSize,
    nameAvatarSpacing = AgentLayoutDefaults.NameAvatarSpacing,
    horizontalPadding = AgentLayoutDefaults.HorizontalPadding,
    replySpacing = AgentLayoutDefaults.ReplySpacing,
    turnSpacing = AgentLayoutDefaults.TurnSpacing,
    messageFontSize = AgentLayoutDefaults.MessageFontSize,
    lineHeightMultiplier = AgentLayoutDefaults.LineHeightMultiplier,
    letterSpacing = AgentLayoutDefaults.LetterSpacing,
    paragraphSpacing = AgentLayoutDefaults.ParagraphSpacing,
    waitingAnimation = AgentLayoutDefaults.WaitingAnimation,
    timelineThinkingAnimation = AgentLayoutDefaults.TimelineThinkingAnimation,
)

private val RoleplayProfileDefaults = ChatLayoutProfileDefaults(
    assistantBubbleEnabled = RoleplayLayoutDefaults.AssistantBubbleEnabled,
    bubbleCornerRadius = RoleplayLayoutDefaults.BubbleCornerRadius,
    avatarSize = RoleplayLayoutDefaults.AvatarSize,
    avatarShape = RoleplayLayoutDefaults.AvatarShape,
    nameFontSize = RoleplayLayoutDefaults.NameFontSize,
    nameAvatarSpacing = RoleplayLayoutDefaults.NameAvatarSpacing,
    horizontalPadding = RoleplayLayoutDefaults.HorizontalPadding,
    replySpacing = RoleplayLayoutDefaults.ReplySpacing,
    turnSpacing = RoleplayLayoutDefaults.TurnSpacing,
    messageFontSize = RoleplayLayoutDefaults.MessageFontSize,
    lineHeightMultiplier = RoleplayLayoutDefaults.LineHeightMultiplier,
    letterSpacing = RoleplayLayoutDefaults.LetterSpacing,
    paragraphSpacing = RoleplayLayoutDefaults.ParagraphSpacing,
    waitingAnimation = RoleplayLayoutDefaults.WaitingAnimation,
    timelineThinkingAnimation = RoleplayLayoutDefaults.TimelineThinkingAnimation,
)

val ChatLayoutMode.layoutDefaults: ChatLayoutProfileDefaults
    get() = when (this) {
        ChatLayoutMode.Social -> SocialProfileDefaults
        ChatLayoutMode.Agent -> AgentProfileDefaults
        ChatLayoutMode.Roleplay -> RoleplayProfileDefaults
    }

/** Social is always bubbled, Roleplay never is, and only Agent honors the stored toggle. */
internal fun resolveAssistantBubbleEnabled(
    mode: ChatLayoutMode,
    storedValue: Boolean?,
): Boolean = when (mode) {
    ChatLayoutMode.Social -> true
    ChatLayoutMode.Agent -> storedValue ?: AgentLayoutDefaults.AssistantBubbleEnabled
    ChatLayoutMode.Roleplay -> false
}

data class UiPreferences(
    val pinnedChatIds: List<String> = emptyList(),
    val hiddenChatIds: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val lastActiveChatSessionId: String = "",
    val activeChatSessionIds: Map<String, String> = emptyMap(),
    val pinnedCreatorWorkspaceIds: List<String> = emptyList(),
    val creatorWorkspaceExpansionOverrides: Map<String, Boolean> = emptyMap(),
    val lastCreatorWorkspaceId: String = "",
    val historySaveMode: String = "all",
    val appearanceMode: AppearanceMode = AppearanceMode.Default,
    val newCharacterBackground: NewCharacterBackground = NewCharacterBackground.Default,
    val listCharacterArtwork: ListCharacterArtwork = ListCharacterArtwork.Default,
    val defaultChatConfigId: String = "",
    val defaultChatModel: String = "",
    val assistantBubbleEnabled: Boolean = RoleplayLayoutDefaults.AssistantBubbleEnabled,
    val chatLayoutMode: ChatLayoutMode = ChatLayoutMode.Default,
    val chatReasoningDisplayMode: ChatReasoningDisplayMode = ChatReasoningDisplayMode.Default,
    /** Global display projection; raw Room and model histories are deliberately unaffected. */
    val chatToolTimelineStyle: ChatToolTimelineStyle = ChatToolTimelineStyle.Default,
    /** Global across layouts: native generation facts describe the request, not a bubble style. */
    val chatGenerationStatsEnabled: Boolean = true,
    val chatCodeBlockStyle: ChatCodeBlockStyle = ChatCodeBlockStyle.Default,
    val chatCodeBlockWrapEnabled: Boolean = ChatCodeBlockDefaults.WrapEnabled,
    val chatCodeBlockShowAllEnabled: Boolean = ChatCodeBlockDefaults.ShowAllEnabled,
    val chatAvatarShape: ChatAvatarShape = RoleplayLayoutDefaults.AvatarShape,
    val chatRoleplayCardPanel: Boolean = RoleplayLayoutDefaults.CardPanel,
    val chatBubbleCornerRadius: Float = RoleplayLayoutDefaults.BubbleCornerRadius,
    val chatAvatarSize: Float = RoleplayLayoutDefaults.AvatarSize,
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
    val chatTimelineThinkingAnimation: ChatTimelineThinkingAnimation =
        RoleplayLayoutDefaults.TimelineThinkingAnimation,
    val appearanceTheme: AppearanceTheme = AppearanceTheme(),
) {
    // The message renderer still only knows narrow-vs-wide. Roleplay reads as wide until its own
    // rendering path lands, so picking it degrades to the Agent look instead of to nothing.
    val chatBubbleWideLayout: Boolean get() = chatLayoutMode.usesFullWidthBody

    // A portrait avatar is a third taller than a square one, which only the roleplay layout has room
    // for. Resolving it here means no screen has to remember the rule.
    val resolvedChatAvatarShape: ChatAvatarShape
        get() = if (chatAvatarShape.isSupportedBy(chatLayoutMode)) {
            chatAvatarShape
        } else {
            chatLayoutMode.layoutDefaults.avatarShape
        }

    fun activeChatSessionId(characterId: String): String {
        return ActiveChatSessionSelection(
            lastSessionId = lastActiveChatSessionId,
            sessionIdsByContext = activeChatSessionIds,
        ).sessionIdFor(characterId)
    }

}

internal fun List<String>.restoreChatEntry(sessionId: String): List<String> {
    val normalizedId = sessionId.trim()
    if (normalizedId.isBlank()) return this
    return filterNot { hiddenId -> hiddenId == normalizedId }
}
