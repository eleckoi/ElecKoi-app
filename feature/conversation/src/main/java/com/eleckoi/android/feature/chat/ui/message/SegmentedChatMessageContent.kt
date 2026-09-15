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
import com.eleckoi.android.feature.chat.data.rich.RichMessagePart
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.roleplay.protocol.RoleplayImagePlacementPart
import com.eleckoi.android.feature.chat.roleplay.protocol.parseRoleplayImagePlacements
import com.eleckoi.android.feature.chat.ui.blocks.image.GeneratedImageGallery
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.sdk.author.AuthorChatGateway

@Composable
internal fun SegmentedChatMessageContent(
    message: ChatMessage,
    parts: List<RichMessagePart>,
    appearance: AppearanceTheme,
    modifier: Modifier,
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
    val partReadinessIds = parts.map { part ->
        when (part) {
            is RichMessagePart.Native -> part.id
            is RichMessagePart.Rich -> "${part.id}:${part.document.contentKey}"
        }
    }
    val referencedFrames = remember(parts, message.pending) {
        parts
            .filterIsInstance<RichMessagePart.Native>()
            .flatMap { part ->
                parseRoleplayImagePlacements(part.source, streaming = message.pending)
                    .filterIsInstance<RoleplayImagePlacementPart.Images>()
                    .flatMap(RoleplayImagePlacementPart.Images::frameIndexes)
            }
            .toSet()
    }
    val unplacedImageAttachments = if (message.pending) {
        emptyList()
    } else {
        message.imageAttachments.filterNot { it.frameIndex in referencedFrames }
    }
    val unplacedImageIds = unplacedImageAttachments.map { it.id }
    var readyPartIds by remember(message.id, visualGeneration) { mutableStateOf(emptySet<String>()) }
    var visualPartIds by remember(message.id, visualGeneration) { mutableStateOf(emptySet<String>()) }
    var readyImageIds by remember(message.id, visualGeneration) { mutableStateOf(emptySet<String>()) }
    var readyReported by remember(message.id, visualGeneration) { mutableStateOf(false) }
    var visualReported by remember(message.id, visualGeneration) { mutableStateOf(false) }
    LaunchedEffect(partReadinessIds) {
        val current = partReadinessIds.toSet()
        readyPartIds = readyPartIds.intersect(current)
        visualPartIds = visualPartIds.intersect(current)
    }
    LaunchedEffect(unplacedImageIds) {
        readyImageIds = readyImageIds.intersect(unplacedImageIds.toSet())
    }
    val allReady = partReadinessIds.all(readyPartIds::contains) &&
        unplacedImageIds.all(readyImageIds::contains)
    LaunchedEffect(allReady) {
        if (allReady && !readyReported) {
            readyReported = true
            onContentReady()
        }
    }
    val allVisualComplete = partReadinessIds.all(visualPartIds::contains) &&
        message.imageAttachments.none {
            it.status == com.eleckoi.android.feature.chat.model.ChatImageStatus.Generating
        }
    LaunchedEffect(allVisualComplete, message.pending) {
        if (allVisualComplete && !message.pending && !visualReported) {
            visualReported = true
            onVisualComplete()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        parts.forEach { part ->
            val readinessId = when (part) {
                is RichMessagePart.Native -> part.id
                is RichMessagePart.Rich -> "${part.id}:${part.document.contentKey}"
            }
            key(part.id, visualGeneration) {
                when (part) {
                    is RichMessagePart.Native -> NativeChatMessageContent(
                        message = message,
                        state = ChatMessageContentState(displayedText = part.source),
                        appearance = appearance,
                        modifier = Modifier,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        letterSpacing = letterSpacing,
                        paragraphSpacing = paragraphSpacing,
                        messageContainerVisible = messageContainerVisible,
                        visualGeneration = visualGeneration,
                        onContentReady = { readyPartIds = readyPartIds + readinessId },
                        onVisualComplete = { visualPartIds = visualPartIds + readinessId },
                        cacheOwnerKey = "$cacheOwnerKey:${part.id}",
                        onRegenerateImage = onRegenerateImage,
                        appendUnplacedImages = false,
                    )

                    is RichMessagePart.Rich -> RichChatMessageContent(
                        message = message,
                        document = part.document,
                        appearance = appearance,
                        modifier = Modifier,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        letterSpacing = letterSpacing,
                        authorGateway = authorGateway,
                        visualGeneration = visualGeneration,
                        onContentReady = { readyPartIds = readyPartIds + readinessId },
                        onVisualComplete = { visualPartIds = visualPartIds + readinessId },
                        onRegenerateImage = onRegenerateImage,
                        includeImageAttachments = false,
                    )
                }
            }
        }
        if (unplacedImageAttachments.isNotEmpty()) {
            GeneratedImageGallery(
                attachments = unplacedImageAttachments,
                appearance = appearance,
                onRegenerate = if (message.pending) null else onRegenerateImage,
                onContentReady = { attachmentId ->
                    readyImageIds = readyImageIds + attachmentId
                },
            )
        }
    }
}

