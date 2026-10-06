package com.eleckoi.android.sdk.author.plugins

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A save request stays pending while the user chooses a document location. */
class PluginTextFileRequest internal constructor(
    val name: String,
    val mimeType: String,
    val text: String,
) {
    internal val result = CompletableDeferred<JsonObject>()
}

object PluginFileExport {
    private val pending = MutableStateFlow<PluginTextFileRequest?>(null)
    val request = pending.asStateFlow()
    @Volatile var available = false
        private set

    fun attach() { available = true }
    fun detach() {
        available = false
        pending.value?.let { fail(it, IllegalStateException("文件保存页面已关闭")) }
    }

    suspend fun saveText(name: String, mimeType: String, text: String): JsonObject {
        check(available) { "文件保存界面尚未就绪" }
        require(name.isNotBlank()) { "文件名不能为空" }
        require(mimeType.contains('/')) { "无效的文件 MIME 类型：$mimeType" }
        val request = PluginTextFileRequest(name, mimeType, text)
        check(pending.compareAndSet(null, request)) { "已有文件正在保存，请先完成或取消" }
        try { return request.result.await() }
        finally { pending.compareAndSet(request, null) }
    }

    suspend fun finish(request: PluginTextFileRequest, resolver: ContentResolver, uri: Uri?) {
        if (uri == null) {
            request.result.complete(buildJsonObject { put("saved", false); put("cancelled", true) })
            return
        }
        try {
            val bytes = withContext(Dispatchers.IO) {
                val bytes = request.text.toByteArray(Charsets.UTF_8)
                checkNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开目标文件：$uri" }.use { it.write(bytes) }
                bytes.size
            }
            request.result.complete(buildJsonObject {
                put("saved", true); put("name", request.name); put("uri", uri.toString()); put("bytes", bytes)
            })
        } catch (error: Throwable) { fail(request, error) }
    }

    fun fail(request: PluginTextFileRequest, error: Throwable) { request.result.completeExceptionally(error) }
}
