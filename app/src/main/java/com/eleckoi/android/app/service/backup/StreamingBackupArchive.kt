package com.eleckoi.android.app.service.backup

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val StreamingBackupVersion = 4
internal const val BackupManifestEntry = "manifest.json"
internal const val BackupEntryKindSection = "section"
internal const val BackupEntryKindChat = "chat"
internal const val BackupEntryKindCreatorChat = "creator_chat"
internal const val BackupEntryKindFile = "file"
internal const val BackupEntryKindSymbolicLink = "symlink"

@Serializable
internal data class StreamingBackupManifest(
    val format: String = "eleckoi-backup",
    val version: Int = StreamingBackupVersion,
    val entries: List<StreamingBackupEntry>,
    val directories: List<String>,
    val excluded: List<String>,
    @SerialName("character_count")
    val characterCount: Int,
    @SerialName("session_count")
    val sessionCount: Int,
    @SerialName("creator_workspace_count")
    val creatorWorkspaceCount: Int,
    @SerialName("creator_conversation_count")
    val creatorConversationCount: Int,
)

@Serializable
internal data class StreamingBackupEntry(
    val name: String,
    val kind: String,
    val bytes: Long,
    val sha256: String,
    @SerialName("owner_id")
    val ownerId: String = "",
)

internal sealed interface BackupArchiveInspection {
    val version: Int
}

internal data object LegacyBackupArchive : BackupArchiveInspection {
    override val version: Int = 2
}

internal data class StreamingBackupArchive(
    val manifest: StreamingBackupManifest,
    val payloadBytes: Long,
) : BackupArchiveInspection {
    override val version: Int = manifest.version
}

/**
 * Writes one ZIP entry at a time and records a content manifest last. Keeping the manifest last
 * means hashes and byte counts describe the bytes that were actually written, without staging a
 * second copy of the backup.
 */
internal class StreamingBackupArchiveWriter(output: OutputStream) : Closeable {
    private val zip = ZipOutputStream(output.buffered())
    private val written = mutableListOf<StreamingBackupEntry>()
    private var finished = false

    suspend fun writeText(
        name: String,
        value: String,
        kind: String = BackupEntryKindSection,
        ownerId: String = "",
    ) {
        writeEntry(name, kind, ownerId) { sink ->
            sink.writer(Charsets.UTF_8).buffered().use { writer -> writer.write(value) }
        }
    }

    suspend fun writeFile(name: String, source: java.io.File) {
        writeEntry(name, BackupEntryKindFile, "") { sink ->
            Files.newByteChannel(
                source.toPath(),
                setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
            ).use { channel ->
                Channels.newInputStream(channel).buffered().use { input -> copyBackupStream(input, sink) }
            }
        }
    }

    suspend fun writeSymbolicLink(name: String, payload: String) {
        require(isCreatorWorkspaceProjectEntry(name) &&
            payload.isNotBlank() && payload.toByteArray(Charsets.UTF_8).size <= MaxBackupLinkPayloadBytes) {
            "备份符号链接无效"
        }
        writeText(name, payload, BackupEntryKindSymbolicLink)
    }

    suspend fun finish(
        directories: List<String>,
        excluded: List<String>,
        characterCount: Int,
        sessionCount: Int,
        creatorWorkspaceCount: Int,
        creatorConversationCount: Int,
    ): StreamingBackupManifest {
        check(!finished) { "备份已经完成" }
        val manifest = StreamingBackupManifest(
            entries = written.toList(),
            directories = directories,
            excluded = excluded,
            characterCount = characterCount,
            sessionCount = sessionCount,
            creatorWorkspaceCount = creatorWorkspaceCount,
            creatorConversationCount = creatorConversationCount,
        )
        val json = ElecKoiPrettyJson.encodeToString(manifest)
        zip.putNextEntry(ZipEntry(BackupManifestEntry))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        finished = true
        return manifest
    }

    private suspend fun writeEntry(
        name: String,
        kind: String,
        ownerId: String,
        writer: suspend (OutputStream) -> Unit,
    ) {
        check(!finished) { "备份已经完成" }
        requireValidEntryName(name)
        require(name != BackupManifestEntry && written.none { it.name == name }) { "备份条目重复：$name" }
        require(kind in SupportedKinds) { "未知备份条目类型：$kind" }
        currentCoroutineContext().ensureActive()
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = DigestCountingOutputStream(zip, digest)
        zip.putNextEntry(ZipEntry(name))
        try {
            writer(sink)
            sink.flush()
        } finally {
            zip.closeEntry()
        }
        written += StreamingBackupEntry(
            name = name,
            kind = kind,
            bytes = sink.count,
            sha256 = digest.digest().toHex(),
            ownerId = ownerId,
        )
    }

