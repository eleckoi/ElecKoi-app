package com.eleckoi.android.feature.chat.data.presentation

import com.eleckoi.android.feature.chat.data.stream.StreamingMarkupAssembler
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.content.ChatContentBlock
import com.eleckoi.android.feature.chat.roleplay.protocol.RoleplayImagePlacementPart
import com.eleckoi.android.feature.chat.roleplay.protocol.parseRoleplayImagePlacements

internal fun assembleChatContentBlocks(
    message: ChatMessage,
    displayedText: String,
    markupAssembler: StreamingMarkupAssembler,
    appendUnplacedImages: Boolean = true,
): List<ChatContentBlock> {
    if (message.role == MessageRole.User) {
        return listOf(ChatContentBlock.Text(id = "user-text", markdown = displayedText))
    }
    val parsed = markupAssembler.update(displayedText, streaming = message.pending).filterNot {
        it is ChatContentBlock.ToolCall || it is ChatContentBlock.Reasoning
    }
    return placeRoleplayImages(
        blocks = parsed,
        message = message,
        appendUnplacedImages = appendUnplacedImages,
    )
}

private fun placeRoleplayImages(
    blocks: List<ChatContentBlock>,
    message: ChatMessage,
    appendUnplacedImages: Boolean,
): List<ChatContentBlock> {
    val placed = buildList {
        blocks.forEach { block ->
            if (block !is ChatContentBlock.Text) {
                addPlacementBlock(block)
                return@forEach
            }
            parseRoleplayImagePlacements(
                raw = block.markdown,
                streaming = message.pending,
            ).forEachIndexed { index, part ->
                when (part) {
                    is RoleplayImagePlacementPart.Text -> addPlacementBlock(
                        ChatContentBlock.Text(
                            id = "${block.id}:image-text-$index",
                            markdown = part.value,
                        ),
                    )

                    is RoleplayImagePlacementPart.Images -> addPlacementBlock(
                        ChatContentBlock.ImagePlacement(
                            id = "${block.id}:images-${part.frameIndexes.joinToString("-")}",
                            frameIndexes = part.frameIndexes,
                        ),
                    )
                }
            }
        }
    }.toMutableList()
    if (!message.pending && appendUnplacedImages) {
        val referenced = placed
            .filterIsInstance<ChatContentBlock.ImagePlacement>()
            .flatMapTo(mutableSetOf(), ChatContentBlock.ImagePlacement::frameIndexes)
        val unplaced = message.imageAttachments
            .map { it.frameIndex }
            .filterNot(referenced::contains)
        if (unplaced.isNotEmpty()) {
            placed.addPlacementBlock(
                ChatContentBlock.ImagePlacement(
                    id = "unplaced-images-${unplaced.joinToString("-")}",
                    frameIndexes = unplaced,
                ),
            )
        }
    }
    return placed
}

private fun MutableList<ChatContentBlock>.addPlacementBlock(block: ChatContentBlock) {
    val previous = lastOrNull()
    if (previous is ChatContentBlock.ImagePlacement && block is ChatContentBlock.ImagePlacement) {
        this[lastIndex] = previous.copy(
            id = "${previous.id}+${block.id}",
            frameIndexes = previous.frameIndexes + block.frameIndexes,
        )
    } else {
        add(block)
    }
}
