package com.eleckoi.android.engine.workspace.runtime.process

import com.eleckoi.android.engine.workspace.runtime.ActiveRuntimePaths
import com.eleckoi.android.engine.workspace.runtime.RuntimeGuestLayout
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Builds the isolated PRoot command for DeepSeek Harness' newline-delimited JSON-RPC server. */
internal class DeepSeekRuntimeProcessSpecFactory(
    private val nativeLibraryDirectory: File,
    private val hostProcDirectory: File = File("/proc"),
    private val hostDeviceDirectory: File = File("/dev"),
) {
    fun create(
        commandId: String,
        activeRuntime: ActiveRuntimePaths,
        workspace: File,
        deepSeekHome: File,
        harnessPatches: List<File> = emptyList(),
        launchSpec: DeepSeekRuntimeLaunchSpec,
        hostResolverConfig: File,
        sessionHome: File,
        sessionGuestTemp: File,
        sessionProotTemp: File,
        resourceGuard: ProcessResourceGuard? = null,
    ): ProcessLaunchSpec {
        require(CommandId.matches(commandId)) { "运行命令编号无效" }
        require(launchSpec.providerBaseUrl.matches(LoopbackUrl)) { "DeepSeek Provider 地址不是安全的本机路由" }
        require(launchSpec.modelContextWindow > 0) { "模型上下文窗口必须为正数" }
        val hostToolsUrl = launchSpec.providerBaseUrl
            .removeSuffix("/")
            .removeSuffix("/v1") + "/host-tools"
        require(launchSpec.model.isNotBlank() && launchSpec.model.length <= MaxModelChars && '\u0000' !in launchSpec.model) {
            "DeepSeek 模型名无效"
        }
        require(
            launchSpec.deepSeekModelsJson.length <= MaxDeepSeekModelsJsonChars &&
                '\u0000' !in launchSpec.deepSeekModelsJson,
        ) { "DeepSeek 官方模型目录过大或包含非法字符" }
        require(
            launchSpec.piAiProvidersJson.length <= MaxPiAiProvidersJsonChars &&
                '\u0000' !in launchSpec.piAiProvidersJson,
        ) { "DSH pi-ai Provider 目录过大或包含非法字符" }

        val rootfs = requireDirectory(activeRuntime.rootfs, "Ubuntu rootfs 不存在")
        require(RuntimeGuestLayout.isPrepared(rootfs)) { "Ubuntu rootfs 缺少安全的挂载目标" }
        val tools = requireDirectory(activeRuntime.tools, "Harness 工具目录不存在")
        val entrypoint = requireFile(activeRuntime.requireHarnessEntrypoint(HarnessId), "DeepSeek Harness 入口不存在")
        val packagedConfig = requireFile(
            activeRuntime.requireHarnessConfig(HarnessId),
            "DeepSeek Harness 配置不存在",
        )
        val patchFiles = (harnessPatches.ifEmpty { listOf(packagedConfig) }).map { patch ->
            requireFile(patch, "DeepSeek Harness 启动配置不存在")
        }
        val ripgrep = requireFile(File(tools, RipgrepRelativePath), "DeepSeek Harness ripgrep 不存在")
        val landlock = requireFile(File(tools, LandlockRelativePath), "DeepSeek Harness Landlock launcher 不存在")
        require(entrypoint.toPath().startsWith(tools.toPath()) && packagedConfig.toPath().startsWith(tools.toPath())) {
            "DeepSeek Harness Runtime 文件路径越界"
        }
        require(ripgrep.toPath().startsWith(tools.toPath()) && landlock.toPath().startsWith(tools.toPath())) {
            "DeepSeek Harness sidecar 路径越界"
        }
        val entrypointRelative = requireNotNull(activeRuntime.manifest.harnessEntrypoints[HarnessId])
        val configRelative = requireNotNull(activeRuntime.manifest.harnessConfigPaths[HarnessId])
        require(isSafeRelativePath(entrypointRelative) && isSafeRelativePath(configRelative)) {
            "DeepSeek Harness 清单路径无效"
        }

        val project = requireDirectory(workspace, "工作区不存在")
        val state = requireDirectory(deepSeekHome, "DeepSeek 状态目录不存在")
        val guestPatches = patchFiles.map { patch ->
            guestPath(
                file = patch,
                roots = listOf(
                    tools to GuestTools,
                    state to GuestDeepSeekHome,
                ),
                message = "DeepSeek Harness 启动配置路径越界",
            )
        }
        val home = requireDirectory(sessionHome, "运行会话 HOME 目录不存在")
        val guestTemp = requireDirectory(sessionGuestTemp, "运行会话临时目录不存在")
        val prootTemp = requireDirectory(sessionProotTemp, "PROot 临时目录不存在")
        val proc = requireDirectory(hostProcDirectory, "系统 /proc 不存在")
        val resolver = requireResolverConfig(hostResolverConfig)

        val proot = requireNativeHost("libeleckoi_proot.so")
        val loader = requireNativeHost("libeleckoi_proot_loader.so")
        requireNativeHost("libtalloc.so")
        requireNativeHost("libandroid-shmem.so")

        val bindMounts = buildList {
            add(proc to GuestProc)
            add(resolver to GuestResolverConfig)
            DeviceNames.forEach { name ->
                add(requireExistingNode(File(hostDeviceDirectory, name), "系统设备节点 /dev/$name 不存在") to "/dev/$name")
            }
            add(project to GuestWorkspace)
            add(state to GuestDeepSeekHome)
            add(tools to GuestTools)
            add(home to GuestHome)
            add(guestTemp to GuestTemp)
            add(guestTemp to GuestVarTemp)
            add(loader to RuntimeGuestLayout.ProotLoaderGuestPath)
        }
        val arguments = buildList {
            add(proot.absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("-0")
            add("-r")
            add(rootfs.absolutePath)
            bindMounts.forEach { (source, target) ->
                add("-b")
                add("${source.absolutePath}:$target")
            }
            add("-w")
            add(GuestWorkspace)
            add("/usr/bin/env")
            add("-i")
            add("HOME=$GuestHome")
            add("USER=root")
            add("LOGNAME=root")
            add("SHELL=/bin/sh")
            add("PATH=/opt/eleckoi/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            add("TMPDIR=$GuestTemp")
            add("LANG=C.UTF-8")
            add("LC_ALL=C.UTF-8")
            add("LD_LIBRARY_PATH=$GuestTools/lib/sharp")
            add("DSH_CWD=$GuestWorkspace")
            add("DSH_HOME=$GuestDeepSeekHome")
            add("DSH_SESSION_ROOT=$GuestDeepSeekHome/sessions")
            add("DSH_TELEMETRY_DISABLED=1")
            add("DSH_RIPGREP_PATH=$GuestTools/$RipgrepRelativePath")
            add("DSH_LANDLOCK_PATH=${RuntimeGuestLayout.LandlockLauncherGuestPath}")
            add("ELECKOI_PROVIDER_BASE_URL=${launchSpec.providerBaseUrl}")
            add("ELECKOI_HOST_TOOLS_URL=$hostToolsUrl")
            add("ELECKOI_SESSION_SNAPSHOT_ROOT=$GuestDeepSeekHome/eleckoi/session-snapshots")
            add("ELECKOI_REQUEST_CONTEXT_ROOT=$GuestDeepSeekHome/eleckoi/request-context")
            add("ELECKOI_PROVIDER_KEY=$InertLoopbackCredential")
            add("ELECKOI_MODEL=${launchSpec.model}")
            add("ELECKOI_CONTEXT_WINDOW=${launchSpec.modelContextWindow}")
            add("ELECKOI_ENABLE_WORKSPACE_TOOLS=${launchSpec.workspaceToolsEnabled}")
            add("ELECKOI_ENABLE_WORKFLOW_TOOLS=${launchSpec.workflowToolsEnabled}")
            add("ELECKOI_ENABLE_COLLABORATION_TOOLS=${launchSpec.collaborationToolsEnabled}")
            add("$GuestTools/$entrypointRelative")
            add("--profile")
            add(DshProfile)
            guestPatches.forEach { patch ->
                add("--patch")
                add(patch)
            }
        }
        return ProcessLaunchSpec(
            commandId = commandId,
            target = LocalRuntimeTarget.DeepSeekHarness,
            arguments = arguments,
            workingDirectory = rootfs,
            environment = linkedMapOf(
                "LD_LIBRARY_PATH" to nativeLibraryDirectory.canonicalFile.absolutePath,
                "PROOT_LOADER" to loader.absolutePath,
                "PROOT_TMP_DIR" to prootTemp.absolutePath,
                "ELECKOI_SHMEM_DIR" to prootTemp.absolutePath,
                "TMPDIR" to prootTemp.absolutePath,
            ),
            resourceGuard = resourceGuard,
            maxTotalOutputBytes = MaxProtocolOutputBytes,
        )
    }

    private fun requireNativeHost(name: String): File =
        requireFile(File(nativeLibraryDirectory, name), "原生运行时文件缺失: $name")

    private fun requireResolverConfig(file: File): File {
        val path = file.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path)) {
            "Android DNS 配置不是安全的可读文件"
        }
        require(Files.size(path) in 1L..MaxResolverConfigBytes) { "Android DNS 配置大小无效" }
        return path.toFile().canonicalFile
    }

    private fun requireDirectory(file: File, message: String): File {
        require(file.isDirectory) { message }
        return file.canonicalFile
    }

    private fun requireFile(file: File, message: String): File {
        require(file.isFile) { message }
        return file.canonicalFile
    }

    private fun requireExistingNode(file: File, message: String): File {
        require(file.exists() && !file.isDirectory) { message }
        return file.canonicalFile
    }

    private fun guestPath(
        file: File,
        roots: List<Pair<File, String>>,
        message: String,
    ): String {
        val target = file.canonicalFile.toPath()
        val (hostRoot, guestRoot) = roots.firstOrNull { (root, _) ->
            target.startsWith(root.canonicalFile.toPath()) && target != root.canonicalFile.toPath()
        } ?: throw IllegalArgumentException(message)
        val relative = hostRoot.canonicalFile.toPath().relativize(target)
            .joinToString("/") { segment -> segment.toString() }
        require(isSafeRelativePath(relative)) { message }
        return "$guestRoot/$relative"
    }

    private fun isSafeRelativePath(value: String): Boolean =
        value.isNotBlank() && !value.startsWith('/') && !value.contains('\\') &&
            value.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private companion object {
        const val HarnessId = "deepseek"
        const val DshProfile = "sdk"
        const val RipgrepRelativePath = "bin/rg"
        const val LandlockRelativePath = "bin/landlock-run"
        const val GuestProc = "/proc"
        const val GuestResolverConfig = "/etc/resolv.conf"
        const val GuestWorkspace = "/workspace"
        const val GuestDeepSeekHome = "/deepseek-home"
        const val GuestTools = "/opt/eleckoi"
        const val GuestHome = "/root"
        const val GuestTemp = "/tmp"
        const val GuestVarTemp = "/var/tmp"
        const val InertLoopbackCredential = "eleckoi-local-route"
        const val MaxModelChars = 512
        const val MaxDeepSeekModelsJsonChars = 512 * 1024
        const val MaxPiAiProvidersJsonChars = 1024 * 1024
        const val MaxResolverConfigBytes = 64L * 1024L
        const val MaxProtocolOutputBytes = 256L * 1024L * 1024L
        val CommandId = Regex("^[A-Za-z0-9_-]{1,100}$")
        val LoopbackUrl = Regex("^http://127\\.0\\.0\\.1:[0-9]{1,5}/[A-Za-z0-9_-]{8,160}/v1/?$")
        val DeviceNames = listOf("null", "zero", "random", "urandom")
    }
}
