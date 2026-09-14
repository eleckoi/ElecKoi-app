package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatOpeningOption
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ModelParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
    fun `send validation rejects blank or unloaded commands before dispatch`() {
        var dispatched = false
        val fixture = fixture(onSend = { dispatched = true })

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
    fun `direct edit accepts a settled assistant message without regenerating`() {
        val assistant = ChatMessage(
            id = "assistant-1",
            role = MessageRole.Assistant,
            content = "旧回复",
        )
        var saved = false
        val fixture = fixture(
            initialState = ChatUiState(
                draft = openingDraft(selectedId = "opening-1").let { draft ->
                    draft.copy(session = draft.session.copy(messages = listOf(assistant)))
                },
            ),
            onSaveEditedMessage = { saved = true },
        )

        val result = fixture.gateway.editMessage(assistant.id, "新回复")

        assertTrue(result.accepted)
        assertTrue(saved)
        assertEquals(assistant, fixture.state.value.editingMessage)
        assertEquals("新回复", fixture.state.value.editInput)
        fixture.scope.cancel()
    }

    private fun fixture(
        onSend: (String) -> Unit = {},
        initialState: ChatUiState = ChatUiState(),
        onSaveEditedMessage: () -> Unit = {},
        onSelectOpening: suspend (String, String) -> Result<ChatDraft> = { _, _ ->
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
                saveEditedMessage = onSaveEditedMessage,
                submitEditedMessage = {},
                createChat = { _, _ -> },
                openChat = {},
                deleteChat = {},
                selectModel = { _: String, _: String, _: ModelParameters -> },
                selectOpening = onSelectOpening,
                replaceVariableState = { _, _ ->
                    Result.failure<ChatDraft>(IllegalStateException("unused"))
                },
                resetVariableState = {
                    Result.failure<ChatDraft>(IllegalStateException("unused"))
                },
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
