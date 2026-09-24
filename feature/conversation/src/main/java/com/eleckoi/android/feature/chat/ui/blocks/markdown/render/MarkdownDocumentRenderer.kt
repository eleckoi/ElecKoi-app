package com.eleckoi.android.feature.chat.ui.blocks.markdown.render

import android.content.Context

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.markdown.MarkdownBlockType
import com.eleckoi.android.feature.chat.model.markdown.MarkdownCodeContent
import com.eleckoi.android.feature.chat.model.markdown.MarkdownNode
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderBlock
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownLayoutGeometryCache
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownLayoutGeometryKey
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlan
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlanCache
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlanEngine
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlanKey
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderScheduler
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownInlineColorPalette
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.nodeCharacterWeight
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.code.MarkdownCodeBlock
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.latex.MarkdownLatexBlock
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.mermaid.currentMermaidGeometryRevision
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.mermaid.MarkdownMermaidBlock
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.mermaid.requestMermaidGeometryPrewarm
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.mermaid.retainedMermaidHeightPx
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.table.MarkdownTableCanvas
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.text.MarkdownTextCanvas
import com.eleckoi.android.feature.chat.ui.LocalChatRenderingPreferences
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.foundation.design.markdownReadingColors
import kotlinx.coroutines.flow.first

