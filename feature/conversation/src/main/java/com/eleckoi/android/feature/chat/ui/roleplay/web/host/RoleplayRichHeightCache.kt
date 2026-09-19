package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import android.content.Context
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.RoleplayRichHeightDao
import com.eleckoi.android.foundation.storage.room.RoleplayRichHeightEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Exact rich-document heights retained in memory and Room across process recreation. */
internal object RoleplayRichHeightCache {
    private const val MaxEntries = 16_384
    private const val MaxKeyCharacters = 512
    private const val MaxHeightPx = 100_000
    private const val MaxRootIndex = 1_024
    private const val MaxViewportWidthPx = 16_384
    private const val KeySeparator = '\u001f'

    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writerMutex = Mutex()
    @Volatile
    private var persistentDao: RoleplayRichHeightDao? = null
    private val deletedSessions = ConcurrentHashMap.newKeySet<String>()
    private val deletedMessages = ConcurrentHashMap.newKeySet<String>()
    private val heights = object : LinkedHashMap<String, Int>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean =
            size > MaxEntries
    }

    @Synchronized
    fun put(key: String, heightPx: Int) {
        rememberIfChanged(key, heightPx)
    }

    fun putPersistent(context: Context, key: String, heightPx: Int) {
        val parsed = parseKey(key) ?: return
        if (
            parsed.sessionId in deletedSessions ||
            deletedKey(parsed.sessionId, parsed.messageId) in deletedMessages
        ) return
        if (heightPx !in 1..MaxHeightPx) return
        if (!rememberIfChanged(key, heightPx)) return
        val entity = parsed.toEntity(heightPx)
        val appContext = context.applicationContext
        writerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writerMutex.withLock {
                if (
                    parsed.sessionId in deletedSessions ||
                    deletedKey(parsed.sessionId, parsed.messageId) in deletedMessages
                ) return@withLock
                runCatching {
                    dao(appContext).deleteOtherRevisions(
                        entity.sessionId,
                        entity.messageId,
                        entity.contentRevision,
                    )
                    dao(appContext).upsert(entity)
                }
            }
        }
    }

    /** Wait for queued writes, then prevent deleted message heights from returning from memory/Room. */
    suspend fun discardMessages(sessionId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        val ids = messageIds.toSet()
        writerMutex.withLock {
            synchronized(this) {
                ids.forEach { deletedMessages += deletedKey(sessionId, it) }
                val keys = heights.keys.filter { key ->
                    val parsed = parseKey(key)
                    parsed?.sessionId == sessionId && parsed.messageId in ids
                }
                keys.forEach { heights.remove(it) }
            }
            persistentDao?.deleteForMessages(sessionId, messageIds)
        }
    }

    /** Prevents deleted conversations from surviving in either cache tier. */
    suspend fun discardSessions(sessionIds: Collection<String>) {
        val ids = sessionIds.filter(String::isNotBlank).toSet()
        if (ids.isEmpty()) return
        writerMutex.withLock {
            synchronized(this) {
                deletedSessions += ids
                val keys = heights.keys.filter { key -> parseKey(key)?.sessionId in ids }
                keys.forEach { heights.remove(it) }
            }
            persistentDao?.deleteForSessions(ids.toList())
        }
    }

    @Synchronized
    private fun rememberIfChanged(key: String, heightPx: Int): Boolean {
        val parsed = parseKey(key)
        if (
            parsed == null ||
            parsed.sessionId in deletedSessions ||
            deletedKey(parsed.sessionId, parsed.messageId) in deletedMessages ||
            heightPx !in 1..MaxHeightPx ||
            heights[key] == heightPx
        ) {
            return false
        }
        heights[key] = heightPx
        return true
    }

    suspend fun restoreSession(context: Context, sessionId: String): JSONObject {
        if (sessionId.isBlank() || sessionId in deletedSessions) return JSONObject()
        val stored = try {
            withContext(Dispatchers.IO) {
                dao(context).heightsForSession(sessionId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }
        hydrate(stored)
        return JSONObject().apply {
            stored.forEach { entity ->
                val key = entity.cacheKey().encoded()
                if (
                    parseKey(key) != null &&
                    entity.sessionId !in deletedSessions &&
                    deletedKey(entity.sessionId, entity.messageId) !in deletedMessages &&
                    entity.heightPx in 1..MaxHeightPx
                ) {
                    put(key, entity.heightPx)
                }
            }
            val current = snapshotJson(sessionId)
            val keys = current.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, current.getInt(key))
            }
        }
    }

    @Synchronized
    fun snapshotJson(sessionId: String? = null): JSONObject = JSONObject().apply {
        val sessionPrefix = sessionId?.takeIf(String::isNotBlank)?.plus(KeySeparator)
        heights.forEach { (key, height) ->
            if (sessionPrefix == null || key.startsWith(sessionPrefix)) put(key, height)
        }
    }

    @Synchronized
    internal fun clearForTest() {
        heights.clear()
        deletedSessions.clear()
        deletedMessages.clear()
    }

    @Synchronized
    internal fun hydrateForTest(stored: List<RoleplayRichHeightEntity>) {
        hydrate(stored)
    }

    @Synchronized
    private fun hydrate(stored: List<RoleplayRichHeightEntity>) {
        stored.forEach { entity ->
            val key = entity.cacheKey()
            val encoded = key.encoded()
            if (
                parseKey(encoded) != null &&
                entity.sessionId !in deletedSessions &&
                deletedKey(entity.sessionId, entity.messageId) !in deletedMessages &&
                entity.heightPx in 1..MaxHeightPx &&
                encoded !in heights
            ) {
                heights[encoded] = entity.heightPx
            }
        }
    }

    private fun dao(context: Context): RoleplayRichHeightDao =
        persistentDao ?: synchronized(this) {
            persistentDao ?: ElecKoiDatabase.get(context.applicationContext)
                .roleplayRichHeightDao()
                .also { persistentDao = it }
        }

    private fun deletedKey(sessionId: String, messageId: String) = "$sessionId$KeySeparator$messageId"

    private fun parseKey(key: String): CacheKey? {
        if (key.isBlank() || key.length > MaxKeyCharacters) return null
        val parts = key.split(KeySeparator, limit = 5)
        if (parts.size != 5) return null
        val rootIndex = parts[3].toIntOrNull() ?: return null
        val viewportWidthPx = parts[4].toIntOrNull() ?: return null
        if (
            parts[0].isBlank() || parts[1].isBlank() ||
            rootIndex !in 0..MaxRootIndex || viewportWidthPx !in 1..MaxViewportWidthPx
        ) return null
        return CacheKey(
            sessionId = parts[0],
            messageId = parts[1],
            contentRevision = parts[2],
            rootIndex = rootIndex,
            viewportWidthPx = viewportWidthPx,
        )
    }

    private data class CacheKey(
        val sessionId: String,
        val messageId: String,
        val contentRevision: String,
        val rootIndex: Int,
        val viewportWidthPx: Int,
    ) {
        fun encoded(): String = listOf(
            sessionId,
            messageId,
            contentRevision,
            rootIndex.toString(),
            viewportWidthPx.toString(),
        ).joinToString(KeySeparator.toString())

        fun toEntity(heightPx: Int): RoleplayRichHeightEntity = RoleplayRichHeightEntity(
            sessionId = sessionId,
            messageId = messageId,
            contentRevision = contentRevision,
            rootIndex = rootIndex,
            viewportWidthPx = viewportWidthPx,
            heightPx = heightPx,
        )
    }

    private fun RoleplayRichHeightEntity.cacheKey(): CacheKey = CacheKey(
        sessionId = sessionId,
        messageId = messageId,
        contentRevision = contentRevision,
        rootIndex = rootIndex,
        viewportWidthPx = viewportWidthPx,
    )
}
