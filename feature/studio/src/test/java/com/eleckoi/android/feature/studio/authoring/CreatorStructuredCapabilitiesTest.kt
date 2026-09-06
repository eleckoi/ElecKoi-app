package com.eleckoi.android.feature.studio.authoring

import com.eleckoi.android.feature.conversation.markdown.*
import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.model.*
import com.eleckoi.android.feature.conversation.timeline.ui.*

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.config.VersionedVariableConfig
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCharacterRoot
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.data.VersionedRegexRuleCollection
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringToolCatalog
import com.eleckoi.android.feature.studio.authoring.capability.RegexRuleCreatorCapabilities
import com.eleckoi.android.feature.studio.authoring.capability.VariableCreatorCapabilities
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorStructuredCapabilitiesTest {
    @Test
    fun `catalog exposes variables and regex through toolsets`() {
        val catalog = CreatorAuthoringToolCatalog.create()
        val ids = catalog.toolsetDefinitions.map { it.id }.toSet()

        assertTrue("creator.variables" in ids)
        assertTrue("creator.regex_rules" in ids)
        assertTrue(catalog.operations("creator.variables").any { it.capabilityId == "variables.preview_changes" })
        assertTrue(catalog.operations("creator.regex_rules").any { it.capabilityId == "regex_rules.preview_changes" })
    }

    @Test
    fun `variable directory is paged and preview preserves structured fields`() = runBlocking {
        val variableConfig = VariableConfig(
            characterId = CharacterId,
            name = "变量配置",
            initialStateJson = "{\"hp\":10}",
            objects = listOf(
                VariableObjectConfig(id = "stats", name = "属性"),
                VariableObjectConfig(id = "flags", name = "标记"),
            ),
            variables = listOf(
                VariableItemConfig(id = "hp", title = "生命", objectId = "stats", type = "number"),
                VariableItemConfig(id = "met", title = "相遇", objectId = "flags", type = "boolean"),
            ),
            activeVersionId = "v1",
            versions = listOf(VariableConfigVersion(id = "v1", name = "变量配置")),
        )
        val context = context(variableConfig, RegexRuleCollection())
        val inspect = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.inspect" }
            .handler(context, buildJsonObject { put("limit", 1) }) as JsonObject

        assertEquals(1, inspect.getValue("objects").jsonArray.size)
        assertTrue(inspect.getValue("objectsNextCursor").jsonPrimitive.content.isNotBlank())
        assertEquals("测试角色", inspect.getValue("targetName").jsonPrimitive.content)

        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }
            .handler(context, buildJsonObject {
                put("base_revision", "stale-variable-revision")
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "set_config")
                        put("schema_code", "z.object({ 属性: z.object({ 生命: z.number() }), 标记: z.object({}) })")
                    })
                    add(buildJsonObject {
                        put("op", "patch_variable")
                        put("id", "hp")
                        put("description", "当前生命值")
                        put("read_mode", "required")
                    })
                    add(buildJsonObject {
                        put("op", "convert_variable_to_object")
                        put("id", "met")
                        put("new_id", "met-object")
                    })
                })
            }) as JsonObject

        val next = context.variableChanges.get(preview.getValue("changeSetId").jsonPrimitive.content)!!.nextConfig
        assertEquals("当前生命值", next.variables.single { it.id == "hp" }.description)
        assertEquals("number", next.variables.single { it.id == "hp" }.type)
        assertTrue(next.variables.none { it.id == "met" })
        assertEquals("相遇", next.objects.single { it.id == "met-object" }.name)
        assertTrue(next.schemaCode.startsWith("z.object"))
        assertTrue(next.initialStateJson.contains("生命"))
        assertTrue(next.initialStateJson.contains("相遇"))
        assertEquals(inspect.getValue("revision"), preview.getValue("baseRevision"))
        assertTrue(preview.toString().contains("schemaValidated"))

        val guide = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.get_authoring_guide" }
            .handler(context, buildJsonObject {})
            .toString()
        assertTrue(guide.contains("logicalTypes"))
        assertTrue(guide.contains("object"))
        assertTrue(guide.contains("convert_variable_to_object"))
    }

    @Test
    fun `variable revision comes from the storage counter without hashing content`() = runBlocking {
        val context = context(
            variables = timestampedVariableConfig("2026-01-01T00:00:00Z"),
            regex = RegexRuleCollection(),
            variableRevisionProvider = { 41L },
        )

        val inspect = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.inspect" }
            .handler(context, buildJsonObject {}) as JsonObject

        assertEquals("41", inspect.getValue("revision").jsonPrimitive.content)
    }

    @Test
    fun `variable preview survives a reload that only refreshed timestamps`() = runBlocking {
        val first = timestampedVariableConfig("2026-01-01T00:00:00Z")
        val reloaded = timestampedVariableConfig("2026-08-29T00:00:00Z")
        var loads = 0
        val context = context(
            variables = first,
            regex = RegexRuleCollection(),
            variablesProvider = { if (loads++ == 0) first else reloaded },
        )
        val inspect = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.inspect" }
            .handler(context, buildJsonObject {}) as JsonObject

        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }
            .handler(context, buildJsonObject {
                put("base_revision", "stale-regex-revision")
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "set_config")
                        put("name", "稳定修改")
                    })
                })
            }) as JsonObject

        assertTrue(preview.getValue("valid").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `variable apply delegates revision check to the atomic save`() = runBlocking {
        var expectedRevision = -1L
        val context = context(
            variables = timestampedVariableConfig("2026-01-01T00:00:00Z"),
            regex = RegexRuleCollection(),
            variableRevisionProvider = { 7L },
            variableSaveProvider = { _, expected ->
                expectedRevision = expected
                null
            },
        )
        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }
            .handler(context, buildJsonObject {
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "set_config")
                        put("name", "并发修改")
                    })
                })
            }) as JsonObject

        val failure = runCatching {
            VariableCreatorCapabilities.capabilities()
                .single { it.definition.capabilityId == "variables.apply_changes" }
                .handler(context, buildJsonObject {
                    put("change_set_id", preview.getValue("changeSetId").jsonPrimitive.content)
                })
        }.exceptionOrNull()

        assertEquals(7L, expectedRevision)
        assertTrue(failure is CreatorAuthoringException)
        assertTrue(failure?.message.orEmpty().contains("已经变化"))
    }

    @Test
    fun `create variable requires an author update rule`() = runBlocking {
        val context = context(VariableConfig(CharacterId), RegexRuleCollection())
        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }

        val failure = runCatching {
            preview.handler(context, buildJsonObject {
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "create_variable")
                        put("title", "好感度")
                        put("type", "number")
                        put("description", "角色对用户的好感程度")
                        put("default_value", "0")
                    })
                })
            })
        }.exceptionOrNull()

        assertTrue(failure is CreatorAuthoringException)
        assertTrue(failure?.message.orEmpty().contains("update_rule"))
    }

    @Test
    fun `create variable is idempotent by object and title and rebuilds static state`() = runBlocking {
        val original = VariableItemConfig(
            id = "affinity-stable",
            title = "好感度",
            type = "number",
            defaultValue = "0",
            description = "旧说明",
            updateRule = "旧规则",
        )
        val context = context(
            VariableConfig(
                characterId = CharacterId,
                initialStateJson = "{\"上轮残留\":true,\"好感度\":0}",
                variables = listOf(original),
            ),
            RegexRuleCollection(),
        )
        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }
            .handler(context, buildJsonObject {
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "create_variable")
                        put("title", "好感度")
                        put("type", "number")
                        put("default_value", "10")
                        put("description", "角色对用户的好感程度")
                        put("update_rule", "根据互动变化，保持在 0 到 100")
                        put("read_mode", "required")
                    })
                })
            }) as JsonObject

        val next = context.variableChanges.get(preview.getValue("changeSetId").jsonPrimitive.content)!!.nextConfig
        assertEquals(1, next.variables.size)
        assertEquals("affinity-stable", next.variables.single().id)
        assertEquals("根据互动变化，保持在 0 到 100", next.variables.single().updateRule)
        assertTrue(next.initialStateJson.contains("\"好感度\""))
        assertTrue(!next.initialStateJson.contains("上轮残留"))
    }

    @Test
    fun `explicit initial state cannot carry unrelated keys into a new variable turn`() = runBlocking {
        val context = context(VariableConfig(CharacterId), RegexRuleCollection())
        val preview = VariableCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "variables.preview_changes" }

        val failure = runCatching {
            preview.handler(context, buildJsonObject {
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "set_config")
                        put("initial_state_json", "{\"好感度\":0,\"上轮字段\":true}")
                    })
                    add(buildJsonObject {
                        put("op", "create_variable")
                        put("title", "好感度")
                        put("type", "number")
                        put("default_value", "0")
                        put("description", "角色对用户的好感程度")
                        put("update_rule", "根据互动变化，保持在 0 到 100")
                    })
                })
            })
        }.exceptionOrNull()

        assertTrue(failure is CreatorAuthoringException)
        assertTrue(failure?.message.orEmpty().contains("上轮字段"))
    }

    @Test
    fun `regex preview compiles pattern and keeps scope and targets`() = runBlocking {
        val collection = RegexRuleCollection(
            characterRules = listOf(
                RegexRule(id = "r1", name = "清理标记", pattern = "/<tag>.*?</tag>/gs"),
            ),
        )
        val context = context(VariableConfig(CharacterId), collection)
        val inspect = RegexRuleCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "regex_rules.inspect" }
            .handler(context, buildJsonObject {}) as JsonObject
        val preview = RegexRuleCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "regex_rules.preview_changes" }
            .handler(context, buildJsonObject {
                put("base_revision", inspect.getValue("revision").jsonPrimitive.content)
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "patch_rule")
                        put("id", "r1")
                        put("replacement", "")
                        put("display_only", true)
                    })
                })
            }) as JsonObject

        val next = context.regexRuleChanges.get(preview.getValue("changeSetId").jsonPrimitive.content)!!.nextCollection
        val rule = next.characterRules.single()
        assertEquals(RegexRuleScope.Character, next.scopedScope(rule.id))
        assertTrue(rule.displayOnly)
        assertTrue(rule.targets.isNotEmpty())
        assertEquals(inspect.getValue("revision"), preview.getValue("baseRevision"))
    }

    private fun context(
        variables: VariableConfig,
        regex: RegexRuleCollection,
        variablesProvider: () -> VariableConfig = { variables },
        variableRevisionProvider: () -> Long = { 1L },
        regexRevisionProvider: () -> Long = { 1L },
        variableSaveProvider: (VariableConfig, Long) -> VersionedVariableConfig? = { config, expected ->
            VersionedVariableConfig(config, expected + 1L)
        },
        regexSaveProvider: (RegexRuleCollection, Long) -> VersionedRegexRuleCollection? = { collection, expected ->
            VersionedRegexRuleCollection(collection, expected + 1L)
        },
    ): CreatorAuthoringContext {
        val workspace = CreatorWorkspace(
            id = WorkspaceId,
            name = "创作项目",
            linkedCharacterId = CharacterId,
            primaryCharacterRootId = RootId,
            characterRoots = listOf(
                CreatorWorkspaceCharacterRoot(
                    id = RootId,
                    characterId = CharacterId,
                    alias = "测试角色",
                    access = CreatorWorkspaceRootAccess.ReadWrite,
                ),
            ),
            createdAt = "now",
            updatedAt = "now",
        )
        @Suppress("UNCHECKED_CAST")
        val service = Proxy.newProxyInstance(
            CreatorAssistantService::class.java.classLoader,
            arrayOf(CreatorAssistantService::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "creatorWorkspace" -> workspace
                "loadCreatorVariableConfig" -> variablesProvider()
                "loadVersionedCreatorVariableConfig" -> VersionedVariableConfig(
                    variablesProvider(),
                    variableRevisionProvider(),
                )
                "loadCreatorRegexRules" -> regex
                "loadVersionedCreatorRegexRules" -> VersionedRegexRuleCollection(
                    regex,
                    regexRevisionProvider(),
                )
                "saveCreatorVariableConfigIfRevision" -> variableSaveProvider(
                    arguments!![2] as VariableConfig,
                    arguments[3] as Long,
                )
                "saveCreatorRegexRulesIfRevision" -> regexSaveProvider(
                    arguments!![2] as RegexRuleCollection,
                    arguments[3] as Long,
                )
                "validateCreatorVariableSchema" -> VariableRuntimeCheckResult(ok = true)
                "validateCreatorVariableState" -> VariableRuntimeCheckResult(ok = true)
                else -> error("Unexpected service call: ${method.name}")
            }
        } as CreatorAssistantService
        return CreatorAuthoringContext(
            workspaceId = WorkspaceId,
            permissionModeProvider = { AgentPermissionMode.ApproveForMe },
            service = service,
        )
    }

    private fun RegexRuleCollection.scopedScope(id: String): RegexRuleScope = when {
        globalRules.any { it.id == id } -> RegexRuleScope.Global
        promptPresetRules.any { it.id == id } -> RegexRuleScope.PromptPreset
        else -> RegexRuleScope.Character
    }

    private fun timestampedVariableConfig(timestamp: String): VariableConfig {
        val objectConfig = VariableObjectConfig(
            id = "stats",
            name = "属性",
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val variable = VariableItemConfig(
            id = "hp",
            title = "生命",
            objectId = "stats",
            type = "number",
            defaultValue = "10",
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val version = VariableConfigVersion(
            id = "v1",
            name = "变量配置",
            initialStateJson = "{\"hp\":10}",
            schemaCode = "z.object({ hp: z.number().max(100) })",
            objects = listOf(objectConfig),
            variables = listOf(variable),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        return VariableConfig(
            characterId = CharacterId,
            name = version.name,
            initialStateJson = version.initialStateJson,
            schemaCode = version.schemaCode,
            objects = version.objects,
            variables = version.variables,
            activeVersionId = version.id,
            versions = listOf(version),
        )
    }

    private companion object {
        const val WorkspaceId = "workspace-1"
        const val CharacterId = "character-1"
        const val RootId = "root-1"
    }
}
