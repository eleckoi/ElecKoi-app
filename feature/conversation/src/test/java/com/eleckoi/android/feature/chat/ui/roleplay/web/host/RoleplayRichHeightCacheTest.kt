package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import com.eleckoi.android.foundation.storage.room.RoleplayRichHeightDao
import com.eleckoi.android.foundation.storage.room.RoleplayRichHeightEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test
import kotlinx.coroutines.runBlocking

class RoleplayRichHeightCacheTest {
    @After
    fun clear() {
        RoleplayRichHeightCache.clearForTest()
    }

    @Test
    fun `cache retains verified rich height and rejects unsafe input`() {
        RoleplayRichHeightCache.put("session\u001fmessage\u001frevision\u001f0\u001f640", 812)
        RoleplayRichHeightCache.put("other\u001fmessage\u001frevision\u001f0\u001f640", 900)
        RoleplayRichHeightCache.put("", 200)
        RoleplayRichHeightCache.put("invalid-height", 0)

        val snapshot = RoleplayRichHeightCache.snapshotJson("session")

        assertEquals(812, snapshot.getInt("session\u001fmessage\u001frevision\u001f0\u001f640"))
        assertFalse(snapshot.has("other\u001fmessage\u001frevision\u001f0\u001f640"))
        assertFalse(snapshot.has("invalid-height"))
    }

    @Test
    fun `cache does not evict a long roleplay at the old 256 entry boundary`() {
        repeat(512) { index ->
            RoleplayRichHeightCache.put("session\u001fmessage-$index\u001frevision\u001f0\u001f640", 400 + index)
        }

        val snapshot = RoleplayRichHeightCache.snapshotJson("session")

        assertEquals(512, snapshot.length())
        assertEquals(400, snapshot.getInt("session\u001fmessage-0\u001frevision\u001f0\u001f640"))
    }

    @Test
    fun `room heights hydrate by stable message geometry key without replacing newer memory`() {
        val key = "session\u001fmessage\u001frevision\u001f2\u001f640"
        RoleplayRichHeightCache.put(key, 900)

        RoleplayRichHeightCache.hydrateForTest(
            listOf(
                RoleplayRichHeightEntity(
                    sessionId = "session",
                    messageId = "message",
                    contentRevision = "revision",
                    rootIndex = 2,
                    viewportWidthPx = 640,
                    heightPx = 812,
                ),
            ),
        )

        assertEquals(900, RoleplayRichHeightCache.snapshotJson("session").getInt(key))
    }

    @Test
    fun `cache rejects malformed persistent geometry keys`() {
        RoleplayRichHeightCache.put("session\u001fmessage\u001frevision\u001froot\u001f640", 812)
        RoleplayRichHeightCache.put("session\u001fmessage\u001frevision\u001f0\u001f0", 812)

        assertEquals(0, RoleplayRichHeightCache.snapshotJson("session").length())
    }

    @Test
    fun `deleted message heights are removed without touching retained messages`() = runBlocking {
        val deleted = "session\u001fdeleted\u001frevision\u001f0\u001f640"
        val retained = "session\u001fretained\u001frevision\u001f0\u001f640"
        RoleplayRichHeightCache.put(deleted, 500)
        RoleplayRichHeightCache.put(retained, 600)

        RoleplayRichHeightCache.discardMessages("session", listOf("deleted"))

        val snapshot = RoleplayRichHeightCache.snapshotJson("session")
        assertFalse(snapshot.has(deleted))
        assertEquals(600, snapshot.getInt(retained))

        RoleplayRichHeightCache.put(deleted, 700)
        RoleplayRichHeightCache.hydrateForTest(listOf(
            RoleplayRichHeightEntity(
                sessionId = "session",
                messageId = "deleted",
                contentRevision = "revision",
                rootIndex = 0,
                viewportWidthPx = 640,
                heightPx = 800,
            ),
        ))
        assertFalse(RoleplayRichHeightCache.snapshotJson("session").has(deleted))
    }

    @Test
    fun `deleted session heights cannot return from memory or hydration`() = runBlocking {
        val deleted = "deleted-session\u001fmessage\u001frevision\u001f0\u001f640"
        val retained = "retained-session\u001fmessage\u001frevision\u001f0\u001f640"
        RoleplayRichHeightCache.put(deleted, 500)
        RoleplayRichHeightCache.put(retained, 600)

        RoleplayRichHeightCache.discardSessions(listOf("deleted-session"))

        assertEquals(0, RoleplayRichHeightCache.snapshotJson("deleted-session").length())
        assertEquals(600, RoleplayRichHeightCache.snapshotJson("retained-session").getInt(retained))

        RoleplayRichHeightCache.put(deleted, 700)
        RoleplayRichHeightCache.hydrateForTest(listOf(
            RoleplayRichHeightEntity(
                sessionId = "deleted-session",
                messageId = "message",
                contentRevision = "revision",
                rootIndex = 0,
                viewportWidthPx = 640,
                heightPx = 800,
            ),
        ))

        assertEquals(0, RoleplayRichHeightCache.snapshotJson("deleted-session").length())
    }

    @Test
    fun `persistent deletion leaves the caller thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        val dao = ThreadRecordingDao()
        RoleplayRichHeightCache.installPersistentDaoForTest(dao)

        RoleplayRichHeightCache.discardMessages("session", listOf("message"))
        RoleplayRichHeightCache.discardSessions(listOf("session"))

        assertNotSame(callerThread, dao.messageDeletionThread)
        assertNotSame(callerThread, dao.sessionDeletionThread)
    }

    private class ThreadRecordingDao : RoleplayRichHeightDao {
        var messageDeletionThread: Thread? = null
        var sessionDeletionThread: Thread? = null

        override fun deleteForMessages(sessionId: String, messageIds: List<String>) {
            messageDeletionThread = Thread.currentThread()
        }

        override fun deleteForSessions(sessionIds: List<String>) {
            sessionDeletionThread = Thread.currentThread()
        }

        override suspend fun heightsForSession(sessionId: String): List<RoleplayRichHeightEntity> = emptyList()

        override suspend fun upsert(height: RoleplayRichHeightEntity) = Unit

        override suspend fun deleteOtherRevisions(
            sessionId: String,
            messageId: String,
            contentRevision: String,
        ) = Unit
    }
}
