package com.eleckoi.android.engine.agent.eleckoi.conversation

import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomConversationLedgerTest {
    @Test
    fun `stable ledger ids encode identity components without hashing message content`() {
        assertEquals("turn.Y2hhdA.bWFpbg", stableLedgerId("turn", "chat", "main"))
    }

    @Test
    fun `user and assistant become one branch turn with one selected response`() {
        val entries = ledgerEntries(
            conversationId = "chat-1",
            messages = listOf(
                LedgerMessage(id = "user-1", role = "user", content = "你好"),
                LedgerMessage(
                    id = "assistant-1",
                    role = "assistant",
                    content = "你好呀",
                    reasoningContent = "简短回应",
                    toolCallsJson = "[{\"name\":\"clock\"}]",
                    runtimeTurnId = "runtime-turn-1",
                ),
            ),
        )

        assertEquals(1, entries.size)
        assertEquals("user", entries.single().turn.kind)
        assertNotNull(entries.single().response)
        assertEquals(
            listOf("user_text", "assistant_text", "reasoning", "tool_calls"),
            entries.single().parts.map { it.kind },
        )
    }

    @Test
    fun `regenerated reply keeps one turn and overwrites the same response identity`() {
        fun entry(runtimeTurnId: String, answer: String) = ledgerEntries(
            conversationId = "chat-1",
            messages = listOf(
                LedgerMessage(id = "user-1", role = "user", content = "你好"),
                LedgerMessage(
                    id = "assistant-1",
                    role = "assistant",
                    content = answer,
                    runtimeTurnId = runtimeTurnId,
                ),
            ),
        ).single()

        val first = entry("runtime-turn-1", "版本一")
        val second = entry("runtime-turn-2", "版本二")

        assertEquals(first.turn.id, second.turn.id)
        assertEquals(first.response?.id, second.response?.id)
    }

    @Test
    fun `one user turn preserves ordered replies and their speaker identities`() {
        val entries = ledgerEntries(
            conversationId = "chat-1",
            messages = listOf(
                LedgerMessage(id = "user-1", role = "user", content = "你们怎么看？"),
                LedgerMessage(
                    id = "reply-a",
                    role = "assistant",
                    content = "甲的回答",
                    speakerId = "member-a",
                    speakerKind = "card_character",
                    speakerName = "甲",
                ),
                LedgerMessage(
                    id = "reply-b",
                    role = "assistant",
                    content = "乙的回答",
                    speakerId = "member-b",
                    speakerKind = "card_character",
                    speakerName = "乙",
                ),
            ),
        )

        val entry = entries.single()
        assertEquals(listOf(0, 1), entry.responses.map { it.responseIndex })
        assertEquals(2, entry.responses.map { it.speakerId }.distinct().size)
        assertEquals(listOf("member-a", "member-b"), entry.speakers.drop(1).map { it.sourceSpeakerId })
        assertEquals(
            listOf("user_text", "assistant_text", "assistant_text"),
            entry.parts.map { it.kind },
        )
    }

    @Test
    fun `long multi speaker history keeps stable identities when processed twice`() {
        val messages = (0 until 300).flatMap { turn ->
            listOf(
                LedgerMessage(id = "user-$turn", role = "user", content = "问题 $turn"),
                LedgerMessage(
                    id = "member-a-$turn",
                    role = "assistant",
                    content = "甲 $turn",
                    speakerId = "member-a",
                ),
                LedgerMessage(
                    id = "member-b-$turn",
                    role = "assistant",
                    content = "乙 $turn",
                    speakerId = "member-b",
                ),
            )
        }

        val first = ledgerEntries("long-chat", messages)
        val second = ledgerEntries("long-chat", messages)

        assertEquals(300, first.size)
        assertTrue(first.all { it.responses.map { response -> response.responseIndex } == listOf(0, 1) })
        assertEquals(600, first.flatMap(LedgerEntry::responses).map { it.id }.distinct().size)
        assertEquals(
            first.flatMap(LedgerEntry::responses).map { it.id },
            second.flatMap(LedgerEntry::responses).map { it.id },
        )
    }

    @Test
    fun `editing user text preserves stable turn identity`() {
        fun turn(text: String) = ledgerEntries(
            conversationId = "chat-1",
            messages = listOf(LedgerMessage(id = "user-1", role = "user", content = text)),
        ).single().turn

        assertEquals(turn("原问题").id, turn("修改后的问题").id)
    }

    @Test
    fun `room history preserves roles and excludes the current prompt`() {
        val history = roomConversationHistory(
            messages = listOf(
                LedgerMessage(id = "opening", role = "assistant", content = "欢迎"),
                LedgerMessage(id = "user-1", role = "user", content = "上一问"),
                LedgerMessage(id = "assistant-1", role = "assistant", content = "上一答"),
                LedgerMessage(id = "user-2", role = "user", content = "本轮问题"),
            ),
            currentUserMessageId = "user-2",
        )

        assertEquals(
            listOf("assistant", "user", "assistant"),
            history.map { item ->
                Json.parseToJsonElement(item.responseItemJson)
                    .jsonObject.getValue("role").jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("欢迎", "上一问", "上一答"),
            history.map { item ->
                Json.parseToJsonElement(item.responseItemJson)
                    .jsonObject.getValue("content").jsonArray.single().jsonObject
                    .getValue("text").jsonPrimitive.content
            },
        )
    }

    @Test
    fun `display cache chunks and restores a multi megabyte tool result`() {
        val toolCalls = """[{"name":"large-result","result":"${"工".repeat(800_000)}"}]"""
        val messages = listOf(
            LedgerMessage(
                id = "assistant-1",
                role = "assistant",
                content = "工具执行完成",
                toolCallsJson = toolCalls,
            ),
        )

        val chunks = encodeDisplayCacheChunks(messages)

        assertTrue(chunks.size > 1)
        assertTrue(
            chunks.all { chunk ->
                chunk.toByteArray(Charsets.UTF_8).size <= CursorWindowChunkCharacters * 4
            },
        )
        assertEquals(messages, decodeDisplayCacheChunks(chunks))
    }

    @Test
    fun `content part chunks preserve emoji boundaries and exact payload`() {
        val text = "a".repeat(CursorWindowChunkCharacters - 1) + "😀" + "尾".repeat(100)
        val payload = "p".repeat(CursorWindowChunkCharacters * 2 + 7)
        val part = AgentContentPartEntity(
            conversationId = "chat-1",
            ownerType = "response",
            ownerId = "response-1",
            partIndex = 3,
            kind = "tool_calls",
            text = text,
            payloadJson = payload,
        )

        val chunks = listOf(part).toStorageChunks()

        assertTrue(chunks.size > 1)
        assertEquals(part, chunks.mergeStorageChunks().single())
    }

    @Test
    fun `long streaming checkpoint rewrites only the growing tail chunk`() {
        val full = "a".repeat(CursorWindowChunkCharacters)
        fun rows(tail: String) = listOf(
            AgentContentPartEntity(
                conversationId = "chat-1",
                ownerType = "response",
                ownerId = "response-1",
                partIndex = 0,
                kind = "assistant_text",
                text = full + tail,
                payloadJson = "",
            ),
        ).toStorageChunks()

        val current = rows("b".repeat(8_000))
        val incoming = rows("b".repeat(8_000) + "新增内容")
        val plan = contentPartWritePlan(current, incoming)

        assertEquals(listOf(1), plan.upserts.map(AgentContentPartEntity::chunkIndex))
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `shortened checkpoint removes only obsolete response chunks`() {
        fun rows(text: String) = listOf(
            AgentContentPartEntity(
                conversationId = "chat-1",
                ownerType = "response",
                ownerId = "response-1",
                partIndex = 0,
                kind = "assistant_text",
                text = text,
                payloadJson = "",
            ),
        ).toStorageChunks()

        val current = rows("a".repeat(CursorWindowChunkCharacters * 2 + 20))
        val incoming = rows("a".repeat(CursorWindowChunkCharacters + 10))
        val plan = contentPartWritePlan(current, incoming)

        assertEquals(listOf(1), plan.upserts.map(AgentContentPartEntity::chunkIndex))
        assertEquals(listOf(2), plan.deletes.map(AgentContentPartEntity::chunkIndex))
    }

    @Test
    fun `recovery checkpoint clears cold cache without publishing a paging revision`() {
        val plan = ledgerMutationPublicationPlan(rebuildDisplayCache = false)

        assertEquals(false, plan.advanceConversationRevision)
        assertEquals(false, plan.rebuildDisplayCache)
        assertEquals(true, plan.clearDisplayCache)
    }

    @Test
    fun `terminal mutation advances revision and rebuilds the cold cache`() {
        val plan = ledgerMutationPublicationPlan(rebuildDisplayCache = true)

        assertEquals(true, plan.advanceConversationRevision)
        assertEquals(true, plan.rebuildDisplayCache)
        assertEquals(false, plan.clearDisplayCache)
    }
}
