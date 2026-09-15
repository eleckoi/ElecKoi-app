package com.eleckoi.android.feature.chat.ui.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.data.CharacterCardMacroValues
import com.eleckoi.android.feature.chat.data.resolveCharacterCardMacros
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatOpeningOption
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme

internal data class ChatMessageRowPresentation(
    val isUser: Boolean,
    val isOpening: Boolean,
    val roleplay: Boolean,
    val social: Boolean,
    val wideBubbleLayout: Boolean,
    val selectedOpeningIndex: Int,
    val openingPagerVisible: Boolean,
    val name: String,
    val avatarPath: String,
    val readingAppearance: AppearanceTheme,
    val displayMessage: ChatMessage,
)

@Composable
internal fun rememberChatMessageRowPresentation(
    message: ChatMessage,
    character: CharacterCard,
    appearance: AppearanceTheme,
    layoutMode: ChatLayoutMode,
    avatarShape: ChatAvatarShape,
    openingOptions: List<ChatOpeningOption>,
    selectedOpeningOptionId: String,
    openingSelectionEnabled: Boolean,
): ChatMessageRowPresentation {
    val isUser = message.role == MessageRole.User
    val isOpening = message.id == OpeningMessageId
    val roleplay = layoutMode == ChatLayoutMode.Roleplay
    val social = layoutMode == ChatLayoutMode.Social
    val selectedOpeningIndex = if (isOpening && openingSelectionEnabled) {
        openingOptions.indexOfFirst { it.id == selectedOpeningOptionId }
    } else {
        -1
    }
    val avatars = if (isUser) character.userAvatars else character.assistantAvatars
    val avatarPath = when (avatarShape) {
        ChatAvatarShape.Portrait -> avatars.portrait
        ChatAvatarShape.RoundedSquare -> avatars.square
        ChatAvatarShape.Circle -> avatars.circle
    }
    val readingAppearance = remember(appearance, roleplay) {
        if (roleplay) appearance.asRoleplayReadingTheme() else appearance
    }
    val displayMessage = remember(
        message,
        character.userName,
        character.characterName,
        character.assistantName,
    ) {
        val values = CharacterCardMacroValues(
            userName = character.userName.ifBlank { "用户" },
            characterName = character.characterName.ifBlank {
                character.assistantName.ifBlank { "AI" }
            },
        )
        message.copy(
            content = message.content.resolveCharacterCardMacros(values),
            reasoningContent = message.reasoningContent.resolveCharacterCardMacros(values),
        )
    }
    return ChatMessageRowPresentation(
        isUser = isUser,
        isOpening = isOpening,
        roleplay = roleplay,
        social = social,
        wideBubbleLayout = layoutMode.usesFullWidthBody,
        selectedOpeningIndex = selectedOpeningIndex,
        openingPagerVisible = selectedOpeningIndex >= 0 && openingOptions.size > 1,
        name = if (isUser) character.userName else character.assistantName.ifBlank { "AI" },
        avatarPath = avatarPath,
        readingAppearance = readingAppearance,
        displayMessage = displayMessage,
    )
}
