package com.eleckoi.android.feature.chat.ui.roleplay.web.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.eleckoi.android.feature.chat.data.CharacterCardMacroValues
import com.eleckoi.android.feature.chat.data.resolveCharacterCardMacros
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.roleplay.protocol.stripRoleplayImageMarkers
import com.eleckoi.android.feature.chat.ui.ChatRenderingPreferences
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBubblePalette
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyFontSizeSp
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyLineHeightSp
import com.eleckoi.android.feature.chat.ui.message.chatAgentTimelineItems
import com.eleckoi.android.feature.chat.ui.message.hasAgentProcessRecord
import com.eleckoi.android.feature.chat.ui.message.liveChatAgentStatus
import com.eleckoi.android.feature.chat.ui.message.shouldShowInlineAgentProcess
import com.eleckoi.android.feature.chat.ui.roleplay.web.display.withoutRoleplayDisplayHtmlComments
import com.eleckoi.android.feature.chat.ui.roleplay.web.display.withoutRoleplayRichReplacementFences
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.markdownReadingColors
import com.eleckoi.android.foundation.design.selectionPalette
import java.io.File
import java.net.URLEncoder

/** Reuses the expensive macro/rich-content projection for every unchanged history message. */
internal class RoleplayTranscriptProjectionCache {
    private data class Entry(
        val source: ChatMessage,
        val context: RoleplayTranscriptMessageContext,
        val projection: RoleplayTranscriptMessageProjection,
    )

    private val entries = linkedMapOf<String, Entry>()
    private val stableLists = ArrayDeque<RoleplayTranscriptListCacheEntry>()
    private var floorAnchorMessages: List<ChatMessage> = emptyList()
    private var floorAnchorStart = 0

    /** Keep loaded floors fixed while an unsaved streaming tail grows or older pages arrive. */
    fun floorStart(messages: List<ChatMessage>, persistedCount: Int): Int {
        val first = messages.firstOrNull() ?: run {
            floorAnchorMessages = emptyList()
            floorAnchorStart = 0
            return 0
        }
        val previous = floorAnchorMessages
        val start = when {
            first.id == OpeningMessageId -> 0
            previous.firstOrNull()?.id == first.id -> floorAnchorStart
            else -> {
                val oldIndex = previous.indexOfFirst { it.id == first.id }
                val newIndex = messages.indexOfFirst { it.id == previous.firstOrNull()?.id }
                when {
                    oldIndex >= 0 -> floorAnchorStart + oldIndex
                    newIndex >= 0 -> (floorAnchorStart - newIndex).coerceAtLeast(0)
                    else -> (persistedCount - messages.size).coerceAtLeast(0)
                }
            }
        }
        floorAnchorMessages = messages
        floorAnchorStart = start
        return start
    }

    fun get(
        source: ChatMessage,
        context: RoleplayTranscriptMessageContext,
    ): RoleplayTranscriptMessageProjection? {
        val entry = entries[source.id] ?: return null
        if (entry.context != context) return null
        return entry.projection.takeIf { entry.source === source || entry.source == source }
    }

    fun put(
        source: ChatMessage,
        context: RoleplayTranscriptMessageContext,
        projection: RoleplayTranscriptMessageProjection,
    ) {
        entries[source.id] = Entry(source, context, projection)
    }

    fun retain(messageIds: Set<String>) {
        entries.keys.retainAll(messageIds)
    }

    fun projectMessages(
        source: List<ChatMessage>,
        context: RoleplayTranscriptMessageContext,
        projectMessage: (ChatMessage) -> RoleplayTranscriptMessageProjection,
    ): RoleplayTranscriptListProjection {
        @Suppress("UNCHECKED_CAST")
        val appended = source as? ImmutableAppendedList<ChatMessage>
        if (appended == null) return projectStableList(source, context, projectMessage)

        val prefix = projectStableList(appended.prefix, context, projectMessage)
        val tail = projectMessage(appended.tail)
        val combinedMedia = if (tail.media.isEmpty()) {
            prefix.media
        } else {
            LinkedHashMap(prefix.media).apply { putAll(tail.media) }
        }
        return RoleplayTranscriptListProjection(
            messages = ImmutableAppendedList(prefix.messages, tail.message),
            media = combinedMedia,
        )
    }

