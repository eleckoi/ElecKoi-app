package com.eleckoi.android.feature.chat.ui.immersive

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiPermission
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorApiRuntimeState
import com.eleckoi.android.sdk.author.AuthorChatBackgroundSnapshot
import com.eleckoi.android.sdk.author.bridge.WebViewAuthorBridge
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.ui.layout.ChatBackground
import com.eleckoi.android.feature.chat.ui.layout.ChatBackdropSpec
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBackgroundChoiceForMode
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.security.AuthorFrontendStoragePrincipal
import com.eleckoi.android.engine.immersive.security.ImmersiveWebSecurity
import com.eleckoi.android.engine.immersive.security.MediaPath
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
    characterAvatarPath: String,
    chatGateway: AuthorChatGateway,
    appearance: AppearanceTheme,
    chatBackdropSpec: ChatBackdropSpec = ChatBackdropSpec(appearance),
    storagePrincipal: AuthorFrontendStoragePrincipal,
    authorApiPermissions: Set<AuthorApiPermission> = AuthorApiPermission.previewLocalFull,
    onExit: () -> Unit,
    onFallbackToNative: () -> Unit,
    onOpenChatBackgroundSettings: (() -> Unit)? = null,
) {
    BackHandler(onBack = onExit)
    val context = LocalContext.current
    val projectRevision = "${project.id}:${project.importedAt}"
    var loadError by remember(projectRevision) { mutableStateOf("") }
    var reloadKey by remember(projectRevision) { mutableIntStateOf(0) }
    val sdkSource = remember {
        context.assets.open("frontend/preview/eleckoi.js").bufferedReader().use { it.readText() }
    }
    val isolatedHost = remember(storagePrincipal) {
        ImmersiveWebSecurity.isolatedHost(storagePrincipal)
    }
    val isolatedOrigin = remember(storagePrincipal) {
        ImmersiveWebSecurity.isolatedOrigin(storagePrincipal)
    }
    val chatBackgroundFile = remember(chatBackdropSpec) {
        resolveChatBackgroundChoiceForMode(
            characterBackgroundPath = chatBackdropSpec.characterBackgroundPath,
            characterFile = chatBackdropSpec.characterBackgroundPath
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf(File::isFile),
            globalFile = chatBackdropSpec.appearance.textureImagePath
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf(File::isFile),
            defaultCharacterFile = chatBackdropSpec.defaultCharacterBackgroundPath
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf(File::isFile),
        ).file
    }
    val characterAvatarFile = remember(characterAvatarPath) {
        characterAvatarPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    }
    val characterAvatarUrl = characterAvatarFile?.let { "$isolatedOrigin${MediaPath}character-avatar" }.orEmpty()
    val chatBackgroundUrl = chatBackgroundFile?.let { "$isolatedOrigin${MediaPath}chat-background" }.orEmpty()
    val assetLoader = remember(
        projectRevision,
        projectDirectory,
        isolatedHost,
        characterAvatarFile,
        chatBackgroundFile,
    ) {
        WebViewAssetLoader.Builder()
            .setDomain(isolatedHost)
            .addPathHandler(ProjectPath) { requestedPath ->
                projectResponse(projectDirectory, requestedPath)
            }
            .addPathHandler(RuntimePath) { requestedPath ->
                if (requestedPath == "eleckoi.js") {
                    WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        ByteArrayInputStream(sdkSource.toByteArray()),
                    )
                } else null
            }
            .addPathHandler(MediaPath) { requestedPath ->
                when (requestedPath) {
                    "character-avatar" -> characterAvatarFile?.let(::localMediaResponse)
                    "chat-background" -> chatBackgroundFile?.let(::localMediaResponse)
                    else -> null
                }
            }
            .build()
    }
    val runtime = remember(
        projectRevision,
        project.characterId,
        characterName,
        characterAvatarUrl,
        chatBackgroundUrl,
        chatBackdropSpec,
    ) {
        AuthorApiRuntimeState(
            surface = "immersive_chat",
            characterId = project.characterId,
            characterName = characterName,
            characterAvatarUrl = characterAvatarUrl,
        ).apply {
            chatBackground = AuthorChatBackgroundSnapshot(
                mode = chatBackgroundMode(chatBackdropSpec.characterBackgroundPath),
                imageUrl = chatBackgroundUrl,
                opacity = chatBackdropSpec.characterBackgroundOpacity.toDouble(),
                blur = chatBackdropSpec.characterBackgroundBlur.toDouble(),
                scrim = chatBackdropSpec.characterBackgroundScrim.toDouble(),
                hasImage = chatBackgroundFile != null,
            )
        }
    }
    runtime.openChatBackgroundSettings = onOpenChatBackgroundSettings
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
    val configuration = LocalConfiguration.current
    val webView = remember(projectRevision, bridge, assetLoader) {
        WebView(context).apply {
            // AndroidView may otherwise attach this child with WRAP_CONTENT. Chromium maps that
            // layout-param mode to force-zero-layout-height, which makes CSS viewport-height
            // units resolve to zero even when the native view is visibly full-screen.
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            AuthorFrontendServiceWorkerBlocker.install()
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            bridge.install(this)
            val injectedAtDocumentStart = if (
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) {
                runCatching {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        sdkSource,
                        setOf(isolatedOrigin),
                    )
                }.isSuccess
            } else false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse {
                    if (!ImmersiveWebSecurity.isAllowedLocalResource(request.url, isolatedHost)) {
                        return blockedResourceResponse()
                    }
                    return assetLoader.shouldInterceptRequest(request.url)
                        ?.withLocalSecurityHeaders()
                        ?: missingLocalResourceResponse()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !ImmersiveWebSecurity.isAllowedLocalResource(request.url, isolatedHost)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!injectedAtDocumentStart) view.evaluateJavascript(sdkSource, null)
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

    androidx.compose.runtime.LaunchedEffect(projectRevision, isolatedOrigin, reloadKey) {
        loadError = ""
        val entry = project.entryFile.split('/').joinToString("/") { Uri.encode(it) }
        webView.loadUrl("$isolatedOrigin$ProjectPath$entry")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileBg),
    ) {
        ChatBackground(
            spec = chatBackdropSpec,
            modifier = Modifier.fillMaxSize(),
        )
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

private fun projectResponse(rootDirectory: File, requestedPath: String): WebResourceResponse? {
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
    return WebResourceResponse(mime, "UTF-8", file.inputStream().buffered())
}

private fun localMediaResponse(file: File): WebResourceResponse {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: when (file.extension.lowercase()) {
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    return WebResourceResponse(mime, null, file.inputStream().buffered())
}

private fun chatBackgroundMode(path: String): String = when {
    path.isBlank() -> "automatic"
    path == com.eleckoi.android.feature.characters.model.AppDefaultChatBackground -> "app_default"
    path == com.eleckoi.android.feature.characters.model.CustomChatBackground -> "custom"
    path == com.eleckoi.android.feature.characters.model.GlobalChatBackground -> "global"
    else -> "character"
}

private fun WebResourceResponse.withLocalSecurityHeaders(): WebResourceResponse = apply {
    val existing = responseHeaders.orEmpty()
    responseHeaders = existing + mapOf(
        "Cache-Control" to "no-store",
        "Content-Security-Policy" to LocalOnlyContentSecurityPolicy,
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
        "Content-Security-Policy" to LocalOnlyContentSecurityPolicy,
        "X-Content-Type-Options" to "nosniff",
    ),
    ByteArrayInputStream(message.toByteArray()),
)

private object AuthorFrontendServiceWorkerBlocker {
    @Volatile
    private var installed = false

    @Synchronized
    fun install() {
        if (installed || !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
        val controller = ServiceWorkerControllerCompat.getInstance()
        val settings = controller.serviceWorkerWebSettings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS)) {
            settings.allowFileAccess = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)) {
            settings.allowContentAccess = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)) {
            settings.blockNetworkLoads = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            controller.setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse =
                    blockedResourceResponse()
            })
        }
        installed = true
    }
}

private const val LocalOnlyContentSecurityPolicy =
    "default-src 'self' data: blob:; " +
        "connect-src 'self'; " +
        "img-src 'self' data: blob:; " +
        "media-src 'self' data: blob:; " +
        "font-src 'self' data:; " +
        "style-src 'self' 'unsafe-inline'; " +
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
        "worker-src 'none'; frame-src 'none'; object-src 'none'; " +
        "base-uri 'self'; form-action 'self'"
