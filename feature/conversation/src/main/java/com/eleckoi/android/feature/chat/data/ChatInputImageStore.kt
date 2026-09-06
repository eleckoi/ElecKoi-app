package com.eleckoi.android.feature.chat.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.deleteOwnedFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val MaxChatInputImages: Int = 4
const val MaxChatInputImageBytes: Long = 20L * 1024L * 1024L
const val MaxChatInputMessageImageBytes: Long = 20L * 1024L * 1024L

/** Copies picker grants into stable app-private files; DSH owns normalization and durable admission. */
class ChatInputImageStore(
    context: Context,
    private val rootDirectory: File = File(context.filesDir, "chat/input-images"),
) {
    private val resolver = context.applicationContext.contentResolver

    fun prepare(uriValues: List<String>): List<ChatUserImageAttachment> {
        if (uriValues.isEmpty()) return emptyList()
        if (uriValues.size > MaxChatInputImages) {
            throw ElecKoiDataException("每条消息最多发送 $MaxChatInputImages 张图片")
        }
        rootDirectory.mkdirs()
        val admitted = mutableListOf<ChatUserImageAttachment>()
        try {
            uriValues.forEach { value -> admitted += copyOne(Uri.parse(value)) }
            if (admitted.sumOf(ChatUserImageAttachment::bytes) > MaxChatInputMessageImageBytes) {
                throw ElecKoiDataException("每条消息的图片总大小不能超过 20 MiB")
            }
            return admitted
        } catch (error: Throwable) {
            admitted.forEach(::delete)
            throw error
        }
    }

    fun delete(image: ChatUserImageAttachment) {
        deletePath(image.localPath)
    }

    /** Resolve only files owned by this store; creator tools never receive a device path. */
    fun findById(imageId: String): File? {
        if (!InputImageId.matches(imageId)) return null
        val root = rootDirectory.canonicalFile
        return AcceptedMediaExtensions.values
            .asSequence()
            .distinct()
            .map { extension -> File(root, "$imageId.$extension").canonicalFile }
            .firstOrNull { candidate ->
                candidate.parentFile == root && candidate.isFile
            }
    }

    fun deletePath(localPath: String) {
        if (localPath.isBlank()) return
        val candidate = File(localPath)
        val root = rootDirectory.canonicalFile
        val resolved = candidate.canonicalFile
        if (resolved.parentFile == root) deleteOwnedFile(root, resolved)
    }

    private fun copyOne(uri: Uri): ChatUserImageAttachment {
        val id = newId(20)
        val temporary = File(rootDirectory, "$id.image")
        var target = temporary
        var bytes = 0L
        try {
            val input = resolver.openInputStream(uri)
                ?: throw ElecKoiDataException("无法读取所选图片")
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(CopyBufferBytes)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > MaxChatInputImageBytes) {
                            throw ElecKoiDataException("单张图片不能超过 20 MiB")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (bytes == 0L) throw ElecKoiDataException("所选图片为空")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            val mediaType = bounds.outMimeType?.lowercase()
                ?.takeIf(AcceptedMediaTypes::contains)
                ?: throw ElecKoiDataException("仅支持 PNG、JPEG、WebP 和 GIF 图片")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw ElecKoiDataException("无法解析所选图片")
            }
            target = File(rootDirectory, "$id.${AcceptedMediaExtensions.getValue(mediaType)}")
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return ChatUserImageAttachment(
                id = id,
                localPath = target.absolutePath,
                mediaType = mediaType,
                displayName = displayName(uri).take(MaxDisplayNameChars),
                bytes = bytes,
                imageWidth = bounds.outWidth,
                imageHeight = bounds.outHeight,
            )
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use ""
            cursor.getString(0).orEmpty()
        }.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val CopyBufferBytes = 64 * 1024
        const val MaxDisplayNameChars = 160
        val InputImageId = Regex("[a-f0-9]{20}")
        val AcceptedMediaTypes = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
        val AcceptedMediaExtensions = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/webp" to "webp",
            "image/gif" to "gif",
        )
    }
}
