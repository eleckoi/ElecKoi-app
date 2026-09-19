package com.eleckoi.android.feature.chat.ui.roleplay.web.model

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.eleckoi.android.foundation.design.components.AppIconPaths
import org.json.JSONArray
import org.json.JSONObject

internal fun RoleplayTranscriptModel.toBootstrapJson(): String = JSONObject().apply {
    put("sessionId", sessionId)
    put("layoutMode", layoutMode)
    put("style", style.toJson())
    put("icons", roleplayTranscriptIconsJson())
    put("frontendRendererEnabled", frontendRendererEnabled)
    put("historyHasMore", historyHasMore)
    put("historyLoading", historyLoading)
    put("deleteMode", deleteMode)
    put("deleteFromMessageId", deleteFromMessageId)
    put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
}.toString()

internal fun RoleplayTranscriptMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", source.id)
    put("role", source.role.name.lowercase())
    put("name", name)
    put("avatarUrl", avatarUrl ?: JSONObject.NULL)
    put("pending", source.pending)
    put("revision", revision)
    put("contentRevision", contentRevision)
    put("copyText", copyText)
    put("parts", JSONArray().apply {
        contentParts.forEach { part ->
            put(part.toJson())
        }
    })
    put("reasoning", reasoning)
    put("openingOptionIds", JSONArray(openingOptionIds))
    put("selectedOpeningIndex", selectedOpeningIndex)
    put("hasAgentProcess", hasAgentProcess)
    put("regenerateEnabled", regenerateEnabled)
    put("liveStatus", liveStatus?.let { status ->
        JSONObject().apply {
            put("label", status.label)
            put("running", status.running)
            put("thinking", status.thinking)
            put("icon", status.icon.toTranscriptVectorJson())
            put("mascotStyle", status.mascotStyle)
        }
    } ?: JSONObject.NULL)
}

private fun RoleplayTranscriptContentPart.toJson(): JSONObject = when (this) {
    is RoleplayTranscriptContentPart.Text -> JSONObject().apply {
        put("type", "text")
        put("markdown", markdown)
    }

    is RoleplayTranscriptContentPart.Images -> JSONObject().apply {
        put("type", "images")
        put("images", JSONArray().apply {
            images.forEach { image -> put(image.toJson()) }
        })
    }
}

private fun RoleplayTranscriptImage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("url", url ?: JSONObject.NULL)
    put("status", status)
    put("error", error)
    put("aspectRatio", aspectRatio.toDouble())
    put("frameIndex", frameIndex)
    put("frameCount", frameCount)
}


internal fun RoleplayTranscriptStyle.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("bodyText", bodyText)
    put("assistantText", assistantText)
    put("userText", userText)
    put("italicText", italicText)
    put("underlineText", underlineText)
    put("quoteText", quoteText)
    put("inlineCodeText", inlineCodeText)
    put("muted", muted)
    put("soft", soft)
    put("accent", accent)
    put("panel", panel)
    put("assistantBubble", assistantBubble)
    put("userBubble", userBubble)
    put("line", line)
    put("jumpSurface", jumpSurface)
    put("avatarBackground", avatarBackground)
    put("avatarPlaceholder", avatarPlaceholder)
    put("fontSizePx", fontSizePx.toDouble())
    put("lineHeightPx", lineHeightPx.toDouble())
    put("letterSpacingPx", letterSpacingPx.toDouble())
    put("paragraphSpacingPx", paragraphSpacingPx.toDouble())
    put("nameFontSizePx", nameFontSizePx.toDouble())
    put("nameLineHeightPx", nameLineHeightPx.toDouble())
    put("avatarWidthPx", avatarWidthPx.toDouble())
    put("avatarHeightPx", avatarHeightPx.toDouble())
    put("avatarRadiusPx", avatarRadiusPx.toDouble())
    put("avatarGapPx", avatarGapPx.toDouble())
    put("horizontalPaddingPx", horizontalPaddingPx.toDouble())
    put("replySpacingPx", replySpacingPx.toDouble())
    put("turnSpacingPx", turnSpacingPx.toDouble())
    put("bubbleRadiusPx", bubbleRadiusPx.toDouble())
    put("assistantBubbleEnabled", assistantBubbleEnabled)
    put("cardPanel", cardPanel)
    put("codeForeground", codeForeground)
    put("codeBackground", codeBackground)
    put("codeBorder", codeBorder)
    put("codeHeaderBackground", codeHeaderBackground)
    put("codeStyle", codeStyle)
    put("codeWrap", codeWrap)
    put("codeShowAll", codeShowAll)
    put("dark", dark)
}

