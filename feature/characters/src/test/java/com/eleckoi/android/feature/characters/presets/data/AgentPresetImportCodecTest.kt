package com.eleckoi.android.feature.characters.presets.data

import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportDocument
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportSource
import com.eleckoi.android.feature.characters.presets.model.AgentPresetExportFormat
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetTimelineItem
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.feature.characters.transfer.format.png.PngTextChunkCodec
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPresetImportCodecTest {
    @Test
    fun `backup without an author avatar treats JSON null as absent`() {
        val item = JSONObject().put("author_avatar_base64", JSONObject.NULL)

        assertNull(decodeBackupAuthorAvatar(item))
    }

    @Test
    fun `imports only the tavern outer list and preserves its switches and order`() {
        val root = JSONObject()
            .put(
                "prompts",
                JSONArray()
                    .put(prompt("menu", "下拉菜单候选", "不要导入", "system"))
                    .put(prompt("first", "第一条", "正文一", "assistant"))
                    .put(prompt("marker", "Chat History", "", "system", marker = true))
                    .put(prompt("second", "第二条", "正文二", "user"))
                    .put(prompt("blank", "空白但可编辑", "", "system")),
            )
            .put(
                "prompt_order",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("character_id", 100000)
                            .put("order", JSONArray().put(order("menu", true))),
                    )
                    .put(
                        JSONObject()
                            .put("character_id", 100001)
                            .put(
                                "order",
                                JSONArray()
                                    .put(order("first", true))
                                    .put(order("marker", true))
                                    .put(order("second", false))
                                    .put(order("blank", false)),
                            ),
                    ),
            )
            .put(
                "extensions",
                JSONObject().put(
                    "regex_scripts",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("scriptName", "已开启")
                                .put("findRegex", "/foo/g")
                                .put("replaceString", "bar")
                                .put("placement", JSONArray().put(2))
                                .put("disabled", false),
                        )
                        .put(
                            JSONObject()
                                .put("scriptName", "已关闭")
                                .put("findRegex", "/baz/g")
                                .put("replaceString", "qux")
                                .put("placement", JSONArray().put(1))
                                .put("disabled", true),
                        ),
                ),
            )

        val conversion = AgentPresetImportCodec.decode(
            AgentPresetImportDocument("测试酒馆预设.json", root.toString()),
            AgentPresetImportSource.SillyTavern,
        )

        assertEquals("测试酒馆预设", conversion.preset.name)
        assertEquals(listOf("第一条", "第二条", "空白但可编辑"), conversion.preset.entries.map { it.title })
        assertEquals(listOf(true, false, false), conversion.preset.entries.map { it.enabled })
        assertEquals(listOf(1, 3, 4), conversion.preset.entries.map { it.treeViewOrder })
        assertEquals(
            listOf(SettingLibraryInsertRole.Assistant, SettingLibraryInsertRole.User, SettingLibraryInsertRole.System),
            conversion.preset.entries.map { it.insertRole },
        )
        assertTrue(conversion.preset.entries.all { it.triggerMode == SettingLibraryTriggerMode.AgentTool })
        assertTrue(conversion.preset.entries.all {
            it.agentReadStrategy == SettingLibraryAgentReadStrategy.Required
        })
        assertEquals(1, conversion.preset.groups.size)
        assertTrue(conversion.preset.entries.all { it.groupId == conversion.preset.groups.single().id })
        assertEquals(1, conversion.skippedUnsupportedEntries)
        assertEquals(listOf("已开启", "已关闭"), conversion.preset.regexRules.map { it.name })
        assertEquals(listOf(true, false), conversion.preset.regexRules.map { it.enabled })
        assertEquals(
            listOf(setOf(RegexRuleTarget.AiOutput), setOf(RegexRuleTarget.UserInput)),
            conversion.preset.regexRules.map { it.targets },
        )
        assertFalse(conversion.preset.entries.any { it.title == "下拉菜单候选" })
        assertFalse(conversion.preset.entries.any { it.title == "Chat History" })
        assertEquals(AgentPresetToolConfiguration(), conversion.preset.toolConfiguration)
        assertEquals(AgentPresetRoleplayPlan(), conversion.preset.roleplayPlan)
    }

    @Test
    fun `round trips the supported ElecKoi preset fields`() {
        val source = AgentPreset(
            id = "source",
            name = "本项目预设",
            activeVersionId = "source:v2",
            activeVersionNumber = 2,
            profile = AgentPresetProfile(
                authorName = "作者",
                usageInstructions = "请先阅读预设提示词，再按需启用工具。",
                timeline = listOf(AgentPresetTimelineItem("t1", "第一版", "2026", "说明")),
            ),
            entries = listOf(
                SettingLibraryEntry(
                    id = "entry",
                    title = "条目",
                    groupId = "group",
                    content = "正文",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                    agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                    enabled = false,
                    treeViewOrder = 3,
                ),
            ),
            groups = listOf(SettingLibraryGroup("group", "文件夹", order = 1, treeViewOrder = 1)),
            regexRules = listOf(
                RegexRule(
                    id = "regex-1",
                    name = "预设正则",
                    pattern = "/foo/g",
                    replacement = "bar",
                    targets = setOf(RegexRuleTarget.AiOutput),
                    enabled = false,
                    promptOnly = true,
                    order = 0,
                ),
            ),
            roleplayPlan = AgentPresetRoleplayPlan(listOf("读取当前设定", "输出最终正文")),
            toolConfiguration = AgentPresetToolConfiguration(
                includedGroupIds = listOf(
                    AgentToolRequestPolicy.BuiltInVariables,
                    AgentToolRequestPolicy.BuiltInWeb,
                    "mcp:device-only",
                ),
                enabledGroupIds = setOf(AgentToolRequestPolicy.BuiltInWeb, "mcp:device-only"),
                subagentModelConfigId = "local-config",
                subagentModel = "local-model",
                toolModelConfigIds = mapOf(AgentToolRequestPolicy.BuiltInWeb to "web-config"),
            ),
            expandedGroupIds = listOf("group"),
        )

        val encoded = AgentPresetImportCodec.encodeElecKoi(source)
        val decoded = AgentPresetImportCodec.decode(
            AgentPresetImportDocument("fallback.json", encoded),
            AgentPresetImportSource.ElecKoi,
        ).preset

        val rootPreset = JSONObject(encoded).getJSONObject("preset")
        val toolConfiguration = rootPreset.getJSONObject("tool_configuration")
        assertEquals(2, rootPreset.getInt("active_version_number"))
        assertEquals("source:v2", rootPreset.getString("active_version_id"))
        assertEquals(
            listOf(AgentToolRequestPolicy.BuiltInVariables, AgentToolRequestPolicy.BuiltInWeb),
            toolConfiguration.getJSONArray("included_group_ids").strings(),
        )
        assertEquals(
            listOf(AgentToolRequestPolicy.BuiltInWeb),
            toolConfiguration.getJSONArray("enabled_group_ids").strings(),
        )
        assertFalse(toolConfiguration.has("subagent_model_config_id"))
        assertFalse(toolConfiguration.has("subagent_model"))
        assertFalse(toolConfiguration.has("tool_model_config_ids"))
        assertEquals(
            listOf("读取当前设定", "输出最终正文"),
            JSONObject(encoded)
                .getJSONObject("preset")
                .getJSONObject("roleplay_plan")
                .getJSONArray("steps")
                .let { steps -> List(steps.length()) { index -> steps.getString(index) } },
        )
        assertEquals(source.name, decoded.name)
        assertEquals(source.profile, decoded.profile)
        assertEquals(
            AgentPresetToolConfiguration(
                includedGroupIds = listOf(
                    AgentToolRequestPolicy.BuiltInVariables,
                    AgentToolRequestPolicy.BuiltInWeb,
                ),
                enabledGroupIds = setOf(AgentToolRequestPolicy.BuiltInWeb),
            ),
            decoded.toolConfiguration,
        )
        assertEquals(source.roleplayPlan, decoded.roleplayPlan)
        assertEquals(source.entries, decoded.entries)
        assertEquals(source.groups, decoded.groups)
        assertEquals(source.regexRules, decoded.regexRules)
        assertEquals(source.expandedGroupIds, decoded.expandedGroupIds)
    }

    @Test
    fun `round trips every preset version and excludes device bound fields`() {
        val current = AgentPreset(
            id = "source",
            name = "多版本预设",
            activeVersionId = "source:v7",
            activeVersionNumber = 7,
            profile = AgentPresetProfile(usageInstructions = "当前说明"),
            entries = listOf(SettingLibraryEntry(id = "current-entry", title = "当前", content = "now")),
            toolConfiguration = AgentPresetToolConfiguration(
                includedGroupIds = listOf(AgentToolRequestPolicy.BuiltInVariables),
                enabledGroupIds = setOf(AgentToolRequestPolicy.BuiltInVariables),
                subagentModelConfigId = "local-only",
                subagentModel = "device-model",
                toolModelConfigIds = mapOf(AgentToolRequestPolicy.BuiltInVariables to "local-config"),
            ),
        )
        val oldVersion = AgentPresetTransferVersion(
            id = "source:v2",
            number = 2,
            name = "旧版",
            createdAtEpochMs = 1234L,
            preset = current.copy(
                profile = current.profile.copy(usageInstructions = "旧版说明"),
                entries = listOf(SettingLibraryEntry(id = "old-entry", title = "旧版", content = "old")),
                toolConfiguration = AgentPresetToolConfiguration(
                    includedGroupIds = listOf(AgentToolRequestPolicy.BuiltInWeb),
                    enabledGroupIds = emptySet(),
                    subagentModelConfigId = "must-not-export",
                ),
            ),
        )
        val activeVersion = AgentPresetTransferVersion(
            id = current.activeVersionId,
            number = current.activeVersionNumber,
            name = "当前版",
            createdAtEpochMs = 5678L,
            preset = current,
        )

        val json = AgentPresetImportCodec.encodeElecKoi(
            current,
            versions = listOf(oldVersion, activeVersion),
        )
        val decoded = AgentPresetImportCodec.decode(
            AgentPresetImportDocument("多版本.json", bytes = json.toByteArray()),
            AgentPresetImportSource.ElecKoi,
        )

        assertEquals(listOf(2, 7), decoded.versions.map { it.number })
        assertEquals(listOf("旧版说明", "当前说明"), decoded.versions.map { it.preset.profile.usageInstructions })
        assertEquals(listOf("旧版", "当前"), decoded.versions.map { it.preset.entries.single().title })
        assertEquals(
            listOf(listOf(AgentToolRequestPolicy.BuiltInWeb), listOf(AgentToolRequestPolicy.BuiltInVariables)),
            decoded.versions.map { it.preset.toolConfiguration.includedGroupIds },
        )
        assertFalse(json.contains("local-only"))
        assertFalse(json.contains("device-model"))
        assertFalse(json.contains("local-config"))
        assertFalse(json.contains("background", ignoreCase = true))
        assertFalse(json.contains("chara"))
        assertFalse(json.contains("ccv3"))
        assertFalse(json.contains("eleckoi_agent_preset_avatar"))
    }

    @Test
    fun `tavern import skips depth regexes instead of making them unconditional`() {
        val root = JSONObject()
            .put(
                "prompts",
                JSONArray().put(prompt("main", "主提示词", "正文", "system")),
            )
            .put(
                "prompt_order",
                JSONArray().put(
                    JSONObject()
                        .put("character_id", 100001)
                        .put("order", JSONArray().put(order("main", true))),
                ),
            )
            .put(
                "extensions",
                JSONObject().put(
                    "regex_scripts",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("scriptName", "普通规则")
                                .put("findRegex", "cat")
                                .put("replaceString", "dog")
                                .put("minDepth", JSONObject.NULL)
                                .put("maxDepth", JSONObject.NULL),
                        )
                        .put(
                            JSONObject()
                                .put("scriptName", "包装最新用户消息")
                                .put("findRegex", "([\\s\\S]*)")
                                .put("replaceString", "<user_input>$1</user_input>")
                                .put("maxDepth", 1),
                        )
                        .put(
                            JSONObject()
                                .put("scriptName", "删除历史用户消息")
                                .put("findRegex", "^([\\s\\S]*)$")
                                .put("replaceString", "")
                                .put("minDepth", 1),
                        ),
                ),
            )

        val conversion = AgentPresetImportCodec.decode(
            AgentPresetImportDocument("深度规则.json", root.toString()),
            AgentPresetImportSource.SillyTavern,
        )

        assertEquals(listOf("普通规则"), conversion.preset.regexRules.map { it.name })
        assertEquals(2, conversion.skippedDepthRegexCount)
        assertEquals(0, conversion.skippedUnsupportedEntries)
    }

    @Test
    fun `imports ElecKoi PNG preset card and restores embedded author avatar`() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val source = AgentPreset(
            id = "source",
            name = "图片预设",
            activeVersionId = "source:v1",
            profile = AgentPresetProfile(authorName = "鹿鹿"),
        )
        val json = AgentPresetImportCodec.encodeElecKoi(
            source,
            authorAvatar = AgentPresetTransferAvatar("image/png", png),
        )
        val legacyCard = PngTextChunkCodec.writeText(
            png,
            mapOf("eleckoi_agent_preset_avatar" to Base64.getEncoder().encodeToString(png)),
        )
        val card = AgentPresetPngFormat.encode(legacyCard, json)

        val conversion = AgentPresetImportCodec.decode(
            AgentPresetImportDocument(fileName = "图片预设.png", bytes = card),
            AgentPresetImportSource.ElecKoi,
        )

        assertEquals(json, AgentPresetPngFormat.decode(card).json)
        assertEquals(setOf("eleckoi_agent_preset"), PngTextChunkCodec.readText(card).keys)
        assertEquals("图片预设", conversion.preset.name)
        assertEquals("鹿鹿", conversion.preset.profile.authorName)
        assertEquals("image/png", conversion.authorAvatar?.mediaType)
        assertTrue(conversion.authorAvatar?.bytes?.contentEquals(png) == true)
    }

    @Test
    fun `JSON export bytes exactly match the payload embedded in PNG export`() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val preset = AgentPreset(
            id = "source",
            name = "同步预设",
            activeVersionId = "source:v1",
            profile = AgentPresetProfile(authorName = "作者"),
        )
        val avatar = AgentPresetTransferAvatar("image/png", png)
        val version = AgentPresetTransferVersion(
            id = "source:v1",
            number = 1,
            name = "初版",
            createdAtEpochMs = 1L,
            preset = preset,
        )

        val json = AgentPresetCardExporter.export(
            preset,
            AgentPresetExportFormat.Json,
            avatar,
            listOf(version),
        )
        val card = AgentPresetCardExporter.export(
            preset,
            AgentPresetExportFormat.Png,
            avatar,
            listOf(version),
        )

        assertEquals(
            json.bytes.toString(Charsets.UTF_8),
            AgentPresetPngFormat.decode(card.bytes).json,
        )
        assertEquals(setOf("eleckoi_agent_preset"), PngTextChunkCodec.readText(card.bytes).keys)
    }

    private fun prompt(
        id: String,
        name: String,
        content: String,
        role: String,
        marker: Boolean = false,
    ): JSONObject = JSONObject()
        .put("identifier", id)
        .put("name", name)
        .put("content", content)
        .put("role", role)
        .put("marker", marker)

    private fun order(id: String, enabled: Boolean): JSONObject = JSONObject()
        .put("identifier", id)
        .put("enabled", enabled)

    private fun JSONArray.strings(): List<String> =
        List(length()) { index -> getString(index) }
}
