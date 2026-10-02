package com.eleckoi.android.sdk.author.plugins

import android.content.Context
import android.view.ViewGroup
import android.view.View
import android.webkit.*
import com.eleckoi.android.sdk.author.*
import com.eleckoi.android.sdk.author.bridge.WebViewAuthorBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/** Plugin documents live outside message renderers and survive message DOM replacement. */
class AuthorPluginRuntime(private val context: Context, private val store: PluginStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pages = mutableMapOf<String, Pair<WebView, WebViewAuthorBridge>>()
    private val hooks = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val ready = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    val errors = ConcurrentHashMap<String, String>()
    val diagnostics = ConcurrentHashMap<String, String>()
    private val binding = MutableStateFlow<AuthorChatGateway?>(null)
    private var restoration: Deferred<Unit>? = null
    var gateway: AuthorChatGateway? = null
        private set

    fun attach(value: AuthorChatGateway) {
        gateway = value
        binding.value = value
        if (restoration != null) return
        restoration = scope.async {
            while (gateway?.snapshot()?.draft == null) delay(50)
            val plugins = withContext(Dispatchers.IO) { store.list("plugins") }
            plugins.forEach { (id, entry) -> if (entry.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull != false) start(id, entry.jsonObject) }
        }
    }

    fun detach(value: AuthorChatGateway) {
        // App-owned scripts keep the last chat context when its presentation is closed.
        // The next attachment replaces it without restarting entry scripts.
    }

    fun start(id: String, manifest: JsonObject) {
        stop(id)
        val delegate = LivePluginGateway(binding) { checkNotNull(gateway) { "插件没有连接聊天宿主" } }
        ready[id] = CompletableDeferred()
        errors.remove(id)
        diagnostics[id] = "创建 WebView"
        val environment = AuthorApiEnvironment.forChat(context, AuthorApiRuntimeState("plugin", "", ""), delegate, AuthorApiPermission.previewLocalFull)
        val bridge = WebViewAuthorBridge(AuthorApiRouter(environment), Origin)
        // JSON encoding also escapes opening tags: an HTML panel inside a JS template literal
        // must not put the outer HTML parser into its nested script-token state.
        val source = JsonPrimitive(manifest.getValue("source").jsonPrimitive.content + "\n//# sourceURL=plugin/$id/entry.js").toString().replace("<", "\\u003c")
        val document = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><script>window.__ElecKoiPluginId=${JsonPrimitive(id)};</script>" +
            AuthorFrontendSdk.documentHead(context) + "</head><body><script>ElecKoi.ready.then(()=>new (Object.getPrototypeOf(async function(){}).constructor)($source).call(window)).then(()=>ElecKoi.call('plugins.runtimeReady',{pluginId:${JsonPrimitive(id)}})).catch(error=>{console.error(error);ElecKoi.call('plugins.hookResult',{error:String(error),pluginId:${JsonPrimitive(id)}});});</script></body></html>"
        val entryUrl = "$Origin/plugin/$id/index.html"
        val view = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            val display = context.resources.displayMetrics
            measure(View.MeasureSpec.makeMeasureSpec(display.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(display.heightPixels, View.MeasureSpec.EXACTLY))
            layout(0, 0, display.widthPixels, display.heightPixels)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    diagnostics[id] = "加载 $url"
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    diagnostics[id] = "加载失败 ${request.url}：${error.description}"
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.toString() == entryUrl) return WebResourceResponse("text/html", "UTF-8", document.byteInputStream())
                    if (request.url.path == "/favicon.ico") return WebResourceResponse("image/x-icon", null, ByteArray(0).inputStream())
                    return AuthorFrontendSdk.runtimeResource(context, request.url.path.orEmpty().removePrefix("/eleckoi-runtime/"))
                        ?: store.resource(id, request.url.path.orEmpty().removePrefix("/plugin/$id/"))
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        diagnostics[id] = "${message.sourceId()}:${message.lineNumber()} ${message.message()}"
                        errors[id] = diagnostics.getValue(id)
                    }
                    android.util.Log.println(if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) android.util.Log.ERROR else android.util.Log.INFO,
                        "Plugin:$id", "${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                    return true
                }
            }
        }
        check(bridge.install(view)) { "当前 Android WebView 缺少消息桥能力" }
        pages[id] = view to bridge
        view.loadUrl(entryUrl)
    }

    fun stop(id: String) {
        pages.remove(id)?.let { (view, bridge) -> bridge.destroy(); view.destroy() }
        ready.remove(id)?.cancel()
        diagnostics.remove(id)
        hooks.filterKeys { it.startsWith("$id:") }.forEach { (key, deferred) ->
            hooks.remove(key); deferred.completeExceptionally(IllegalStateException("插件 $id 已停止"))
        }
        PluginUiRegistry.removeOwner(id)
        com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline.remove(id)
    }

    suspend fun beforeGeneration(conversationId: String, purpose: String) = withContext(Dispatchers.Main.immediate) {
        try {
            withTimeout(30_000L) { restoration?.await(); ready.values.toList().forEach { it.await() } }
        } catch (error: TimeoutCancellationException) {
            throw IllegalStateException("插件启动超时：$diagnostics", error)
        }
        // Await every listener, including its queued storage writes, before the native prompt freezes.
        val tokens = mutableListOf<String>()
        try {
            val completions = pages.toList().map { (id, pair) ->
                val token = "$id:${java.util.UUID.randomUUID()}"
                tokens += token
                val completion = CompletableDeferred<Unit>()
                hooks[token] = completion
                val payload = buildJsonObject { put("token", token); put("conversationId", conversationId); put("purpose", purpose) }
                pair.first.evaluateJavascript("window.__ElecKoiBeforeGeneration($payload)", null)
                completion
            }
            withTimeout(30_000L) { completions.forEach { it.await() } }
        } finally {
            tokens.forEach { hooks.remove(it)?.cancel() }
        }
    }

    suspend fun flushAndStop(id: String) = withContext(Dispatchers.Main.immediate) {
        val view = pages[id]?.first ?: return@withContext
        if (ready[id]?.isCompleted == true && !ready.getValue(id).isCancelled) {
            val token = "$id:${java.util.UUID.randomUUID()}"
            val completion = CompletableDeferred<Unit>()
            hooks[token] = completion
            try {
                view.evaluateJavascript("window.__ElecKoiFlush(${JsonPrimitive(token)})", null)
                withTimeout(30_000L) { completion.await() }
            } finally { hooks.remove(token) }
        }
        stop(id)
    }

    fun runtimeReady(pluginId: String) {
        checkNotNull(ready[pluginId]) { "插件未启动：$pluginId" }.complete(Unit)
        diagnostics[pluginId] = "运行中"
    }

    fun completeHook(params: JsonObject) {
        val token = params["token"]?.jsonPrimitive?.contentOrNull
        val error = params["error"]?.jsonPrimitive?.contentOrNull
        if (token == null) {
            if (error != null) {
                val id = params.getValue("pluginId").jsonPrimitive.content
                errors[id] = error
                ready[id]?.completeExceptionally(IllegalStateException("插件 $id 启动失败：$error"))
            }
            return
        }
        val completion = checkNotNull(hooks.remove(token)) { "插件钩子已失效：$token" }
        if (error != null) completion.completeExceptionally(IllegalStateException(error)) else completion.complete(Unit)
    }

    companion object { const val Origin = "https://eleckoi-plugin.local" }
}

