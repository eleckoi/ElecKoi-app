package com.eleckoi.android.app.service.backup

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
internal data class BackupSymbolicLinkPayload(
    val target: String,
    val relativeToAppFiles: Boolean,
)

internal fun encodeBackupSymbolicLinkTarget(target: String, appFilesRoot: String): String {
    require(target.isNotBlank() && target.toByteArray(Charsets.UTF_8).size <= MaxBackupLinkBytes) {
        "备份符号链接目标无效"
    }
    val normalizedTarget = target.replace('\\', '/')
    val normalizedRoot = appFilesRoot.replace('\\', '/').trimEnd('/')
    val withinAppFiles = normalizedTarget.startsWith("$normalizedRoot/")
    val payload = BackupSymbolicLinkPayload(
        target = if (withinAppFiles) normalizedTarget.removePrefix("$normalizedRoot/") else target,
        relativeToAppFiles = withinAppFiles,
    )
    return ElecKoiPrettyJson.encodeToString(payload)
}

internal fun decodeBackupSymbolicLinkTarget(payloadJson: String, appFilesRoot: String): String {
    val payload = ElecKoiJson.decodeFromString<BackupSymbolicLinkPayload>(payloadJson)
    val target = if (payload.relativeToAppFiles) {
        val relative = payload.target.replace('\\', '/')
        require(relative.split('/').none { it.isBlank() || it == "." || it == ".." || ':' in it }) {
            "备份符号链接目标不安全"
        }
        File(appFilesRoot, relative.replace('/', File.separatorChar)).absolutePath
    } else {
        payload.target
    }
    require(target.isNotBlank() && target.toByteArray(Charsets.UTF_8).size <= MaxBackupLinkBytes) {
        "备份符号链接目标无效"
    }
    return target
}

internal const val MaxBackupLinkPayloadBytes = 16 * 1024
