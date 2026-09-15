package com.eleckoi.android.feature.chat.ui.immersive

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiPermission
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorApiRuntimeState
import com.eleckoi.android.sdk.author.AuthorFrontendSdk
import com.eleckoi.android.sdk.author.bridge.WebViewAuthorBridge
import com.eleckoi.android.feature.chat.ui.web.configureDesktopAlignedAuthorFrontend
import com.eleckoi.android.feature.chat.ui.web.installDesktopAlignedWindowOpenHandler
import com.eleckoi.android.feature.chat.ui.web.isDesktopAllowedAuthorFrontendResource
import com.eleckoi.android.feature.chat.ui.web.openDesktopAlignedExternalUri
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.security.AuthorFrontendStoragePrincipal
import com.eleckoi.android.engine.immersive.security.ImmersiveWebSecurity
import com.eleckoi.android.engine.immersive.security.ProjectPath
import com.eleckoi.android.engine.immersive.security.RuntimePath
import com.eleckoi.android.sdk.author.AuthorChatGateway
import java.io.ByteArrayInputStream
import java.io.File

@Composable
fun ImmersiveChatScreen(
    project: FrontendProject,
    projectDirectory: File,
    characterName: String,
    chatGateway: AuthorChatGateway,
    appearance: AppearanceTheme,
    storagePrincipal: AuthorFrontendStoragePrincipal,
    authorApiPermissions: Set<AuthorApiPermission> = AuthorApiPermission.previewLocalFull,
    onExit: () -> Unit,
    onFallbackToNative: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val context = LocalContext.current
    var loadError by remember(project.id) { mutableStateOf("") }
    var reloadKey by remember(project.id) { mutableIntStateOf(0) }
    val authorRuntimeHead = remember(context.applicationContext) {
        AuthorFrontendSdk.documentHead(context.applicationContext)
    }
    val isolatedHost = remember(storagePrincipal) {
        ImmersiveWebSecurity.isolatedHost(storagePrincipal)
    }
    val isolatedOrigin = remember(storagePrincipal) {
        ImmersiveWebSecurity.isolatedOrigin(storagePrincipal)
    }
    val runtime = remember(project.id, project.characterId, characterName) {
        AuthorApiRuntimeState(
            surface = "immersive_chat",
            characterId = project.characterId,
            characterName = characterName,
        )
    }
    val router = remember(runtime, chatGateway, authorApiPermissions) {
        AuthorApiRouter(
            AuthorApiEnvironment.forChat(
                appContext = context.applicationContext,
                runtime = runtime,
                gateway = chatGateway,
                permissions = authorApiPermissions,
            ),
        )
    }
    val bridge = remember(router, isolatedOrigin) {
        WebViewAuthorBridge(router = router, allowedOrigin = isolatedOrigin)
    }
    val assetLoader = remember(project.id, projectDirectory, isolatedHost, router) {
        WebViewAssetLoader.Builder()
            .setDomain(isolatedHost)
            .addPathHandler(ProjectPath) { requestedPath ->
                projectResponse(projectDirectory, requestedPath, authorRuntimeHead)
            }
            .addPathHandler(RuntimePath) { requestedPath ->
                AuthorFrontendSdk.runtimeResource(context.applicationContext, requestedPath)
                    ?: router.runtimeResource(requestedPath)
            }
            .build()
    }
    val configuration = LocalConfiguration.current
    val webView = remember(project.id, bridge, assetLoader) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.configureDesktopAlignedAuthorFrontend()
            installDesktopAlignedWindowOpenHandler { uri ->
                context.applicationContext.openDesktopAlignedExternalUri(uri)
            }
            bridge.install(this)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    if (ImmersiveWebSecurity.isAllowedLocalResource(request.url, isolatedHost)) {
                        return assetLoader.shouldInterceptRequest(request.url)
                            ?.withLocalSecurityHeaders()
                            ?: missingLocalResourceResponse()
                    }
                    if (isDesktopAllowedAuthorFrontendResource(request.url.scheme)) return null
                    return blockedResourceResponse()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    if (ImmersiveWebSecurity.isAllowedLocalResource(request.url, isolatedHost)) return false
                    return context.applicationContext.openDesktopAlignedExternalUri(request.url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        loadError = error.description?.toString().orEmpty().ifBlank { "前端页面加载失败" }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        loadError = "前端页面加载失败（HTTP ${errorResponse.statusCode}）"
                    }
                }
            }
        }
    }

    DisposableEffect(webView, bridge) {
        onDispose {
            bridge.destroy()
            webView.stopLoading()
            webView.destroy()
        }
    }

    androidx.compose.runtime.LaunchedEffect(project.id, isolatedOrigin, reloadKey) {
        loadError = ""
        val entry = project.entryFile.split('/').joinToString("/") { Uri.encode(it) }
        webView.loadUrl("$isolatedOrigin$ProjectPath$entry")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileBg),
    ) {
        AndroidView(
            factory = { webView },
            update = { view ->
                // Keep the existing WebView and let it resize in place on rotation.
                configuration.orientation
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loadError.isNotBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .background(appearance.mobileSurface, RoundedCornerShape(18.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("沉浸前端加载失败", color = appearance.mobileText, fontSize = 17.sp)
                Text(loadError, color = appearance.mobileMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuntimeAction("重试", appearance, onClick = { reloadKey += 1 })
                    RuntimeAction("使用原生界面", appearance, onClick = onFallbackToNative)
                }
            }
        }
    }
}

@Composable
private fun RuntimeAction(
    label: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = appearance.mobileBlue.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = appearance.mobileBlue,
            fontSize = 12.sp,
        )
    }
}

