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
import java.util.concurrent.atomic.AtomicInteger
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
    fun `repeated assistant edits in a long chat persist only the selected message`() {
        val messages = (0 until 300).flatMap { index ->
            listOf(
                ChatMessage("user-$index", MessageRole.User, "问题 $index"),
                ChatMessage("assistant-$index", MessageRole.Assistant, "回复 $index"),
            )
        }
        val target = messages.first { it.id == "assistant-150" }
        val session = ChatSession(
            id = "session-1", title = "", characterId = "", characterName = "",
            characterAvatar = "", characterPersona = CharacterCard(),
            messages = messages, updatedAt = "",
        )
        val draft = ChatDraft(session = session, selectedModelConfig = ModelConfig(), selectedModel = "model")
        val calls = AtomicReference<List<Triple<String, String, String>>>(emptyList())
        val eventCount = AtomicInteger(0)
        val firstSaved = CountDownLatch(1)
        val secondSaved = CountDownLatch(1)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader, arrayOf(ChatService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "editAssistantMessage" -> {
                    calls.updateAndGet {
                        it + Triple(args!![0] as String, args[1] as String, args[2] as String)
                    }
                    Unit
                }
                else -> error("Unexpected ChatService call: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(
            ChatUiState(draft = draft, editingMessage = target, editInput = "第一次修改"),
        )
        val events = AtomicReference<List<Triple<String, String, List<String>>>>(emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform -> state.updateAndGet(transform) },
            onStopRequested = {},
            onMessagesChanged = { id, reason, ids ->
                events.updateAndGet { it + Triple(id, reason, ids) }
                if (eventCount.incrementAndGet() == 1) firstSaved.countDown() else secondSaved.countDown()
            },
        )

        try {
            coordinator.saveEditedMessage()
            assertTrue(firstSaved.await(2, TimeUnit.SECONDS))
            assertTrue("保存 AI 消息不应复制或替换 Paging 时间线", state.get().draft === draft)

            state.updateAndGet {
                it.copy(editingMessage = target, editInput = "第二次修改", isSavingEditedMessage = false)
            }
            coordinator.saveEditedMessage()
            assertTrue(secondSaved.await(2, TimeUnit.SECONDS))

            assertEquals(
                listOf(
                    Triple("session-1", "assistant-150", "第一次修改"),
                    Triple("session-1", "assistant-150", "第二次修改"),
                ),
                calls.get(),
            )
            assertEquals(
                listOf(
                    Triple("session-1", "edited", listOf("assistant-150")),
                    Triple("session-1", "edited", listOf("assistant-150")),
                ),
                events.get(),
            )
            assertEquals(600, state.get().draft?.session?.messages?.size)
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
        val changed = AtomicReference<List<Triple<String, String, List<String>>>>(emptyList())
        val publishedAssistantIds = AtomicReference<List<String>>(emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope,
            chatService = service,
            state = state::get,
            updateState = { transform -> state.updateAndGet(transform) },
            onStopRequested = {},
            onMessagesChanged = { conversationId, reason, ids ->
                changed.updateAndGet { it + Triple(conversationId, reason, ids) }
                state.get().generationPresentation?.assistantMessageId?.let { messageId ->
                    publishedAssistantIds.updateAndGet { it + messageId }
                }
            },
        )

        try {
            coordinator.regenerateFrom(oldReply)

            assertTrue("模型回合没有启动", modelTurnStarted.await(2, TimeUnit.SECONDS))
            assertEquals(
                listOf(Triple("session-1", "regenerated", listOf("user-1", "assistant-1"))),
                changed.get(),
            )
            assertEquals(listOf("assistant-1"), publishedAssistantIds.get())
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
    fun `sent is published once after the user turn is persisted not after the model finishes`() {
        val session = ChatSession(
            id = "session-1", title = "", characterId = "", characterName = "",
            characterAvatar = "", characterPersona = CharacterCard(),
            messages = emptyList(), updatedAt = "",
        )
        val draft = ChatDraft(session = session, selectedModelConfig = ModelConfig(), selectedModel = "model")
        val user = ChatMessage("user-1", MessageRole.User, "你好")
        val persisted = draft.copy(session = session.copy(messages = listOf(user)))
        val releaseModel = CountDownLatch(1)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader, arrayOf(ChatService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "sendMessage" -> {
                    @Suppress("UNCHECKED_CAST")
                    val persistedCallback = args!![4] as (ChatDraft, String) -> Unit
                    persistedCallback(persisted, user.id)
                    releaseModel.await(2, TimeUnit.SECONDS)
                    ChatSendResult(persisted)
                }
                "isStreamCancelled" -> false
                else -> error("Unexpected ChatService call: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(ChatUiState(draft = draft))
        val event = AtomicReference<List<Triple<String, String, List<String>>>>(emptyList())
        val emitted = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope, chatService = service, state = state::get,
            updateState = { transform -> state.updateAndGet(transform) },
            onStopRequested = {},
            onMessagesChanged = { id, reason, ids ->
                event.updateAndGet { it + Triple(id, reason, ids) }
                emitted.countDown()
            },
        )
        try {
            coordinator.send("你好")
            assertTrue(emitted.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(Triple("session-1", "sent", listOf("user-1"))), event.get())
            assertEquals(listOf(user), state.get().draft?.session?.messages)
            releaseModel.countDown()
        } finally {
            releaseModel.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `editing the user text reports edited rather than regenerated`() {
        val user = ChatMessage("user-1", MessageRole.User, "原文")
        val oldReply = ChatMessage("reply-1", MessageRole.Assistant, "旧回复")
        val session = ChatSession(
            id = "session-1", title = "", characterId = "", characterName = "",
            characterAvatar = "", characterPersona = CharacterCard(),
            messages = listOf(user, oldReply), updatedAt = "",
        )
        val draft = ChatDraft(session = session, selectedModelConfig = ModelConfig(), selectedModel = "model")
        val editedUser = user.copy(content = "新原文")
        val truncated = draft.copy(session = session.copy(messages = listOf(editedUser)))
        val prepared = PreparedChatRegeneration(
            truncatedDraft = truncated, session = truncated.session,
            prompt = editedUser.content, config = ModelConfig(), pendingMessageId = "new-reply-1",
        )
        val modelStarted = CountDownLatch(1)
        val releaseModel = CountDownLatch(1)
        val service = Proxy.newProxyInstance(
            ChatService::class.java.classLoader, arrayOf(ChatService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "prepareRegeneration" -> prepared
                "runPreparedRegeneration" -> {
                    modelStarted.countDown()
                    releaseModel.await(2, TimeUnit.SECONDS)
                    ChatSendResult(truncated)
                }
                "isStreamCancelled" -> false
                else -> error("Unexpected ChatService call: ${method.name}")
            }
        } as ChatService
        val state = AtomicReference(ChatUiState(draft = draft, editingMessage = user, editInput = "新原文"))
        val events = AtomicReference<List<Triple<String, String, List<String>>>>(emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChatGenerationCoordinator(
            scope = scope, chatService = service, state = state::get,
            updateState = { transform -> state.updateAndGet(transform) },
            onStopRequested = {},
            onMessagesChanged = { id, reason, ids ->
                events.updateAndGet { it + Triple(id, reason, ids) }
            },
        )
        try {
            coordinator.submitEditedMessage()
            assertTrue(modelStarted.await(2, TimeUnit.SECONDS))
            assertEquals(
                listOf(Triple("session-1", "edited", listOf("user-1", "new-reply-1"))),
                events.get(),
            )
            assertEquals("新原文", state.get().draft?.session?.messages?.lastOrNull()?.content)
        } finally {
            releaseModel.countDown()
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
