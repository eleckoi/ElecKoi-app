package com.eleckoi.android.feature.chat.ui.roleplay.web.model

internal enum class RoleplayRendererFailureKind(
    val code: String,
    val displayName: String,
) {
    BridgeUnavailable(
        code = "bridge_unavailable",
        displayName = "WebView 消息桥接不可用",
    ),
    PageLoad(
        code = "page_load_failed",
        displayName = "WebView 页面加载失败",
    ),
    JavaScript(
        code = "javascript_error",
        displayName = "页面脚本执行失败",
    ),
    RenderProcessGone(
        code = "render_process_gone",
        displayName = "WebView 渲染进程已退出",
    ),
}

internal data class RoleplayRendererFailure(
    val kind: RoleplayRendererFailureKind,
    val message: String,
    val context: Map<String, String> = emptyMap(),
    val stackTrace: String = "",
    val rawPayload: String = "",
) {
    val displayMessage: String
        get() = message.ifBlank { kind.displayName }

    fun copyDetails(): String = buildString {
        appendLine("ElecKoi 聊天渲染失败")
        appendLine("错误类型：${kind.displayName}")
        appendLine("错误代码：${kind.code}")
        appendLine("错误信息：$displayMessage")
        context.forEach { (label, value) ->
            if (value.isNotBlank()) appendLine("$label：$value")
        }
        if (stackTrace.isNotBlank()) {
            appendLine()
            appendLine("堆栈：")
            appendLine(stackTrace)
        }
        if (rawPayload.isNotBlank()) {
            appendLine()
            appendLine("原始错误：")
            appendLine(rawPayload)
        }
    }.trimEnd()
}
