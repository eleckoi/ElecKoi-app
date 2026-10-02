package com.eleckoi.android.sdk.author.plugins

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eleckoi.android.sdk.author.*
import com.eleckoi.android.sdk.author.bridge.WebViewAuthorBridge
import kotlinx.serialization.json.JsonPrimitive

/** The dialog and device tests use this same local document and resource loader. */
class AuthorPluginPanelWebView(
    context: Context,
    gateway: AuthorChatGateway,
    owner: String,
    html: String,
    onError: (String) -> Unit = {},
) {
    private val store = PluginStore(context)
    private val environment = AuthorApiEnvironment.forChat(
        context, AuthorApiRuntimeState("plugin-panel", "", ""), gateway, AuthorApiPermission.previewLocalFull,
    )
    private val bridge = WebViewAuthorBridge(AuthorApiRouter(environment), AuthorPluginRuntime.Origin)
    private val resourcePrefix = "/panel/$owner/"
    private val entryUrl = "${AuthorPluginRuntime.Origin}${resourcePrefix}index.html"
    private val document = run {
        val head = "<script>window.__ElecKoiPluginId=${JsonPrimitive(owner).toString().replace("<", "\\u003c")};</script>" + AuthorFrontendSdk.documentHead(context)
        val match = Regex("(?i)<head[^>]*>").find(html)
        if (match != null) html.substring(0, match.range.last + 1) + head + html.substring(match.range.last + 1)
        else "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">$head</head><body>$html</body></html>"
    }
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.toString() == entryUrl) return WebResourceResponse("text/html", "UTF-8", document.byteInputStream())
                val path = request.url.path.orEmpty()
                if (path == "/favicon.ico") return WebResourceResponse("image/x-icon", null, ByteArray(0).inputStream())
                return AuthorFrontendSdk.runtimeResource(context, path.removePrefix("/eleckoi-runtime/"))
                    ?: if (path.startsWith(resourcePrefix)) store.resource(owner, path.removePrefix(resourcePrefix)) else null
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                val detail = "插件页面加载失败 ${request.url}：${error.description}"
                Log.e("PluginPanel:$owner", detail)
                onError(detail)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val detail = "${message.sourceId()}:${message.lineNumber()} ${message.message()}"
                val failed = message.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                Log.println(if (failed) Log.ERROR else Log.INFO, "PluginPanel:$owner", detail)
                if (failed) onError(detail)
                return true
            }
        }
        check(bridge.install(this)) { "WebView 消息桥不可用" }
        // loadDataWithBaseURL can leave this AndroidView at an empty about:blank document.
        // Serve the actual main document through the same local URL as its relative resources.
        loadUrl(entryUrl)
    }

    fun close() {
        bridge.destroy()
        webView.destroy()
    }
}
