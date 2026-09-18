package com.eleckoi.android.engine.workspace.runtime

import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekPluginCompositionManagerTest {
    @Test
    fun `composes base config with bundled provider and context bridges`() {
        val fixture = Fixture()
        try {
            val composition = fixture.prepare()
            val active = composition.productPatch
            val providers = composition.providerPatch

            assertEquals(File(fixture.deepSeekHome, "eleckoi/cordis.patch.yml").canonicalFile, active)
            assertEquals(File(fixture.deepSeekHome, "eleckoi/providers.patch.json").canonicalFile, providers)
            assertTrue(active.readText().contains("- id: sdk-jsonrpc-server"))
            assertFalse(active.readText().contains("- id: llm-deepseek"))
            assertFalse(active.readText().contains("- id: llm-pi-ai"))
            assertFalse(active.readText().contains("- id: eleckoi-deepseek-official"))
            assertFalse(active.readText().contains("- id: eleckoi-pi-ai"))
            assertTrue(providers.readText().contains("\"id\": \"llm-deepseek\""))
            assertTrue(providers.readText().contains("provider-wire/deepseek/v1"))
            assertTrue(providers.readText().contains("\"id\": \"deepseek-flash\""))
            assertTrue(providers.readText().contains("\"id\": \"llm-pi-ai\""))
            assertTrue(providers.readText().contains("\"eleckoi-deepseek-responses\""))
            assertFalse(active.readText().contains("ELECKOI_DSH_DEEPSEEK_MODELS"))
            assertFalse(providers.readText().contains("!!js"))
            assertTrue(active.readText().contains("includeRuntimeContext: false"))
            assertTrue(
                active.readText().contains(
                    "path: /deepseek-home/eleckoi/plugins/eleckoi-context-pressure/1.0.0/cordis.yml",
                ),
            )
            assertTrue(
                active.readText().contains(
                    "path: /deepseek-home/eleckoi/plugins/eleckoi-agent-session-bridge/1.0.0/cordis.yml",
                ),
            )
            assertFalse(active.readText().contains("path: ./plugins/"))
            assertTrue(
                File(
                    fixture.deepSeekHome,
                    "eleckoi/plugins/eleckoi-context-pressure/1.0.0/context-pressure.mjs",
                ).isFile,
            )
            assertTrue(File(fixture.deepSeekHome, "eleckoi/eleckoi-host-tools.mjs").isFile)
            assertEquals(fixture.originalConfig, fixture.packagedConfig.readText())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `composes enabled installed plugin from persistent registry`() {
        val fixture = Fixture()
        try {
            val externalRoot = File(fixture.deepSeekHome, "eleckoi/plugins/community-clock/2.0.0")
            assertTrue(externalRoot.mkdirs())
            File(externalRoot, "manifest.json").writeText(
                ElecKoiPrettyJson.encodeToString(
                    DeepSeekPluginManifest(
                        schemaVersion = 1,
                        id = "community-clock",
                        version = "2.0.0",
                        cordisConfig = "cordis.yml",
                        files = listOf("cordis.yml", "index.mjs"),
                    ),
                ),
            )
            File(externalRoot, "cordis.yml").writeText("- id: community-clock\n  name: ./index.mjs\n")
            File(externalRoot, "index.mjs").writeText("export function apply() {}\n")
            File(fixture.deepSeekHome, "eleckoi/enabled-plugins.json").writeText(
                ElecKoiPrettyJson.encodeToString(
                    DeepSeekPluginRegistry(
                        enabled = listOf(DeepSeekPluginSelection("community-clock", "2.0.0")),
                    ),
                ),
            )

            val active = fixture.prepare().productPatch

            assertTrue(
                active.readText().contains(
                    "path: /deepseek-home/eleckoi/plugins/community-clock/2.0.0/cordis.yml",
                ),
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `does not require or synthesize a process global compaction plugin`() {
        val fixture = Fixture()
        try {
            val active = fixture.prepare().productPatch

            val composed = active.readText()
            assertFalse(composed.contains("compaction-basic"))
            assertFalse(composed.contains("thresholdRatio:"))
            assertEquals(fixture.originalConfig, fixture.packagedConfig.readText())
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val temp = Files.createTempDirectory("deepseek-plugins").toFile()
        private val runtimeConfigRoot = File(temp, "runtime/etc/deepseek").apply { mkdirs() }
        val originalConfig = """
            # Runtime base
            - id: sdk-jsonrpc-server
              name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
            - id: eleckoi-host-tools
              name: ./eleckoi-host-tools.mjs
            - id: system-prompt
              config:
                includeHarnessIdentity: false
                includeRuntimeContext: false
        """.trimIndent() + "\n"
        val packagedConfig = File(runtimeConfigRoot, "cordis.yml").apply { writeText(originalConfig) }
        val deepSeekHome = File(temp, "deepseek-home").apply { mkdirs() }
        val deepSeekProviderBaseUrl = "http://127.0.0.1:43210/${"r".repeat(32)}/provider-wire/deepseek/v1"
        val deepSeekModelsJson = "[{\"id\":\"deepseek-flash\"}]"
        val piAiProvidersJson = """
            {
              "eleckoi-deepseek-responses": {
                "api": "openai-responses",
                "baseURL": "http://127.0.0.1:43210/${"r".repeat(32)}/provider-wire/responses/v1",
                "models": [{"id":"deepseek-flash"}]
              }
            }
        """.trimIndent()
        val manager = DeepSeekPluginCompositionManager(
            assetReader = { path ->
                when (path) {
                    "dsh-plugins/context-pressure/manifest.json" -> """
                        {
                          "schemaVersion":1,
                          "id":"eleckoi-context-pressure",
                          "version":"1.0.0",
                          "cordisConfig":"cordis.yml",
                          "files":["cordis.yml","context-pressure.mjs"]
                        }
                    """.trimIndent().toByteArray()
                    "dsh-plugins/context-pressure/cordis.yml" -> """
                        - id: eleckoi-session-projection
                          name: '@deepseek-ai/dsh-session-projection'
                        - id: eleckoi-context-pressure-bridge
                          name: ./context-pressure.mjs
                    """.trimIndent().toByteArray()
                    "dsh-plugins/context-pressure/context-pressure.mjs" ->
                        "export function apply() {}\n".toByteArray()
                    "dsh-plugins/agent-session-bridge/manifest.json" -> """
                        {
                          "schemaVersion":1,
                          "id":"eleckoi-agent-session-bridge",
                          "version":"1.0.0",
                          "cordisConfig":"cordis.yml",
                          "files":["cordis.yml","agent-session-bridge.mjs"]
                        }
                    """.trimIndent().toByteArray()
                    "dsh-plugins/agent-session-bridge/cordis.yml" -> """
                        - id: eleckoi-agent-session-bridge
                          name: ./agent-session-bridge.mjs
                    """.trimIndent().toByteArray()
                    "dsh-plugins/agent-session-bridge/agent-session-bridge.mjs" ->
                        "export function apply() {}\n".toByteArray()
                    else -> error("unexpected bundled plugin asset: $path")
                }
            },
        )

        init {
            File(runtimeConfigRoot, "eleckoi-host-tools.mjs").writeText("export function apply() {}\n")
        }

        fun prepare(): DeepSeekPluginComposition = manager.prepare(
            packagedConfig = packagedConfig,
            deepSeekHome = deepSeekHome,
            deepSeekProviderBaseUrl = deepSeekProviderBaseUrl,
            deepSeekModelsJson = deepSeekModelsJson,
            piAiProvidersJson = piAiProvidersJson,
        )

        fun close() {
            temp.deleteRecursively()
        }
    }
}