    override fun close() {
        zip.close()
    }
}

internal suspend fun inspectBackupArchive(
    input: InputStream,
    maximumPayloadBytes: Long,
    maximumEntryCount: Int,
    onEntryRead: (Int) -> Unit = {},
): BackupArchiveInspection {
    require(maximumPayloadBytes >= 0L)
    val actualEntries = mutableListOf<ActualBackupEntry>()
    val seenNames = mutableSetOf<String>()
    var manifest: StreamingBackupManifest? = null
    var payloadBytes = 0L
    var count = 0
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = zip.nextEntry ?: break
            count++
            require(count <= maximumEntryCount) { "备份文件数量过多" }
            val name = entry.name.replace('\\', '/')
            requireValidEntryName(name)
            require(seenNames.add(name)) { "备份包含重复文件：$name" }
            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }
            if (name == BackupManifestEntry) {
                val json = readBackupText(zip, MaximumManifestBytes)
                val root = ElecKoiJson.parseToJsonElement(json).jsonObject
                require(root["format"]?.jsonPrimitive?.content == "eleckoi-backup") {
                    "不是 ElecKoi 数据备份"
                }
                when (val version = root["version"]?.jsonPrimitive?.content?.toIntOrNull()) {
                    2 -> return LegacyBackupArchive
                    3, StreamingBackupVersion -> manifest = ElecKoiJson.decodeFromString(json)
                    else -> error("不支持的数据备份版本：${version ?: "未知"}")
                }
            } else {
                val digest = MessageDigest.getInstance("SHA-256")
                var entryBytes = 0L
                val buffer = ByteArray(CopyBufferBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read
                    require(payloadBytes <= maximumPayloadBytes - read) {
                        "可用空间不足，无法恢复这份备份"
                    }
                    payloadBytes += read
                    digest.update(buffer, 0, read)
                }
                actualEntries += ActualBackupEntry(name, entryBytes, digest.digest().toHex())
            }
            zip.closeEntry()
            onEntryRead(count)
        }
    }
    val checkedManifest = requireNotNull(manifest) { "备份缺少清单" }
    validateManifest(checkedManifest, actualEntries, maximumEntryCount)
    return StreamingBackupArchive(checkedManifest, payloadBytes)
}

internal suspend fun readStreamingBackupEntries(
    input: InputStream,
    manifest: StreamingBackupManifest,
    onEntryRead: (completed: Int, total: Int) -> Unit = { _, _ -> },
    consume: suspend (StreamingBackupEntry, InputStream) -> Unit,
) {
    var index = 0
    var sawManifest = false
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val zipEntry = zip.nextEntry ?: break
            val name = zipEntry.name.replace('\\', '/')
            if (zipEntry.isDirectory) {
                zip.closeEntry()
                continue
            }
            if (name == BackupManifestEntry) {
                sawManifest = true
                readBackupText(zip, MaximumManifestBytes)
                zip.closeEntry()
                continue
            }
            val expected = manifest.entries.getOrNull(index) ?: error("备份包含未登记条目：$name")
            require(name == expected.name) { "备份条目顺序不正确：$name" }
            val digest = MessageDigest.getInstance("SHA-256")
            val source = DigestCountingInputStream(zip, digest)
            consume(expected, source)
            drainBackupStream(source)
            require(source.count == expected.bytes && digest.digest().toHex() == expected.sha256) {
                "备份条目校验失败：$name"
            }
            zip.closeEntry()
            index++
            onEntryRead(index, manifest.entries.size)
        }
    }
    require(index == manifest.entries.size && sawManifest) { "备份内容不完整" }
}

internal suspend fun copyBackupStream(input: InputStream, output: OutputStream): Long {
    val buffer = ByteArray(CopyBufferBytes)
    var copied = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) return copied
        output.write(buffer, 0, read)
        copied += read
    }
}

