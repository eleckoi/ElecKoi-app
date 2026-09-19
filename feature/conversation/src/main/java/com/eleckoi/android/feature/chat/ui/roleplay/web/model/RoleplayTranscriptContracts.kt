package com.eleckoi.android.feature.chat.ui.roleplay.web.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.eleckoi.android.feature.chat.model.ChatMessage
import java.io.File

internal data class RoleplayTranscriptModel(
    val sessionId: String,
    val layoutMode: String = "roleplay",
    val messages: List<RoleplayTranscriptMessage>,
    val style: RoleplayTranscriptStyle,
    val media: Map<String, File>,
    val frontendRendererEnabled: Boolean = true,
    val historyHasMore: Boolean,
    val historyLoading: Boolean,
    val deleteMode: Boolean = false,
    val deleteFromMessageId: String = "",
)

internal data class RoleplayTranscriptMessage(
    val source: ChatMessage,
    val name: String,
    val avatarUrl: String?,
    val copyText: String,
    val contentParts: List<RoleplayTranscriptContentPart>,
    val reasoning: String,
    val openingOptionIds: List<String>,
    val selectedOpeningIndex: Int,
    val hasAgentProcess: Boolean,
    val regenerateEnabled: Boolean,
    val liveStatus: RoleplayTranscriptLiveStatus? = null,
) {
    val contentRevision: String = contentParts.hashCode().toString()
    val revision: String = buildString {
        append(contentRevision)
        append(':')
        append(reasoning.hashCode())
        append(':')
        append(source.pending)
        append(':')
        append(source.variableStateJson.hashCode())
        append(':')
        append(selectedOpeningIndex)
        append(':')
        append(source.imageAttachments.hashCode())
        append(':')
        append(source.inputImageAttachments.hashCode())
        append(':')
        append(source.toolCalls.hashCode())
        append(':')
        append(name.hashCode())
        append(':')
        append(avatarUrl.hashCode())
        append(':')
        append(liveStatus.hashCode())
    }
}

internal sealed interface RoleplayTranscriptContentPart {
    data class Text(val markdown: String) : RoleplayTranscriptContentPart

    data class Images(val images: List<RoleplayTranscriptImage>) : RoleplayTranscriptContentPart
}

internal data class RoleplayTranscriptLiveStatus(
    val label: String,
    val running: Boolean,
    val thinking: Boolean,
    val icon: ImageVector,
    val mascotStyle: String,
)

internal data class RoleplayTranscriptImage(
    val id: String,
    val url: String?,
    val status: String,
    val error: String,
    val aspectRatio: Float,
    val frameIndex: Int,
    val frameCount: Int,
)

internal data class RoleplayTranscriptStyle(
    val text: String,
    val bodyText: String,
    val assistantText: String = bodyText,
    val userText: String = bodyText,
    val italicText: String,
    val underlineText: String,
    val quoteText: String,
    val inlineCodeText: String,
    val muted: String,
    val soft: String,
    val accent: String,
    val panel: String,
    val assistantBubble: String = panel,
    val userBubble: String = panel,
    val line: String,
    val jumpSurface: String,
    val avatarBackground: String,
    val avatarPlaceholder: String,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val letterSpacingPx: Float,
    val paragraphSpacingPx: Float,
    val nameFontSizePx: Float,
    val nameLineHeightPx: Float,
    val avatarWidthPx: Float,
    val avatarHeightPx: Float,
    val avatarRadiusPx: Float,
    val avatarGapPx: Float,
    val horizontalPaddingPx: Float,
    val replySpacingPx: Float,
    val turnSpacingPx: Float,
    val bubbleRadiusPx: Float = 12f,
    val assistantBubbleEnabled: Boolean = false,
    val cardPanel: Boolean,
    val codeForeground: String,
    val codeBackground: String,
    val codeBorder: String,
    val codeHeaderBackground: String,
    val codeStyle: String,
    val codeWrap: Boolean,
    val codeShowAll: Boolean,
    val dark: Boolean,
)
