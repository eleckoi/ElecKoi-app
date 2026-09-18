package com.eleckoi.android.engine.workspace.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDistributionCatalogTest {
    @Test
    fun `accepts pinned https hosts and rejects untrusted downloads`() {
        validCatalog().validate()
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                rootfs = validCatalog().rootfs.copy(url = "https://example.com/rootfs.tar.gz"),
            ).validate()
        }
    }

    @Test
    fun `embedded DSH accepts no url and rejects a fallback url`() {
        validCatalog().validate()
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(
                harnesses = mapOf(
                    "deepseek" to validCatalog().requireHarness("deepseek").copy(
                        url = "https://github.com/deepseek-ai/deepseek-harness/releases/download/fallback",
                    ),
                ),
            ).validate()
        }
    }

    @Test
    fun `repository catalog is strict valid and contains only DSH`() {
        val catalogFile = sequenceOf(
            File("runtime/catalog/runtime-catalog.json"),
            File("../runtime/catalog/runtime-catalog.json"),
        ).firstOrNull(File::isFile) ?: error("找不到仓库 runtime catalog")
        val catalog = RuntimeDistributionCatalog.parse(catalogFile.readText())

        assertEquals(5, catalog.schemaVersion)
        assertEquals("arm64-v8a", catalog.architecture)
        assertEquals(null, catalog.toolchain)
        assertEquals(setOf("deepseek"), catalog.harnesses.keys)
        assertEquals("ubuntu-base-24.04.4-arm64.egruntime", catalog.rootfs.assetPath)
        val deepSeek = catalog.requireHarness("deepseek")
        assertEquals("0.1.5-rc.2", deepSeek.version)
        assertEquals("deepseek-harness-0.1.5-rc.2-eleckoi.3-arm64.egruntime", deepSeek.assetPath)
        assertEquals("bin/dsh-jsonrpc-agent", deepSeek.entrypoint)
        assertEquals("etc/deepseek/cordis.yml", deepSeek.configPath)
        assertEquals("fb2c4b9e698e30edb738bca4cf0618587db7d203", deepSeek.sourceCommit)
        assertTrue(deepSeek.embeddedOnly)
    }

    @Test
    fun `repository DSH SDK server declares the model resolver dependency`() {
        val configFile = sequenceOf(
            File("runtime/deepseek/cordis.yml"),
            File("../runtime/deepseek/cordis.yml"),
        ).firstOrNull(File::isFile) ?: error("找不到仓库 DeepSeek Cordis 配置")
        val sdkServer = configFile.readText().replace("\r\n", "\n")
            .substringAfter("- id: sdk-jsonrpc-server")
            .substringBefore("\n- id:")

        assertTrue(sdkServer.contains("  inject:\n"))
        assertTrue(sdkServer.contains("    - sdkAppStartup\n"))
        assertTrue(sdkServer.contains("    - loader\n"))
        assertTrue(sdkServer.contains("    - llm\n"))
        assertTrue(sdkServer.contains("  config:\n"))
    }

    @Test
    fun `strict parser rejects unknown fields and non DSH harness`() {
        val unknown = kotlinx.serialization.json.Json.encodeToString(
            RuntimeDistributionCatalog.serializer(),
            validCatalog(),
        ).dropLast(1) + ",\"typoField\":true}"
        assertThrows(Exception::class.java) { RuntimeDistributionCatalog.parse(unknown) }
        assertThrows(IllegalArgumentException::class.java) {
            validCatalog().copy(harnesses = emptyMap()).validate()
        }
    }

    @Test
    fun `DSH archive pin changes installation fingerprint`() {
        val original = validCatalog()
        val changed = original.copy(
            harnesses = mapOf(
                "deepseek" to original.requireHarness("deepseek").copy(sha256 = "c".repeat(64)),
            ),
        )

        assertTrue(original.contentFingerprint() != changed.contentFingerprint())
        assertEquals(64, original.contentFingerprint().length)
    }

    private fun validCatalog() = RuntimeDistributionCatalog(
        schemaVersion = 5,
        runtimeVersion = "test_arm64",
        architecture = "arm64-v8a",
        rootfs = RuntimeArchive(
            kind = "ubuntu-base",
            version = "1",
            url = "https://cdimage.ubuntu.com/rootfs.tar.gz",
            sha256 = "a".repeat(64),
            archiveBytesLimit = 10_000,
            assetPath = "rootfs.egruntime",
        ),
        harnesses = mapOf(
            "deepseek" to HarnessRuntimeArchive(
                kind = "deepseek-harness-single-executable",
                version = "1",
                url = "",
                sha256 = "b".repeat(64),
                archiveBytesLimit = 10_000,
                assetPath = "deepseek.egruntime",
                sourceCommit = "c".repeat(40),
                entrypoint = "bin/dsh-jsonrpc-agent",
                configPath = "etc/deepseek/cordis.yml",
                embeddedOnly = true,
            ),
        ),
    )
}
