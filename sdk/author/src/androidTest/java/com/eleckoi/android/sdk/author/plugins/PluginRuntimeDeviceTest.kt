package com.eleckoi.android.sdk.author.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eleckoi.android.sdk.author.*
import com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline
import com.eleckoi.android.engine.creator.plugins.PluginGenerationClient
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import android.view.View
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse

@RunWith(AndroidJUnit4::class)
class PluginRuntimeDeviceTest {
    @Test fun apiProbeZipResourcesEntryDashboardAndRpcAssertionsRunInRealWebView() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = PluginStore(context)
        val id = "eleckoi-api-probe"
        store.list("kv:$id").keys.forEach { store.delete("kv:$id", it) }
        // Reproduce startup with an existing report larger than Android's CursorWindow.
        store.put("kv:$id", "probe-report", buildJsonObject {
            put("version", "0.1.0")
            put("tests", buildJsonArray { add(buildJsonObject { put("name", "interrupted"); put("status", "running") }) })
            put("calls", buildJsonArray { add(buildJsonObject { put("method", "large-previous-result"); put("ok", true); put("result", "🐳".repeat(800_000)) }) })
        })
        val events = MutableSharedFlow<AuthorApiEvent>(extraBufferCapacity = 32)
        val testScope = this
        var activeConversation: String? = "chat-test"
        lateinit var runtime: AuthorPluginRuntime
        var panelView: WebView? = null
        var panelHost: AuthorPluginPanelWebView? = null
        val gateway = object : AuthorChatGateway by NoopGateway() {
            override val authorEvents = events
            override fun snapshot(): AuthorChatSnapshot {
                val base = testSnapshot()
                val draft = base.draft!!.let { it.copy(session = it.session.copy(id = activeConversation.orEmpty())) }
                return base.copy(draft = if (activeConversation == null) null else draft)
            }
            override fun createNewChat(characterId: String): AuthorCommandResult {
                activeConversation = null
                testScope.launch {
                    delay(120)
                    activeConversation = "chat-created"
                    events.emit(AuthorApiEvent("chat.changed", buildJsonObject { put("conversationId", "chat-created") }))
                }
                return AuthorCommandResult(true)
            }
            override suspend fun invokeExtension(method: String, params: JsonObject): JsonElement = when (method) {
                "plugins.bootstrap" -> {
                    checkNotNull(activeConversation) { "聊天尚未就绪" }
                    JsonObject(testBootstrap() + ("conversationId" to JsonPrimitive(activeConversation!!)))
                }
                "plugins.runtimeReady" -> { runtime.runtimeReady(id); JsonNull }
                "storage.get" -> store.get("kv:$id", params.getValue("key").jsonPrimitive.content)
                "storage.set" -> { store.put("kv:$id", params.getValue("key").jsonPrimitive.content, params.getValue("value")); JsonNull }
                "storage.delete" -> JsonPrimitive(store.delete("kv:$id", params.getValue("key").jsonPrimitive.content))
                "ui.register" -> { PluginUiRegistry.register(id, params.getValue("descriptor").jsonObject); JsonNull }
                "files.saveText" -> PluginFileExport.saveText(params.getValue("name").jsonPrimitive.content,
                    params.getValue("mimeType").jsonPrimitive.content, params.getValue("text").jsonPrimitive.content)
                "plugins.emitEvent" -> {
                    events.emit(AuthorApiEvent("plugin.event", buildJsonObject {
                        put("pluginId", id); put("event", params.getValue("event")); put("payload", params["payload"] ?: JsonNull)
                    })); JsonNull
                }
                else -> error("Unimplemented probe test route: $method")
            }
        }
        val resources = buildJsonObject {
            context.assets.list("api-probe")!!.forEach { name ->
                val bytes = context.assets.open("api-probe/$name").use { it.readBytes() }
                val encoded = JsonPrimitive(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                put(name, encoded)
                store.put("resources:$id", name, encoded)
            }
        }
        val source = context.assets.open("api-probe/plugin.js").bufferedReader().use { it.readText() }
        store.put("plugins", id, buildJsonObject { put("id", id); put("source", source); put("enabled", true); put("resources", resources) })
        fun report() = store.get("kv:$id", "probe-report")
        suspend fun waitFor(description: String, condition: () -> Boolean) {
            withTimeout(40_000) {
                while (!condition()) {
                    check(runtime.errors[id] == null) { "$description: ${runtime.errors[id]}; ${runtime.diagnostics}" }
                    delay(50)
                }
            }
        }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { runtime = AuthorPluginRuntime(context, store); runtime.attach(gateway) }
            waitFor("probe entry / resources did not load") { (report() as? JsonObject)?.get("routes") is JsonArray }
            assertEquals(108, report().jsonObject.getValue("routes").jsonArray.size)
            assertFalse(report().jsonObject.getValue("running").jsonPrimitive.boolean)
            assertEquals("fail", report().jsonObject.getValue("tests").jsonArray.first().jsonObject.getValue("status").jsonPrimitive.content)
            assertEquals(4, PluginUiRegistry.items.value.count { it["pluginId"] == JsonPrimitive(id) })
            assertTrue(PluginUiRegistry.items.value.first { it["id"] == JsonPrimitive("api-probe-panel") }.getValue("html").jsonPrimitive.content.contains("panel.js"))
            suspend fun command(commandId: String, method: String, params: JsonObject, expected: JsonElement): JsonObject {
                events.emit(AuthorApiEvent("plugin.event", buildJsonObject {
                    put("pluginId", id); put("event", "probe-command"); put("payload", buildJsonObject {
                        put("id", commandId); put("action", "invoke"); put("kind", "route"); put("name", method);
                        put("params", params); put("expected", expected); put("hasExpected", true)
                    })
                }))
                waitFor("probe command $commandId not completed") { store.get("kv:$id", "probe-command-$commandId") is JsonObject }
                return store.get("kv:$id", "probe-command-$commandId").jsonObject
            }
            val value = buildJsonObject { put("text", "中文 \"quotes\" 🐳"); put("count", 2) }
            assertTrue(command("set", "storage.set", buildJsonObject { put("key", "device-probe"); put("value", value) }, JsonNull).getValue("ok").jsonPrimitive.boolean)
            assertTrue(command("get", "storage.get", buildJsonObject { put("key", "device-probe") }, value).getValue("ok").jsonPrimitive.boolean)
            val failed = command("wrong", "storage.get", buildJsonObject { put("key", "device-probe") }, JsonPrimitive("wrong"))
            assertFalse(failed.getValue("ok").jsonPrimitive.boolean)
            val rows = report().jsonObject.getValue("routes").jsonArray.map { it.jsonObject }
            assertEquals("fail", rows.first { it.getValue("name") == JsonPrimitive("storage.get") }.getValue("status").jsonPrimitive.content)
            assertEquals("untested", rows.first { it.getValue("name") == JsonPrimitive("generation.invoke") }.getValue("status").jsonPrimitive.content)

            // Use the exact production panel loader, including SDK injection and local resources.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val html = context.assets.open("api-probe/panel.html").bufferedReader().use { it.readText() }
                panelHost = AuthorPluginPanelWebView(context, gateway, id, html)
                panelView = panelHost!!.webView.apply {
                    measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 600, 800)
                }
            }
            suspend fun evaluate(script: String): JsonElement = suspendCancellableCoroutine { continuation ->
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    panelView!!.evaluateJavascript(script) { value ->
                        if (continuation.isActive) continuation.resumeWith(Result.success(Json.parseToJsonElement(value)))
                    }
                }
            }
            val dashboardReady = withTimeoutOrNull(15_000) {
                while (evaluate("document.querySelectorAll('#routes tr').length").jsonPrimitive.intOrNull != 108) delay(50)
                true
            }
            assertTrue("Dashboard did not render: " + evaluate("JSON.stringify({url:location.href,state:document.readyState,error:document.getElementById('error')?.textContent,html:document.body?.innerHTML?.slice(0,200),sdk:typeof ElecKoi})"), dashboardReady == true)
            assertEquals("", evaluate("document.getElementById('error').textContent").jsonPrimitive.content)
            evaluate("document.querySelector('[data-action=prepare]').click();true")
            val prepared = withTimeoutOrNull(15_000) {
                while ((report().jsonObject["workspace"] as? JsonObject)?.get("conversationId") != JsonPrimitive("chat-created")) delay(50)
                true
            }
            assertTrue("Create did not await draft readiness: " + evaluate("document.getElementById('error').textContent"), prepared == true)
            assertTrue(report().jsonObject.getValue("calls").jsonArray.none { it.jsonObject["error"] == JsonPrimitive("Error: 聊天尚未就绪") })
            assertEquals("", evaluate("document.getElementById('error').textContent").jsonPrimitive.content)
            evaluate("window.__probeFavicon=null;fetch('/favicon.ico').then(r=>window.__probeFavicon=r.status,e=>window.__probeFavicon=String(e));true")
            withTimeout(10_000) { while (evaluate("window.__probeFavicon") == JsonNull) delay(50) }
            assertEquals(200, evaluate("window.__probeFavicon").jsonPrimitive.int)
            assertEquals("${AuthorPluginRuntime.Origin}/panel/$id/index.html", evaluate("location.href").jsonPrimitive.content)
            assertTrue(evaluate("document.getElementById('viewport').textContent").jsonPrimitive.content.contains("100vh="))
            evaluate("document.getElementById('params').value=JSON.stringify({key:'device-probe'});document.getElementById('expected').value=JSON.stringify(${value});document.getElementById('invoke').click();true")
            val commandCompleted = withTimeoutOrNull(15_000) {
                while (report().jsonObject.getValue("routes").jsonArray.map { it.jsonObject }.first { it.getValue("name") == JsonPrimitive("storage.get") }.getValue("status") != JsonPrimitive("pass")) delay(50)
                true
            }
            assertTrue("Dashboard command not completed: " + evaluate("JSON.stringify({error:document.getElementById('error')?.textContent,command:document.getElementById('command-state')?.textContent,output:document.getElementById('output')?.textContent})"), commandCompleted == true)
            assertEquals("", evaluate("document.getElementById('error').textContent").jsonPrimitive.content)
            evaluate("window.__burst=null;(async()=>{for(let i=0;i<180;i++)await ElecKoi.storage.kv.get('device-probe');window.__burst=180})().catch(e=>window.__burst=String(e));true")
            withTimeout(30_000) { while (evaluate("window.__burst") == JsonNull) delay(50) }
            assertEquals(180, evaluate("window.__burst").jsonPrimitive.int)
            PluginFileExport.attach()
            evaluate("document.getElementById('export').click();true")
            val file = withTimeout(15_000) { while (PluginFileExport.request.value == null) delay(50); PluginFileExport.request.value!! }
            assertEquals("application/json", file.mimeType)
            assertEquals(108, Json.parseToJsonElement(file.text).jsonObject.getValue("routes").jsonArray.size)
            PluginFileExport.finish(file, context.contentResolver, null)
            withTimeout(10_000) { while (evaluate("document.getElementById('command-state').textContent").jsonPrimitive.content != "已取消文件保存") delay(50) }
            assertEquals("", evaluate("document.getElementById('error').textContent").jsonPrimitive.content)
        } finally {
            PluginFileExport.detach()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { panelHost?.close(); runtime.stop(id) }
            store.delete("plugins", id)
        }
    }
    @Test fun memoryExampleExtractsOverHttpPersistsInjectsAndRestoresAfterRuntimeRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = PluginStore(context)
        val id = "memory-${System.nanoTime()}"
        val source = context.assets.open("plugin.js").bufferedReader().use { it.readText() }
        val events = MutableSharedFlow<AuthorApiEvent>(extraBufferCapacity = 16)
        val server = MockWebServer()
        server.start()
        val modelConfig = ModelConfig(model = "test", baseUrl = server.url("/").toString().trimEnd('/'), apiFormat = ModelApiFormat.ChatCompletions)
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"memory\":\"Alice reached the tower\"}"}}]}"""))
        lateinit var runtime: AuthorPluginRuntime
        val gateway = object : AuthorChatGateway by NoopGateway() {
            override val authorEvents = events
            override fun snapshot() = testSnapshot()
            override suspend fun invokeExtension(method: String, params: JsonObject): JsonElement = when (method) {
                "plugins.bootstrap" -> testBootstrap()
                "plugins.runtimeReady" -> { runtime.runtimeReady(id); JsonNull }
                "plugins.hookResult" -> { runtime.completeHook(params); JsonNull }
                "storage.sql", "storage.transaction" -> store.sql(id, params.getValue("database").jsonPrimitive.content,
                    params.getValue("statements").jsonArray, method == "storage.transaction")
                "settings.get" -> buildJsonObject {}
                "messages.read" -> testBootstrap().getValue("messages")
                "generation.invoke" -> PluginGenerationClient().invoke(modelConfig, params) { _, _ -> }
                "ui.register" -> { PluginUiRegistry.register(id, params.getValue("descriptor").jsonObject); JsonNull }
                "prompts.set" -> { PluginPromptPipeline.set(id, params.getValue("entries").jsonArray); JsonNull }
                else -> error("Unimplemented test capability: $method")
            }
        }
        val manifest = buildJsonObject { put("source", source); put("enabled", true) }
        store.put("plugins", id, manifest)
        fun rows() = store.sql(id, "memory.db", Json.parseToJsonElement("""[{"sql":"SELECT * FROM memories"}]""").jsonArray, false).single().jsonObject.getValue("rows").jsonArray
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { runtime = AuthorPluginRuntime(context, store); runtime.attach(gateway) }
            runtime.beforeGeneration("chat-test", "chat")
            assertEquals(3, PluginUiRegistry.items.value.count { it["pluginId"] == JsonPrimitive(id) })
            events.emit(AuthorApiEvent("agent.run.finished", Json.parseToJsonElement("""{"conversationId":"chat-test","runId":"run-1","message":{"id":"ai-test"}}""").jsonObject))
            withTimeout(20_000) { while (rows().isEmpty()) {
                check(runtime.errors[id] == null) { "运行时错误：${runtime.errors[id]}；HTTP 请求=${server.requestCount}；${runtime.diagnostics}" }
                delay(50)
            } }
            assertEquals("Alice reached the tower", rows().single().jsonObject.getValue("content").jsonPrimitive.content)
            val request = Json.parseToJsonElement(checkNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)).body.readUtf8()).jsonObject
            assertEquals("Alice reached the tower.", request.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content)
            runtime.beforeGeneration("chat-test", "chat")
            val injection = PluginPromptPipeline.snapshot("chat-test").single()
            assertTrue(injection.content.contains("Alice reached the tower")); assertEquals(1, injection.historyDepth)
            assertTrue(PluginPromptPipeline.snapshot("chat-test").isEmpty())
            runtime.flushAndStop(id)
            assertTrue(PluginUiRegistry.items.value.none { it["pluginId"] == JsonPrimitive(id) })
            InstrumentationRegistry.getInstrumentation().runOnMainSync { runtime = AuthorPluginRuntime(context, PluginStore(context)); runtime.attach(gateway) }
            runtime.beforeGeneration("chat-test", "chat")
            assertTrue(PluginPromptPipeline.snapshot("chat-test").single().content.contains("Alice reached the tower"))
            events.emit(AuthorApiEvent("agent.run.finished", Json.parseToJsonElement("""{"conversationId":"chat-test","runId":"run-1","message":{"id":"ai-test"}}""").jsonObject))
            delay(150)
            assertEquals(1, server.requestCount)
            assertEquals(1, rows().size)
            runtime.flushAndStop(id)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { runtime.stop(id) }
            store.delete("plugins", id)
            server.shutdown()
        }
    }
    @Test fun realWebViewEntryAndBeforeGenerationUseNativeBridgeAndPersistentStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = PluginStore(context)
        val id = "runtime-${System.nanoTime()}"
        lateinit var runtime: AuthorPluginRuntime
        val gateway = object : AuthorChatGateway {
            override val authorEvents = emptyFlow<AuthorApiEvent>()
            override fun snapshot() = AuthorChatSnapshot(AuthorChatDraftSnapshot(
                AuthorChatSessionSnapshot("chat-test", "test", "character-test", "Role", messages = emptyList(), createdAt = "", updatedAt = "", variableStateJson = "{}"), "", ""), emptyList(), "", false, "", emptyList())
            override suspend fun invokeExtension(method: String, params: JsonObject): JsonElement = when (method) {
                "plugins.bootstrap" -> Json.parseToJsonElement("""{"conversationId":"chat-test","characterId":"character-test","characterName":"Role","userName":"User","presetId":"preset-test","messages":[],"variables":{"chat":{},"message":{}},"settings":{},"metadata":{}}""")
                "plugins.runtimeReady" -> { runtime.runtimeReady(id); JsonNull }
                "plugins.hookResult" -> { runtime.completeHook(params); JsonNull }
                "storage.set" -> { store.put("kv:$id", params.getValue("key").jsonPrimitive.content, params.getValue("value")); JsonNull }
                "storage.get" -> store.get("kv:$id", params.getValue("key").jsonPrimitive.content)
                "prompts.set" -> JsonNull
                else -> error("Unimplemented test capability: $method")
            }
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = AuthorPluginRuntime(context, store)
            runtime.attach(gateway)
            runtime.start(id, buildJsonObject {
                put("source", """
                    await ElecKoi.storage.kv.set('started', true);
                    ElecKoi.prompt.beforeGeneration(async()=>{
                      await ElecKoi.storage.kv.set('before', 'committed');
                    });
                """.trimIndent())
            })
        }
        try {
            withTimeout(40_000) { runtime.beforeGeneration("chat-test", "chat") }
            assertEquals(JsonPrimitive(true), store.get("kv:$id", "started"))
            assertEquals(JsonPrimitive("committed"), PluginStore(context).get("kv:$id", "before"))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { runtime.stop(id) }
        }
    }
}

private fun testSnapshot() = AuthorChatSnapshot(AuthorChatDraftSnapshot(
    AuthorChatSessionSnapshot("chat-test", "test", "character-test", "Role", messages = emptyList(), createdAt = "", updatedAt = "", variableStateJson = "{}"), "", ""), emptyList(), "", false, "", emptyList())
private fun testBootstrap() = Json.parseToJsonElement("""{"conversationId":"chat-test","characterId":"character-test","characterName":"Role","userName":"User","presetId":"preset-test","messages":[{"id":"ai-test","role":"assistant","content":"Alice reached the tower.","metadata":{},"pending":false}],"variables":{"chat":{},"message":{}},"settings":{},"metadata":{}}""").jsonObject
private open class NoopGateway : AuthorChatGateway {
    override val authorEvents = emptyFlow<AuthorApiEvent>()
    override fun snapshot() = testSnapshot()
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