/** Coordinates an immutable render plan and delegates each block to its specialized renderer. */
@Composable
internal fun MarkdownDocumentRenderer(
    nodes: List<MarkdownNode>,
    appearance: AppearanceTheme,
    isUser: Boolean,
    modifier: Modifier,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    paragraphSpacing: Float,
    messageContainerVisible: Boolean,
    streaming: Boolean,
    visualGeneration: Int,
    onContentReady: () -> Unit,
    onRevealComplete: () -> Unit,
    cacheOwnerKey: String,
    sourceHash: Int,
    sourceLength: Int,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val readingColors = appearance.markdownReadingColors(isUser)
    val textColor = readingColors.text
    val quoteColor = readingColors.quote
    val inlineColors = MarkdownInlineColorPalette(
        italicArgb = readingColors.italic.toArgb(),
        underlineArgb = readingColors.underline.toArgb(),
        quoteArgb = readingColors.quote.toArgb(),
        inlineCodeArgb = readingColors.inlineCode.toArgb(),
    )
    val field = appearance.fieldPalette()
    val codeDark = readingColors.codeBackground.luminance() < 0.5f
    val codeBorder = if (codeDark) {
        codeSurface(readingColors.codeBackground, Color.White, 0.18f)
    } else {
        codeSurface(readingColors.codeBackground, Color.Black, 0.16f)
    }
    val mermaidDark = appearance.mobileBg.luminance() < 0.5f
    val renderingPreferences = LocalChatRenderingPreferences.current
    val codeBlockStyle = renderingPreferences.codeBlockStyle
    val codeBlockLayoutRevision =
        (codeBlockStyle.ordinal shl 2) or
            (if (renderingPreferences.codeBlockWrapEnabled) 2 else 0) or
            (if (renderingPreferences.codeBlockShowAllEnabled) 1 else 0)
    val codePalette = if (codeBlockStyle == ChatCodeBlockStyle.Simple) {
        CodePalette(
            foreground = readingColors.codeForeground,
            background = readingColors.codeBackground,
            headerBackground = readingColors.codeBackground,
            border = codeBorder,
            gutter = Color.Transparent,
        )
    } else if (codeDark) {
        CodePalette(
            foreground = readingColors.codeForeground,
            background = readingColors.codeBackground,
            headerBackground = codeSurface(readingColors.codeBackground, Color.White, 0.06f),
            border = codeBorder,
            gutter = readingColors.codeForeground.copy(alpha = 0.58f),
        )
    } else {
        CodePalette(
            foreground = readingColors.codeForeground,
            background = readingColors.codeBackground,
            headerBackground = codeSurface(readingColors.codeBackground, Color.Black, 0.045f),
            border = codeBorder,
            gutter = readingColors.codeForeground.copy(alpha = 0.58f),
        )
    }
    val currentOnContentReady by rememberUpdatedState(onContentReady)
    val currentOnRevealComplete by rememberUpdatedState(onRevealComplete)
    var activePlan by remember(visualGeneration) { mutableStateOf<MarkdownRenderPlan?>(null) }

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val fontSizePx = with(density) { fontSize.toPx() }
        val lineHeightPx = with(density) { lineHeight.toPx() }
        val letterSpacingPx = with(density) { letterSpacing.toPx() }
        val mermaidSources = remember(nodes) {
            nodes.asReversed().mapNotNull(MarkdownNode::stableMermaidSource).distinct()
        }
        LaunchedEffect(context, mermaidSources, mermaidDark, widthPx) {
            requestMermaidGeometryPrewarm(
                context = context,
                sources = mermaidSources,
                dark = mermaidDark,
                layoutWidthPx = widthPx,
            )
        }
        // Reading this Compose-backed revision lets a still-visible placeholder adopt geometry
        // produced by the low-priority prewarmer before its bitmap reaches the viewport.
        val mermaidGeometryRevision = currentMermaidGeometryRevision()
        // Read as Compose state so a font change reaches messages that are already on screen: they
        // are holding a plan whose key would otherwise be unchanged, and would keep the old
        // typeface until they scrolled out of the viewport and came back.
        val typefaceRevision = MarkdownRenderPlanEngine.bodyTypefaceRevision
        val key = remember(
            nodes,
            widthPx,
            textColor,
            quoteColor,
            inlineColors,
            fontSizePx,
            lineHeightPx,
            letterSpacingPx,
            typefaceRevision,
        ) {
            MarkdownRenderPlanKey(
                cacheOwnerKey = cacheOwnerKey,
                nodes = nodes,
                widthPx = widthPx,
                textColorArgb = textColor.toArgb(),
                quoteColorArgb = quoteColor.toArgb(),
                inlineColors = inlineColors,
                fontSizeBits = fontSizePx.toRawBits(),
                lineHeightBits = lineHeightPx.toRawBits(),
                letterSpacingBits = letterSpacingPx.toRawBits(),
                typefaceRevision = typefaceRevision,
            )
        }
        val geometryKey = remember(
            cacheOwnerKey,
            sourceHash,
            sourceLength,
            widthPx,
            fontSizePx,
            lineHeightPx,
            letterSpacingPx,
            paragraphSpacing,
            typefaceRevision,
            codeBlockLayoutRevision,
        ) {
            MarkdownLayoutGeometryKey(
                cacheOwnerKey = cacheOwnerKey,
                sourceHash = sourceHash,
                sourceLength = sourceLength,
                widthPx = widthPx,
                fontSizeBits = fontSizePx.toRawBits(),
                lineHeightBits = lineHeightPx.toRawBits(),
                letterSpacingBits = letterSpacingPx.toRawBits(),
                paragraphSpacingBits = paragraphSpacing.toRawBits(),
                typefaceRevision = typefaceRevision,
                codeBlockLayoutRevision = codeBlockLayoutRevision,
            )
        }
        val retainedHeightPx = remember(geometryKey) {
            MarkdownLayoutGeometryCache.get(context, geometryKey)
        }
        val cached = remember(key) {
            if (key.nodes.isEmpty()) {
                null
            } else {
                MarkdownRenderPlanCache.get(key) ?: MarkdownRenderPlanCache.getHandoff(key)
            }
        }
        // The terminal parser promotes its streaming tail to stable nodes, changing [key] even
        // though this is the same message. Keep the last plan painted across that owner handoff;
        // [requestedContentReady] below remains false until the exact terminal key is ready.
        val latestHandoff = remember(cacheOwnerKey) {
            MarkdownRenderPlanCache.getLatestHandoff(cacheOwnerKey)
        }
        val currentKey by rememberUpdatedState(key)
        // Do not key this worker by provider content. A fast stream would cancel layout before it
        // can publish even one frame, leaving the message blank until the final chunk. The worker
        // always finishes its current plan, publishes it, then immediately coalesces to the newest
        // requested key.
        LaunchedEffect(visualGeneration) {
            while (true) {
                val requested = currentKey
                if (requested.nodes.isEmpty()) {
                    activePlan = null
                    snapshotFlow { currentKey }.first { it.nodes.isNotEmpty() }
                    continue
                }
                val builtPlan = MarkdownRenderPlanCache.get(requested)
                    ?: MarkdownRenderScheduler.build(requested)
                activePlan = builtPlan
                MarkdownRenderPlanCache.putHandoff(builtPlan)
                if (currentKey == requested) {
                    snapshotFlow { currentKey }.first { it != requested }
                }
            }
        }

        // Keep the last complete plan while its replacement is prepared. Removing it for one
        // frame collapses the LazyColumn item and destroys the user's scroll anchor.
        val plan = cached ?: activePlan ?: latestHandoff
        val requestedContentReady = nodes.isNotEmpty() && plan?.key == key
        LaunchedEffect(requestedContentReady) {
            // A retained plan is useful geometry while the replacement is built, but it is not
            // readiness for the new source. Reporting it as ready hid the creator assistant's
            // plain-text hand-off and exposed a screen-tall blank retained-height spacer.
            if (requestedContentReady) currentOnContentReady()
        }
        val immediateCode = codeContentForRenderPlanFallback(
            nodes = nodes,
            exactPlanReady = plan?.key == key,
        )
        if (immediateCode != null) {
            // A completed long answer is later promoted into one LazyColumn item per Markdown
            // node. Its code node already has an indexed source, so do not replace a visible
            // code block with an empty retained-height spacer while the render plan is queued.
            MarkdownCodeBlock(
                code = immediateCode,
                style = codeBlockStyle,
                color = codePalette.foreground,
                dark = codeDark,
                background = codePalette.background,
                headerBackground = codePalette.headerBackground,
                borderColor = codePalette.border,
                gutterColor = codePalette.gutter,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                wrapLines = renderingPreferences.codeBlockWrapEnabled,
                showAll = renderingPreferences.codeBlockShowAllEnabled,
                streaming = streaming,
                copyEnabled = nodes.single().stable,
            )
        } else if (plan == null) {
            val placeholderHeightPx = retainedHeightPx?.toFloat()
                ?: estimateHeightPx(
                    context = context,
                    nodes = nodes,
                    widthPx = widthPx,
                    lineHeightPx = lineHeightPx,
                    paragraphSpacingPx = with(density) { paragraphSpacing.dp.toPx() },
                    mermaidFramePaddingPx = with(density) { 16.dp.toPx() },
                    mermaidGeometryRevision = mermaidGeometryRevision,
                )
            Spacer(Modifier.height(with(density) { placeholderHeightPx.toDp() }))
        } else {
            // Provider deltas are already losslessly coalesced by the Responses replay buffer.
            // Render each published snapshot once instead of starting a second, per-grapheme
            // animation clock in Compose. The unstable tail grows with real upstream batches while
            // stable cached blocks remain unchanged.
            LaunchedEffect(plan.key, key, streaming) {
                if (!streaming && plan.key == key) currentOnRevealComplete()
            }
            Column(
                Modifier
                    .onSizeChanged { size ->
                        // A fallback plan belongs to the previous source revision. Do not record
                        // its height under the terminal geometry key while the replacement builds.
                        if (plan.key == key) {
                            MarkdownLayoutGeometryCache.put(
                                context = context,
                                key = geometryKey,
                                heightPx = size.height,
                                persistAcrossProcessRestart = !streaming,
                            )
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        text = AnnotatedString(plan.accessibilityText)
                    },
            ) {
                plan.blocks.forEachIndexed { index, block ->
                    key(block.id) {
                        when (block) {
                            is MarkdownRenderBlock.Text -> MarkdownTextCanvas(block = block)
                            is MarkdownRenderBlock.Code -> MarkdownCodeBlock(
                                code = block.content,
                                style = codeBlockStyle,
                                color = codePalette.foreground,
                                dark = codeDark,
                                background = codePalette.background,
                                headerBackground = codePalette.headerBackground,
                                borderColor = codePalette.border,
                                gutterColor = codePalette.gutter,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                letterSpacing = letterSpacing,
                                wrapLines = renderingPreferences.codeBlockWrapEnabled,
                                showAll = renderingPreferences.codeBlockShowAllEnabled,
                                streaming = streaming,
                                copyEnabled = block.copyEnabled,
                            )
                            is MarkdownRenderBlock.Table -> MarkdownTableCanvas(
                                block = block,
                                borderColor = textColor.copy(alpha = 0.22f),
                            )
                            is MarkdownRenderBlock.Latex -> MarkdownLatexBlock(
                                block = block,
                                textColor = textColor,
                                fontSize = fontSize,
                                retainedLayoutHeightPx = retainedHeightPx
                                    ?.takeIf { plan.blocks.size == 1 },
                            )
                            is MarkdownRenderBlock.Mermaid -> MarkdownMermaidBlock(
                                block = block,
                                dark = mermaidDark,
                                textColor = textColor,
                                background = if (messageContainerVisible) {
                                    field.container.copy(alpha = 0.62f)
                                } else {
                                    Color.Transparent
                                },
                                borderColor = textColor.copy(alpha = 0.20f),
                                retainedLayoutHeightPx = retainedHeightPx
                                    ?.takeIf { plan.blocks.size == 1 },
                            )
                            is MarkdownRenderBlock.Rule -> Canvas(
                                Modifier.fillMaxWidth().height(1.dp),
                            ) {
                                drawRect(textColor.copy(alpha = 0.22f))
                            }
                        }
                    }
                    if (index != plan.blocks.lastIndex) {
                        Spacer(Modifier.height(paragraphSpacing.dp))
                    }
                }
            }
        }
    }
}

