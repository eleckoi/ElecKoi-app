package com.eleckoi.android.feature.chat.ui.blocks.rich

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.eleckoi.android.feature.chat.ui.web.configureDesktopAlignedAuthorFrontend
import com.eleckoi.android.feature.chat.ui.web.installDesktopAlignedWindowOpenHandler
import com.eleckoi.android.feature.chat.ui.web.isDesktopAllowedAuthorFrontendResource
import com.eleckoi.android.feature.chat.ui.web.openDesktopAlignedExternalUri
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorFrontendSdk
import com.eleckoi.android.sdk.author.bridge.WebViewAuthorBridge
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
internal class RichMessageWebView(context: Context) : WebView(context) {
    private var bindingKey: String? = null
    private var bindingGeneration = 0L
    private var nextReadyRequestId = 0L
    private var expectedReadyRequestId: Long? = null
    private var acceptedReadyRequestId: Long? = null
    private var installedHeightBridge = false
    private var allowedOrigin: String = ""
    private var onHeightChanged: (Float) -> Unit = {}
    private var onContentReady: () -> Unit = {}
    private var authorApiRouter: AuthorApiRouter? = null
    private var authorBridge: WebViewAuthorBridge? = null
    private var heightBridge: RichMessageHeightBridge? = null

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        // Keep transparent rich content on one retained GPU layer while its Compose row moves.
        // Pre-rasterization prevents Chromium from exposing an empty tile during fast scrolling.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            configureDesktopAlignedAuthorFrontend()
            textZoom = 100
            offscreenPreRaster = true
        }
        installDesktopAlignedWindowOpenHandler { uri ->
            context.openDesktopAlignedExternalUri(uri)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == allowedOrigin.toUri().host) {
                    val runtimePath = uri.path
                        ?.takeIf { it.startsWith(AuthorRuntimePath) }
                        ?.removePrefix(AuthorRuntimePath)
                    if (runtimePath != null) {
                        return AuthorFrontendSdk.runtimeResource(context, runtimePath)
                            ?: authorApiRouter?.runtimeResource(runtimePath)
                            ?: blockedResourceResponse()
                    }
                    return null
                }
                // Authored frontends may load the remote resources they declare. Main-frame
                // navigation is still handled separately.
                if (isDesktopAllowedAuthorFrontendResource(uri.scheme)) return null
                return blockedResourceResponse()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                if (uri.scheme == "https" && uri.host == allowedOrigin.toUri().host) return false
                return openExternalUri(uri)
            }

            override fun onPageFinished(view: WebView, url: String) {
                requestReadyMeasurementAfterLayout()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestReadyMeasurementAfterLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (oldw <= 0 || oldh <= 0 || w != oldw)) {
            if (w != oldw && oldw > 0) acceptedReadyRequestId = null
            requestReadyMeasurementAfterLayout()
        }
    }

    fun bind(
        bindingKey: String,
        origin: String,
        html: String,
        authorApiRouter: AuthorApiRouter,
        onHeightChanged: (Float) -> Unit,
        onContentReady: () -> Unit,
    ) {
        if (this.bindingKey == bindingKey && allowedOrigin == origin) {
            this.onHeightChanged = onHeightChanged
            this.onContentReady = onContentReady
            requestMeasuredHeight()
            return
        }
        resetBinding()
        this.onHeightChanged = onHeightChanged
        this.onContentReady = onContentReady
        this.bindingKey = bindingKey
        allowedOrigin = origin
        this.authorApiRouter = authorApiRouter
        authorBridge = WebViewAuthorBridge(
            router = authorApiRouter,
            allowedOrigin = origin,
        ).also { bridge -> bridge.install(this) }
        installHeightBridge()
        loadDataWithBaseURL(
            "$origin/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun resetBinding() {
        bindingGeneration += 1
        stopLoading()
        if (installedHeightBridge) {
            removeJavascriptInterface(HeightBridgeName)
        }
        authorBridge?.destroy()
        authorBridge = null
        authorApiRouter = null
        installedHeightBridge = false
        heightBridge = null
        expectedReadyRequestId = null
        acceptedReadyRequestId = null
        bindingKey = null
        allowedOrigin = ""
        onHeightChanged = {}
        onContentReady = {}
    }

    fun prepareForReuse() {
        // Lazy layouts may temporarily deactivate and later reattach this exact view.
        // Keep the rendered page alive here; bind() resets it only when the next item differs.
    }

    fun release() {
        resetBinding()
        webViewClient = WebViewClient()
        destroy()
    }

    private fun installHeightBridge() {
        val bridge = RichMessageHeightBridge(
            webView = this,
            bindingGeneration = bindingGeneration,
        )
        heightBridge = bridge
        addJavascriptInterface(bridge, HeightBridgeName)
        installedHeightBridge = true
    }

    private fun requestMeasuredHeight() {
        // The bootstrap measures the document range plus overflowing body children. body bounds
        // alone can under-report authored layouts, so all height publications use that one source.
        evaluateJavascript(
            "if (window.__ElecKoiMeasure) window.__ElecKoiMeasure();",
            null,
        )
    }

    private fun requestReadyMeasurementAfterLayout() {
        val requestedGeneration = bindingGeneration
        postOnAnimation {
            postOnAnimation {
                if (
                    bindingKey != null &&
                    bindingGeneration == requestedGeneration &&
                    hasUsableRichMessageViewport(width, height, isAttachedToWindow)
                ) {
                    val readyRequestId = ++nextReadyRequestId
                    expectedReadyRequestId = readyRequestId
                    evaluateJavascript(
                        "if (window.__ElecKoiMeasureReady) window.__ElecKoiMeasureReady('$readyRequestId'); else if (window.__ElecKoiMeasure) window.__ElecKoiMeasure();",
                        null,
                    )
                }
            }
        }
    }

    private fun publishHeight(
        callbackGeneration: Long,
        height: Float,
    ) {
        val current = shouldAcceptRichMessageBridgeCallback(
            activeGeneration = bindingGeneration,
            callbackGeneration = callbackGeneration,
        )
        val laidOut = hasUsableRichMessageViewport(width, this.height, isAttachedToWindow)
        val ready = acceptedReadyRequestId != null
        if (
            current &&
            laidOut &&
            ready &&
            height.isFinite() &&
            height > 0f
        ) {
            onHeightChanged(height)
        }
    }

    private fun publishReady(
        callbackGeneration: Long,
        readyRequestId: Long?,
        height: Float,
    ) {
        val current = shouldAcceptRichMessageBridgeCallback(
            activeGeneration = bindingGeneration,
            callbackGeneration = callbackGeneration,
        )
        val laidOut = hasUsableRichMessageViewport(width, this.height, isAttachedToWindow)
        val requested = readyRequestId != null && readyRequestId == expectedReadyRequestId
        if (
            !current ||
            !laidOut ||
            !requested ||
            !height.isFinite() ||
            height <= 0f
        ) return
        // Mark this WebView instance ready first so a caller holding a verified cached height can
        // accept this final measurement while still ignoring earlier provisional measurements.
        acceptedReadyRequestId = readyRequestId
        onContentReady()
        onHeightChanged(height)
    }

    private class RichMessageHeightBridge(
        private val webView: RichMessageWebView,
        private val bindingGeneration: Long,
    ) {
        @JavascriptInterface
        fun postMessage(value: String) {
            webView.post {
                value.toFloatOrNull()?.let { height ->
                    webView.publishHeight(bindingGeneration, height)
                }
            }
        }

        @JavascriptInterface
        fun postReady(value: String) {
            webView.post {
                parseRichMessageReadyPayload(value)?.let { payload ->
                    webView.publishReady(
                        callbackGeneration = bindingGeneration,
                        readyRequestId = payload.requestId,
                        height = payload.height,
                    )
                }
            }
        }
    }

    private fun openExternalUri(uri: Uri): Boolean {
        return context.openDesktopAlignedExternalUri(uri)
    }

    private fun blockedResourceResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        403,
        "Blocked",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )
}

internal fun shouldAcceptRichMessageHeight(
    hasVerifiedCachedHeight: Boolean,
    contentReady: Boolean,
): Boolean = !hasVerifiedCachedHeight || contentReady

internal fun shouldAcceptRichMessageBridgeCallback(
    activeGeneration: Long,
    callbackGeneration: Long,
): Boolean = activeGeneration == callbackGeneration

internal fun hasUsableRichMessageViewport(
    width: Int,
    height: Int,
    attached: Boolean,
): Boolean = attached && width > 0 && height > 0

internal data class RichMessageReadyPayload(
    val requestId: Long?,
    val height: Float,
)

internal fun parseRichMessageReadyPayload(value: String): RichMessageReadyPayload? {
    val separator = value.indexOf('|')
    if (separator < 0) {
        return value.toFloatOrNull()?.let { height ->
            RichMessageReadyPayload(requestId = null, height = height)
        }
    }
    val height = value.substring(separator + 1).toFloatOrNull() ?: return null
    return RichMessageReadyPayload(
        requestId = value.substring(0, separator).toLongOrNull(),
        height = height,
    )
}

private const val HeightBridgeName = "ElecKoiRichHost"
private const val AuthorRuntimePath = "/eleckoi-runtime/"
