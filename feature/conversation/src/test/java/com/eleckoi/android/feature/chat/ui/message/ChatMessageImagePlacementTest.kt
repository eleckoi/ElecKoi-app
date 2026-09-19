package com.eleckoi.android.feature.chat.ui.message

import com.eleckoi.android.feature.chat.data.presentation.assembleChatContentBlocks
import com.eleckoi.android.feature.chat.data.stream.StreamingMarkupAssembler
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.content.ChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageImagePlacementTest {
    @Test
    fun `pending images stay hidden until their markers arrive`() {
        val message = assistant(
            content = "正文正在流式输出",
            pending = true,
            imageCount = 3,
        )

        val blocks = assembleChatContentBlocks(
            message = message,
            displayedText = message.content,
            markupAssembler = StreamingMarkupAssembler(),
        )

        assertTrue(blocks.none { it is ChatContentBlock.ImagePlacement })
    }

    @Test
    fun `first streaming text block keeps its identity when an image marker arrives`() {
        val assembler = StreamingMarkupAssembler()
        val before = assistant(
            content = "第一段正文。",
            pending = true,
            imageCount = 1,
        )
        val beforeText = assembleChatContentBlocks(
            message = before,
            displayedText = before.content,
            markupAssembler = assembler,
        ).filterIsInstance<ChatContentBlock.Text>().single()

        val after = before.copy(content = "第一段正文。\n\n[[IMAGE:1]]\n\n第二段正文。")
        val afterBlocks = assembleChatContentBlocks(
            message = after,
            displayedText = after.content,
            markupAssembler = assembler,
        )
        val afterText = afterBlocks.filterIsInstance<ChatContentBlock.Text>()

        assertEquals(beforeText.id, afterText.first().id)
        assertEquals("第一段正文。", beforeText.markdown)
        assertEquals("第一段正文。\n\n", afterText.first().markdown)
        assertEquals(
            listOf(1),
            afterBlocks.filterIsInstance<ChatContentBlock.ImagePlacement>()
                .single()
                .frameIndexes,
        )
    }

    @Test
    fun `adjacent arrived markers become one gallery block`() {
        val message = assistant(
            content = "正文。\n\n[[IMAGE:1]]\n[[IMAGE:2]]\n[[IMAGE:3]]",
            pending = true,
            imageCount = 3,
        )

        val galleries = assembleChatContentBlocks(
            message = message,
            displayedText = message.content,
            markupAssembler = StreamingMarkupAssembler(),
        ).filterIsInstance<ChatContentBlock.ImagePlacement>()

        assertEquals(1, galleries.size)
        assertEquals(listOf(1, 2, 3), galleries.single().frameIndexes)
    }

    @Test
    fun `completed reply appends only images whose markers were omitted`() {
        val message = assistant(
            content = "正文。\n\n[[IMAGE:1]]\n\n结尾。",
            pending = false,
            imageCount = 3,
        )

        val galleries = assembleChatContentBlocks(
            message = message,
            displayedText = message.content,
            markupAssembler = StreamingMarkupAssembler(),
        ).filterIsInstance<ChatContentBlock.ImagePlacement>()

        assertEquals(listOf(listOf(1), listOf(2, 3)), galleries.map { it.frameIndexes })
    }

    @Test
    fun `long repeated streaming updates keep the first block stable without duplicating images`() {
        val assembler = StreamingMarkupAssembler()
        var content = "序章\n\n" + "长文本".repeat(4_000)
        var message = assistant(content = content, pending = true, imageCount = 4)
        val firstTextId = assembleChatContentBlocks(
            message = message,
            displayedText = content,
            markupAssembler = assembler,
        ).filterIsInstance<ChatContentBlock.Text>().first().id

        repeat(120) { index ->
            content += when (index) {
                30 -> "\n\n[[IMAGE:1]]\n\n"
                60 -> "\n\n[[IMAGE:2]]\n\n"
                90 -> "\n\n[[IMAGE:3]]\n\n"
                else -> "增量$index"
            }
            message = message.copy(content = content)
            val blocks = assembleChatContentBlocks(
                message = message,
                displayedText = content,
                markupAssembler = assembler,
            )
            assertEquals(firstTextId, blocks.filterIsInstance<ChatContentBlock.Text>().first().id)
        }

        val completed = assembleChatContentBlocks(
            message = message.copy(pending = false),
            displayedText = content,
            markupAssembler = assembler,
        )
        val placedFrames = completed.filterIsInstance<ChatContentBlock.ImagePlacement>()
            .flatMap(ChatContentBlock.ImagePlacement::frameIndexes)

        assertEquals(listOf(1, 2, 3, 4), placedFrames)
    }

    private fun assistant(
        content: String,
        pending: Boolean,
        imageCount: Int,
    ): ChatMessage = ChatMessage(
        id = "assistant-1",
        role = MessageRole.Assistant,
        content = content,
        pending = pending,
        imageAttachments = (1..imageCount).map { frameIndex ->
            ChatImageAttachment(
                id = "image-$frameIndex",
                frameIndex = frameIndex,
                frameCount = imageCount,
            )
        },
    )
}