/** Descriptors are owned by installed scripts; presentation can be replaced independently. */
object PluginUiRegistry {
    val managerOpen = MutableStateFlow(false)
    val items = MutableStateFlow<List<JsonObject>>(emptyList())
    val opened = MutableStateFlow<JsonObject?>(null)
    @Synchronized fun register(owner: String, value: JsonObject) {
        val descriptor = JsonObject(value + ("pluginId" to JsonPrimitive(owner)))
        items.value = items.value.filterNot { it["id"] == value["id"] && it["pluginId"] == JsonPrimitive(owner) } + descriptor
    }
    @Synchronized fun unregister(owner: String, id: String) {
        items.value = items.value.filterNot { it["id"]?.jsonPrimitive?.content == id && it["pluginId"]?.jsonPrimitive?.content == owner }
        if (opened.value?.get("id")?.jsonPrimitive?.content == id && opened.value?.get("pluginId")?.jsonPrimitive?.content == owner) {
            opened.value = null
        }
    }
    fun removeOwner(owner: String) {
        items.value = items.value.filterNot { it["pluginId"]?.jsonPrimitive?.content == owner }
        if (opened.value?.get("pluginId")?.jsonPrimitive?.content == owner) opened.value = null
    }
    fun open(owner: String, id: String) { opened.value = items.value.first { it["id"]?.jsonPrimitive?.content == id && it["pluginId"]?.jsonPrimitive?.content == owner } }
}
