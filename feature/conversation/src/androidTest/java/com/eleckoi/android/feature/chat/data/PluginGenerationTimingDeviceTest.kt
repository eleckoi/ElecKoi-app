package com.eleckoi.android.feature.chat.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eleckoi.android.engine.agent.api.*
import com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline
import com.eleckoi.android.engine.creator.plugins.projectPluginWorldbooks
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.config.ModelSecretCodec
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.chat.model.*
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.JsonFileStore
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises production send/persistence up to the hook, without a runtime or model request. */
@RunWith(AndroidJUnit4::class)
class PluginGenerationTimingDeviceTest {
    @Test fun currentInputIsPersistedAndPublishedBeforeWorldbookScan() = runBlocking {
        Fixture().use { fixture ->
            val stop = IllegalStateException("stop before model request")
            var published = false
            var hooks = 0
            val owner = "timing-${UUID.randomUUID()}"
            val previous = PluginPromptPipeline.beforeGeneration
            PluginPromptPipeline.beforeGeneration = { conversation, purpose, inputs ->
                hooks++
                assertTrue(published)
                assertEquals("chat", purpose)
                assertTrue(inputs.isEmpty())
                val stored = fixture.sessions.activeMessages(conversation)
                assertEquals("Alice enters the tower", stored.last().content)
                val book = Json.parseToJsonElement("""{"entries":[{"uid":1,"content":"tower lore","strategy":{"type":"selective","keys":["tower"],"scan_depth":1}}]}""").jsonObject
                PluginPromptPipeline.set(owner, projectPluginWorldbooks(
                    mapOf("book" to book), stored.map { it.content }, "", conversation,
                ))
                assertEquals("tower lore", PluginPromptPipeline.snapshot(conversation).single { it.traceSource == "plugin:$owner" }.content)
                throw stop
            }
            try {
                val error = runCatching {
                    fixture.service.sendMessage("run", fixture.draft, "Alice enters the tower", emptyList(), {}, { _, id ->
                        assertEquals(id, fixture.sessions.activeMessages(fixture.draft.session.id).last().id)
                        published = true
                    })
                }.exceptionOrNull()
                assertSame(stop, error)
                assertEquals(1, hooks)
            } finally {
                PluginPromptPipeline.beforeGeneration = previous
                PluginPromptPipeline.remove(owner)
            }
        }
    }

    @Test fun invalidEmptyInputDoesNotExecuteHooksOrPersistATurn() = runBlocking {
        Fixture().use { fixture ->
            var hooks = 0
            val previous = PluginPromptPipeline.beforeGeneration
            PluginPromptPipeline.beforeGeneration = { _, _, _ -> hooks++ }
            try {
                val error = runCatching { fixture.service.sendMessage("run", fixture.draft, "  ", emptyList(), {}) }.exceptionOrNull()
                assertTrue(error is ElecKoiDataException)
                assertEquals(0, hooks)
                assertTrue(fixture.sessions.activeMessages(fixture.draft.session.id).isEmpty())
            } finally {
                PluginPromptPipeline.beforeGeneration = previous
            }
        }
    }

    private class Fixture : AutoCloseable {
        private val app = ApplicationProvider.getApplicationContext<Context>()
        private val root = File(app.cacheDir, "generation-timing-${UUID.randomUUID()}").apply { mkdirs() }
        private val context = object : ContextWrapper(app) { override fun getFilesDir(): File = root }
        private val database = Room.inMemoryDatabaseBuilder(context, ElecKoiDatabase::class.java).build()
        private val characters = CharacterRepository(JsonFileStore(context), database)
        private val character = characters.createCharacter()
        private val attempts = GenerationAttemptRepository(database)
        val sessions = ChatSessionStore(database, characters, attempts)
        private val config = ModelConfig(id = "test-config", model = "test-model")
        val draft = ChatDraft(ChatSession(id = "test-chat", title = "test", characterId = character.id,
            characterName = character.name, characterAvatar = "", characterPersona = character.persona,
            messages = emptyList(), updatedAt = "now"), config, config.model)
        val service: CharacterAgentGenerationService

        init {
            sessions.write(draft.session)
            val codec = object : ModelSecretCodec {
                override fun protect(configId: String, plaintext: String) = plaintext
                override fun reveal(configId: String, stored: String) = stored
                override fun isProtected(stored: String) = false
            }
            val runtime = Proxy.newProxyInstance(LocalRuntimeGateway::class.java.classLoader,
                arrayOf(LocalRuntimeGateway::class.java)) { _, method, _ -> error("Unexpected runtime call: ${method.name}") } as LocalRuntimeGateway
            val search = object : AgentVirtualFileSearch {
                override suspend fun glob(files: List<AgentVirtualFile>, request: AgentVirtualGlobRequest): AgentVirtualGlobResult = error("Unexpected glob")
                override suspend fun grep(files: List<AgentVirtualFile>, request: AgentVirtualGrepRequest): AgentVirtualGrepResult = error("Unexpected grep")
            }
            service = CharacterAgentGenerationService(characters, sessions,
                ModelConfigRepository(database, secretCodec = codec),
                CreatorWorkspaceRepository(File(root, "workspaces"), Instant::now, { UUID.randomUUID().toString() }, database = database),
                SettingLibraryRepository(database, characters), RegexRuleRepository(database, characters),
                VariableConfigRepository(database) { characters.characterById(it) != null }, VariableRuntimeService(context),
                runtime, AgentSessionFactory { error("Unexpected Agent creation") }, search,
                { error("Unexpected tool context") }, { _, _ -> error("Unexpected draft projection") },
                ReplyImageGenerator(File(root, "images")), attempts, { error("Unexpected preset read") },
                captureProviderRequests = false)
        }

        override fun close() {
            database.close()
            root.deleteRecursively()
        }
    }
}
