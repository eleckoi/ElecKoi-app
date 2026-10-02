package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.ui.author.toAuthorSnapshot
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayRendererFailure
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayRendererFailureKind
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptOrigin
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatCallbacks
import com.eleckoi.android.feature.chat.ui.web.openDesktopAlignedExternalUri
import com.eleckoi.android.sdk.author.AuthorApiEnvironment
import com.eleckoi.android.sdk.author.AuthorApiRouter
import com.eleckoi.android.sdk.author.AuthorChatGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.json.JSONObject
import org.json.JSONTokener

internal class RoleplayTranscriptBridge(
    private val appContext: Context,
    private val messageProvider: (String) -> ChatMessage?,
    private val messageGatewayProvider: () -> AuthorChatGateway?,
    private val chatAuthorRouter: AuthorApiRouter,
    private val callbacksProvider: () -> RoleplayWebChatCallbacks,
    private val onReady: (Long, String) -> Unit,
    private val onTransactionCommitted: (Long, String) -> Unit,
    private val onTransactionRejected: (Long) -> Unit,
    private val onRichHeight: (String, Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var installed = false
    private var pageReplyProxy: JavaScriptReplyProxy? = null

    fun install(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(
            webView,
            NativeObjectName,
            setOf(RoleplayTranscriptOrigin),
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame || sourceOrigin != RoleplayTranscriptOrigin.toUri()) return@addWebMessageListener
            pageReplyProxy = replyProxy
            val raw = message.data ?: return@addWebMessageListener
            if (raw.toByteArray().size > MaxBridgeMessageBytes) return@addWebMessageListener
            val value = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
            when (value.optString("type")) {
                "ready" -> onReady(
                    value.optLong("transactionId"),
                    value.optString("sessionId"),
                )
                "rendererError" -> callbacksProvider().onRendererUnavailable(
                    RoleplayRendererFailure(
                        kind = RoleplayRendererFailureKind.JavaScript,
                        message = value.optString("message").ifBlank {
                            "页面脚本报告了未提供说明的渲染错误"
                        },
                        context = buildMap {
                            value.optString("phase")
                                .takeIf(String::isNotBlank)
                                ?.let { put("执行阶段", it) }
                            value.optString("sessionId")
                                .takeIf(String::isNotBlank)
                                ?.let { put("会话 ID", it) }
                            if (value.has("transactionId") && !value.isNull("transactionId")) {
                                put("事务 ID", value.optLong("transactionId").toString())
                            }
                        },
                        stackTrace = value.optString("stack"),
                        rawPayload = value.toString(2),
                    ),
                )
                "transactionCommitted" -> onTransactionCommitted(
                    value.optLong("transactionId"),
                    value.optString("sessionId"),
                )
                "transactionRejected" -> onTransactionRejected(value.optLong("transactionId"))
                "richHeight" -> onRichHeight(
                    value.optString("key"),
                    value.optInt("height"),
                )
                "messageRendered" -> value.optString("messageId")
                    .takeIf(String::isNotBlank)
                    ?.let(callbacksProvider().onMessageRendered)
                "scrollState" -> callbacksProvider().onScrollStateChanged(
                    value.optBoolean("browsingHistory"),
                    value.optBoolean("canScrollForward"),
                )
                "loadOlder" -> callbacksProvider().onLoadOlder()
                "opening" -> value.optString("optionId")
                    .takeIf(String::isNotBlank)
                    ?.let(callbacksProvider().onSelectOpeningOption)
                "openingJump" -> callbacksProvider().onRequestOpeningJump()
                "deleteSelect" -> value.optString("messageId")
                    .takeIf(String::isNotBlank)
                    ?.let(callbacksProvider().onSelectDeleteFrom)
                "userAvatar" -> callbacksProvider().onUserAvatarClick()
                "assistantAvatar" -> callbacksProvider().onAssistantAvatarClick()
                "messageAction" -> {
                    val source = messageProvider(value.optString("messageId")) ?: return@addWebMessageListener
                    callbacksProvider().onMessageAction(value.optString("action"), source)
                }
                "pluginAction" -> {
                    val owner = value.getString("pluginId")
                    val id = value.getString("id")
                    val entry = com.eleckoi.android.sdk.author.plugins.PluginUiRegistry.items.value.first {
                        it["pluginId"]?.toString() == JSONObject.quote(owner) && it["id"]?.toString() == JSONObject.quote(id)
                    }
                    if (entry["html"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) com.eleckoi.android.sdk.author.plugins.PluginUiRegistry.open(owner, id)
                    scope.launch {
                        checkNotNull(messageGatewayProvider()).invokeExtension("plugins.emitEvent", kotlinx.serialization.json.buildJsonObject {
                            put("pluginId", kotlinx.serialization.json.JsonPrimitive(owner))
                            put("event", kotlinx.serialization.json.JsonPrimitive("plugin:$owner:button:$id"))
                            put("payload", kotlinx.serialization.json.buildJsonObject { put("messageId", kotlinx.serialization.json.JsonPrimitive(value.getString("messageId"))) })
                        })
                    }
                }
                "imageAction" -> {
                    val source = messageProvider(value.optString("messageId")) ?: return@addWebMessageListener
                    val attachmentId = value.optString("attachmentId")
                    val attachment = source.imageAttachments.firstOrNull { it.id == attachmentId }
                        ?: return@addWebMessageListener
                    callbacksProvider().onImageAction(
                        value.optString("action"),
                        source,
                        attachment,
                    )
                }
                "openLink" -> openExternal(value.optString("url"))
                "author" -> {
                    val request = value.optString("request")
                    if (request.isBlank()) return@addWebMessageListener
                    val messageId = value.optString("messageId")
                    val source = messageProvider(messageId)
                    scope.launch {
                        val response = when {
                            messageId.isBlank() -> chatAuthorRouter.route(request)
                            source != null -> AuthorApiRouter(AuthorApiEnvironment.forInlineMessage(
                                appContext = appContext,
                                message = source.toAuthorSnapshot(),
                                messageGateway = messageGatewayProvider(),
                            )).route(request)
                            else -> JSONObject()
                                .put("id", runCatching { JSONObject(request).optString("id") }.getOrDefault(""))
                                .put("ok", false)
                                .put("error", JSONObject().put("code", "NOT_FOUND").put("message", "消息不存在：$messageId"))
                                .toString()
                        }
                        replyProxy.postMessage(
                            JSONObject()
                                .put("type", "authorResult")
                                .put("response", response)
                                .toString(),
                        )
                    }
                }
            }
        }
        installed = true
        messageGatewayProvider()?.let { gateway ->
            scope.launch {
                gateway.authorEvents.collect { event ->
                    val proxy = pageReplyProxy ?: return@collect
                    val payload = runCatching { JSONTokener(event.payload.toString()).nextValue() }
                        .getOrNull()
                    proxy.postMessage(
                        JSONObject()
                            .put("type", "authorEvent")
                            .put("event", event.name)
                            .put("payload", payload)
                            .toString(),
                    )
                }
            }
        }
        return true
    }

    fun postCommand(method: String, json: String): Boolean {
        val replyProxy = pageReplyProxy ?: return false
        val command = buildString(json.length + method.length + 64) {
            append("{\"type\":\"nativeCommand\",\"method\":")
            append(JSONObject.quote(method))
            append(",\"payload\":")
            append(json)
            append('}')
        }
        if (command.toByteArray(Charsets.UTF_8).size > MaxNativeCommandBytes) return false
        return runCatching { replyProxy.postMessage(command) }.isSuccess
    }

    fun resetPage() {
        pageReplyProxy = null
    }

    fun destroy(webView: WebView) {
        if (installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, NativeObjectName)
        }
        installed = false
        pageReplyProxy = null
        scope.cancel()
    }

    private fun openExternal(value: String) {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return
        appContext.openDesktopAlignedExternalUri(uri)
    }
}