private fun ImageVector.toTranscriptVectorJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("viewportWidth", viewportWidth.toDouble())
    put("viewportHeight", viewportHeight.toDouble())
    put("mirrorX", name.endsWith("AutoStories"))
    put("paths", JSONArray().apply {
        fun appendGroup(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorPath -> put(JSONObject().apply {
                        put("data", node.pathData.toSvgPathData())
                        put("fill", node.fill != null)
                        put("fillAlpha", node.fillAlpha.toDouble())
                        put("stroke", node.stroke != null)
                        put("strokeAlpha", node.strokeAlpha.toDouble())
                        put("strokeWidth", node.strokeLineWidth.toDouble())
                    })
                    is VectorGroup -> appendGroup(node)
                }
            }
        }
        appendGroup(root)
    })
}

private fun List<PathNode>.toSvgPathData(): String = buildString {
    fun number(value: Float) {
        val rounded = value.toInt()
        append(if (value == rounded.toFloat()) rounded.toString() else value.toString())
    }
    fun pair(first: Float, second: Float) {
        number(first)
        append(' ')
        number(second)
    }
    this@toSvgPathData.forEach { node ->
        if (isNotEmpty()) append(' ')
        when (node) {
            is PathNode.MoveTo -> { append('M'); pair(node.x, node.y) }
            is PathNode.RelativeMoveTo -> { append('m'); pair(node.dx, node.dy) }
            is PathNode.LineTo -> { append('L'); pair(node.x, node.y) }
            is PathNode.RelativeLineTo -> { append('l'); pair(node.dx, node.dy) }
            is PathNode.HorizontalTo -> { append('H'); number(node.x) }
            is PathNode.RelativeHorizontalTo -> { append('h'); number(node.dx) }
            is PathNode.VerticalTo -> { append('V'); number(node.y) }
            is PathNode.RelativeVerticalTo -> { append('v'); number(node.dy) }
            is PathNode.CurveTo -> {
                append('C'); pair(node.x1, node.y1); append(' ')
                pair(node.x2, node.y2); append(' '); pair(node.x3, node.y3)
            }
            is PathNode.RelativeCurveTo -> {
                append('c'); pair(node.dx1, node.dy1); append(' ')
                pair(node.dx2, node.dy2); append(' '); pair(node.dx3, node.dy3)
            }
            is PathNode.ReflectiveCurveTo -> {
                append('S'); pair(node.x1, node.y1); append(' '); pair(node.x2, node.y2)
            }
            is PathNode.RelativeReflectiveCurveTo -> {
                append('s'); pair(node.dx1, node.dy1); append(' '); pair(node.dx2, node.dy2)
            }
            is PathNode.QuadTo -> {
                append('Q'); pair(node.x1, node.y1); append(' '); pair(node.x2, node.y2)
            }
            is PathNode.RelativeQuadTo -> {
                append('q'); pair(node.dx1, node.dy1); append(' '); pair(node.dx2, node.dy2)
            }
            is PathNode.ReflectiveQuadTo -> { append('T'); pair(node.x, node.y) }
            is PathNode.RelativeReflectiveQuadTo -> { append('t'); pair(node.dx, node.dy) }
            is PathNode.ArcTo -> {
                append('A'); pair(node.horizontalEllipseRadius, node.verticalEllipseRadius)
                append(' '); number(node.theta); append(' ')
                append(if (node.isMoreThanHalf) '1' else '0'); append(' ')
                append(if (node.isPositiveArc) '1' else '0'); append(' ')
                pair(node.arcStartX, node.arcStartY)
            }
            is PathNode.RelativeArcTo -> {
                append('a'); pair(node.horizontalEllipseRadius, node.verticalEllipseRadius)
                append(' '); number(node.theta); append(' ')
                append(if (node.isMoreThanHalf) '1' else '0'); append(' ')
                append(if (node.isPositiveArc) '1' else '0'); append(' ')
                pair(node.arcStartDx, node.arcStartDy)
            }
            PathNode.Close -> append('Z')
        }
    }
}

internal fun roleplayTranscriptIconsJson(): JSONObject = JSONObject().apply {
    fun add(name: String, paths: List<String>, viewport: Float = 24f, filled: Boolean = false) {
        put(name, JSONObject().apply {
            put("paths", JSONArray(paths))
            put("viewport", viewport.toDouble())
            put("filled", filled)
        })
    }
    add("chevronLeft", AppIconPaths.ChevronLeft)
    add("chevronRight", AppIconPaths.ChevronRight)
    add("chevronDown", AppIconPaths.ChevronDown)
    add("history", AppIconPaths.History)
    add("translate", AppIconPaths.Translate)
    add("speaker", AppIconPaths.Speaker)
    add("refresh", AppIconPaths.Refresh)
    add("copy", AppIconPaths.Copy)
    add("edit", AppIconPaths.MessageEditPencil, viewport = 512f, filled = true)
}
