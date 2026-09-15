package com.eleckoi.android.feature.chat.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

internal val AuthorFrontendInlineResourceSchemes = setOf("data", "blob", "about")
internal val AuthorFrontendNetworkResourceSchemes = setOf("https", "http")

@SuppressLint("SetJavaScriptEnabled")
internal fun WebSettings.configureDesktopAlignedAuthorFrontend(
    zoomEnabled: Boolean = false,
) {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    javaScriptCanOpenWindowsAutomatically = true
    setSupportMultipleWindows(true)
    setSupportZoom(zoomEnabled)
    builtInZoomControls = false
    displayZoomControls = false
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    mediaPlaybackRequiresUserGesture = false
    safeBrowsingEnabled = false
    cacheMode = WebSettings.LOAD_DEFAULT
}

internal fun isDesktopAllowedAuthorFrontendResource(scheme: String?): Boolean =
    scheme in AuthorFrontendInlineResourceSchemes || scheme in AuthorFrontendNetworkResourceSchemes

internal fun isDesktopAllowedExternalNavigation(scheme: String?): Boolean = scheme == "https"

internal fun Context.openDesktopAlignedExternalUri(uri: Uri): Boolean {
    if (!isDesktopAllowedExternalNavigation(uri.scheme)) return true
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    return true
}

/**
 * PC denies the requested child window and hands HTTPS URLs to the operating system. WebView
 * needs a temporary child transport to observe the requested URL, so mirror that behavior without
 * keeping a second in-app browser alive.
 */
internal fun WebView.installDesktopAlignedWindowOpenHandler(
    openExternal: (Uri) -> Boolean,
) {
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(view.context)
            var closed = false

            fun closePopup() {
                if (closed) return
                closed = true
                popup.stopLoading()
                popup.destroy()
            }

            fun handle(url: String): Boolean {
                runCatching { Uri.parse(url) }.getOrNull()?.let(openExternal)
                closePopup()
                return true
            }

            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = handle(request.url.toString())

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    handle(url)
            }
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }
}
