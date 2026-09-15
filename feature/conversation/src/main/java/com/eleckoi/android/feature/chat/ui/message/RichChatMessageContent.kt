package com.eleckoi.android.feature.chat.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.chat.data.rich.RichMessageDocument
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.blocks.image.GeneratedImageGallery
import com.eleckoi.android.feature.chat.ui.blocks.rich.RichMessageBlock
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.sdk.author.AuthorChatGateway

@Composable
internal fun RichChatMessageContent(
    message: ChatMessage,
    document: RichMessageDocument,
    appearance: AppearanceTheme,
    modifier: Modifier,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    authorGateway: AuthorChatGateway,
    visualGeneration: Int,
    onContentReady: () -> Unit,
    onVisualComplete: () -> Unit,
    onRegenerateImage: (String) -> Unit,
    includeImageAttachments: Boolean,
) {
    val renderedImageAttachments = if (includeImageAttachments) message.imageAttachments else emptyList()
    val imageIds = renderedImageAttachments.map { it.id }
    var richReady by remember(message.id, visualGeneration, document.contentKey) {
        mutableStateOf(false)
    }
    var readyImageIds by remember(message.id, visualGeneration, document.contentKey) {
        mutableStateOf(emptySet<String>())
    }
    var readyReported by remember(message.id, visualGeneration, document.contentKey) {
        mutableStateOf(false)
    }
    var visualReported by remember(message.id, visualGeneration, document.contentKey) {
        mutableStateOf(false)
    }
    LaunchedEffect(imageIds) {
        readyImageIds = readyImageIds.intersect(imageIds.toSet())
    }
    val allReady = richReady && imageIds.all(readyImageIds::contains)
    LaunchedEffect(allReady) {
        if (allReady && !readyReported) {
            readyReported = true
            onContentReady()
        }
    }
    LaunchedEffect(allReady, message.pending, renderedImageAttachments) {
        if (
            allReady &&
            !message.pending &&
            !visualReported &&
            renderedImageAttachments.none {
                it.status == com.eleckoi.android.feature.chat.model.ChatImageStatus.Generating
            }
        ) {
            visualReported = true
            onVisualComplete()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        key(document.contentKey) {
            RichMessageBlock(
                message = message,
                document = document,
                isUser = message.role == MessageRole.User,
                appearance = appearance,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                authorGateway = authorGateway,
                onContentReady = { richReady = true },
            )
        }
        if (renderedImageAttachments.isNotEmpty()) {
            GeneratedImageGallery(
                attachments = renderedImageAttachments,
                appearance = appearance,
                onRegenerate = onRegenerateImage,
                onContentReady = { attachmentId ->
                    readyImageIds = readyImageIds + attachmentId
                },
            )
        }
    }
}

