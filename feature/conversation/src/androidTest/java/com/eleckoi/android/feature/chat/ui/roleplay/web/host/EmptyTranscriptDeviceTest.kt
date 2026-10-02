package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.*
import com.eleckoi.android.feature.chat.ui.ChatRenderingPreferences
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.*
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatCallbacks
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.sdk.author.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EmptyTranscriptDeviceTest {
    @Test fun newChatWithoutMessagesCompletesBootstrapThroughProductionBridge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val failure = AtomicReference<RoleplayRendererFailure?>()
        var bootstrapCalls = 0
        val gateway = object : EmptyGateway() {
            override suspend fun invokeExtension(method: String, params: JsonObject): JsonElement {
                check(method == "plugins.bootstrap") { method }
                bootstrapCalls++
                return Json.parseToJsonElement("""{"conversationId":"empty-chat","characterId":"character","characterName":"AI","userName":"用户","presetId":"test","messages":[],"variables":{"chat":{},"message":{}},"settings":{},"metadata":{}}""")
            }
        }
        val draft = ChatDraft(session = ChatSession(id = "empty-chat", title = "empty", characterId = "character",
            characterName = "AI", characterAvatar = "", characterPersona = CharacterCard(userName = "用户", assistantName = "AI"),
            messages = emptyList(), updatedAt = "now"), selectedModelConfig = ModelConfig(), selectedModel = "model")
        val model = buildRoleplayTranscriptModel(draft = draft, messages = emptyList(), layoutMode = ChatLayoutMode.Roleplay,
            appearance = AppearanceTheme(), avatarShape = ChatAvatarShape.Portrait, avatarSize = 55f, nameFontSize = 15f,
            avatarGap = 10f, horizontalPadding = 10f, replySpacing = 4f, turnSpacing = 5f, messageFontSize = 14f,
            lineHeightMultiplier = 1f, letterSpacing = 0f, paragraphSpacing = 10f, cardPanel = false,
            renderingPreferences = ChatRenderingPreferences(), frontendRendererEnabled = true, historyHasMore = false, historyLoading = false)
        lateinit var host: RoleplayWebChatHost
        instrumentation.runOnMainSync {
            host = RoleplayWebChatHost(context, RoleplayWebChatCallbacks(onReady = {}, onMessageRendered = {},
                onScrollStateChanged = { _, _ -> }, onLoadOlder = {}, onSelectOpeningOption = {}, onRequestOpeningJump = {},
                onMessageAction = { _, _ -> }, onImageAction = { _, _, _ -> }, onUserAvatarClick = {}, onAssistantAvatarClick = {},
                onRendererUnavailable = { failure.set(it) }), gateway)
            host.webView.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.webView.layout(0, 0, 600, 800)
            host.bind(model)
        }
        suspend fun evaluate(script: String): JsonElement = suspendCancellableCoroutine { continuation ->
            instrumentation.runOnMainSync {
                host.webView.evaluateJavascript(script) { value ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(Json.parseToJsonElement(value)))
                }
            }
        }
        try {
            val settled = withTimeoutOrNull(15_000) {
                while (evaluate("window.__ElecKoiTranscript?.diagnostics().initialPresentationPhase").jsonPrimitive.contentOrNull != "committed") {
                    assertNull("Renderer failed: ${failure.get()}", failure.get())
                    delay(50)
                }
                true
            }
            assertTrue("Empty chat did not settle: " + evaluate("JSON.stringify({diagnostics:window.__ElecKoiTranscript?.diagnostics(),pending:window.__ElecKoiAuthorPendingCount?.()})"), settled == true)
            assertTrue(bootstrapCalls > 0)
            assertEquals(0, evaluate("window.__ElecKoiAuthorPendingCount()").jsonPrimitive.int)
            assertEquals("empty-chat", evaluate("SillyTavern.getContext().getCurrentChatId()").jsonPrimitive.content)
            assertNull(failure.get())
        } finally { instrumentation.runOnMainSync { host.release() } }
    }
}

private open class EmptyGateway : AuthorChatGateway {
    override val authorEvents = emptyFlow<AuthorApiEvent>()
    override fun snapshot() = AuthorChatSnapshot(AuthorChatDraftSnapshot(AuthorChatSessionSnapshot(
        "empty-chat", "empty", "character", "AI", messages = emptyList(), createdAt = "", updatedAt = "", variableStateJson = "{}"), "", ""), emptyList(), "", false, "", emptyList())
    override fun setInput(value: String) = AuthorCommandResult(true)
    override fun stopGeneration() = AuthorCommandResult(true)
    override fun regenerate(messageId: String) = AuthorCommandResult(true)
    override fun editAndRegenerate(messageId: String, text: String) = AuthorCommandResult(true)
    override suspend fun deleteMessagesFrom(messageId: String) = AuthorDeleteMessagesResult(0, 0)
    override fun createNewChat(characterId: String) = AuthorCommandResult(true)
    override fun openChat(sessionId: String) = AuthorCommandResult(true)
    override fun deleteChat(sessionId: String) = AuthorCommandResult(true)
    override fun selectModel(configId: String, model: String) = AuthorCommandResult(true)
    override suspend fun replaceVariableState(stateJson: String) = AuthorCommandResult(true)
    override suspend fun resetVariableState() = AuthorCommandResult(true)
    override suspend fun send(text: String, attachments: List<AuthorSendImageAttachment>) = AuthorCommandResult(true)
    override suspend fun selectOpening(openingOptionId: String) = AuthorCommandResult(true)
}
