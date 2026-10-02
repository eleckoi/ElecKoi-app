package com.eleckoi.android.sdk.author.plugins

import com.eleckoi.android.sdk.author.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.serialization.json.*

/** Resolve the chat binding at invocation time, rather than capturing the first screen forever. */
internal class LivePluginGateway(binding: StateFlow<AuthorChatGateway?>, private val current: () -> AuthorChatGateway) : AuthorChatGateway {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val authorEvents = binding.filterNotNull().flatMapLatest { it.authorEvents }
    override fun snapshot() = current().snapshot()
    override suspend fun invokeExtension(method: String, params: JsonObject) = current().invokeExtension(method, params)
    override fun setInput(value: String) = current().setInput(value)
    override fun stopGeneration() = current().stopGeneration()
    override fun regenerate(messageId: String) = current().regenerate(messageId)
    override fun editAndRegenerate(messageId: String, text: String) = current().editAndRegenerate(messageId, text)
    override suspend fun deleteMessagesFrom(messageId: String) = current().deleteMessagesFrom(messageId)
    override fun createNewChat(characterId: String) = current().createNewChat(characterId)
    override fun openChat(sessionId: String) = current().openChat(sessionId)
    override fun deleteChat(sessionId: String) = current().deleteChat(sessionId)
    override fun selectModel(configId: String, model: String) = current().selectModel(configId, model)
    override suspend fun replaceVariableState(stateJson: String) = current().replaceVariableState(stateJson)
    override suspend fun resetVariableState() = current().resetVariableState()
    override suspend fun send(text: String, attachments: List<AuthorSendImageAttachment>) = current().send(text, attachments)
    override suspend fun selectOpening(openingOptionId: String) = current().selectOpening(openingOptionId)
}
