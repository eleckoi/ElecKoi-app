package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class RegexRuleBatchImportTest {
    @Test
    fun multipleDocumentsKeepFileAndRuleOrderWhileReportingInvalidFiles() {
        val documents = listOf(
            RegexRuleImportDocument(
                displayName = "first.json",
                json = """{"rules":[{"name":"A","pattern":"a"},{"name":"B","pattern":"b"}]}""",
            ),
            RegexRuleImportDocument(displayName = "broken.json", json = "not-json"),
            RegexRuleImportDocument(
                displayName = "second.json",
                json = """{"regex_scripts":[{"scriptName":"C","findRegex":"c"}]}""",
            ),
        )

        val decoded = decodeRegexImportDocuments(documents, RegexRuleScope.Character)

        assertEquals(2, decoded.importedFileCount)
        assertEquals(listOf("A", "B", "C"), decoded.rules.map { it.rule.name })
        assertEquals(
            listOf(RegexRuleScope.Character, RegexRuleScope.Character, RegexRuleScope.Character),
            decoded.rules.map { it.scope },
        )
        assertEquals(listOf("broken.json"), decoded.failedFileNames)
    }

    @Test
    fun depthMetadataIsIgnoredWithoutFilteringThePcCompatibleRules() {
        val document = RegexRuleImportDocument(
            displayName = "mixed.json",
            json = """
                {
                  "regex_scripts": [
                    {"scriptName":"普通规则","findRegex":"cat","replaceString":"dog","minDepth":null,"maxDepth":null},
                    {"scriptName":"删除历史消息","findRegex":"^([\\s\\S]*)$","replaceString":"","minDepth":1,"maxDepth":null},
                    {"scriptName":"只改最新消息","findRegex":"([\\s\\S]*)","replaceString":"<$1>","minDepth":null,"maxDepth":1}
                  ]
                }
            """.trimIndent(),
        )

        val decoded = decodeRegexImportDocuments(listOf(document), RegexRuleScope.PromptPreset)

        assertEquals(listOf("普通规则", "删除历史消息", "只改最新消息"), decoded.rules.map { it.rule.name })
        assertEquals(emptyList<String>(), decoded.failedFileNames)
    }

    @Test
    fun repeatedLargeImportsPreserveEveryPcCompatibleRule() {
        val scripts = (0 until 300).joinToString(",") { index ->
            if (index % 3 == 0) {
                """{"scriptName":"depth-$index","findRegex":"x$index","minDepth":1}"""
            } else {
                """{"scriptName":"plain-$index","findRegex":"x$index","minDepth":null}"""
            }
        }
        val document = RegexRuleImportDocument("large.json", """{"regex_scripts":[$scripts]}""")

        repeat(12) {
            val decoded = decodeRegexImportDocuments(listOf(document), RegexRuleScope.Character)

            assertEquals(300, decoded.rules.size)
            assertEquals(100, decoded.rules.count { it.rule.name.startsWith("depth-") })
        }
    }

    @Test
    fun pcTransferFieldsKeepScopeFlagsTargetsAndLimits() {
        val longPattern = "x".repeat(4_100)
        val decoded = RegexRuleImportCodec.decodeScoped(
            """
                {
                  "rules": [{
                    "scope": "AgentPreset",
                    "name": "  PC 规则  ",
                    "pattern": "$longPattern",
                    "replacement": "",
                    "replaceString": "fallback",
                    "targets": ["Reasoning", "Reasoning", "unknown"],
                    "disabled": false,
                    "enabled": true,
                    "display_only": true,
                    "prompt_only": true,
                    "run_on_edit": true,
                    "minDepth": 2,
                    "maxDepth": 5
                  }]
                }
            """.trimIndent(),
            RegexRuleScope.Character,
        ).single()

        assertEquals(RegexRuleScope.PromptPreset, decoded.scope)
        assertEquals("PC 规则", decoded.rule.name)
        assertEquals(4_000, decoded.rule.pattern.length)
        assertEquals("fallback", decoded.rule.replacement)
        assertEquals(setOf(RegexRuleTarget.Reasoning), decoded.rule.targets)
        assertTrue(decoded.rule.enabled)
        assertTrue(decoded.rule.displayOnly)
        assertTrue(decoded.rule.promptOnly)
        assertTrue(decoded.rule.runOnEdit)
    }

    @Test
    fun pcTypeRulesDoNotCoerceStringsOrFractionalPlacements() {
        val rule = RegexRuleImportCodec.decode(
            """[{"pattern":"x","disabled":"true","enabled":"false","markdownOnly":"true","placement":["1",2.5]}]""",
        ).single()

        assertTrue(rule.enabled)
        assertFalse(rule.displayOnly)
        assertEquals(setOf(RegexRuleTarget.AiOutput), rule.targets)
    }

    @Test
    fun validEmptyPcDocumentsAreNotReportedAsBroken() {
        val decoded = decodeRegexImportDocuments(
            listOf(
                RegexRuleImportDocument("empty.json", """{"rules":[]}"""),
                RegexRuleImportDocument("broken.json", "not-json"),
            ),
            RegexRuleScope.Global,
        )

        assertEquals(0, decoded.importedFileCount)
        assertEquals(emptyList<ScopedRegexRule>(), decoded.rules)
        assertEquals(listOf("broken.json"), decoded.failedFileNames)
    }

    @Test
    fun enabledImportsJoinTheActiveVersionLikePcWhileDisabledImportsStayOut() {
        val version = RegexRuleVersion(id = "active", globalEnabledIds = setOf("existing"))
        val enabled = ScopedRegexRule(
            RegexRuleScope.Global,
            RegexRule(id = "enabled-import", enabled = true),
        )
        val disabled = ScopedRegexRule(
            RegexRuleScope.Character,
            RegexRule(id = "disabled-import", enabled = false),
        )
        val updated = RegexRuleCollection(
            versions = listOf(version),
            activeVersionId = version.id,
        ).includeImportedRulesInActiveVersion(listOf(enabled, disabled))

        assertEquals(setOf("existing", "enabled-import"), updated.versions.single().globalEnabledIds)
        assertFalse("disabled-import" in updated.versions.single().characterEnabledIds)
    }

    @Test
    fun exportUsesThePcTransferShapeAndScopeNames() {
        val json = encodeRegexExport(
            listOf(
                ScopedRegexRule(
                    RegexRuleScope.PromptPreset,
                    RegexRule(
                        id = "preset-rule",
                        pattern = "x",
                        displayOnly = true,
                        promptOnly = true,
                        runOnEdit = true,
                    ),
                ),
            ),
        )
        val root = JSONObject(json)
        val rule = root.getJSONArray("rules").getJSONObject(0)

        assertEquals("eleckoi.regex-rules-export", root.getString("format"))
        assertEquals(2, root.getInt("version"))
        assertEquals("AgentPreset", rule.getString("scope"))
        assertTrue(rule.getBoolean("displayOnly"))
        assertTrue(rule.getBoolean("promptOnly"))
        assertTrue(rule.getBoolean("runOnEdit"))
        assertTrue(rule.getBoolean("display_only"))
        assertTrue(rule.getBoolean("prompt_only"))
        assertTrue(rule.getBoolean("run_on_edit"))
    }
}
