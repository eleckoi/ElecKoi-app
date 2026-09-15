package com.eleckoi.android.feature.chat.ui

import androidx.paging.LoadState
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryLoadStateTest {
    @Test
    fun `opening row proves the initial projection reached the oldest boundary`() {
        assertFalse(
            initialHistoryHasMore(
                listOf(ChatMessage(OpeningMessageId, MessageRole.Assistant, "开场")),
            ),
        )
        assertTrue(
            initialHistoryHasMore(
                listOf(ChatMessage("assistant-1", MessageRole.Assistant, "回复")),
            ),
        )
        assertFalse(initialHistoryHasMore(emptyList()))
    }

    @Test
    fun `refresh loading cannot invent older history`() {
        assertFalse(
            resolveHistoryHasMore(
                previous = false,
                prepend = LoadState.Loading,
                timelineMutationActive = false,
            ),
        )
    }

    @Test
    fun `refresh loading preserves a known older page`() {
        assertTrue(
            resolveHistoryHasMore(
                previous = true,
                prepend = LoadState.Loading,
                timelineMutationActive = false,
            ),
        )
    }

    @Test
    fun `settled prepend state alone changes the history boundary`() {
        assertFalse(
            resolveHistoryHasMore(
                previous = true,
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                timelineMutationActive = false,
            ),
        )
        assertTrue(
            resolveHistoryHasMore(
                previous = false,
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                timelineMutationActive = false,
            ),
        )
    }

    @Test
    fun `tail mutation freezes prepend boundary across every paging state`() {
        assertFalse(
            resolveHistoryHasMore(
                previous = false,
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                timelineMutationActive = true,
            ),
        )
        assertTrue(
            resolveHistoryHasMore(
                previous = true,
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                timelineMutationActive = true,
            ),
        )
    }
}
