package com.eleckoi.android.engine.workspace.runtime.process

import com.eleckoi.android.engine.workspace.runtime.RuntimeGuestLayout
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class RuntimeGuestCommand(
    val commandId: String,
    val rootfs: File,
    val tools: File,
    val hostResolverConfig: File,
    val arguments: List<String>,
    val guestWorkingDirectory: String = "/",
    val environment: Map<String, String> = emptyMap(),
    val timeoutMillis: Long = DefaultGuestCommandTimeoutMillis,
    val retainFullOutput: Boolean = false,
)

internal data class RuntimeGuestCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    fun requireSuccess(action: String): RuntimeGuestCommandResult {
        if (exitCode != 0) {
            val detail = stderr.ifBlank { stdout }.takeLast(MaxFailureDetailChars).trim()
            error(if (detail.isBlank()) "$action 失败（退出码 $exitCode）" else "$action 失败：$detail")
        }
        return this
    }

    private companion object {
        const val MaxFailureDetailChars = 4_096
    }
}

internal fun interface RuntimeGuestCommandExecutor {
    suspend fun execute(command: RuntimeGuestCommand): RuntimeGuestCommandResult
}

/** Runs fixed maintenance commands inside the app-owned Ubuntu rootfs. */
internal class ProotRuntimeGuestCommandExecutor(
    private val specFactory: RuntimeGuestProcessSpecFactory,
    private val processFactory: RuntimeProcessFactory = RuntimeProcessFactory { spec ->
        ProcessBuilder(spec.arguments)
            .directory(spec.workingDirectory)
            .redirectErrorStream(false)
            .also { builder ->
                builder.environment().clear()
                builder.environment().putAll(spec.environment)
            }
            .start()
    },
) : RuntimeGuestCommandExecutor {
    override suspend fun execute(command: RuntimeGuestCommand): RuntimeGuestCommandResult = coroutineScope {
        val spec = specFactory.create(command)
        val process = withContext(Dispatchers.IO) { processFactory.start(spec) }
        try {
            val stdout = async(Dispatchers.IO) { process.inputStream.readBoundedOutput(command.retainFullOutput) }
            val stderr = async(Dispatchers.IO) { process.errorStream.readBoundedOutput() }
            val exitCode = withTimeout(command.timeoutMillis) {
                withContext(Dispatchers.IO) { process.waitFor() }
            }
            RuntimeGuestCommandResult(exitCode, stdout.await(), stderr.await())
        } catch (error: Throwable) {
            process.destroy()
            if (!withContext(Dispatchers.IO) { process.waitFor(StopGraceMillis, TimeUnit.MILLISECONDS) }) {
                process.destroyForcibly()
            }
            throw error
        }
    }

    private companion object {
        const val StopGraceMillis = 750L
    }
}

