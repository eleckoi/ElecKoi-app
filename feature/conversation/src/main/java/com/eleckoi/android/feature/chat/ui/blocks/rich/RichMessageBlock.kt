package com.eleckoi.android.feature.chat.ui.blocks.rich

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eleckoi.android.feature.chat.data.rich.RichMessageCssTheme
import com.eleckoi.android.feature.chat.data.rich.RichMessageDocument
import com.eleckoi.android.feature.chat.data.rich.buildRichMessageHtml
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorChatGateway
import com.eleckoi.android.sdk.author.AuthorFrontendSdk
import com.eleckoi.android.feature.chat.ui.author.toAuthorSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun RichMessageBlock(
    message: ChatMessage,
    document: RichMessageDocument,
    isUser: Boolean,
    appearance: AppearanceTheme,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    authorGateway: AuthorChatGateway,
    modifier: Modifier = Modifier,
    onContentReady: () -> Unit,
) {
    val incomingSnapshotKey = remember(message.variableStateJson) {
        richMessageSnapshotKey(message.variableStateJson)
    }
    var activeSnapshotKey by remember(message.id, document.contentKey) {
        mutableStateOf(incomingSnapshotKey)
    }
    var activeMessage by remember(message.id, document.contentKey) { mutableStateOf(message) }
    var pendingSnapshotKey by remember(message.id, document.contentKey) {
        mutableStateOf<String?>(null)
    }
    var pendingMessage by remember(message.id, document.contentKey) {
        mutableStateOf<ChatMessage?>(null)
    }
    LaunchedEffect(incomingSnapshotKey) {
        if (incomingSnapshotKey == activeSnapshotKey) {
            pendingSnapshotKey = null
            pendingMessage = null
        } else {
            pendingSnapshotKey = incomingSnapshotKey
            pendingMessage = message
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidthDp = maxWidth.value.roundToInt()
        val activeLayerMessage = if (incomingSnapshotKey == activeSnapshotKey) {
            message
        } else {
            activeMessage
        }
        val stagedSnapshotKey = pendingSnapshotKey
        val stagedLayerMessage = if (incomingSnapshotKey == stagedSnapshotKey) {
            message
        } else {
            pendingMessage
        }
        val layers = buildList {
            add(
                RichMessageLayer(
                    snapshotKey = activeSnapshotKey,
                    message = activeLayerMessage,
                    pending = false,
                ),
            )
            if (stagedSnapshotKey != null && stagedLayerMessage != null) {
                add(
                    RichMessageLayer(
                        snapshotKey = stagedSnapshotKey,
                        message = stagedLayerMessage,
                        pending = true,
                    ),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            layers.forEach { layer ->
                key(layer.snapshotKey) {
                    RichMessageBlockContent(
                        message = layer.message,
                        document = document,
                        isUser = isUser,
                        appearance = appearance,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        letterSpacing = letterSpacing,
                        authorGateway = authorGateway,
                        viewportWidthDp = viewportWidthDp,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (layer.pending) 0f else 1f
                        },
                        onContentReady = {
                            if (layer.pending && pendingSnapshotKey == layer.snapshotKey) {
                                activeSnapshotKey = layer.snapshotKey
                                activeMessage = layer.message
                                pendingSnapshotKey = null
                                pendingMessage = null
                            }
                            onContentReady()
                        },
                    )
                }
            }
        }
    }
}

private data class RichMessageLayer(
    val snapshotKey: String,
    val message: ChatMessage,
    val pending: Boolean,
)

@Composable
private fun RichMessageBlockContent(
    message: ChatMessage,
    document: RichMessageDocument,
    isUser: Boolean,
    appearance: AppearanceTheme,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    authorGateway: AuthorChatGateway,
    viewportWidthDp: Int,
    modifier: Modifier,
    onContentReady: () -> Unit,
) {
    val context = LocalContext.current
    val theme = remember(appearance, isUser, fontSize, lineHeight, letterSpacing) {
        RichMessageCssTheme(
            foreground = if (isUser) {
                appearance.mobileChatUserFg.toCssColor()
            } else {
                appearance.mobileChatMessageFg.toCssColor()
            },
            muted = appearance.mobileMuted.toCssColor(),
            accent = appearance.mobileBlue.toCssColor(),
            fontSizePx = fontSize.value,
            lineHeightPx = lineHeight.value,
            letterSpacingPx = letterSpacing.value,
            dark = appearance.isDark,
        )
    }
    val authorRuntimeHead = remember(context.applicationContext) {
        AuthorFrontendSdk.documentHead(context.applicationContext)
    }
    val snapshotKey = remember(message.variableStateJson) {
        richMessageSnapshotKey(message.variableStateJson)
    }
    val messageSnapshot = message.toAuthorSnapshot()
    val authorEnvironment = remember(message.id, snapshotKey, authorGateway) {
        AuthorApiEnvironment.forInlineMessage(
            appContext = context.applicationContext,
            message = messageSnapshot,
            messageGateway = authorGateway,
        )
    }
    SideEffect {
        authorEnvironment.runtime.currentMessage = messageSnapshot
        authorEnvironment.runtime.variableStateJson = messageSnapshot.variableStateJson
    }
    val authorApiRouter = remember(context.applicationContext, authorEnvironment) {
        AuthorApiRouter(authorEnvironment)
    }
    val html = remember(document, theme, authorRuntimeHead) {
        buildRichMessageHtml(
            document = document,
            theme = theme,
            authorRuntimeHead = authorRuntimeHead,
        )
    }
    val bindingKey = remember(document.contentKey, theme, snapshotKey, viewportWidthDp) {
        "${document.contentKey}:${theme.hashCode()}:$snapshotKey:width=$viewportWidthDp"
    }
    val origin = remember(message.id) { richMessageOrigin(message.id) }
    val cachedHeight = remember(bindingKey) { RichMessageHeightCache.get(context, bindingKey) }
    var measuredHeight by remember(bindingKey) {
        mutableStateOf(
            cachedHeight ?: document.kind.initialHeight(),
        )
    }
    var readyReported by remember(bindingKey) { mutableStateOf(false) }
    val currentOnContentReady by rememberUpdatedState(onContentReady)
    AndroidView(
        factory = { context -> RichMessageWebView(context) },
        update = { webView ->
            webView.bind(
                bindingKey = bindingKey,
                origin = origin,
                html = html,
                authorApiRouter = authorApiRouter,
                onHeightChanged = { rawHeight ->
                    // A recycled WebView can publish its viewport-sized provisional height before
                    // authored CSS/API content has settled. Keep the verified cached height until
                    // this new instance reports ready, otherwise LazyColumn loses its read anchor.
                    if (shouldAcceptRichMessageHeight(cachedHeight != null, readyReported)) {
                        val next = rawHeight
                            .coerceIn(MinRichMessageHeight.value, MaxRichMessageHeight.value)
                            .dp
                        if (abs(next.value - measuredHeight.value) >= 0.5f) {
                            measuredHeight = next
                        }
                        if (readyReported) {
                            RichMessageHeightCache.put(context, bindingKey, next)
                        }
                    }
                },
                onContentReady = {
                    if (!readyReported) {
                        readyReported = true
                        currentOnContentReady()
                    }
                },
            )
        },
        onReset = RichMessageWebView::prepareForReuse,
        onRelease = RichMessageWebView::release,
        modifier = modifier
            .fillMaxWidth()
            .height(measuredHeight),
    )
}