    private fun projectStableList(
        source: List<ChatMessage>,
        context: RoleplayTranscriptMessageContext,
        projectMessage: (ChatMessage) -> RoleplayTranscriptMessageProjection,
    ): RoleplayTranscriptListProjection {
        val iterator = stableLists.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.source === source && entry.context == context) {
                iterator.remove()
                stableLists.addLast(entry)
                return entry.projection
            }
        }
        val projection = source.map(projectMessage).toRoleplayTranscriptListProjection()
        stableLists.addLast(RoleplayTranscriptListCacheEntry(source, context, projection))
        while (stableLists.size > MaxStableLists) stableLists.removeFirst()
        return projection
    }

    private companion object {
        const val MaxStableLists = 4
    }
}

private data class RoleplayTranscriptListCacheEntry(
    val source: List<ChatMessage>,
    val context: RoleplayTranscriptMessageContext,
    val projection: RoleplayTranscriptListProjection,
)

internal data class RoleplayTranscriptMessageContext(
    val macroValues: CharacterCardMacroValues,
    val userName: String,
    val assistantName: String,
    val userAvatarUrl: String?,
    val assistantAvatarUrl: String?,
    val avatarShape: ChatAvatarShape,
    val openingIds: List<String>,
    val selectedOpeningIndex: Int,
    val openingSelectionEnabled: Boolean,
    val mascotStyle: String,
)

internal data class RoleplayTranscriptMessageProjection(
    val message: RoleplayTranscriptMessage,
    val media: Map<String, File>,
)

internal data class RoleplayTranscriptListProjection(
    val messages: List<RoleplayTranscriptMessage>,
    val media: Map<String, File>,
)

private fun List<RoleplayTranscriptMessageProjection>.toRoleplayTranscriptListProjection():
    RoleplayTranscriptListProjection {
    val media = linkedMapOf<String, File>()
    forEach { projection -> media.putAll(projection.media) }
    return RoleplayTranscriptListProjection(
        messages = map(RoleplayTranscriptMessageProjection::message),
        media = media,
    )
}

