package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.eleckoi.android.feature.chat.ui.web.configureDesktopAlignedAuthorFrontend
import com.eleckoi.android.feature.chat.ui.web.installDesktopAlignedWindowOpenHandler
import com.eleckoi.android.feature.chat.ui.web.isDesktopAllowedAuthorFrontendResource
import com.eleckoi.android.feature.chat.ui.web.openDesktopAlignedExternalUri
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.buildRoleplayTranscriptDocument
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptAssetPath
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptMediaPath
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptOrigin
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.toBootstrapJson
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatCallbacks
import com.eleckoi.android.sdk.author.AuthorFrontendSdk
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorApiRuntimeState
import com.eleckoi.android.sdk.author.AuthorChatGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

internal class RoleplayWebChatHost(
    context: Context,
    initialCallbacks: RoleplayWebChatCallbacks,
    messageGateway: AuthorChatGateway,
) {
    private val appContext = context.applicationContext
    private var callbacks = initialCallbacks
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestModel: RoleplayTranscriptModel? = null
    private val transactions = RoleplayContentTransactionCoordinator()
    private var pageReady = false
    private var released = false
    private var ingressSequence = 0L
    private var flushScheduled = false
    private var updatesPaused = false
    private var media = emptyMap<String, File>()
    private var pendingPresentationReady: Pair<Long, String>? = null
    private var visualStateRequestId = 0L
    private var richHeightLoadJob: Job? = null
    private var richHeightLoadingSessionId = ""
    private var richHeightReadySessionId = ""
    private var restoredRichHeights = JSONObject()
    private val authorRuntimeRouter = AuthorApiRouter(
        AuthorApiEnvironment.forChat(
            appContext = appContext,
            runtime = AuthorApiRuntimeState(
                surface = "chat-ui",
                characterId = messageGateway.snapshot().draft?.session?.characterId.orEmpty(),
                characterName = messageGateway.snapshot().draft?.session?.characterName.orEmpty(),
            ),
            gateway = messageGateway,
        ),
    )
    private val bridge = RoleplayTranscriptBridge(
        appContext = appContext,
        messageProvider = { id -> latestModel?.messages?.firstOrNull { it.source.id == id }?.source },
        messageGatewayProvider = { messageGateway },
        callbacksProvider = { callbacks },
        onReady = ::onPresentationReady,
        onTransactionCommitted = ::onTransactionCommitted,
        onTransactionRejected = ::onTransactionRejected,
        onRichHeight = ::onRichHeight,
    )
    private val document = buildRoleplayTranscriptDocument(
        authorSdkSource = AuthorFrontendSdk.source(appContext),
        authorLibrariesHead = AuthorFrontendSdk.librariesHead(),
    )

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        overScrollMode = WebView.OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
        settings.apply {
            configureDesktopAlignedAuthorFrontend()
            textZoom = 100
            offscreenPreRaster = true
        }
        installDesktopAlignedWindowOpenHandler { uri ->
            appContext.openDesktopAlignedExternalUri(uri)
        }
        val bridgeAvailable = bridge.install(this)
        if (!bridgeAvailable) {
            post { callbacks.onRendererUnavailable() }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val uri = request.url
                if (
                    uri.scheme == "https" &&
                    uri.host == RoleplayTranscriptOrigin.toUri().host &&
                    uri.path?.startsWith(AuthorRuntimePath) == true
                ) {
                    val runtimePath = uri.path.orEmpty().removePrefix(AuthorRuntimePath)
                    return AuthorFrontendSdk.runtimeResource(appContext, runtimePath)
                        ?: authorRuntimeRouter.runtimeResource(runtimePath)
                        ?: missingMediaResponse()
                }
                if (
                    uri.scheme == "https" &&
                    uri.host == RoleplayTranscriptOrigin.toUri().host &&
                    uri.path?.startsWith(RoleplayTranscriptMediaPath) == true
                ) {
                    val token = Uri.decode(uri.path.orEmpty().removePrefix(RoleplayTranscriptMediaPath))
                    return media[token]?.let(::mediaResponse) ?: missingMediaResponse()
                }
                if (
                    uri.scheme == "https" &&
                    uri.host == RoleplayTranscriptOrigin.toUri().host &&
                    uri.path?.startsWith(RoleplayTranscriptAssetPath) == true
                ) {
                    val assetName = Uri.decode(
                        uri.path.orEmpty().removePrefix(RoleplayTranscriptAssetPath),
                    )
                    return transcriptAssetResponse(assetName)
                }
                // Leave authored iframe networking to the browser so declared fonts, styles,
                // images, scripts, and fetch requests are not replaced by a synthetic 403.
                if (isDesktopAllowedAuthorFrontendResource(uri.scheme)) return null
                return blockedResourceResponse()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                if (
                    shouldKeepRoleplayNavigationInWebView(
                        isForMainFrame = request.isForMainFrame,
                        url = uri.toString(),
                    )
                ) {
                    return false
                }
                return openExternalUri(uri)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!url.startsWith(RoleplayTranscriptOrigin)) return
                bridge.resetPage()
                transactions.resetPage()
                pendingPresentationReady = null
                visualStateRequestId += 1
                pageReady = true
                if (!updatesPaused) {
                    latestModel?.let { model ->
                        if (ensureRichHeightsReady(model.sessionId)) submitFull(model)
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                pageReady = false
                bridge.resetPage()
                pendingPresentationReady = null
                visualStateRequestId += 1
                callbacks.onRendererUnavailable()
                return true
            }
        }
        loadDataWithBaseURL(
            "$RoleplayTranscriptOrigin/",
            document,
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun updateCallbacks(next: RoleplayWebChatCallbacks) {
        callbacks = next
    }

    fun setUpdatesPaused(paused: Boolean) {
        if (updatesPaused == paused) return
        updatesPaused = paused
        if (!paused) scheduleFlush()
    }

    fun bind(model: RoleplayTranscriptModel) {
        if (released) return
        latestModel = model
        transactions.offer(model)
        media = model.media
        val richHeightsReady = ensureRichHeightsReady(model.sessionId)
        if (!pageReady || updatesPaused || !richHeightsReady) return
        if (flushScheduled) return
        flushScheduled = true
        webView.postOnAnimation {
            flushScheduled = false
            latestModel?.let(::flush)
        }
    }

    private fun flush(model: RoleplayTranscriptModel) {
        if (released || !pageReady || updatesPaused || transactions.hasInFlight) return
        val baseline = transactions.baseline
        if (baseline == null || baseline.sessionId != model.sessionId) {
            submitFull(model)
            return
        }
        RoleplayTranscriptPatchPlanner.plan(baseline, model)?.let { patch ->
            submitTransaction("applyPatch", patch, model)
        }
    }

    fun scrollToBottom() {
        if (pageReady && !released) {
            evaluate("scrollToEnd", "{}")
        }
    }

    fun release() {
        if (released) return
        released = true
        richHeightLoadJob?.cancel()
        hostScope.cancel()
        pendingPresentationReady = null
        visualStateRequestId += 1
        bridge.destroy(webView)
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    private fun submitFull(model: RoleplayTranscriptModel) {
        if (richHeightReadySessionId != model.sessionId) {
            ensureRichHeightsReady(model.sessionId)
            return
        }
        submitTransaction(
            "applyFull",
            JSONObject(model.toBootstrapJson()).put(
                "richHeights",
                restoredRichHeights,
            ),
            model,
        )
    }

    private fun ensureRichHeightsReady(sessionId: String): Boolean {
        if (sessionId.isBlank()) return true
        if (richHeightReadySessionId == sessionId) return true
        if (richHeightLoadingSessionId == sessionId && richHeightLoadJob?.isActive == true) return false
        richHeightLoadJob?.cancel()
        richHeightLoadingSessionId = sessionId
        richHeightReadySessionId = ""
        restoredRichHeights = JSONObject()
        richHeightLoadJob = hostScope.launch {
            val restored = RoleplayRichHeightCache.restoreSession(appContext, sessionId)
            if (released || latestModel?.sessionId != sessionId) return@launch
            restoredRichHeights = restored
            richHeightLoadingSessionId = ""
            richHeightReadySessionId = sessionId
            if (pageReady && !updatesPaused) scheduleFlush()
        }
        return false
    }

    private fun onRichHeight(key: String, heightPx: Int) {
        RoleplayRichHeightCache.putPersistent(appContext, key, heightPx)
        val sessionId = richHeightReadySessionId.takeIf(String::isNotBlank) ?: return
        if (!key.startsWith("$sessionId\u001f")) return
        val verified = RoleplayRichHeightCache.snapshotJson(sessionId).optInt(key)
        if (verified > 0) restoredRichHeights.put(key, verified)
    }

    private fun submitTransaction(
        method: String,
        payload: JSONObject,
        model: RoleplayTranscriptModel,
    ) {
        val transaction = transactions.begin(model) ?: return
        payload
            .put("transactionId", transaction.id)
            .put("baseTransactionId", transaction.baseId)
            .put("sessionId", transaction.sessionId)
        evaluate(method, payload.toString())
    }

    private fun onTransactionCommitted(id: Long, sessionId: String) {
        val commit = transactions.acknowledge(id, sessionId) ?: return
        publishPresentationIfCommitted()
        if (commit.hasNewerCandidate) scheduleFlush()
    }

    private fun onPresentationReady(id: Long, sessionId: String) {
        if (released) return
        pendingPresentationReady = id to sessionId
        publishPresentationIfCommitted()
    }

    private fun publishPresentationIfCommitted() {
        val (transactionId, sessionId) = pendingPresentationReady ?: return
        if (!transactions.acceptsPresentation(transactionId, sessionId)) return
        pendingPresentationReady = null
        val requestId = ++visualStateRequestId
        webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
            override fun onComplete(completedRequestId: Long) {
                if (released || completedRequestId != visualStateRequestId) return
                if (!transactions.acceptsPresentation(transactionId, sessionId)) return
                callbacks.onReady()
            }
        })
    }

    private fun onTransactionRejected(id: Long) {
        if (!transactions.reject(id)) return
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (released || !pageReady || updatesPaused || flushScheduled) return
        val model = latestModel ?: return
        if (!ensureRichHeightsReady(model.sessionId)) return
        flushScheduled = true
        webView.postOnAnimation {
            flushScheduled = false
            latestModel?.let(::flush)
        }
    }

    private fun evaluate(method: String, json: String) {
        if (bridge.postCommand(method, json)) return
        val token = "native-${++ingressSequence}"
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        webView.evaluateJavascript(
            "window.__ElecKoiTranscriptIngress && window.__ElecKoiTranscriptIngress.start(${JSONObject.quote(token)});",
            null,
        )
        encoded.chunked(IngressChunkCharacters).forEach { chunk ->
            webView.evaluateJavascript(
                "window.__ElecKoiTranscriptIngress && window.__ElecKoiTranscriptIngress.append(${JSONObject.quote(token)},${JSONObject.quote(chunk)});",
                null,
            )
        }
        webView.evaluateJavascript(
            "window.__ElecKoiTranscriptIngress && window.__ElecKoiTranscriptIngress.commit(${JSONObject.quote(token)},${JSONObject.quote(method)});",
            null,
        )
    }

    private fun mediaResponse(file: File): WebResourceResponse {
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        return runCatching {
            WebResourceResponse(mime, null, FileInputStream(file))
        }.getOrElse { missingMediaResponse() }
    }

    private fun transcriptAssetResponse(assetName: String): WebResourceResponse {
        val asset = TranscriptAssets[assetName] ?: return missingMediaResponse()
        return runCatching {
            WebResourceResponse(
                asset.mimeType,
                asset.encoding,
                appContext.assets.open(asset.path),
            )
        }.getOrElse { missingMediaResponse() }
    }

    private fun openExternalUri(uri: Uri): Boolean {
        return appContext.openDesktopAlignedExternalUri(uri)
    }
}

private const val AuthorRuntimePath = "/eleckoi-runtime/"

internal fun shouldKeepRoleplayNavigationInWebView(
    isForMainFrame: Boolean,
    url: String,
): Boolean = !isForMainFrame || url.startsWith(RoleplayTranscriptOrigin)