private fun projectResponse(
    rootDirectory: File,
    requestedPath: String,
    authorRuntimeHead: String,
): WebResourceResponse? {
    val root = rootDirectory.canonicalFile
    val decoded = Uri.decode(requestedPath).trimStart('/')
    val file = File(root, decoded).canonicalFile
    if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return null
    val extension = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
        "js" -> "application/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
    if (extension == "html" || extension == "htm") {
        val html = injectAuthorRuntime(file.readText(Charsets.UTF_8), authorRuntimeHead)
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
    }
    return WebResourceResponse(mime, "UTF-8", file.inputStream().buffered())
}

private fun injectAuthorRuntime(source: String, runtimeHead: String): String {
    val head = HtmlHeadOpen.find(source)
    if (head != null) {
        val insertion = head.range.last + 1
        return source.substring(0, insertion) + runtimeHead + source.substring(insertion)
    }
    val html = HtmlOpen.find(source)
    if (html != null) {
        val insertion = html.range.last + 1
        return source.substring(0, insertion) + "<head>$runtimeHead</head>" + source.substring(insertion)
    }
    return if (HtmlBodyOpen.containsMatchIn(source)) {
        "<!doctype html><html><head>$runtimeHead</head>$source</html>"
    } else {
        "<!doctype html><html><head>$runtimeHead</head><body>$source</body></html>"
    }
}

private fun WebResourceResponse.withLocalSecurityHeaders(): WebResourceResponse = apply {
    val existing = responseHeaders.orEmpty()
    responseHeaders = existing + mapOf(
        "Referrer-Policy" to "no-referrer",
        "X-Content-Type-Options" to "nosniff",
        "Permissions-Policy" to "camera=(), microphone=(), geolocation=()",
    )
}

private fun blockedResourceResponse(): WebResourceResponse = localErrorResponse(
    statusCode = 403,
    reason = "Forbidden",
    message = "External resources are disabled for author frontends.",
)

private fun missingLocalResourceResponse(): WebResourceResponse = localErrorResponse(
    statusCode = 404,
    reason = "Not Found",
    message = "Local frontend resource not found.",
)

private fun localErrorResponse(statusCode: Int, reason: String, message: String) = WebResourceResponse(
    "text/plain",
    "UTF-8",
    statusCode,
    reason,
    mapOf(
        "Cache-Control" to "no-store",
        "X-Content-Type-Options" to "nosniff",
    ),
    ByteArrayInputStream(message.toByteArray()),
)

private val HtmlHeadOpen = Regex("(?i)<head(?:\\s[^>]*)?>")
private val HtmlOpen = Regex("(?i)<html(?:\\s[^>]*)?>")
private val HtmlBodyOpen = Regex("(?i)<body(?:\\s[^>]*)?>")
