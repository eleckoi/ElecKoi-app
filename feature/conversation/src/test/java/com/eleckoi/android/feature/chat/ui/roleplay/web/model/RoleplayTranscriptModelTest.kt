package com.eleckoi.android.feature.chat.ui.roleplay.web.model

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.ChatRenderingPreferences
import com.eleckoi.android.feature.preferences.AgentLayoutDefaults
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RoleplayTranscriptModelTest {
    @Test
    fun visualRevisionChangesWhenRenderedPartsChange() {
        val source = ChatMessage(
            id = "message-1",
            role = MessageRole.Assistant,
            content = "stored source",
        )
        val first = transcriptMessage(source, "第一版")
        val second = transcriptMessage(source, "第二版")

        assertNotEquals(first.revision, second.revision)
    }

    @Test
    fun timestampChangeUpdatesTheMessagePatchRevision() {
        val source = ChatMessage(
            id = "message-1",
            role = MessageRole.Assistant,
            content = "正文",
            createdAt = "2026-09-24T19:09:00Z",
        )
        val first = transcriptMessage(source, "正文")
        val second = transcriptMessage(source.copy(createdAt = "2026-09-24T19:10:00Z"), "正文")

        assertNotEquals(first.revision, second.revision)
        assertEquals("2026-09-24T19:10:00Z", second.toJson().getString("createdAt"))
    }

    @Test
    fun visualRevisionChangesWhenStreamingSettles() {
        val pending = transcriptMessage(
            ChatMessage(
                id = "message-1",
                role = MessageRole.Assistant,
                content = "same",
                pending = true,
            ),
            "same",
        )
        val settled = transcriptMessage(pending.source.copy(pending = false), "same")

        assertNotEquals(pending.revision, settled.revision)
    }

    @Test
    fun contentRevisionTracksOnlyRenderedContentParts() {
        val source = ChatMessage(
            id = "message-1",
            role = MessageRole.Assistant,
            content = "stored source",
            pending = true,
        )
        val first = transcriptMessage(source, "same")
        val settled = transcriptMessage(source.copy(pending = false), "same")
        val changed = transcriptMessage(source, "changed")

        assertEquals(first.contentRevision, first.toJson().getString("contentRevision"))
        assertEquals(first.contentRevision, settled.contentRevision)
        assertNotEquals(first.contentRevision, changed.contentRevision)
    }

    @Test
    fun assistantImageMarkerBecomesAnImagePartAtTheMarkerPosition() {
        val image = transcriptImage(id = "image-1", frameIndex = 1)

        val parts = placeRoleplayTranscriptImages(
            role = MessageRole.Assistant,
            content = "图片之前\n\n[[IMAGE:1]]\n\n图片之后",
            streaming = false,
            images = listOf(image),
        )

        assertEquals(
            listOf(
                RoleplayTranscriptContentPart.Text("图片之前\n\n"),
                RoleplayTranscriptContentPart.Images(listOf(image)),
                RoleplayTranscriptContentPart.Text("\n\n图片之后"),
            ),
            parts,
        )
        assertFalse(parts.toString().contains("[[IMAGE:"))
    }

    @Test
    fun userInputImagesAreRenderedAfterTheirText() {
        val image = transcriptImage(id = "input-image-1", frameIndex = 1)

        val parts = placeRoleplayTranscriptImages(
            role = MessageRole.User,
            content = "看看这张图",
            streaming = false,
            images = listOf(image),
        )

        assertEquals(
            listOf(
                RoleplayTranscriptContentPart.Text("看看这张图"),
                RoleplayTranscriptContentPart.Images(listOf(image)),
            ),
            parts,
        )
    }

    @Test
    fun transcriptJsonKeepsTextAndImagesInTheirOriginalOrder() {
        val source = ChatMessage(
            id = "message-1",
            role = MessageRole.Assistant,
            content = "stored source",
        )
        val image = transcriptImage(id = "image-1", frameIndex = 1)
        val message = transcriptMessage(
            source = source,
            renderedText = "copy text",
            contentParts = listOf(
                RoleplayTranscriptContentPart.Text("前"),
                RoleplayTranscriptContentPart.Images(listOf(image)),
                RoleplayTranscriptContentPart.Text("后"),
            ),
        )

        val parts = message.toJson().getJSONArray("parts")

        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("前", parts.getJSONObject(0).getString("markdown"))
        assertEquals("images", parts.getJSONObject(1).getString("type"))
        assertEquals("image-1", parts.getJSONObject(1).getJSONArray("images").getJSONObject(0).getString("id"))
        assertEquals("text", parts.getJSONObject(2).getString("type"))
        assertEquals("后", parts.getJSONObject(2).getString("markdown"))
    }

    @Test
    fun regeneratedReplyImageGetsANewWebViewMediaUrl() {
        val baseUrl = "https://appassets.androidplatform.net/roleplay-media/image-message-1-0"
        val first = ChatImageAttachment(
            id = "reply-image-message-1-1-first",
            generationAttemptId = "generation-first",
            localPath = "/generated/first.png",
        )
        val regenerated = first.copy(
            id = "reply-image-message-1-1-second",
            generationAttemptId = "generation-second",
            localPath = "/generated/second.png",
        )

        assertNotEquals(
            roleplayGeneratedImageMediaUrl(baseUrl, first),
            roleplayGeneratedImageMediaUrl(baseUrl, regenerated),
        )
    }

    @Test
    fun manuallyRegeneratedImageGetsANewWebViewMediaUrlWithTheSameAttachmentId() {
        val baseUrl = "https://appassets.androidplatform.net/roleplay-media/image-message-1-0"
        val first = ChatImageAttachment(
            id = "stable-image-id",
            generationAttemptId = "generation-first",
            localPath = "/generated/first.png",
        )
        val regenerated = first.copy(
            generationAttemptId = "generation-second",
            localPath = "/generated/second.png",
        )

        assertNotEquals(
            roleplayGeneratedImageMediaUrl(baseUrl, first),
            roleplayGeneratedImageMediaUrl(baseUrl, regenerated),
        )
    }

    @Test
    fun unchangedLongHistoryMessagesReuseTheirProjectedWebModelsDuringStreaming() {
        val history = (0 until 1_000).map { index ->
            ChatMessage(
                id = "message-$index",
                role = if (index % 2 == 0) MessageRole.User else MessageRole.Assistant,
                content = "stable-$index",
            )
        }
        val pending = ChatMessage(
            id = "pending",
            role = MessageRole.Assistant,
            content = "first",
            pending = true,
        )
        val cache = RoleplayTranscriptProjectionCache()
        val draft = draft(ImmutableAppendedList(history, pending))

        val first = buildModel(draft, ImmutableAppendedList(history, pending), cache)
        val secondPending = pending.copy(content = "first second")
        val second = buildModel(draft, ImmutableAppendedList(history, secondPending), cache)

        val firstMessages = first.messages as ImmutableAppendedList
        val secondMessages = second.messages as ImmutableAppendedList
        assertSame(firstMessages.prefix, secondMessages.prefix)
        assertSame(first.messages.first(), second.messages.first())
        assertSame(first.messages[500], second.messages[500])
        assertNotSame(first.messages.last(), second.messages.last())
        assertEquals("first second", second.messages.last().copyText)
    }

    @Test
    fun completedCodeFenceStaysInThePreviousTurnAfterAnotherReplyIsAppended() {
        val codeReply = ChatMessage(
            id = "code-reply",
            role = MessageRole.Assistant,
            content = "```text\n" + (1..120).joinToString("\n") { "rule_$it = keep" } + "\n```",
        )
        val initial = listOf(
            ChatMessage(id = "prompt", role = MessageRole.User, content = "请列出规则"),
            codeReply,
        )
        val extended = initial + ChatMessage(
            id = "follow-up",
            role = MessageRole.User,
            content = "继续",
        )
        val cache = RoleplayTranscriptProjectionCache()

        val before = buildModel(draft(initial), initial, cache)
        val after = buildModel(draft(extended), extended, cache)
        val beforeCode = before.messages[1].contentParts.single() as RoleplayTranscriptContentPart.Text
        val afterCode = after.messages[1].contentParts.single() as RoleplayTranscriptContentPart.Text

        assertEquals(codeReply.content, beforeCode.markdown)
        assertEquals(beforeCode.markdown, afterCode.markdown)
        assertEquals(codeReply.content, after.messages[1].toJson().getJSONArray("parts")
            .getJSONObject(0).getString("markdown"))
    }

    @Test
    fun floorsStayAbsoluteAcrossPagingStreamingAndRepeatedProjection() {
        val history = (0 until 500).map { index ->
            ChatMessage(
                id = "message-$index",
                role = if (index % 2 == 0) MessageRole.User else MessageRole.Assistant,
                content = "正文 $index",
            )
        }
        val cache = RoleplayTranscriptProjectionCache()
        val recent = history.takeLast(20)
        val source = draft(recent).let { draft ->
            draft.copy(session = draft.session.copy(historyMessageCount = history.size))
        }

        val first = buildModel(source, recent, cache)
        val streaming = ImmutableAppendedList(
            recent,
            ChatMessage(id = "pending", role = MessageRole.Assistant, content = "新回复", pending = true),
        )
        val next = buildModel(source, streaming, cache)
        val older = history.takeLast(30) + streaming.last()
        val prepended = buildModel(source, older, cache)
        val repeated = buildModel(source, older, cache)

        assertEquals(480, first.floorStart)
        assertEquals(480, next.floorStart)
        assertEquals(470, prepended.floorStart)
        assertEquals(470, repeated.floorStart)
        assertEquals(31, prepended.messages.size)
    }

    @Test
    fun agentProjectionUsesDeepSeekReadingColorsAndDefaultTypography() {
        val messages = listOf(
            ChatMessage(id = "message-1", role = MessageRole.User, content = "你好"),
        )
        val source = draft(messages)
        val light = buildModel(
            draft = source,
            messages = messages,
            cache = RoleplayTranscriptProjectionCache(),
            layoutMode = ChatLayoutMode.Agent,
            appearance = AppearanceTheme(),
            messageFontSize = AgentLayoutDefaults.MessageFontSize,
            lineHeightMultiplier = AgentLayoutDefaults.LineHeightMultiplier,
        )
        val dark = buildModel(
            draft = source,
            messages = messages,
            cache = RoleplayTranscriptProjectionCache(),
            layoutMode = ChatLayoutMode.Agent,
            appearance = AppearanceTheme(isDark = true),
            messageFontSize = AgentLayoutDefaults.MessageFontSize,
            lineHeightMultiplier = AgentLayoutDefaults.LineHeightMultiplier,
        )

        assertEquals("rgba(237,243,254,1.0)", light.style.userBubble)
        assertEquals(light.style.userBubble, light.style.assistantBubble)
        assertEquals("rgba(15,15,15,1.0)", light.style.bodyText)
        assertEquals(16f, light.style.fontSizePx)
        assertEquals(25f, light.style.lineHeightPx, 0.001f)
        assertEquals("rgba(51,51,51,1.0)", dark.style.userBubble)
        assertEquals(dark.style.userBubble, dark.style.assistantBubble)
        assertEquals("rgba(248,248,248,1.0)", dark.style.bodyText)
    }

    private fun draft(messages: List<ChatMessage>) = ChatDraft(
        session = ChatSession(
            id = "session",
            title = "chat",
            characterId = "character",
            characterName = "AI",
            characterAvatar = "",
            characterPersona = CharacterCard(
                userName = "用户",
                assistantName = "角色",
            ),
            messages = messages,
            updatedAt = "now",
        ),
        selectedModelConfig = ModelConfig(),
        selectedModel = "model",
    )

    private fun buildModel(
        draft: ChatDraft,
        messages: List<ChatMessage>,
        cache: RoleplayTranscriptProjectionCache,
        layoutMode: ChatLayoutMode = ChatLayoutMode.Roleplay,
        appearance: AppearanceTheme = AppearanceTheme(),
        messageFontSize: Float = 14f,
        lineHeightMultiplier: Float = 1f,
    ) = buildRoleplayTranscriptModel(
        draft = draft,
        messages = messages,
        layoutMode = layoutMode,
        appearance = appearance,
        avatarShape = ChatAvatarShape.Portrait,
        avatarSize = 55f,
        nameFontSize = 15f,
        avatarGap = 10f,
        horizontalPadding = 10f,
        replySpacing = 4f,
        turnSpacing = 5f,
        messageFontSize = messageFontSize,
        lineHeightMultiplier = lineHeightMultiplier,
        letterSpacing = 0f,
        paragraphSpacing = 10f,
        cardPanel = false,
        renderingPreferences = ChatRenderingPreferences(),
        frontendRendererEnabled = true,
        historyHasMore = false,
        historyLoading = false,
        projectionCache = cache,
    )

    private fun transcriptMessage(
        source: ChatMessage,
        renderedText: String,
        contentParts: List<RoleplayTranscriptContentPart> = listOf(
            RoleplayTranscriptContentPart.Text(renderedText),
        ),
    ) = RoleplayTranscriptMessage(
        source = source,
        name = "角色",
        avatarUrl = null,
        copyText = renderedText,
        contentParts = contentParts,
        reasoning = "",
        openingOptionIds = emptyList(),
        selectedOpeningIndex = -1,
        hasAgentProcess = false,
        regenerateEnabled = true,
    )

    private fun transcriptImage(
        id: String,
        frameIndex: Int,
    ) = RoleplayTranscriptImage(
        id = id,
        url = "https://appassets.androidplatform.net/roleplay-media/$id",
        status = "ready",
        error = "",
        aspectRatio = 0.75f,
        frameIndex = frameIndex,
        frameCount = 1,
    )
}
