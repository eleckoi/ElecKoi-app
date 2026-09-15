package com.eleckoi.android.sdk.author.bridge

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorApiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets

class WebViewAuthorBridge(
    private val router: AuthorApiRouter,
    private val allowedOrigin: String,
    events: Flow<AuthorApiEvent>? = router.eventFlow(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestGate = AuthorBridgeRequestGate()
    private var installedWebView: WebView? = null
    private var activeReplyProxy: JavaScriptReplyProxy? = null

    init {
        events?.let { eventFlow ->
            scope.launch {
                eventFlow.collect(::emit)
            }
        }
    }

    fun install(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(
            webView,
            NativeObjectName,
            setOf(allowedOrigin),
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame || sourceOrigin != allowedOrigin.toUri()) return@addWebMessageListener
            val request = message.data ?: return@addWebMessageListener
            activeReplyProxy = replyProxy
            val rejection = requestGate.tryAcquire(request)
            if (rejection != null) {
                replyProxy.postMessage(rejectedRequestResponse(request, rejection))
                return@addWebMessageListener
            }
            scope.launch {
                try {
                    replyProxy.postMessage(router.route(request))
                } finally {
                    requestGate.release()
                }
            }
        }
        installedWebView = webView
        return true
    }

    fun emit(event: AuthorApiEvent) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        val message = buildJsonObject {
            put("type", "event")
            put("event", event.name)
            put("payload", event.payload)
        }.toString()
        activeReplyProxy?.postMessage(message)
    }

    fun destroy() {
        installedWebView?.let { webView ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.removeWebMessageListener(webView, NativeObjectName)
            }
        }
        installedWebView = null
        activeReplyProxy = null
        scope.cancel()
    }

    companion object {
        const val NativeObjectName = "ElecKoiNative"
    }
}

internal enum class AuthorBridgeRequestRejection(val code: String, val message: String) {
    RequestTooLarge("BRIDGE_REQUEST_TOO_LARGE", "作者 API 请求超过本地桥接大小限制"),
    TooManyInFlight("BRIDGE_BUSY", "作者 API 同时请求过多，请稍后重试"),
    RateLimited("BRIDGE_RATE_LIMITED", "作者 API 请求过于频繁，请稍后重试"),
}

internal class AuthorBridgeRequestGate(
    private val maxRequestBytes: Int = 32 * 1024 * 1024,
    private val maxInFlight: Int = 8,
    private val maxRequestsPerWindow: Int = 120,
    private val windowMillis: Long = 10_000L,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val acceptedAt = ArrayDeque<Long>()
    private var inFlight = 0

    @Synchronized
    fun tryAcquire(request: String): AuthorBridgeRequestRejection? {
        if (
            request.length > maxRequestBytes ||
            request.toByteArray(StandardCharsets.UTF_8).size > maxRequestBytes
        ) {
            return AuthorBridgeRequestRejection.RequestTooLarge
        }
        val now = clockMillis()
        while (acceptedAt.firstOrNull()?.let { now - it >= windowMillis } == true) {
            acceptedAt.removeFirst()
        }
        if (acceptedAt.size >= maxRequestsPerWindow) {
            return AuthorBridgeRequestRejection.RateLimited
        }
        if (inFlight >= maxInFlight) {
            return AuthorBridgeRequestRejection.TooManyInFlight
        }
        acceptedAt.addLast(now)
        inFlight += 1
        return null
    }

    @Synchronized
    fun release() {
        if (inFlight > 0) inFlight -= 1
    }
}

private fun rejectedRequestResponse(
    request: String,
    rejection: AuthorBridgeRequestRejection,
): String = buildJsonObject {
    put("id", BridgeRequestId.find(request.take(4096))?.groupValues?.get(1).orEmpty())
    put("ok", false)
    put("error", buildJsonObject {
        put("code", rejection.code)
        put("message", rejection.message)
    })
}.toString()

private val BridgeRequestId = Regex("""[\"']id[\"']\s*:\s*[\"']([A-Za-z0-9._:-]{1,128})[\"']""")
