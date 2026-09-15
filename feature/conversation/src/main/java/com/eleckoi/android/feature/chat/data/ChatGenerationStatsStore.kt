package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Small PC-style sidecar projection for DSH generation statistics.
 *
 * The append-only DSH event log remains authoritative. This snapshot prevents UI paging from
 * changing totals and avoids replaying the complete native log whenever a conversation opens.
 */
class ChatGenerationStatsStore(
    rootDirectory: File,
) {
    private val root = rootDirectory.canonicalFile
    private val mutationLock = Any()

    init {
        require(root.mkdirs() || root.isDirectory) { "无法创建生成统计目录" }
    }

    fun load(conversationId: String, runtimeThreadId: String): ChatSessionGenerationStats {
        if (runtimeThreadId.isBlank()) return ChatSessionGenerationStats()
        return synchronized(mutationLock) {
            val file = statsFile(conversationId, runtimeThreadId)
            if (!file.isFile || file.length() > MaximumStatsBytes) {
                return@synchronized ChatSessionGenerationStats(runtimeThreadId = runtimeThreadId)
            }
            runCatching {
                ElecKoiJson.decodeFromString<StoredChatGenerationStats>(
                    file.readText(Charsets.UTF_8),
                ).takeIf { stored ->
                    stored.version == CurrentVersion && stored.runtimeThreadId == runtimeThreadId
                }?.toDomain()
            }.getOrNull() ?: ChatSessionGenerationStats(runtimeThreadId = runtimeThreadId)
        }
    }

    fun persist(conversationId: String, stats: ChatSessionGenerationStats) {
        if (stats.runtimeThreadId.isBlank()) return
        synchronized(mutationLock) {
            val target = statsFile(conversationId, stats.runtimeThreadId)
            val directory = requireNotNull(target.parentFile)
            require(directory.mkdirs() || directory.isDirectory) { "无法创建生成统计会话目录" }
            val temporary = File(
                directory,
                ".${target.name}.tmp-${UUID.randomUUID().toString().replace("-", "")}",
            )
            try {
                temporary.writeText(
                    ElecKoiJson.encodeToString(StoredChatGenerationStats.fromDomain(stats)),
                    Charsets.UTF_8,
                )
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
    }

    fun discardThreads(
        conversationId: String,
        runtimeThreadIds: Set<String>,
        selectedThreadId: String = "",
    ) {
        synchronized(mutationLock) {
            runtimeThreadIds
                .filter { it.isNotBlank() && it != selectedThreadId }
                .forEach { threadId -> Files.deleteIfExists(statsFile(conversationId, threadId).toPath()) }
            deleteEmptyConversationDirectory(conversationId)
        }
    }

    fun deleteConversation(conversationId: String) {
        synchronized(mutationLock) {
            val directory = conversationDirectory(conversationId)
            if (!Files.exists(directory.toPath())) return@synchronized
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(
                    file: java.nio.file.Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    directory: java.nio.file.Path,
                    error: java.io.IOException?,
                ): FileVisitResult {
                    error?.let { throw it }
                    Files.deleteIfExists(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

    private fun statsFile(conversationId: String, runtimeThreadId: String): File {
        val directory = File(
            conversationDirectory(conversationId),
            GenerationStatsDirectory,
        ).canonicalFile
        require(directory.toPath().startsWith(root.toPath()) && directory != root) {
            "生成统计会话目录越界"
        }
        val file = File(directory, "${safeSegment(runtimeThreadId, 160)}.json").canonicalFile
        require(file.parentFile == directory) { "生成统计文件路径越界" }
        return file
    }

    private fun conversationDirectory(conversationId: String): File {
        val directory = File(root, safeSegment(conversationId, 96)).canonicalFile
        require(directory.toPath().startsWith(root.toPath()) && directory != root) {
            "生成统计对话目录越界"
        }
        return directory
    }

    private fun deleteEmptyConversationDirectory(conversationId: String) {
        val conversation = conversationDirectory(conversationId)
        val statsDirectory = File(conversation, GenerationStatsDirectory)
        if (statsDirectory.isDirectory && statsDirectory.list().isNullOrEmpty()) {
            Files.deleteIfExists(statsDirectory.toPath())
        }
        if (conversation.isDirectory && conversation.list().isNullOrEmpty()) {
            Files.deleteIfExists(conversation.toPath())
        }
    }

    private fun safeSegment(value: String, maximumLength: Int): String = value
        .replace(UnsafeSegmentCharacters, "_")
        .take(maximumLength)
        .ifBlank { "default" }

    private companion object {
        const val CurrentVersion = 1
        const val MaximumStatsBytes = 64L * 1024L
        const val GenerationStatsDirectory = "eleckoi-generation-stats"
        val UnsafeSegmentCharacters = Regex("[^A-Za-z0-9._-]")
    }
}

@Serializable
private data class StoredChatGenerationStats(
    val version: Int = 1,
    val runtimeThreadId: String,
    val metrics: ChatGenerationMetricsJson,
    val contextWindowUsage: ChatContextWindowUsageJson? = null,
) {
    fun toDomain() = ChatSessionGenerationStats(
        runtimeThreadId = runtimeThreadId,
        metrics = metrics.toDomain(),
        contextWindowUsage = contextWindowUsage?.toDomain(),
    )

    companion object {
        fun fromDomain(stats: ChatSessionGenerationStats) = StoredChatGenerationStats(
            runtimeThreadId = stats.runtimeThreadId,
            metrics = ChatGenerationMetricsJson.fromDomain(stats.metrics),
            contextWindowUsage = stats.contextWindowUsage?.let(ChatContextWindowUsageJson::fromDomain),
        )
    }
}
