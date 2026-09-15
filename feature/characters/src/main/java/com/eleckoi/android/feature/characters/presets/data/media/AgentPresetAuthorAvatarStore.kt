package com.eleckoi.android.feature.characters.presets.data.media

import com.eleckoi.android.feature.characters.presets.data.AgentPresetTransferAvatar
import com.eleckoi.android.feature.characters.transfer.format.png.PngTextChunkCodec
import com.eleckoi.android.foundation.storage.JsonFileStore
import com.eleckoi.android.foundation.storage.newId
import java.io.File

/** Owns files used by preset author profiles; Room stores only the resulting absolute path. */
internal class AgentPresetAuthorAvatarStore(
    private val store: JsonFileStore,
) {
    fun storeImported(presetId: String, avatar: AgentPresetTransferAvatar?): File? {
        if (avatar == null) return null
        require(avatar.bytes.size <= MaxImportedAuthorAvatarBytes) {
            "预设卡作者头像不能超过 8 MB"
        }
        require(avatar.bytes.isNotEmpty() && avatar.bytes.matchesMediaType(avatar.mediaType)) {
            "预设卡作者头像格式与数据不匹配"
        }
        return destination(presetId, avatar.mediaType.extension()).also { file ->
            file.parentFile?.mkdirs()
            file.writeBytes(avatar.bytes)
        }
    }

    fun copyFrom(presetId: String, source: File): File? {
        if (!source.exists()) return null
        return destination(presetId, "png").also { destination ->
            destination.parentFile?.mkdirs()
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun destination(presetId: String, extension: String): File {
        return store.file(
            "agent-presets",
            "author-$presetId-${newId(10)}.$extension",
        )
    }
}

internal fun ByteArray.detectAgentPresetAvatarMediaType(): String? = when {
    PngTextChunkCodec.isPng(this) -> "image/png"
    startsWith(0xff, 0xd8, 0xff) -> "image/jpeg"
    size >= 12 && copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
    size >= 6 && copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> "image/gif"
    else -> null
}

private fun ByteArray.matchesMediaType(mediaType: String): Boolean =
    detectAgentPresetAvatarMediaType() == mediaType.lowercase()

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xff == expected[index] }

private fun String.extension(): String = when (lowercase()) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> error("预设卡作者头像格式不受支持")
}

private const val MaxImportedAuthorAvatarBytes = 8 * 1024 * 1024
