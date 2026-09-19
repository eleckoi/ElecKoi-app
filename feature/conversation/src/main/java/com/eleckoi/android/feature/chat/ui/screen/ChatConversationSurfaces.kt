package com.eleckoi.android.feature.chat.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.ui.ChatIntent
import com.eleckoi.android.feature.chat.ui.ChatPresentationReadinessState
import com.eleckoi.android.feature.chat.ui.blocks.image.rememberGeneratedImageDownloader
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatCallbacks
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatController
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatSurface
import com.eleckoi.android.sdk.author.AuthorChatGateway

/** Keeps the WebView-only interaction protocol out of the route-level screen coordinator. */
@Composable
internal fun ChatRoleplayConversationSurface(
    context: Context,
    draft: ChatDraft,
    model: RoleplayTranscriptModel,
    visibleMessages: List<ChatMessage>,
    rendererRevision: Int,
    updatesPaused: Boolean,
    controller: RoleplayWebChatController,
    presentationReadiness: ChatPresentationReadinessState,
    presentationAlpha: Float,
    onIntent: (ChatIntent) -> Unit,
    onMessageRendered: (String) -> Unit,
    onScrollStateChanged: (browsingHistory: Boolean, canScrollForward: Boolean) -> Unit,
    onRequestOpeningJump: () -> Unit,
    onSelectDeleteFrom: (String) -> Unit,
    onSelectText: (String) -> Unit,
    onRegenerate: (ChatMessage) -> Unit,
    onOpenProcess: (String) -> Unit,
    onOpenUserAvatars: () -> Unit,
    onOpenCharacterSettings: (String) -> Unit,
    onRendererUnavailable: () -> Unit,
    messageGateway: AuthorChatGateway,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = androidx.compose.runtime.remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val downloadGeneratedImage = rememberGeneratedImageDownloader()

    RoleplayWebChatSurface(
        model = model,
        rendererRevision = rendererRevision,
        updatesPaused = updatesPaused,
        controller = controller,
        callbacks = RoleplayWebChatCallbacks(
            onReady = {
                presentationReadiness.reveal()
                visibleMessages.lastOrNull()?.id?.let(onMessageRendered)
            },
            onMessageRendered = onMessageRendered,
            onScrollStateChanged = onScrollStateChanged,
            onLoadOlder = { onIntent(ChatIntent.LoadOlderMessages) },
            onSelectOpeningOption = { onIntent(ChatIntent.SelectOpeningOption(it)) },
            onRequestOpeningJump = onRequestOpeningJump,
            onSelectDeleteFrom = onSelectDeleteFrom,
            onMessageAction = { action, message ->
                val displayText = model.messages
                    .firstOrNull { it.source.id == message.id }
                    ?.copyText
                    .orEmpty()
                when (action) {
                    "copy" -> {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText("ElecKoi message", displayText),
                        )
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    "select" -> onSelectText(displayText)
                    "edit" -> onIntent(ChatIntent.OpenEditMessage(message))
                    "regenerate" -> if (message.id != OpeningMessageId) onRegenerate(message)
                    "history" -> onOpenProcess(message.id)
                }
            },
            onImageAction = { action, message, attachment ->
                when (action) {
                    "download" -> downloadGeneratedImage(attachment)
                    "regenerate" -> if (!message.pending && message.id != OpeningMessageId) {
                        onIntent(
                            ChatIntent.RegenerateImage(
                                messageId = message.id,
                                attachmentId = attachment.id,
                            ),
                        )
                    }
                }
            },
            onUserAvatarClick = onOpenUserAvatars,
            onAssistantAvatarClick = {
                draft.session.characterId
                    .takeIf(String::isNotBlank)
                    ?.let(onOpenCharacterSettings)
            },
            onRendererUnavailable = onRendererUnavailable,
        ),
        messageGateway = messageGateway,
        modifier = modifier.graphicsLayer { alpha = presentationAlpha },
    )
}
