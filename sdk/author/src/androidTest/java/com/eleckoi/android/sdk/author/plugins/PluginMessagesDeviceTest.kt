package com.eleckoi.android.sdk.author.plugins

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.engine.agent.eleckoi.conversation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginMessagesDeviceTest {
    @Test fun editingUserAndDeletingOneMessagePreservesLaterNativeHistory() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ElecKoiDatabase::class.java).build()
        try {
            val ledger = RoomConversationLedger(database)
            database.runInTransaction {
                ledger.ensureConversationInTransaction("chat", "now", "now", listOf(
                    LedgerMessage("u1", "user", "old"), LedgerMessage("a1", "assistant", "reply"),
                    LedgerMessage("u2", "user", "later"), LedgerMessage("a2", "assistant", "later reply")))
                ledger.editAssistantMessageInTransaction("chat", "u1", "edited user", "now", assistantOnly = false)
            }
            assertEquals("edited user", ledger.allMessages("chat").first().content)
            database.runInTransaction {
                val messages = ledger.allMessages("chat").filterNot { it.id == "a1" }.toMutableList()
                messages.add(1, LedgerMessage("insert", "system", "injected history"))
                ledger.replaceActiveMessagesInTransaction("chat", "later", messages)
            }
            val messages = ledger.allMessages("chat")
            assertEquals(listOf("u1", "insert", "u2", "a2"), messages.map { it.id })
            assertEquals("later reply", messages.last().content)
            assertEquals(4, ledger.activeMessageCount("chat"))
        } finally { database.close() }
    }
}
