package com.eleckoi.android.app.service

import android.content.Context
import com.eleckoi.android.engine.creator.plugins.*
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.presets.data.AgentPresetRepository
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.model.*
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.sdk.author.*
import com.eleckoi.android.sdk.author.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/** Application-owned implementation of the portable plugin protocol. */
internal class AuthorPluginService(
    context: Context,
    private val sessions: ChatSessionStore,
    private val characters: CharacterRepository,
    private val models: ModelConfigRepository,
    private val libraries: SettingLibraryRepository,
    private val presets: AgentPresetRepository,
    private val regex: RegexRuleRepository,
    private val profiles: com.eleckoi.android.feature.characters.data.UserProfileRepository,
    private val variableConfig: com.eleckoi.android.engine.story.variables.config.VariableConfigRepository,
    private val variableRuntime: com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService,
    private val invalidateRuntime: (Set<String>) -> Unit,
) {
    private val store = PluginStore(context)
    private val runtime = AuthorPluginRuntime(context, store)
    private val generation = PluginGenerationClient()
    private val networkClient = OkHttpClient.Builder().readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
    private val networkCalls = java.util.concurrent.ConcurrentHashMap<String, Call>()
    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = java.util.concurrent.ConcurrentHashMap<String, JsonObject>()
    private val taskJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val taskOwners = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val mutableEvents = MutableSharedFlow<AuthorApiEvent>(extraBufferCapacity = 128)
    val events: Flow<AuthorApiEvent> = mutableEvents.asSharedFlow()

    fun attach(gateway: AuthorChatGateway) {
        runtime.attach(gateway)
        PluginPromptPipeline.beforeGeneration = { id, purpose, inputs ->
            runtime.beforeGeneration(id, purpose)
            refreshWorldbookPrompts(id, inputs)
        }
    }
    fun detach(gateway: AuthorChatGateway) = runtime.detach(gateway)

    private fun contextId(params: JsonObject): String = params.text("conversationId").ifBlank {
        checkNotNull(runtime.gateway?.snapshot()?.draft?.session?.id) { "聊天尚未就绪" }
    }
    private fun characterId(params: JsonObject): String = params.text("characterId").ifBlank {
        sessions.load(contextId(params), false).characterId
    }
    private fun owner(params: JsonObject): String = params.text("pluginId").ifBlank { "frontend" }
    private fun variableScope(params: JsonObject): String = when (val scope = params.text("scope").ifBlank { "chat" }) {
        "global" -> "variables:global"
        "chat" -> "variables:chat:${contextId(params)}"
        "message" -> "variables:message:${contextId(params)}:${params.getValue("messageId").jsonPrimitive.content}"
        "character" -> "variables:character:${characterId(params)}"
        "preset" -> "variables:preset:${params.text("presetId").ifBlank { params.text("activePresetId") }}"
        "script", "extension", "plugin" -> "variables:$scope:${owner(params)}"
        else -> error("未知变量作用域：$scope")
    }

    suspend fun invoke(method: String, params: JsonObject): JsonElement {
        val plugin = owner(params)
        if (method == "files.saveText") return PluginFileExport.saveText(
            params.text("name"), params.text("mimeType").ifBlank { "text/plain" }, params.getValue("text").jsonPrimitive.content,
        )
        if (method == "plugins.runtimeReady") { runtime.runtimeReady(plugin); return JsonNull }
        if (method == "plugins.openManager") { PluginUiRegistry.managerOpen.value = true; return JsonNull }
        if (method == "plugins.emitEvent") {
            mutableEvents.emit(AuthorApiEvent("plugin.event", buildJsonObject { put("pluginId", plugin); put("event", params.getValue("event")); put("payload", params["payload"] ?: JsonNull) }))
            return JsonNull
        }
        if (method == "plugins.hookResult") { runtime.completeHook(params); return JsonNull }
        if (method == "generation.start") {
            val id = params.text("id").ifBlank { UUID.randomUUID().toString() }
            val running = buildJsonObject { put("id", id); put("status", "running") }
            check(tasks.putIfAbsent(id, running) == null) { "生成 id 已存在：$id" }
            taskOwners[id] = plugin
            val job = taskScope.launch(start = CoroutineStart.LAZY) {
                try { val result = invokeGeneration(JsonObject(params + ("id" to JsonPrimitive(id))))
                    tasks[id] = buildJsonObject { put("id", id); put("status", "completed"); put("result", result) }
                } catch (error: Throwable) {
                    tasks[id] = buildJsonObject { put("id", id); put("status", if (error is CancellationException) "cancelled" else "failed"); put("error", error.message ?: error.toString()) }
                } finally { taskJobs.remove(id); taskOwners.remove(id) }
            }
            taskJobs[id] = job; job.start()
            return running
        }
        if (method == "generation.get") return checkNotNull(tasks[params.text("id")]) { "生成任务不存在：${params.text("id")}" }
        if (method == "generation.cancel") {
            val id = params.text("id")
            val callCancelled = generation.cancel(id)
            val job = taskJobs[id]
            job?.cancel()
            return JsonPrimitive(callCancelled || job != null)
        }
        if (method == "network.cancel") return JsonPrimitive(networkCalls[params.text("id")]?.let { it.cancel(); true } ?: false)
        if (method == "ui.register") { PluginUiRegistry.register(plugin, params.getValue("descriptor").jsonObject); return JsonNull }
        if (method == "ui.unregister") { PluginUiRegistry.unregister(plugin, params.text("id")); return JsonNull }
        if (method == "ui.open") { PluginUiRegistry.open(plugin, params.text("id")); return JsonNull }
        if (method == "ui.list") return JsonArray(PluginUiRegistry.items.value)
        if (method == "plugins.install" || method == "plugins.setEnabled" || method == "plugins.remove") {
            val id = params.text("id").ifBlank { plugin }
            runtime.flushAndStop(id)
            taskOwners.filterValues { it == id }.keys.forEach { taskId -> generation.cancel(taskId); taskJobs[taskId]?.cancel() }
            val manifest = withContext(Dispatchers.IO) {
                if (method == "plugins.remove") { store.delete("plugins", id); null } else {
                    val value = if (method == "plugins.install") params.getValue("manifest").jsonObject else
                        JsonObject(store.get("plugins", id).jsonObject + ("enabled" to params.getValue("enabled")))
                    require(value.text("source").isNotBlank()) { "插件缺少 entry JavaScript" }
                    (value["resources"] as? JsonObject)?.forEach { (path, bytes) -> store.put("resources:$id", path, bytes) }
                    store.put("plugins", id, value); value
                }
            }
            withContext(Dispatchers.Main.immediate) {
                if (manifest == null || manifest["enabled"]?.jsonPrimitive?.booleanOrNull == false) runtime.stop(id) else runtime.start(id, manifest)
            }
            return JsonPrimitive(true)
        }
        return withContext(Dispatchers.IO) {
            when (method) {
                "plugins.list" -> JsonObject(store.list("plugins").mapValues { (id, value) ->
                    JsonObject(value.jsonObject + ("error" to JsonPrimitive(runtime.errors[id].orEmpty())))
                })
                "plugins.bootstrap" -> bootstrap(params)
                "storage.get" -> store.get("kv:$plugin", params.text("key"))
                "storage.set" -> { store.put("kv:$plugin", params.text("key"), params.getValue("value")); JsonNull }
                "storage.delete" -> JsonPrimitive(store.delete("kv:$plugin", params.text("key")))
                "storage.list" -> store.list("kv:$plugin")
                "storage.sql", "storage.transaction" -> store.sql(plugin, params.text("database").ifBlank { "default.db" },
                    params.getValue("statements").jsonArray, method == "storage.transaction")
                "variables.readScope" -> store.get(variableScope(params), "state").let { if (it == JsonNull) buildJsonObject {} else it }
                "variables.writeScope" -> { store.put(variableScope(params), "state", params.getValue("value")); params.getValue("value") }
                "settings.get" -> store.get("settings", plugin).let { if (it == JsonNull) buildJsonObject {} else it }
                "settings.set" -> { store.put("settings", plugin, params.getValue("value")); params.getValue("value") }
                "messages.read" -> readMessages(contextId(params))
                "messages.update" -> updateMessages(params)
                "messages.insert" -> insertMessages(params)
                "messages.delete" -> deleteMessages(params)
                "messages.metadata" -> {
                    val id = contextId(params); val key = params.text("id").ifBlank { "chat" }
                    params["value"]?.let { store.put("metadata:$id", key, it) }
                    store.get("metadata:$id", key).let { if (it == JsonNull) buildJsonObject {} else it }
                }
                "messages.swipes" -> swipes(params)
                "prompts.set" -> { PluginPromptPipeline.set(plugin, params.getValue("entries").jsonArray); JsonNull }
                "prompts.remove" -> { PluginPromptPipeline.remove(plugin); JsonNull }
                "macros.register" -> { PluginPromptPipeline.registerMacro(params.text("name"), params.text("value"), plugin); JsonNull }
                "macros.unregister" -> { PluginPromptPipeline.unregisterMacro(params.text("name"), plugin); JsonNull }
                "generation.invoke" -> invokeGeneration(params)
                "network.request" -> network(params)
                "worldbooks.list" -> buildJsonArray {
                    characters.loadCharacters().items.forEach { add("character:${it.id}") }
                    store.list("worldbooks").keys.forEach { add(it) }
                }
                "worldbooks.get" -> getBook(params.text("name"))
                "worldbooks.put" -> {
                    val name = params.text("name"); val book = params.getValue("book")
                    if (name.startsWith("character:")) libraries.importJson(name.removePrefix("character:"), book.toString()) else store.put("worldbooks", name, book)
                    getBook(name)
                }
                "worldbooks.delete" -> {
                    val name = params.text("name")
                    require(!name.startsWith("character:")) { "角色内置设定库不能删除；可清空其条目" }
                    JsonPrimitive(store.deleteWorldbook(name))
                }
                "worldbooks.bind" -> {
                    val scope = bindingScope(params)
                    val names = params.getValue("names").jsonArray
                    names.forEach { getBook(it.jsonPrimitive.content) }
                    store.put("bindings", scope, names); names
                }
                "worldbooks.bindings" -> store.get("bindings", bindingScope(params)).let { if (it == JsonNull) JsonArray(emptyList()) else it }
                "characters.list" -> Json.parseToJsonElement(characters.exportCharacters()).jsonObject.getValue("items")
                "characters.read" -> characterDocument(characterId(params))
                "characters.write" -> {
                    val id = characterId(params)
                    val root = Json.parseToJsonElement(characters.exportCharacters()).jsonObject
                    val items = root.getValue("items").jsonArray.map { if (it.jsonObject.text("id") == id) params.getValue("character") else it }
                    characters.importCharacters(JsonObject(root + ("items" to JsonArray(items))).toString())
                    characterDocument(id)
                }
                "personas.get" -> profiles.load().let { profile -> buildJsonObject {
                    put("name", profile.userName); put("avatar", profile.userAvatar); put("square", profile.userSquare); put("portrait", profile.userPortrait); put("cover", profile.userCover)
                } }
                "personas.set" -> {
                    val value = params.getValue("persona").jsonObject
                    val current = profiles.load()
                    profiles.restoreSnapshot(current.copy(userName = value.text("name").ifBlank { current.userName },
                        userAvatar = value["avatar"]?.jsonPrimitive?.content ?: current.userAvatar,
                        userSquare = value["square"]?.jsonPrimitive?.content ?: current.userSquare,
                        userPortrait = value["portrait"]?.jsonPrimitive?.content ?: current.userPortrait,
                        userCover = value["cover"]?.jsonPrimitive?.content ?: current.userCover))
                    invoke("personas.get", params)
                }
                "presets.list" -> {
                    val root = Json.parseToJsonElement(presets.exportBackupJson()).jsonObject
                    JsonArray(root.getValue("presets").jsonArray.map { wrapper ->
                        JsonObject(wrapper.jsonObject.getValue("payload").jsonObject.getValue("preset").jsonObject + ("id" to wrapper.jsonObject.getValue("source_id")))
                    })
                }
                "presets.get" -> presetDocument(params.text("id"))
                "presets.select" -> { presets.setActive(params.text("id")); presetDocument(params.text("id")) }
                "presets.update" -> {
                    val id = params.text("id"); val value = params.getValue("preset").jsonObject
                    presets.updateAuthorJson(id, value.toString())
                    presetDocument(id)
                }
                "regex.get" -> Json.parseToJsonElement(regex.exportBackupJson(characterId(params)))
                "regex.set" -> { regex.restoreBackupJson(characterId(params), params.getValue("rules").toString()); Json.parseToJsonElement(regex.exportBackupJson(characterId(params))) }
                else -> error("插件宿主未实现：$method")
            }
        }
    }

    private fun characterDocument(id: String): JsonElement = Json.parseToJsonElement(characters.exportCharacters()).jsonObject.getValue("items").jsonArray
        .first { it.jsonObject.text("id") == id }
    private suspend fun presetDocument(id: String): JsonElement {
        val preset = if (id.isBlank()) presets.activePreset() else checkNotNull(presets.preset(id)) { "预设不存在：$id" }
        val backup = Json.parseToJsonElement(presets.exportBackupJson()).jsonObject.getValue("presets").jsonArray
        val wrapper = backup.first { it.jsonObject.text("source_id") == preset.id }.jsonObject
        return JsonObject(wrapper.getValue("payload").jsonObject.getValue("preset").jsonObject + ("id" to JsonPrimitive(preset.id)))
    }
    private fun getBook(name: String): JsonElement = if (name.startsWith("character:"))
        Json.parseToJsonElement(libraries.exportJson(name.removePrefix("character:"))) else
        store.get("worldbooks", name).also { require(it != JsonNull) { "世界书不存在：$name" } }
    private fun bindingScope(params: JsonObject): String = when (params.text("scope").ifBlank { "chat" }) {
        "global" -> "global"
        "character" -> "character:${characterId(params)}"
        "chat" -> "chat:${contextId(params)}"
        else -> error("未知世界书绑定范围")
    }

    private fun readMessages(id: String): JsonArray = JsonArray(sessions.activeMessages(id).map { message -> buildJsonObject {
        put("id", message.id); put("role", message.role.name.lowercase()); put("content", message.content); put("reasoning", message.reasoningContent)
        put("createdAt", message.createdAt); put("pending", message.pending)
        put("metadata", store.get("metadata:$id", message.id).let { if (it == JsonNull) buildJsonObject {} else it })
        val candidates = store.get("swipes:$id", message.id) as? JsonObject
        put("swipes", candidates?.get("swipes") ?: buildJsonArray { add(message.content) })
        put("swipe_id", candidates?.get("swipe_id") ?: JsonPrimitive(0))
    } })
    private suspend fun updateMessages(params: JsonObject): JsonElement {
        val id = contextId(params)
        params.getValue("messages").jsonArray.forEach { update ->
            val value = update.jsonObject; val messageId = value.text("id")
            val current = checkNotNull(sessions.message(id, messageId)) { "消息不存在：$messageId" }
            value["expectedContent"]?.let { require(it.jsonPrimitive.content == current.content) { "消息已被其他操作修改：$messageId" } }
            value["content"]?.let { invalidateRuntime(sessions.editAssistantMessage(id, messageId, it.jsonPrimitive.content, assistantOnly = false)) }
            value["metadata"]?.let { store.put("metadata:$id", messageId, it) }
        }
        mutableEvents.emit(AuthorApiEvent("messages.changed", buildJsonObject { put("conversationId", id); put("operation", "edit") }))
        return readMessages(id)
    }
    private suspend fun insertMessages(params: JsonObject): JsonElement {
        val id = contextId(params)
        val current = sessions.activeMessages(id).toMutableList()
        val index = params["index"]?.jsonPrimitive?.int ?: current.size
        require(index in 0..current.size) { "插入位置越界：$index" }
        val messages = params.getValue("messages").jsonArray.map { entry ->
            val value = entry.jsonObject
            ChatMessage(value.text("id").ifBlank { UUID.randomUUID().toString() }, MessageRole.entries.first { it.name.lowercase() == value.text("role") }, value.text("content"), createdAt = nowIso())
        }
        current.addAll(index, messages)
        invalidateRuntime(sessions.replaceAuthorMessages(id, current))
        params.getValue("messages").jsonArray.zip(messages).forEach { (input, message) ->
            input.jsonObject["metadata"]?.let { store.put("metadata:$id", message.id, it) }
        }
        mutableEvents.emit(AuthorApiEvent("messages.changed", buildJsonObject { put("conversationId", id); put("operation", "insert") }))
        return readMessages(id)
    }
    private suspend fun deleteMessages(params: JsonObject): JsonElement {
        val id = contextId(params); val ids = params.getValue("ids").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val current = sessions.activeMessages(id)
        require(ids.all { wanted -> current.any { it.id == wanted } }) { "有待删除消息不存在" }
        invalidateRuntime(sessions.replaceAuthorMessages(id, current.filterNot { it.id in ids }))
        ids.forEach { store.delete("metadata:$id", it); store.delete("swipes:$id", it) }
        mutableEvents.emit(AuthorApiEvent("messages.changed", buildJsonObject { put("conversationId", id); put("operation", "delete") }))
        return readMessages(id)
    }

    private fun swipes(params: JsonObject): JsonObject {
        val id = contextId(params); val messageId = params.text("id")
        val message = checkNotNull(sessions.message(id, messageId)) { "消息不存在：$messageId" }
        require(message.role == MessageRole.Assistant) { "只有 AI 消息有候选回复" }
        val previous = store.get("swipes:$id", messageId) as? JsonObject
            ?: buildJsonObject { put("swipes", buildJsonArray { add(message.content) }); put("swipe_id", 0) }
        val choices = params["swipes"] as? JsonArray ?: previous.getValue("swipes").jsonArray
        val selected = params["swipe_id"]?.jsonPrimitive?.int ?: previous.getValue("swipe_id").jsonPrimitive.int
        require(selected in choices.indices) { "候选回复索引越界：$selected" }
        if (params.containsKey("swipes") || params.containsKey("swipe_id")) {
            invalidateRuntime(sessions.editAssistantMessage(id, messageId, choices[selected].jsonPrimitive.content))
            store.put("swipes:$id", messageId, buildJsonObject { put("swipes", choices); put("swipe_id", selected) })
        }
        return buildJsonObject { put("swipes", choices); put("swipe_id", selected) }
    }

    private suspend fun bootstrap(params: JsonObject): JsonObject {
        val id = contextId(params); val session = sessions.load(id, false); val activePreset = presets.activePreset()
        return buildJsonObject {
            put("conversationId", id); put("characterId", session.characterId); put("characterName", session.characterName)
            put("userName", session.characterPersona.userName); put("presetId", activePreset.id)
            put("messages", readMessages(id)); put("metadata", store.get("metadata:$id", "chat").let { if (it == JsonNull) buildJsonObject {} else it })
            put("settings", store.get("settings", owner(params)).let { if (it == JsonNull) buildJsonObject {} else it })
            put("variables", buildJsonObject {
                listOf("chat", "character", "preset", "global", "script", "extension", "plugin").forEach { scope ->
                    put(scope, store.get(variableScope(JsonObject(params + mapOf("scope" to JsonPrimitive(scope), "activePresetId" to JsonPrimitive(activePreset.id)))), "state")
                        .let { if (it == JsonNull) buildJsonObject {} else it })
                }
                put("message", buildJsonObject { sessions.activeMessages(id).forEach { message ->
                    put(message.id, store.get("variables:message:$id:${message.id}", "state").let { if (it == JsonNull) buildJsonObject {} else it })
                } })
            })
        }
    }

    private suspend fun invokeGeneration(params: JsonObject): JsonElement {
        val id = params.text("id").ifBlank { UUID.randomUUID().toString() }
        val snapshot = runtime.gateway?.snapshot()
        val draft = snapshot?.draft
        val configId = params.text("configId").ifBlank { snapshot?.draft?.selectedConfigId.orEmpty() }
        val config = models.loadModelConfigCollection().configs.first { it.id == configId }.let { base ->
            if (params.text("model").isBlank() && draft?.selectedConfigId == configId) base.copy(model = draft.selectedModel) else base
        }
        val conversation = contextId(params)
        val contextual = params["raw"]?.jsonPrimitive?.booleanOrNull == false
        val messages = params["messages"] as? JsonArray ?: buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", params.text("prompt")) }) }
        if (contextual) PluginPromptPipeline.beforeGeneration?.invoke(
            conversation, params.text("purpose").ifBlank { "independent" },
            messages.map { it.jsonObject.getValue("content").jsonPrimitive.content },
        )
        val prepared = if (!contextual) messages else {
            val session = sessions.load(conversation, false)
            com.eleckoi.android.feature.chat.data.composePluginContext(session.copy(messages = sessions.activeMessages(conversation)),
                messages, presets.activePreset(), libraries, regex, variableConfig, variableRuntime)
        }
        val request = JsonObject(params + mapOf("id" to JsonPrimitive(id), "messages" to prepared))
        mutableEvents.emit(AuthorApiEvent("generation.started", buildJsonObject { put("id", id); put("conversationId", conversation) }))
        try {
            val result = generation.invoke(config, request) { delta, reasoning -> mutableEvents.emit(AuthorApiEvent("generation.delta", buildJsonObject {
                put("id", id); put("delta", delta); put("reasoning", reasoning)
            })) }
            mutableEvents.emit(AuthorApiEvent("generation.finished", result)); return result
        } catch (error: Throwable) {
            mutableEvents.emit(AuthorApiEvent("generation.failed", buildJsonObject { put("id", id); put("message", error.message ?: error.toString()) }))
            throw error
        }
    }

    private fun refreshWorldbookPrompts(conversation: String, inputs: List<String>) {
        val session = sessions.load(conversation, false)
        val names = listOf("global", "character:${session.characterId}", "chat:$conversation").flatMap {
            (store.get("bindings", it) as? JsonArray).orEmpty().map(JsonElement::jsonPrimitive).map(JsonPrimitive::content)
        }.distinct().filterNot { it == "character:${session.characterId}" }
        val entries = projectPluginWorldbooks(names.associateWith { getBook(it).jsonObject },
            sessions.activeMessages(conversation).map { it.content } + inputs, PluginPromptPipeline.scanText(conversation), conversation)
        PluginPromptPipeline.set("worldbooks:$conversation", entries)
    }

    private suspend fun network(params: JsonObject): JsonElement {
        val request = Request.Builder().url(params.text("url"))
        (params["headers"] as? JsonObject)?.forEach { (name, value) -> request.header(name, value.jsonPrimitive.content) }
        val body = params["body"]?.jsonPrimitive?.contentOrNull?.toRequestBody(params.text("contentType").ifBlank { "application/json" }.toMediaType())
        request.method(params.text("method").ifBlank { "GET" }, body)
        val id = params.text("id").ifBlank { UUID.randomUUID().toString() }
        val call = networkClient.newCall(request.build())
        check(networkCalls.putIfAbsent(id, call) == null) { "网络请求 id 已在运行：$id" }
        val cancellation = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { call.execute().use { response ->
            return buildJsonObject { put("status", response.code); put("ok", response.isSuccessful)
                put("headers", buildJsonObject { response.headers.names().forEach { put(it, response.header(it).orEmpty()) } }); put("body", response.body?.string().orEmpty()) }
        } } finally { networkCalls.remove(id, call); cancellation.cancel() }
    }
}

private fun JsonObject.text(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