internal fun codeContentForRenderPlanFallback(
    nodes: List<MarkdownNode>,
    exactPlanReady: Boolean,
): MarkdownCodeContent? = nodes.singleOrNull()
    ?.takeIf { !exactPlanReady && it.type == MarkdownBlockType.CodeFence }
    ?.code
    ?.takeUnless { it.language.equals("mermaid", ignoreCase = true) }

private fun codeSurface(base: Color, overlay: Color, amount: Float): Color {
    val alpha = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - alpha) + overlay.red * alpha,
        green = base.green * (1f - alpha) + overlay.green * alpha,
        blue = base.blue * (1f - alpha) + overlay.blue * alpha,
        alpha = 1f,
    )
}

private data class CodePalette(
    val foreground: Color,
    val background: Color,
    val headerBackground: Color,
    val border: Color,
    val gutter: Color,
)

private fun MarkdownNode.stableMermaidSource(): String? {
    if (!stable || type != MarkdownBlockType.CodeFence) return null
    val content = code ?: return null
    if (!content.language.equals("mermaid", ignoreCase = true)) return null
    return content.accessibilityText(content.textLength)
}

@Suppress("UNUSED_PARAMETER")
private fun estimateHeightPx(
    context: Context,
    nodes: List<MarkdownNode>,
    widthPx: Int,
    lineHeightPx: Float,
    paragraphSpacingPx: Float,
    mermaidFramePaddingPx: Float,
    mermaidGeometryRevision: Int,
): Float {
    var lines = 0f
    var accumulatedMermaidHeightPx = 0f
    nodes.forEach { node ->
        val mermaidSource = node.stableMermaidSource()
        val retainedHeight = mermaidSource?.let { source ->
            retainedMermaidHeightPx(
                context = context,
                source = source,
                layoutWidthPx = widthPx,
                framePaddingPx = mermaidFramePaddingPx,
            )
        }
        if (retainedHeight != null) {
            accumulatedMermaidHeightPx += retainedHeight
        } else {
            lines += when (node.type) {
                MarkdownBlockType.CodeFence ->
                    (node.code?.lineCount ?: 1).coerceAtMost(14).toFloat()
                MarkdownBlockType.HorizontalRule -> 1f
                else -> (nodeCharacterWeight(node) / 28f).coerceAtLeast(1f)
            }
        }
    }
    val estimatedTextHeightPx = if (lines > 0f) {
        (lines * lineHeightPx).coerceIn(lineHeightPx, lineHeightPx * 30f)
    } else {
        0f
    }
    val interBlockSpacingPx = (nodes.size - 1).coerceAtLeast(0) * paragraphSpacingPx
    return (estimatedTextHeightPx + accumulatedMermaidHeightPx + interBlockSpacingPx)
        .coerceAtLeast(lineHeightPx)
}