internal suspend fun readBackupText(input: InputStream, maximumBytes: Long): String {
    val bytes = ByteArrayOutputStream(minOf(maximumBytes, 64L * 1024L).toInt())
    val buffer = ByteArray(CopyBufferBytes)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maximumBytes) { "单个备份数据分块过大" }
        bytes.write(buffer, 0, read)
    }
    return bytes.toString(Charsets.UTF_8.name())
}

private suspend fun drainBackupStream(input: InputStream) {
    val buffer = ByteArray(CopyBufferBytes)
    while (true) {
        currentCoroutineContext().ensureActive()
        if (input.read(buffer) < 0) return
    }
}

private fun validateManifest(
    manifest: StreamingBackupManifest,
    actualEntries: List<ActualBackupEntry>,
    maximumEntryCount: Int,
) {
    require(manifest.format == "eleckoi-backup" && manifest.version in 3..StreamingBackupVersion) {
        "备份清单格式不正确"
    }
    require(manifest.entries.size <= maximumEntryCount) { "备份文件数量过多" }
    require(manifest.entries.map { it.name }.distinct().size == manifest.entries.size) {
        "备份清单包含重复条目"
    }
    require(manifest.directories.distinct().size == manifest.directories.size) {
        "备份清单包含重复目录"
    }
    require(manifest.directories.none { directory -> manifest.entries.any { it.name == directory } }) {
        "备份清单目录与文件重名"
    }
    manifest.directories.forEach {
        requireValidEntryName(it)
        require(it.startsWith("files/")) { "备份目录路径不安全" }
    }
    require(manifest.entries.size == actualEntries.size) { "备份清单与内容不一致" }
    manifest.entries.zip(actualEntries).forEach { (expected, actual) ->
        requireValidEntryName(expected.name)
        require(expected.kind in SupportedKinds &&
            (manifest.version >= 4 || expected.kind != BackupEntryKindSymbolicLink)) {
            "未知备份条目类型：${expected.kind}"
        }
        require(expected.name == actual.name && expected.bytes == actual.bytes &&
            expected.sha256.equals(actual.sha256, ignoreCase = true)) {
            "备份条目校验失败：${expected.name}"
        }
        require((expected.kind == BackupEntryKindFile ||
            expected.kind == BackupEntryKindSymbolicLink) == expected.name.startsWith("files/")) {
            "备份条目类型不正确：${expected.name}"
        }
        if (expected.kind == BackupEntryKindSymbolicLink) {
            require(isCreatorWorkspaceProjectEntry(expected.name) &&
                expected.bytes in 1..MaxBackupLinkPayloadBytes.toLong()) {
                "备份链接无效：${expected.name}"
            }
        }
    }
    val linkNames = manifest.entries.filter { it.kind == BackupEntryKindSymbolicLink }.map { it.name }
    val allNames = manifest.directories + manifest.entries.map { it.name }
    require(linkNames.none { link -> allNames.any { it != link && it.startsWith("$link/") } }) {
        "备份链接不能作为目录"
    }
}

private fun requireValidEntryName(name: String) {
    require(name.isNotBlank() && !name.startsWith('/') &&
        name.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "备份文件路径不安全"
    }
}

private data class ActualBackupEntry(val name: String, val bytes: Long, val sha256: String)

private class DigestCountingOutputStream(
    private val delegate: OutputStream,
    private val digest: MessageDigest,
) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(value: Int) {
        delegate.write(value)
        digest.update(value.toByte())
        count++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        delegate.write(bytes, offset, length)
        digest.update(bytes, offset, length)
        count += length
    }

    override fun flush() = delegate.flush()
    override fun close() = flush()
}

private class DigestCountingInputStream(
    private val delegate: InputStream,
    private val digest: MessageDigest,
) : InputStream() {
    var count: Long = 0L
        private set

    override fun read(): Int = delegate.read().also { value ->
        if (value >= 0) {
            digest.update(value.toByte())
            count++
        }
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        delegate.read(bytes, offset, length).also { read ->
            if (read > 0) {
                digest.update(bytes, offset, read)
                count += read
            }
        }
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}

private val SupportedKinds = setOf(
    BackupEntryKindSection,
    BackupEntryKindChat,
    BackupEntryKindCreatorChat,
    BackupEntryKindFile,
    BackupEntryKindSymbolicLink,
)

private const val CopyBufferBytes = 64 * 1024
private const val MaximumManifestBytes = 32L * 1024L * 1024L