internal class RuntimeGuestProcessSpecFactory(
    private val nativeLibraryDirectory: File,
    private val hostTempDirectory: File,
    private val hostProcDirectory: File = File("/proc"),
    private val hostDeviceDirectory: File = File("/dev"),
) {
    fun create(command: RuntimeGuestCommand): ProcessLaunchSpec {
        require(CommandId.matches(command.commandId)) { "运行命令编号无效" }
        require(command.arguments.isNotEmpty()) { "Ubuntu 命令不能为空" }
        require(command.arguments.size <= MaxArguments) { "Ubuntu 命令参数过多" }
        require(command.arguments.all { it.length <= MaxArgumentChars && '\u0000' !in it }) {
            "Ubuntu 命令参数无效"
        }
        require(GuestPath.matches(command.guestWorkingDirectory)) { "Ubuntu 工作目录无效" }
        require(command.environment.size <= MaxEnvironmentEntries) { "Ubuntu 环境变量过多" }
        require(command.environment.all { (key, value) ->
            EnvironmentName.matches(key) && value.length <= MaxEnvironmentValueChars && '\u0000' !in value
        }) { "Ubuntu 环境变量无效" }

        val rootfs = requireDirectory(command.rootfs, "Ubuntu rootfs 不存在")
        require(RuntimeGuestLayout.isPrepared(rootfs)) { "Ubuntu rootfs 缺少挂载目标" }
        val tools = requireDirectory(command.tools, "本地工具目录不存在")
        val temp = requireDirectory(hostTempDirectory, "PROot 临时目录不存在")
        val proc = requireDirectory(hostProcDirectory, "系统 /proc 不存在")
        val resolver = requireResolver(command.hostResolverConfig)
        val proot = requireNativeHost("libeleckoi_proot.so")
        val loader = requireNativeHost("libeleckoi_proot_loader.so")
        requireNativeHost("libtalloc.so")
        requireNativeHost("libandroid-shmem.so")

        val bindMounts = buildList {
            add(proc to GuestProc)
            add(resolver to GuestResolverConfig)
            DeviceNames.forEach { name ->
                add(requireNode(File(hostDeviceDirectory, name), "系统设备节点 /dev/$name 不存在") to "/dev/$name")
            }
            add(tools to GuestTools)
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
            add(command.guestWorkingDirectory)
            add("/usr/bin/env")
            add("-i")
            CommonGuestEnvironment.forEach { (key, value) -> add("$key=$value") }
            command.environment.toSortedMap().forEach { (key, value) -> add("$key=$value") }
            addAll(command.arguments)
        }
        return ProcessLaunchSpec(
            commandId = command.commandId,
            target = LocalRuntimeTarget.SystemProbe,
            arguments = arguments,
            workingDirectory = rootfs,
            environment = linkedMapOf(
                "LD_LIBRARY_PATH" to nativeLibraryDirectory.canonicalFile.absolutePath,
                "PROOT_LOADER" to loader.absolutePath,
                "PROOT_TMP_DIR" to temp.absolutePath,
                "ELECKOI_SHMEM_DIR" to temp.absolutePath,
                "TMPDIR" to temp.absolutePath,
            ),
        )
    }

    private fun requireNativeHost(name: String): File =
        requireFile(File(nativeLibraryDirectory, name), "原生运行时文件缺失: $name")

    private fun requireResolver(file: File): File {
        val path = file.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path)) {
            "Android DNS 配置无效"
        }
        require(Files.size(path) in 1L..MaxResolverBytes) { "Android DNS 配置大小无效" }
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

    private fun requireNode(file: File, message: String): File {
        require(file.exists() && !file.isDirectory) { message }
        return file.canonicalFile
    }

    private companion object {
        val CommandId = Regex("^[A-Za-z0-9_-]{1,100}$")
        val GuestPath = Regex("^/[A-Za-z0-9._/-]{0,240}$")
        val EnvironmentName = Regex("^[A-Z][A-Z0-9_]{0,63}$")
        const val MaxArguments = 128
        const val MaxArgumentChars = 32 * 1024
        const val MaxEnvironmentEntries = 32
        const val MaxEnvironmentValueChars = 32 * 1024
        const val MaxResolverBytes = 64L * 1024L
        const val GuestProc = "/proc"
        const val GuestResolverConfig = "/etc/resolv.conf"
        const val GuestTools = "/opt/eleckoi"
        val DeviceNames = listOf("null", "zero", "random", "urandom")
        val CommonGuestEnvironment = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "LOGNAME" to "root",
            "SHELL" to "/bin/sh",
            "PATH" to "/opt/eleckoi/toolchain/node/bin:/opt/eleckoi/toolchain/pnpm:/opt/eleckoi/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR" to "/tmp",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
        )
    }
}

internal fun InputStream.readBoundedOutput(retainFullOutput: Boolean = false): String {
    val reader = InputStreamReader(this, Charsets.UTF_8)
    val retained = StringBuilder()
    val line = StringBuilder()
    val buffer = CharArray(4 * 1024)
    var total = 0
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        total += count
        require(total <= MaxProcessOutputChars) { "Ubuntu 命令输出超过安全上限" }
        repeat(count) { index ->
            val char = buffer[index]
            if (char == '\n') {
                appendRetained(retained, line, retainFullOutput)
                line.setLength(0)
            } else if (char != '\r') {
                require(line.length < MaxProcessLineChars) { "Ubuntu 命令单行输出超过安全上限" }
                line.append(char)
            }
        }
    }
    if (line.isNotEmpty()) appendRetained(retained, line, retainFullOutput)
    return retained.toString()
}

private fun appendRetained(retained: StringBuilder, line: StringBuilder, retainFullOutput: Boolean) {
    if (retained.isNotEmpty()) retained.append('\n')
    retained.append(line)
    if (!retainFullOutput && retained.length > MaxRetainedOutputChars) {
        retained.delete(0, retained.length - MaxRetainedOutputChars)
    }
}

private const val DefaultGuestCommandTimeoutMillis = 20 * 60 * 1_000L
private const val MaxProcessOutputChars = 16 * 1024 * 1024
private const val MaxProcessLineChars = 64 * 1024
private const val MaxRetainedOutputChars = 256 * 1024
