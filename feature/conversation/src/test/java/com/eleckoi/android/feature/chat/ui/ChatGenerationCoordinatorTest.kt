package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.data.PreparedChatRegeneration
import com.eleckoi.android.feature.chat.data.ChatSendResult
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGenerationCoordinatorTest {
    @Test
    fun `saving an assistant edit persists it without starting generation`() {
        val originalReply = ChatMessage(
            id = "assistant-1",
            role = MessageRole.Assistant,
            content = "旧回复",
        )
        val session = ChatSession(
            id = "session-1",
            title = "",
            characterId = "",
            characterName = "",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = listOf(originalReply),
            updatedAt = "",
        )
        val originalDraft = ChatDraft(
            session = session,
            selectedModelConfig = ModelConfig(),
            selectedModel = "test-model",
        )
        val editedReply = originalReply.copy(content = "新回复")
        val updatedDraft = originalDraft.copy(
            session = session.copy(messages = listOf(editedReply)),
        )
        val updatedPublished = CountDownLatch(1)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader,
            arrayOf(ChatService::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "editChatMessage" -> {
                    assertEquals(session.id, arguments?.get(0))
                    assertEquals(originalReply.id, arguments?.get(1))
                    assertEquals(editedReply.content, arguments?.get(2))
                    updatedDraft
                }
                else -> error("Unexpected ChatService call in test: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(
            ChatUiState(
                draft = originalDraft,
                editingMessage = originalReply,
                editInput = editedReply.content,
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform ->
                state.updateAndGet(transform).also { next ->
                    if (next.draft == updatedDraft) updatedPublished.countDown()
                }
            },
            showModeConflictIfNeeded = { _, _ -> false },
            onStopRequested = {},
        )

        try {
            coordinator.saveEditedMessage()

            assertTrue("修改后的回复没有发布", updatedPublished.await(2, TimeUnit.SECONDS))
            assertEquals(updatedDraft, state.get().draft)
            assertEquals(null, state.get().editingMessage)
            assertEquals("", state.get().editInput)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `stale pending reply can regenerate when no request is active`() {
        val user = ChatMessage(id = "user-1", role = MessageRole.User, content = "重写")
        val oldReply = ChatMessage(
            id = "assistant-1",
            role = MessageRole.Assistant,
            content = "旧回复",
            pending = true,
        )
        val session = ChatSession(
            id = "session-1",
            title = "",
            characterId = "",
            characterName = "",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = listOf(user, oldReply),
            updatedAt = "",
        )
        val config = ModelConfig()
        val originalDraft = ChatDraft(
            session = session,
            selectedModelConfig = config,
            selectedModel = "test-model",
        )
        val truncatedDraft = originalDraft.copy(session = session.copy(messages = listOf(user)))
        val prepared = PreparedChatRegeneration(
            truncatedDraft = truncatedDraft,
            session = truncatedDraft.session,
            prompt = user.content,
            config = config,
            pendingMessageId = oldReply.id,
        )
        val modelTurnStarted = CountDownLatch(1)
        val releaseModelTurn = CountDownLatch(1)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader,
            arrayOf(ChatService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "prepareRegeneration" -> prepared
                "runPreparedRegeneration" -> {
                    modelTurnStarted.countDown()
                    releaseModelTurn.await(2, TimeUnit.SECONDS)
                    ChatSendResult(truncatedDraft)
                }
                "cancelActiveStream" -> Unit
                "isStreamCancelled" -> true
                else -> error("Unexpected ChatService call in test: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(ChatUiState(draft = originalDraft))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform -> state.updateAndGet(transform) },
            showModeConflictIfNeeded = { _, _ -> false },
            onStopRequested = {},
        )

        try {
            coordinator.regenerateFrom(oldReply)

            assertTrue("模型回合没有启动", modelTurnStarted.await(2, TimeUnit.SECONDS))
            assertEquals(
                "模型开始前应只保留用户消息，不能保留旧回复或创建空 AI 行",
                listOf(user),
                state.get().draft?.session?.messages,
            )
        } finally {
            releaseModelTurn.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `immediate stop cannot restore reply deleted by regeneration`() {
        val user = ChatMessage(id = "user-1", role = MessageRole.User, content = "重写")
        val oldReply = ChatMessage(id = "assistant-1", role = MessageRole.Assistant, content = "旧回复")
        val pendingReply = oldReply.copy(content = "", pending = true)
        val session = ChatSession(
            id = "session-1",
            title = "",
            characterId = "",
            characterName = "",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = listOf(user, oldReply),
            updatedAt = "",
        )
        val config = ModelConfig()
        val originalDraft = ChatDraft(
            session = session,
            selectedModelConfig = config,
            selectedModel = "test-model",
        )
        val truncatedDraft = originalDraft.copy(session = session.copy(messages = listOf(user)))
        val prepared = PreparedChatRegeneration(
            truncatedDraft = truncatedDraft,
            session = truncatedDraft.session,
            prompt = user.content,
            config = config,
            pendingMessageId = pendingReply.id,
        )
        val preparationStarted = CountDownLatch(1)
        val releasePreparation = CountDownLatch(1)
        val truncatedPublished = CountDownLatch(1)
        val modelTurnStarted = AtomicBoolean(false)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader,
            arrayOf(ChatService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "prepareRegeneration" -> {
                    preparationStarted.countDown()
                    releasePreparation.await(2, TimeUnit.SECONDS)
                    prepared
                }
                "runPreparedRegeneration" -> {
                    modelTurnStarted.set(true)
                    error("停止后不应启动模型")
                }
                "cancelActiveStream" -> Unit
                "isStreamCancelled" -> true
                else -> error("Unexpected ChatService call in test: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(ChatUiState(draft = originalDraft))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform ->
                state.updateAndGet(transform).also { next ->
                    if (next.draft?.session?.messages == listOf(user)) {
                        truncatedPublished.countDown()
                    }
                }
            },
            showModeConflictIfNeeded = { _, _ -> false },
            onStopRequested = {},
        )

        try {
            coordinator.regenerateFrom(oldReply)
            assertTrue(preparationStarted.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(user, oldReply), state.get().draft?.session?.messages)
            coordinator.stop()
            releasePreparation.countDown()

            assertTrue("旧 AI 回复没有在截断完成后立即撤下", truncatedPublished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(user), state.get().draft?.session?.messages)
            assertEquals(false, modelTurnStarted.get())
        } finally {
            releasePreparation.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `generation failure does not restore an already sent message into the composer`() {
        val sentText = "已经发送的消息"
        val draft = ChatDraft(
            session = ChatSession(
                id = "session-1",
                title = "",
                characterId = "",
                characterName = "",
                characterAvatar = "",
                characterPersona = CharacterCard(),
                messages = listOf(
                    ChatMessage(
                        id = "user-1",
                        role = MessageRole.User,
                        content = sentText,
                    ),
                ),
                updatedAt = "",
            ),
            selectedModelConfig = ModelConfig(),
            selectedModel = "test-model",
        )
        val state = AtomicReference(ChatUiState(draft = draft, input = sentText))
        val settled = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = failingChatService(IllegalStateException("模型失败")),
            state = state::get,
            updateState = { transform ->
                state.updateAndGet(transform).also { next ->
                    if (!next.isSending && next.errorMessage == "模型失败") settled.countDown()
                }
            },
            showModeConflictIfNeeded = { _, _ -> false },
            onStopRequested = {},
        )

        try {
            coordinator.send(sentText)

            assertTrue("生成失败状态未及时落定", settled.await(2, TimeUnit.SECONDS))
            assertEquals("", state.get().input)
            assertEquals("模型失败", state.get().errorMessage)
            assertEquals(null, state.get().generationPresentation)
            assertEquals(
                listOf(sentText),
                state.get().draft?.session?.messages?.map(ChatMessage::content),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `final reply retains generation identity when no pending frame is observed`() {
        val session = ChatSession(
            id = "session-1",
            title = "",
            characterId = "",
            characterName = "",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = emptyList(),
            updatedAt = "",
        )
        val originalDraft = ChatDraft(
            session = session,
            selectedModelConfig = ModelConfig(),
            selectedModel = "test-model",
        )
        val user = ChatMessage(id = "user-1", role = MessageRole.User, content = "继续")
        val reply = ChatMessage(
            id = "assistant-1",
            role = MessageRole.Assistant,
            content = "后台生成完成的回复",
            pending = false,
        )
        val finalDraft = originalDraft.copy(
            session = session.copy(messages = listOf(user, reply)),
        )
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader,
            arrayOf(ChatService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "sendMessage" -> ChatSendResult(finalDraft)
                "isStreamCancelled" -> false
                else -> error("Unexpected ChatService call in test: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(ChatUiState(draft = originalDraft, input = user.content))
        val settled = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform ->
                state.updateAndGet(transform).also { next ->
                    if (!next.isSending && next.draft == finalDraft) settled.countDown()
                }
            },
            showModeConflictIfNeeded = { _, _ -> false },
            onStopRequested = {},
        )

        try {
            coordinator.send(user.content)

            assertTrue("最终回复没有及时落定", settled.await(2, TimeUnit.SECONDS))
            val presentation = requireNotNull(state.get().generationPresentation)
            assertEquals(session.id, presentation.sessionId)
            assertEquals(reply.id, presentation.assistantMessageId)
            assertTrue(presentation.generation > 0)
        } finally {
            scope.cancel()
        }
    }

    private fun failingChatService(error: Throwable): ChatService {
        return Proxy.newProxyInstance(
            ChatService::class.java.classLoader,
            arrayOf(ChatService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "sendMessage" -> throw error
                "isStreamCancelled" -> false
                else -> error("Unexpected ChatService call in test: ${method.name}")
            }
        } as ChatService
    }
}