internal fun buildRoleplayTranscriptModel(
    draft: ChatDraft,
    messages: List<ChatMessage>,
    layoutMode: ChatLayoutMode = ChatLayoutMode.Roleplay,
    appearance: AppearanceTheme,
    avatarShape: ChatAvatarShape,
    avatarSize: Float,
    nameFontSize: Float,
    avatarGap: Float,
    horizontalPadding: Float,
    replySpacing: Float,
    turnSpacing: Float,
    messageFontSize: Float,
    lineHeightMultiplier: Float,
    letterSpacing: Float,
    paragraphSpacing: Float,
    assistantBubbleEnabled: Boolean = false,
    bubbleCornerRadius: Float = 12f,
    cardPanel: Boolean,
    showRoleplayTimestamps: Boolean = true,
    showRoleplayMessageFloors: Boolean = true,
    renderingPreferences: ChatRenderingPreferences,
    frontendRendererEnabled: Boolean,
    historyHasMore: Boolean,
    historyLoading: Boolean,
    deleteMode: Boolean = false,
    deleteFromMessageId: String = "",
    projectionCache: RoleplayTranscriptProjectionCache? = null,
): RoleplayTranscriptModel {
    val character = draft.session.characterPersona
    val values = CharacterCardMacroValues(
        userName = character.userName.ifBlank { "用户" },
        characterName = character.characterName.ifBlank {
            character.assistantName.ifBlank { "AI" }
        },
    )
    val media = linkedMapOf<String, File>()
    fun registerMedia(key: String, path: String): String? {
        val file = path.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return null
        media[key] = file
        return "$RoleplayTranscriptOrigin$RoleplayTranscriptMediaPath$key"
    }

    val userAvatarPath = character.userAvatars.pathFor(avatarShape)
    val assistantAvatarPath = character.assistantAvatars.pathFor(avatarShape)
    val userAvatarUrl = registerMedia("avatar-user", userAvatarPath)
    val assistantAvatarUrl = registerMedia("avatar-assistant", assistantAvatarPath)
    val openingIds = draft.openingOptions.map { it.id }
    val selectedOpeningIndex = openingIds.indexOf(draft.selectedOpeningOptionId)
    val messageContext = RoleplayTranscriptMessageContext(
        macroValues = values,
        userName = character.userName.ifBlank { "用户" },
        assistantName = character.assistantName.ifBlank { "AI" },
        userAvatarUrl = userAvatarUrl,
        assistantAvatarUrl = assistantAvatarUrl,
        avatarShape = avatarShape,
        openingIds = openingIds,
        selectedOpeningIndex = selectedOpeningIndex,
        openingSelectionEnabled = draft.openingSelectionEnabled,
        mascotStyle = renderingPreferences.timelineThinkingAnimation.name.lowercase(),
    )
    fun projectMessage(message: ChatMessage): RoleplayTranscriptMessageProjection {
        projectionCache?.get(message, messageContext)?.let { cached ->
            return cached
        }
        val messageMedia = linkedMapOf<String, File>()
        fun registerMessageMedia(key: String, path: String): String? {
            val file = path.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return null
            messageMedia[key] = file
            return "$RoleplayTranscriptOrigin$RoleplayTranscriptMediaPath$key"
        }
        val displayContent = message.content
            .resolveCharacterCardMacros(values)
            .withoutRoleplayDisplayHtmlComments()
        val renderedContent = displayContent.withoutRoleplayRichReplacementFences()
        val displayReasoning = message.reasoningContent.resolveCharacterCardMacros(values)
        val copyText = stripRoleplayImageMarkers(displayContent)
        val liveStatus = if (
            shouldShowInlineAgentProcess(
                message = message,
                displayedText = copyText.trim(),
            )
        ) {
            liveChatAgentStatus(
                chatAgentTimelineItems(
                    messageId = message.id,
                    reasoningContent = displayReasoning,
                    calls = message.toolCalls,
                    running = true,
                ),
            )
        } else {
            null
        }
        val generatedImages = message.imageAttachments.mapIndexed { index, attachment ->
            val token = "image-${message.id.hashCode().toUInt().toString(16)}-$index"
            RoleplayTranscriptImage(
                id = attachment.id,
                url = registerMessageMedia(token, attachment.localPath)?.let { baseUrl ->
                    roleplayGeneratedImageMediaUrl(baseUrl, attachment)
                },
                status = when (attachment.status) {
                    ChatImageStatus.Generating -> "generating"
                    ChatImageStatus.Ready -> "ready"
                    ChatImageStatus.Failed -> "failed"
                },
                error = attachment.errorMessage,
                aspectRatio = attachment.imageWidth.toFloat()
                    .div(attachment.imageHeight.coerceAtLeast(1))
                    .coerceIn(0.2f, 5f),
                frameIndex = attachment.frameIndex,
                frameCount = attachment.frameCount,
            )
        }
        val inputImages = message.inputImageAttachments.mapIndexed { index, attachment ->
            val token = "input-image-${message.id.hashCode().toUInt().toString(16)}-$index"
            RoleplayTranscriptImage(
                id = attachment.id,
                url = registerMessageMedia(token, attachment.localPath),
                status = "ready",
                error = "",
                aspectRatio = attachment.imageWidth.toFloat()
                    .div(attachment.imageHeight.coerceAtLeast(1))
                    .takeIf { it > 0f }
                    ?.coerceIn(0.2f, 5f)
                    ?: 1f,
                frameIndex = index + 1,
                frameCount = message.inputImageAttachments.size,
            )
        }
        val contentParts = placeRoleplayTranscriptImages(
            role = message.role,
            content = renderedContent,
            streaming = message.pending,
            images = generatedImages + inputImages,
        )
        val projectedMessage = RoleplayTranscriptMessage(
            source = message,
            name = if (message.role == MessageRole.User) messageContext.userName else messageContext.assistantName,
            avatarUrl = if (message.role == MessageRole.User) userAvatarUrl else assistantAvatarUrl,
            copyText = copyText,
            contentParts = contentParts,
            reasoning = displayReasoning,
            openingOptionIds = if (
                message.id == OpeningMessageId &&
                draft.openingSelectionEnabled &&
                selectedOpeningIndex >= 0
            ) {
                openingIds
            } else {
                emptyList()
            },
            selectedOpeningIndex = if (message.id == OpeningMessageId) selectedOpeningIndex else -1,
            hasAgentProcess = message.hasAgentProcessRecord(),
            regenerateEnabled = message.id != OpeningMessageId,
            liveStatus = liveStatus?.let { status ->
                RoleplayTranscriptLiveStatus(
                    label = status.label,
                    running = status.running,
                    thinking = status.thinking,
                    icon = status.icon,
                    mascotStyle = messageContext.mascotStyle,
                )
            },
        )
        return RoleplayTranscriptMessageProjection(
            message = projectedMessage,
            media = messageMedia,
        ).also { projection ->
            projectionCache?.put(message, messageContext, projection)
        }
    }
    val transcriptProjection = projectionCache?.projectMessages(
        source = messages,
        context = messageContext,
        projectMessage = ::projectMessage,
    ) ?: messages.map(::projectMessage).toRoleplayTranscriptListProjection()
    val transcriptMessages = transcriptProjection.messages
    media.putAll(transcriptProjection.media)
    if (projectionCache != null && messages !is ImmutableAppendedList<*>) {
        projectionCache.retain(messages.mapTo(hashSetOf()) { message -> message.id })
    }
    val reading = if (layoutMode == ChatLayoutMode.Roleplay) {
        appearance.asRoleplayReadingTheme()
    } else {
        appearance
    }
    val selection = reading.selectionPalette()
    val readingColors = reading.markdownReadingColors(isUser = false)
    val userReadingColors = reading.markdownReadingColors(isUser = true)
    val assistantBubblePalette = resolveChatBubblePalette(reading, layoutMode, user = false)
    val userBubblePalette = resolveChatBubblePalette(reading, layoutMode, user = true)
    val codeDark = readingColors.codeBackground.luminance() < 0.5f
    val codeBorder = (if (codeDark) Color.White else Color.Black)
        .copy(alpha = if (codeDark) 0.18f else 0.16f)
        .compositeOver(readingColors.codeBackground)
    val codeHeader = if (renderingPreferences.codeBlockStyle == ChatCodeBlockStyle.Simple) {
        readingColors.codeBackground
    } else {
        (if (codeDark) Color.White else Color.Black)
            .copy(alpha = if (codeDark) 0.06f else 0.045f)
            .compositeOver(readingColors.codeBackground)
    }
    val resolvedAvatarWidth = avatarSize.coerceIn(24f, 96f)
    val resolvedAvatarHeight = resolvedAvatarWidth / avatarShape.widthToHeight
    val radius = when (avatarShape) {
        ChatAvatarShape.Circle -> resolvedAvatarWidth / 2f
        ChatAvatarShape.RoundedSquare ->
            resolvedAvatarWidth * ChatAvatarShape.RoundedSquareCornerRatio
        ChatAvatarShape.Portrait -> resolvedAvatarWidth * 0.14f
    }
    return RoleplayTranscriptModel(
        sessionId = draft.session.id,
        layoutMode = layoutMode.storageKey,
        showRoleplayTimestamps = showRoleplayTimestamps,
        showRoleplayMessageFloors = showRoleplayMessageFloors,
        messages = transcriptMessages,
        floorStart = projectionCache?.floorStart(messages, draft.session.historyMessageCount)
            ?: (draft.session.historyMessageCount - messages.size).coerceAtLeast(0),
        style = RoleplayTranscriptStyle(
            text = if (layoutMode == ChatLayoutMode.Agent) {
                assistantBubblePalette.content.toCssColor()
            } else {
                reading.mobileText.toCssColor()
            },
            bodyText = if (layoutMode == ChatLayoutMode.Agent) {
                assistantBubblePalette.content.toCssColor()
            } else {
                readingColors.text.toCssColor()
            },
            assistantText = assistantBubblePalette.content.toCssColor(),
            userText = userBubblePalette.content.toCssColor(),
            italicText = readingColors.italic.toCssColor(),
            underlineText = readingColors.underline.toCssColor(),
            quoteText = readingColors.quote.toCssColor(),
            inlineCodeText = readingColors.inlineCode.toCssColor(),
            muted = reading.mobileMuted.toCssColor(),
            soft = reading.mobileSoft.toCssColor(),
            accent = reading.mobileBlue.toCssColor(),
            panel = reading.mobileChatMessageBg.toCssColor(),
            assistantBubble = assistantBubblePalette.container.toCssColor(),
            userBubble = userBubblePalette.container.toCssColor(),
            line = reading.mobileLine.toCssColor(),
            jumpSurface = appearance.mobileSurface.copy(alpha = 0.96f).toCssColor(),
            avatarBackground = selection.activeContainer.toCssColor(),
            avatarPlaceholder = selection.indicator.toCssColor(),
            fontSizePx = resolveChatBodyFontSizeSp(messageFontSize),
            lineHeightPx = resolveChatBodyLineHeightSp(messageFontSize, lineHeightMultiplier),
            letterSpacingPx = letterSpacing.coerceIn(-1f, 4f),
            paragraphSpacingPx = paragraphSpacing.coerceIn(0f, 32f),
            nameFontSizePx = nameFontSize.coerceIn(10f, 18f),
            nameLineHeightPx = maxOf(16f, nameFontSize.coerceIn(10f, 18f) * 1.35f),
            avatarWidthPx = resolvedAvatarWidth,
            avatarHeightPx = resolvedAvatarHeight,
            avatarRadiusPx = radius,
            avatarGapPx = avatarGap.coerceIn(0f, 20f),
            horizontalPaddingPx = horizontalPadding.coerceIn(0f, 40f),
            replySpacingPx = replySpacing.coerceIn(0f, 32f),
            turnSpacingPx = turnSpacing.coerceIn(0f, 48f),
            bubbleRadiusPx = bubbleCornerRadius.coerceIn(0f, 24f),
            assistantBubbleEnabled = assistantBubbleEnabled,
            cardPanel = cardPanel,
            codeForeground = readingColors.codeForeground.toCssColor(),
            codeBackground = readingColors.codeBackground.toCssColor(),
            codeBorder = codeBorder.toCssColor(),
            codeHeaderBackground = codeHeader.toCssColor(),
            codeStyle = renderingPreferences.codeBlockStyle.storageKey,
            codeWrap = renderingPreferences.codeBlockWrapEnabled,
            codeShowAll = renderingPreferences.codeBlockShowAllEnabled,
            dark = reading.isDark,
        ),
        media = media,
        frontendRendererEnabled = frontendRendererEnabled,
        historyHasMore = historyHasMore,
        historyLoading = historyLoading,
        deleteMode = deleteMode,
        deleteFromMessageId = deleteFromMessageId,
    )
}

/**
 * Chromium caches local transcript media by URL. The stable path keeps the host's media lookup
 * transaction-safe, while this query revision ensures a regenerated bitmap is fetched again.
 */
internal fun roleplayGeneratedImageMediaUrl(
    baseUrl: String,
    attachment: ChatImageAttachment,
): String {
    val revision = attachment.generationAttemptId
        .ifBlank { attachment.id }
        .ifBlank { attachment.localPath.hashCode().toUInt().toString(16) }
    return "$baseUrl?v=${URLEncoder.encode(revision, Charsets.UTF_8.name())}"
}
