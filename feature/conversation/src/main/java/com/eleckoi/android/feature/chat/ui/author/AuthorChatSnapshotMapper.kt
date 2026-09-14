package com.eleckoi.android.feature.chat.ui.author

import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.modelconfig.model.ModelParameters
import com.eleckoi.android.sdk.author.AuthorChatDraftSnapshot
import com.eleckoi.android.sdk.author.AuthorAgentActivitySnapshot
import com.eleckoi.android.sdk.author.AuthorChatListItemSnapshot
import com.eleckoi.android.sdk.author.AuthorChatSessionSnapshot
import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorModelParameters
import com.eleckoi.android.sdk.author.AuthorOpeningOptionSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningStateSnapshot
import com.eleckoi.android.sdk.author.AuthorToolCallSnapshot
import com.eleckoi.android.feature.chat.ui.message.chatAgentTimelineItems
import com.eleckoi.android.feature.chat.ui.message.hasAgentProcessRecord
import com.eleckoi.android.feature.chat.ui.message.liveChatAgentStatus
import com.eleckoi.android.feature.chat.ui.message.shouldShowInlineAgentProcess

internal fun ChatDraft.toAuthorSnapshot() = AuthorChatDraftSnapshot(
    session = session.toAuthorSnapshot(),
    selectedConfigId = selectedModelConfig.id,
    selectedModel = selectedModel,
    modelParameters = modelParameters.toAuthorSnapshot(),
    openings = AuthorOpeningStateSnapshot(
        items = openingOptions.map { option ->
            AuthorOpeningOptionSnapshot(id = option.id, title = option.title)
        },
        selectedId = selectedOpeningOptionId,
        selectionEnabled = openingSelectionEnabled,
    ),
)

internal fun ChatSession.toAuthorSnapshot() = AuthorChatSessionSnapshot(
    id = id,
    title = title,
    characterId = characterId,
    characterName = characterName,
    characterMode = characterMode,
    messages = messages.map(ChatMessage::toAuthorSnapshot),
    createdAt = createdAt,
    updatedAt = updatedAt,
    variableStateJson = variableStateJson,
)

internal fun ChatListItem.toAuthorSnapshot() = AuthorChatListItemSnapshot(
    id = id,
    title = title,
    characterId = characterId,
    characterName = characterName,
    characterAvatar = characterAvatar,
    summary = summary,
    updatedAt = updatedAt,
    messageCount = messageCount,
)

internal fun ChatMessage.toAuthorSnapshot(): AuthorMessageSnapshot {
    val activity = if (shouldShowInlineAgentProcess(message = this, displayedText = content.trim())) {
        liveChatAgentStatus(
            chatAgentTimelineItems(
                messageId = id,
                reasoningContent = reasoningContent,
                calls = toolCalls,
                running = true,
            ),
        )
    } else {
        null
    }
    return AuthorMessageSnapshot(
        id = id,
        role = role.name.lowercase(),
        content = content,
        reasoningContent = reasoningContent,
        provider = provider,
        model = model,
        createdAt = createdAt,
        pending = pending,
        variableStateJson = variableStateJson,
        toolCalls = toolCalls.map { call ->
            AuthorToolCallSnapshot(
                callId = call.callId,
                name = call.name,
                arguments = call.arguments,
                result = call.result,
                state = call.state.name.lowercase(),
                rollbackOnAbort = call.rollbackOnAbort,
            )
        },
        hasAgentProcess = hasAgentProcessRecord(),
        agentActivity = activity?.let { status ->
            AuthorAgentActivitySnapshot(
                label = status.label,
                running = status.running,
                thinking = status.thinking,
            )
        },
    )
}

internal fun ModelParameters.toAuthorSnapshot() = AuthorModelParameters(
    stream = stream,
    temperature = temperature,
    topP = topP,
)

internal fun AuthorModelParameters.toFeatureModel() = ModelParameters(
    stream = stream,
    temperature = temperature,
    topP = topP,
)
