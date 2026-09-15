package com.eleckoi.android.sdk.author

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.sdk.author.audio.AuthorAudioHost
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream

class AuthorApiRouter(
    private val environment: AuthorApiEnvironment,
) {
    private val registry = AuthorCapabilityRegistry.Default
    private val executor = AuthorCapabilityExecutor(environment, registry)

    fun eventFlow(): Flow<AuthorApiEvent> {
        if (AuthorApiPermission.EventsRead !in environment.permissions) return emptyFlow()
        val conversationId = environment.runtime.chatGateway?.snapshot()?.draft?.session?.id
            ?: environment.runtime.currentMessage?.conversationId.orEmpty()
        val chatEvents = environment.runtime.chatGateway?.authorEvents ?: emptyFlow()
        val audioEvents = AuthorAudioHost.events.filter { event ->
            event.payload.jsonObject["conversationId"]?.jsonPrimitive?.content == conversationId
        }
        return merge(chatEvents, audioEvents)
            .filter { event -> AuthorApiEventAccess.canReceive(event.name, environment.permissions) }
    }

    fun runtimeResource(requestedPath: String): WebResourceResponse? {
        val parts = requestedPath
            .removePrefix(AuthorMediaRuntimePrefix)
            .split('/')
            .map(Uri::decode)
        if (!requestedPath.startsWith(AuthorMediaRuntimePrefix) || parts.size != 2) return null
        val (messageId, attachmentId) = parts
        val runtime = environment.runtime
        val messages = runtime.chatGateway?.snapshot()?.draft?.session?.messages
            ?: runtime.chatSession?.messages
            ?: runtime.currentMessage?.let(::listOf)
            ?: return null
        val media = messages.firstOrNull { it.id == messageId }
            ?.attachments
            ?.firstOrNull { it.id == attachmentId }
            ?: return null
        val file = File(media.sourcePath).takeIf { media.sourcePath.isNotBlank() && it.isFile }
            ?: return null
        val mimeType = media.mimeType.ifBlank {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
        }
        return runCatching { WebResourceResponse(mimeType, null, FileInputStream(file)) }.getOrNull()
    }

    suspend fun route(rawRequest: String): String {
        var requestId = ""
        return try {
            val request = ElecKoiJson.decodeFromString<AuthorApiRequest>(rawRequest)
            requestId = request.id
            require(request.id.isNotBlank()) {
                throw AuthorApiCallException(AuthorApiErrorCode.InvalidRequest, "请求 id 不能为空")
            }
            if (request.apiVersion != AuthorApiVersion) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.UnsupportedVersion,
                    "不支持的 API 版本：${request.apiVersion}",
                )
            }
            successResponse(request.id, executor.invoke(request.method, request.params))
        } catch (error: AuthorApiCallException) {
            errorResponse(requestId, error.code, error.message)
        } catch (_: SerializationException) {
            errorResponse(requestId, AuthorApiErrorCode.InvalidRequest, "请求不是有效的作者 API JSON")
        } catch (_: IllegalArgumentException) {
            errorResponse(requestId, AuthorApiErrorCode.InvalidRequest, "请求格式不正确")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            errorResponse(requestId, AuthorApiErrorCode.InternalError, "原生 API 调用失败")
        }
    }

    private fun successResponse(id: String, result: JsonElement): String {
        return ElecKoiJson.encodeToString(
            buildJsonObject {
                put("id", id)
                put("ok", true)
                put("result", result)
            },
        )
    }

    private fun errorResponse(id: String, code: String, message: String): String {
        return ElecKoiJson.encodeToString(
            buildJsonObject {
                put("id", id)
                put("ok", false)
                put("error", buildJsonObject {
                    put("code", code)
                    put("message", message)
                })
            },
        )
    }
}

private const val AuthorMediaRuntimePrefix = "author-media/"

/**
 * Events are another read surface, not a permission bypass around the request router.
 * Unknown event names deliberately fail closed until their payload contract is classified.
 */
internal object AuthorApiEventAccess {
    private val eventPermissions = listOf(
        "messages.changed" to AuthorApiPermission.MessagesRead,
        "agent.output.delta" to AuthorApiPermission.ChatRead,
        "agent.run.finished" to AuthorApiPermission.ChatRead,
        "agent.run.failed" to AuthorApiPermission.ChatRead,
        "agent.state.changed" to AuthorApiPermission.ChatRead,
        "agent.process.updated" to AuthorApiPermission.ChatRead,
        "agent.generation.stats" to AuthorApiPermission.ChatRead,
        "audio.state.changed" to AuthorApiPermission.AudioRead,
        "audio.time.updated" to AuthorApiPermission.AudioRead,
    )
    private val requiredDataPermission = eventPermissions.toMap()

    val knownEventNames: List<String> = eventPermissions.map { it.first }

    fun canReceive(name: String, permissions: Set<AuthorApiPermission>): Boolean {
        if (AuthorApiPermission.EventsRead !in permissions) return false
        val dataPermission = requiredDataPermission[name] ?: return false
        return dataPermission in permissions
    }
}
