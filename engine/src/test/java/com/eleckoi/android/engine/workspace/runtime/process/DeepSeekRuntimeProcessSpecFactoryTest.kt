package com.eleckoi.android.engine.workspace.runtime.process

import com.eleckoi.android.engine.workspace.runtime.ActiveRuntimePaths
import com.eleckoi.android.engine.workspace.runtime.RuntimeGuestLayout
import com.eleckoi.android.engine.workspace.runtime.RuntimeInstallationManifest
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekRuntimeProcessSpecFactoryTest {
    @Test
    fun `passes authenticated host tool route and independent builtin switches`() {
        val fixture = Fixture()
        try {
            val spec = fixture.factory.create(
                commandId = "deepseek-session-1",
                activeRuntime = fixture.activeRuntime,
                workspace = fixture.workspace,
                deepSeekHome = fixture.deepSeekHome,
                harnessConfig = fixture.managedConfig,
                launchSpec = DeepSeekRuntimeLaunchSpec(
                    workspaceId = "role-1",
                    providerBaseUrl = "http://127.0.0.1:43210/${"r".repeat(32)}/v1/",
                    model = "deepseek-v4",
                    modelContextWindow = 1_000_000,
                    hostToolCatalogJson = "{\"tools\":[{\"name\":\"roleplay\"}]}",
                    workspaceToolsEnabled = true,
                    workflowToolsEnabled = false,
                    collaborationToolsEnabled = true,
                ),
                hostResolverConfig = fixture.hostResolverConfig,
                sessionHome = fixture.sessionHome,
                sessionGuestTemp = fixture.sessionGuestTemp,
                sessionProotTemp = fixture.sessionProotTemp,
            )

            assertEquals(LocalRuntimeTarget.DeepSeekHarness, spec.target)
            assertTrue(spec.arguments.contains("ELECKOI_HOST_TOOLS_URL=http://127.0.0.1:43210/${"r".repeat(32)}/host-tools"))
            assertTrue(spec.arguments.contains("ELECKOI_HOST_TOOL_CATALOG={\"tools\":[{\"name\":\"roleplay\"}]}"))
            assertTrue(spec.arguments.contains("ELECKOI_CONTEXT_WINDOW=1000000"))
            assertTrue(spec.arguments.contains("ELECKOI_ENABLE_WORKSPACE_TOOLS=true"))
            assertTrue(spec.arguments.contains("ELECKOI_ENABLE_WORKFLOW_TOOLS=false"))
            assertTrue(spec.arguments.contains("ELECKOI_ENABLE_COLLABORATION_TOOLS=true"))
            assertTrue(spec.arguments.contains("DSH_RIPGREP_PATH=/opt/eleckoi/bin/rg"))
            assertTrue(spec.arguments.contains("DSH_LANDLOCK_PATH=/run/eleckoi/landlock-run"))
            assertTrue(spec.arguments.any { it.endsWith(":/run/eleckoi/proot-loader") })
            assertTrue(spec.arguments.contains("LD_LIBRARY_PATH=/opt/eleckoi/lib/sharp"))
            assertTrue(spec.arguments.contains("/opt/eleckoi/bin/dsh-jsonrpc-agent"))
            assertTrue(spec.arguments.contains("DSH_CORDIS_CONFIG=/deepseek-home/eleckoi/cordis.yml"))
            assertTrue(spec.arguments.contains("DSH_SYSTEM_PROMPT="))
            assertTrue(spec.arguments.contains("/deepseek-home/eleckoi/cordis.yml"))
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val temp = Files.createTempDirectory("deepseek-runtime-process").toFile()
        private val rootfs = File(temp, "runtime/rootfs").directory().also { root ->
            RuntimeGuestLayout.prepare(root) { _, _ -> }
        }
        private val tools = File(temp, "runtime/tools").directory()
        private val entrypoint = File(tools, "bin/dsh-jsonrpc-agent").file()
        @Suppress("unused")
        private val ripgrep = File(tools, "bin/rg").file()
        @Suppress("unused")
        private val landlock = File(tools, "bin/landlock-run").file()
        private val config = File(tools, "etc/deepseek/cordis.yml").file()
        val workspace = File(temp, "app/workspaces/role-1/project").directory()
        val deepSeekHome = File(temp, "app/local_runtime/state/workspace_deepseek_homes/role-1").directory()
        val managedConfig = File(deepSeekHome, "eleckoi/cordis.yml").file()
        private val nativeLibraries = File(temp, "app/native-libs").directory()
        val sessionHome = File(temp, "app/local_runtime/sessions/test/home").directory()
        val sessionGuestTemp = File(temp, "app/local_runtime/sessions/test/guest-tmp").directory()
        val sessionProotTemp = File(temp, "app/local_runtime/sessions/test/proot-tmp").directory()
        private val hostProc = File(temp, "system/proc").directory()
        val hostResolverConfig = File(temp, "app/local_runtime/network/resolv.conf").file().apply {
            writeText("nameserver 10.0.0.1\n")
        }
        private val hostDev = File(temp, "system/dev").directory().also { directory ->
            listOf("null", "zero", "random", "urandom").forEach { File(directory, it).file() }
        }
        private val proot = File(nativeLibraries, "libeleckoi_proot.so").file()
        @Suppress("unused")
        private val loader = File(nativeLibraries, "libeleckoi_proot_loader.so").file()
        @Suppress("unused")
        private val talloc = File(nativeLibraries, "libtalloc.so").file()
        @Suppress("unused")
        private val shmem = File(nativeLibraries, "libandroid-shmem.so").file()

        val activeRuntime = ActiveRuntimePaths(
            manifest = RuntimeInstallationManifest(
                runtimeVersion = "test-runtime",
                architecture = "arm64-v8a",
                installationDirectory = "test-runtime",
                rootfsArchiveSha256 = "a".repeat(64),
                harnessEntrypoints = mapOf("deepseek" to "bin/dsh-jsonrpc-agent"),
                harnessArchiveSha256s = mapOf("deepseek" to "b".repeat(64)),
                harnessConfigPaths = mapOf("deepseek" to "etc/deepseek/cordis.yml"),
                installedAtEpochMillis = 1L,
            ),
            rootfs = rootfs,
            tools = tools,
            harnessEntrypoints = mapOf("deepseek" to entrypoint),
            harnessConfigs = mapOf("deepseek" to config),
        )
        val factory = DeepSeekRuntimeProcessSpecFactory(
            nativeLibraryDirectory = nativeLibraries,
            hostProcDirectory = hostProc,
            hostDeviceDirectory = hostDev,
        )

        fun close() {
            temp.deleteRecursively()
        }

        private fun File.directory(): File = apply { check(mkdirs()) }

        private fun File.file(): File = apply {
            parentFile?.mkdirs()
            check(createNewFile())
        }
    }
}
