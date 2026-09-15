package com.eleckoi.android.feature.chat.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.chat.data.rich.RichMessagePart
import com.eleckoi.android.feature.chat.data.rich.detectRichMessagePresentation
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.blocks.image.UserInputImageGallery
import com.eleckoi.android.sdk.author.AuthorChatGateway

data class ChatMessageContentState(
    val displayedText: String,
)

@Composable
fun rememberChatMessageContentState(message: ChatMessage): ChatMessageContentState {
    // Provider chunks are rendered directly. Markdown owns its own append-only session, so an
    // additional character-by-character copy would only multiply parsing and recomposition work.
    val displayedText = message.content
    return ChatMessageContentState(
        displayedText = displayedText,
    )
}

@Composable
fun ChatMessageContent(
    message: ChatMessage,
    state: ChatMessageContentState,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    authorGateway: AuthorChatGateway,
    paragraphSpacing: Float,
    messageContainerVisible: Boolean,
    visualGeneration: Int,
    onContentReady: () -> Unit,
    onVisualComplete: () -> Unit,
    cacheOwnerKey: String,
    onRegenerateImage: (String) -> Unit,
) {
    val isUser = message.role == MessageRole.User
    if (isUser && message.inputImageAttachments.isNotEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserInputImageGallery(
                images = message.inputImageAttachments,
                appearance = appearance,
            )
            ChatMessageContent(
                message = message.copy(inputImageAttachments = emptyList()),
                state = state,
                appearance = appearance,
                modifier = Modifier,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                authorGateway = authorGateway,
                paragraphSpacing = paragraphSpacing,
                messageContainerVisible = messageContainerVisible,
                visualGeneration = visualGeneration,
                onContentReady = onContentReady,
                onVisualComplete = onVisualComplete,
                cacheOwnerKey = cacheOwnerKey,
                onRegenerateImage = onRegenerateImage,
            )
        }
        return
    }
    val richPresentation = remember(state.displayedText, message.pending) {
        detectRichMessagePresentation(
            source = state.displayedText,
            streaming = message.pending,
        )
    }
    val parts = richPresentation?.parts ?: listOf(
        RichMessagePart.Native(
            id = "part-0-native",
            source = state.displayedText,
        ),
    )
    val pureRichDocument = (parts.singleOrNull() as? RichMessagePart.Rich)?.document
    var lastNativeText by remember(message.id, visualGeneration) { mutableStateOf("") }
    val nativeSnapshot = parts
        .filterIsInstance<RichMessagePart.Native>()
        .joinToString(separator = "\n\n", transform = RichMessagePart.Native::source)
    if (pureRichDocument == null && nativeSnapshot.isNotBlank()) {
        SideEffect {
            lastNativeText = nativeSnapshot
        }
    }
    val liveAssistantHandoff = !isUser && visualGeneration > 0
    if (pureRichDocument == null) {
        SegmentedChatMessageContent(
            message = message,
            parts = parts,
            appearance = appearance,
            modifier = modifier,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            authorGateway = authorGateway,
            paragraphSpacing = paragraphSpacing,
            messageContainerVisible = messageContainerVisible,
            visualGeneration = visualGeneration,
            onContentReady = onContentReady,
            onVisualComplete = onVisualComplete,
            cacheOwnerKey = cacheOwnerKey,
            onRegenerateImage = onRegenerateImage,
        )
        return
    }
    if (liveAssistantHandoff) {
        var richReady by remember(
            message.id,
            pureRichDocument.contentKey,
        ) {
            mutableStateOf(false)
        }
        val keepNativeLayer = shouldKeepNativeLayerDuringRichHandoff(
            richDocumentAvailable = true,
            richReady = richReady,
        )
        val nativeState = ChatMessageContentState(displayedText = lastNativeText)
        Box(modifier = modifier) {
            // This call stays at the same Compose position before and during the hand-off. The
            // already-rendered Markdown plan therefore remains on screen while WebView prepares.
            if (keepNativeLayer) {
                NativeChatMessageContent(
                    message = message,
                    state = nativeState,
                    appearance = appearance,
                    modifier = Modifier,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    letterSpacing = letterSpacing,
                    paragraphSpacing = paragraphSpacing,
                    messageContainerVisible = messageContainerVisible,
                    visualGeneration = visualGeneration,
                    onContentReady = onContentReady,
                    onVisualComplete = onVisualComplete,
                    cacheOwnerKey = cacheOwnerKey,
                    onRegenerateImage = onRegenerateImage,
                )
            }
            RichChatMessageContent(
                message = message,
                document = pureRichDocument,
                appearance = appearance,
                modifier = Modifier.graphicsLayer {
                    alpha = if (keepNativeLayer) 0f else 1f
                },
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                authorGateway = authorGateway,
                visualGeneration = visualGeneration,
                onContentReady = {
                    richReady = true
                    onContentReady()
                },
                onVisualComplete = onVisualComplete,
                onRegenerateImage = onRegenerateImage,
                includeImageAttachments = true,
            )
        }
        return
    }

    RichChatMessageContent(
        message = message,
        document = pureRichDocument,
        appearance = appearance,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        authorGateway = authorGateway,
        visualGeneration = visualGeneration,
        onContentReady = onContentReady,
        onVisualComplete = onVisualComplete,
        onRegenerateImage = onRegenerateImage,
        includeImageAttachments = true,
    )
}

