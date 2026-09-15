package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatOpeningOption
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.data.ChatDeleteMessagesResult
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.sdk.author.AuthorSendImageAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAuthorGatewayAdapterTest {
    @Test
    fun `set input writes through the ViewModel state callback`() {
        val fixture = fixture()

        val result = fixture.gateway.setInput("hello")

        assertTrue(result.accepted)
        assertEquals("hello", fixture.state.value.input)
        fixture.scope.cancel()
    }

    @Test
    fun `send validation rejects blank or unloaded commands before dispatch`() = runBlocking {
        var dispatched = false
        val fixture = fixture(onSend = { _, _ ->
            dispatched = true
            Result.success(Unit)
        })

        val blank = fixture.gateway.send("  ")
        val unloaded = fixture.gateway.send("hello")

        assertFalse(blank.accepted)
        assertFalse(unloaded.accepted)
        assertFalse(dispatched)
        fixture.scope.cancel()
    }

    @Test
    fun `select opening validates and publishes returned draft`() = runBlocking {
        val original = openingDraft(selectedId = "opening-1")
        var request: Pair<String, String>? = null
        val fixture = fixture(
            initialState = ChatUiState(draft = original),
            onSelectOpening = { sessionId, openingId ->
                request = sessionId to openingId
                Result.success(openingDraft(selectedId = openingId))
            },
        )

        val result = fixture.gateway.selectOpening("opening-2")

        assertTrue(result.accepted)
        assertEquals("session-1" to "opening-2", request)
        assertEquals("opening-2", fixture.state.value.draft?.selectedOpeningOptionId)
        fixture.scope.cancel()
    }

    @Test
    fun `select opening rejects unknown option before persistence`() = runBlocking {
        var dispatched = false
        val fixture = fixture(
            initialState = ChatUiState(draft = openingDraft(selectedId = "opening-1")),
            onSelectOpening = { _, _ ->
                dispatched = true
                Result.failure(IllegalStateException("unexpected"))
            },
        )

        val result = fixture.gateway.selectOpening("missing")

        assertFalse(result.accepted)
        assertFalse(dispatched)
        fixture.scope.cancel()
    }

    @Test
    fun `delete event is emitted only after the action returns its cleaned draft`() = runBlocking {
        val initial = openingDraft("opening-1").let { draft ->
            draft.copy(session = draft.session.copy(messages = listOf(
                ChatMessage("user-1", MessageRole.User, "原文"),
                ChatMessage("reply-1", MessageRole.Assistant, "回复"),
            )))
        }
        var actionReturned = false
        val fixture = fixture(
            initialState = ChatUiState(draft = initial),
            onDeleteFrom = { _, _ ->
                actionReturned = true
                Result.success(ChatDeleteMessagesResult(
                    draft = initial.copy(session = initial.session.copy(messages = emptyList())),
                    deletedMessageIds = listOf("user-1", "reply-1"),
                    remainingMessageCount = 0,
                ))
            },
        )
        val observed = mutableListOf<com.eleckoi.android.sdk.author.AuthorApiEvent>()
        val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.gateway.authorEvents.collect { event ->
                if (event.name == "messages.changed") observed += event
            }
        }

        val result = fixture.gateway.deleteMessagesFrom("user-1")
        yield()

        assertTrue(actionReturned)
        assertEquals(2, result.deletedMessageCount)
        assertEquals(0, result.remainingMessageCount)
        assertEquals(1, observed.size)
        val payload = observed.single().payload.jsonObject
        assertEquals("session-1", payload["conversationId"]!!.jsonPrimitive.content)
        assertEquals("deleted", payload["reason"]!!.jsonPrimitive.content)
        assertEquals(listOf("user-1", "reply-1"),
            payload["messageIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertFalse("sessionId" in payload)
        subscription.cancel()
        fixture.scope.cancel()
    }

    @Test
    fun `image-only author send keeps the PC attachment contract`() = runBlocking {
        val image = AuthorSendImageAttachment(
            mediaType = "image/png",
            data = "cG5n",
            name = "frame.png",
        )
        var dispatched: Pair<String, List<AuthorSendImageAttachment>>? = null
        val fixture = fixture(
            initialState = ChatUiState(draft = openingDraft("opening-1")),
            onSend = { text, attachments ->
                dispatched = text to attachments
                Result.success(Unit)
            },
        )

        val result = fixture.gateway.send("", listOf(image))

        assertTrue(result.accepted)
        assertEquals("" to listOf(image), dispatched)
        fixture.scope.cancel()
    }

    @Test
    fun `author message list exposes the reserved regeneration reply without persisting a fake row`() {
        val user = ChatMessage("user-1", MessageRole.User, "原文")
        val draft = openingDraft("opening-1").let { current ->
            current.copy(session = current.session.copy(messages = listOf(user)))
        }
        val fixture = fixture(
            initialState = ChatUiState(
                draft = draft,
                isSending = true,
                generationPresentation = ChatGenerationPresentation(
                    generation = 7,
                    sessionId = draft.session.id,
                    assistantMessageId = "reply-next",
                ),
            ),
        )

        val messages = fixture.gateway.snapshot().draft!!.session.messages

        assertEquals(listOf("user-1", "reply-next"), messages.map { it.id })
        assertTrue(messages.last().pending)
        assertEquals(listOf(user), fixture.state.value.draft?.session?.messages)
        fixture.scope.cancel()
    }

    private fun fixture(
        onSend: suspend (String, List<AuthorSendImageAttachment>) -> Result<Unit> = { _, _ ->
            Result.success(Unit)
        },
        initialState: ChatUiState = ChatUiState(),
        onSelectOpening: suspend (String, String) -> Result<ChatDraft> = { _, _ ->
            Result.failure(IllegalStateException("unused"))
        },
        onDeleteFrom: suspend (String, String) -> Result<ChatDeleteMessagesResult> = { _, _ ->
            Result.failure(IllegalStateException("unused"))
        },
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(initialState)
        val publisher = ChatAuthorEventPublisher(scope, state)
        val gateway = ChatAuthorGatewayAdapter(
            state = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            actions = ChatAuthorActions(
                send = onSend,
                stopGeneration = {},
                regenerate = {},
                submitEditedMessage = {},
                createChat = {},
                openChat = {},
                deleteChat = {},
                selectModel = { _: String, _: String -> },
                selectOpening = onSelectOpening,
                replaceVariableState = { _, _ ->
                    Result.failure<ChatDraft>(IllegalStateException("unused"))
                },
                resetVariableState = {
                    Result.failure<ChatDraft>(IllegalStateException("unused"))
                },
                deleteMessagesFrom = onDeleteFrom,
            ),
            publisher = publisher,
        )
        return Fixture(scope, state, gateway)
    }

    private fun openingDraft(selectedId: String) = ChatDraft(
        session = ChatSession(
            id = "session-1",
            title = "测试聊天",
            characterId = "character-1",
            characterName = "角色",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = emptyList(),
            updatedAt = "",
        ),
        selectedModelConfig = ModelConfig(),
        selectedModel = "test-model",
        openingOptions = listOf(
            ChatOpeningOption(id = "opening-1", title = "第一幕"),
            ChatOpeningOption(id = "opening-2", title = "第二幕"),
        ),
        selectedOpeningOptionId = selectedId,
        openingSelectionEnabled = true,
    )

    private data class Fixture(
        val scope: CoroutineScope,
        val state: MutableStateFlow<ChatUiState>,
        val gateway: ChatAuthorGatewayAdapter,
    )
}
