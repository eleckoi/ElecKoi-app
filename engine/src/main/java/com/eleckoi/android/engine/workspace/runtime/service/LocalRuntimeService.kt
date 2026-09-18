package com.eleckoi.android.engine.workspace.runtime.service

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.eleckoi.android.engine.workspace.runtime.AndroidRuntimeCapabilityProbe
import com.eleckoi.android.engine.workspace.runtime.AndroidDnsConfigWriter
import com.eleckoi.android.engine.workspace.runtime.DeepSeekPluginCompositionManager
import com.eleckoi.android.engine.workspace.runtime.RuntimeBundleInstaller
import com.eleckoi.android.engine.workspace.runtime.RuntimeDistributionCatalog
import com.eleckoi.android.engine.workspace.runtime.RuntimeHealthCommand
import com.eleckoi.android.engine.workspace.runtime.RuntimeHealthInspector
import com.eleckoi.android.engine.workspace.runtime.RuntimeInstallationInspector
import com.eleckoi.android.engine.workspace.runtime.RuntimeInstallationProgressThrottle
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.toDomain
import com.eleckoi.android.engine.workspace.runtime.wireName
import com.eleckoi.android.engine.workspace.runtime.maintenanceOperationFromWire
import com.eleckoi.android.engine.workspace.runtime.process.DeepSeekRuntimeProcessSpecFactory
import com.eleckoi.android.engine.workspace.runtime.process.ProcessSupervisor
import com.eleckoi.android.engine.workspace.runtime.process.RuntimeGuestCommand
import com.eleckoi.android.engine.workspace.runtime.process.RuntimeGuestProcessSpecFactory
import com.eleckoi.android.engine.workspace.runtime.process.CompositeProcessResourceGuard
import com.eleckoi.android.engine.workspace.runtime.process.FileTreeQuota
import com.eleckoi.android.engine.workspace.runtime.process.FileTreeQuotaGuard
import com.eleckoi.android.engine.workspace.runtime.process.FileTreeSymbolicLinkPolicy
import com.eleckoi.android.engine.workspace.runtime.process.MinimumUsableSpaceGuard
import com.eleckoi.android.engine.workspace.runtime.process.StartupOnlyProcessResourceGuard
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeCapabilities
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeMaintenanceOperation
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeInstallationProgress
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

/**
 * Process owner for local tools. It runs in `:local_runtime`, outside the UI process.
 * This is crash/lifecycle isolation only; both processes still share the app UID.
 */
class LocalRuntimeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = CopyOnWriteArraySet<Messenger>()
    private val paths by lazy { RuntimePaths(applicationContext) }
    private val ipc = LocalRuntimeIpcBridge(
        clients = clients,
        cleanupSessionScratch = { commandId -> paths.cleanupSessionScratch(commandId) },
    )
    private val catalog by lazy { RuntimeDistributionCatalog.load(applicationContext) }
    private lateinit var capabilities: AtomicReference<LocalRuntimeCapabilities>
    private val dnsConfigWriter by lazy {
        AndroidDnsConfigWriter(applicationContext, paths.hostResolverConfig)
    }
    private val deepSeekProcessSpecFactory by lazy {
        DeepSeekRuntimeProcessSpecFactory(nativeLibraryDirectory = paths.nativeLibraryRoot)
    }
    private val deepSeekPluginCompositionManager by lazy {
        DeepSeekPluginCompositionManager(applicationContext)
    }
    private val runtimeInstaller by lazy { RuntimeBundleInstaller(applicationContext, paths) }
    private val healthInspector by lazy {
        RuntimeHealthInspector(applicationContext, paths, catalog)
    }
    private val guestProcessSpecFactory by lazy {
        RuntimeGuestProcessSpecFactory(
            nativeLibraryDirectory = paths.nativeLibraryRoot,
            hostTempDirectory = paths.hostTemp,
        )
    }
    private val processDiagnostics by lazy {
        RuntimeProcessDiagnosticLogger(
            enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            sink = { line -> Log.w(DshDiagnosticTag, line) },
        )
    }
    private val supervisor by lazy {
        ProcessSupervisor(
            scope = serviceScope,
            onEvent = { event ->
                processDiagnostics.record(event)
                ipc.broadcastProcessEvent(event)
            },
        )
    }
    private val inputSpool by lazy { RuntimeInputSpool.create(applicationContext) }
    private val incomingMessenger by lazy { Messenger(IncomingHandler()) }
    private val installationLock = Any()
    /** Serializes the check-and-reserve boundary between process starts and runtime maintenance. */
    private val operationAdmissionMutex = Mutex()
    @Volatile private var installationJob: Job? = null
    @Volatile private var latestInstallationProgress: RuntimeInstallationProgress? = null
    @Volatile private var maintenanceOperation: RuntimeMaintenanceOperation? = null
    @Volatile private var healthRefreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        inputSpool.cleanup()
        paths.cleanupAllSessionScratch()
        capabilities = AtomicReference(AndroidRuntimeCapabilityProbe.inspect(paths, catalog))
        refreshCapabilities()
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // The loopback provider adapter lives in the UI process. Keeping its App Server child
        // alive after that client disappears creates an unrecoverable orphan with a dead endpoint.
        supervisor.shutdownNow()
        paths.cleanupAllSessionScratch()
        clients.clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        synchronized(installationLock) { installationJob }?.cancel()
        healthRefreshJob?.cancel()
        supervisor.shutdownNow()
        paths.cleanupAllSessionScratch()
        serviceScope.cancel()
        clients.clear()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                RuntimeIpc.RegisterClient -> message.replyTo?.let { client ->
                    clients += client
                    ipc.sendReady(
                        client = client,
                        detected = capabilities.get(),
                        latestProgress = latestInstallationProgress,
                        operation = maintenanceOperation ?: RuntimeMaintenanceOperation.Install,
                    )
                }
                RuntimeIpc.UnregisterClient -> message.replyTo?.let(clients::remove)
                // Android recycles Message instances after handleMessage returns. Snapshot the
                // Bundle before crossing the coroutine boundary; reading message.data inside the
                // coroutine can otherwise race with recycling and turn a valid command id into an
                // empty string.
                RuntimeIpc.StartProcess -> Bundle(message.data).let { data ->
                    serviceScope.launch { startProcess(data) }
                }
                RuntimeIpc.SendLine -> Bundle(message.data).let { data ->
                    serviceScope.launch { sendLine(data) }
                }
                RuntimeIpc.StopProcess -> Bundle(message.data).let { data ->
                    serviceScope.launch { stopProcess(data) }
                }
                RuntimeIpc.InstallRuntime -> startRuntimeMaintenance(
                    maintenanceOperationFromWire(message.data.getString(RuntimeIpc.KeyMaintenanceOperation))
                        ?: RuntimeMaintenanceOperation.Install,
                )
                RuntimeIpc.CancelRuntimeInstallation -> cancelRuntimeInstallation()
                RuntimeIpc.RefreshRuntimeStatus -> refreshCapabilities(force = true)
                else -> super.handleMessage(message)
            }
        }
    }

    private suspend fun startProcess(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        runCatching {
            require(CommandId.matches(commandId)) { "运行任务编号无效" }
            operationAdmissionMutex.withLock {
                when (data.getString(RuntimeIpc.KeyTarget)) {
                RuntimeIpc.TargetProbe -> {
                    require(!supervisor.hasActiveProcess) { "DSH 运行期间无需重复验证 Ubuntu" }
                    require(synchronized(installationLock) { installationJob == null }) {
                        "本地创作环境正在维护"
                    }
                    val activeRuntime = requireNotNull(RuntimeInstallationInspector.activePaths(paths)) {
                        "本地创作环境尚未安装或需要修复"
                    }
                    val command = RuntimeGuestCommand(
                        commandId = commandId,
                        rootfs = activeRuntime.rootfs,
                        tools = activeRuntime.tools,
                        hostResolverConfig = dnsConfigWriter.refresh(),
                        arguments = listOf("/bin/sh", "-c", RuntimeHealthCommand.script(catalog)),
                        timeoutMillis = 45_000L,
                    )
                    supervisor.start(guestProcessSpecFactory.create(command))
                }
                RuntimeIpc.TargetDeepSeek -> {
                    // A previous DSH owner can disappear before its fire-and-forget stop IPC is
                    // observed. Reclaim only a stale DSH child here; the transport's exit barrier
                    // handles the normal handoff.
                    supervisor.stopActive(LocalRuntimeTarget.DeepSeekHarness)
                    require(supervisor.awaitIdle(HarnessSurfaceHandoffMillis)) {
                        "DeepSeek Harness 进程启动冲突，请稍后重试"
                    }
                    val launchSpec = deepSeekLaunchSpec(data)
                    val workspace = paths.workspaceProject(
                        launchSpec.workspaceId,
                        launchSpec.workspaceProjectPath,
                    )
                    val detected = capabilities.get()
                    require(detected.supportsArm64Runtime) { "当前设备不是受支持的 arm64-v8a 运行环境" }
                    require(detected.isUsable) { detected.healthMessage ?: "本地 Harness 运行时尚未就绪" }
                    require(synchronized(installationLock) { installationJob == null }) {
                        "本地 Harness 运行时正在安装"
                    }
                    val activeRuntime = requireNotNull(RuntimeInstallationInspector.activePaths(paths)) {
                        "本地 Harness 运行时安装不完整，请重新安装"
                    }
                    val scratch = paths.prepareSessionScratch(commandId)
                    val deepSeekHome = if (launchSpec.ephemeral) {
                        java.io.File(scratch.home, "deepseek-state").also {
                            require(it.mkdir()) { "无法创建 DeepSeek 临时状态目录" }
                        }
                    } else {
                        paths.workspaceDeepSeekHome(launchSpec.workspaceId)
                    }
                    try {
                        val harnessComposition = deepSeekPluginCompositionManager.prepare(
                            packagedConfig = activeRuntime.requireHarnessConfig("deepseek"),
                            deepSeekHome = deepSeekHome,
                            deepSeekProviderBaseUrl = launchSpec.providerBaseUrl
                                .removeSuffix("/")
                                .removeSuffix("/v1") + "/provider-wire/deepseek/v1",
                            deepSeekModelsJson = launchSpec.deepSeekModelsJson,
                            piAiProvidersJson = launchSpec.piAiProvidersJson,
                        )
                        supervisor.start(
                            deepSeekProcessSpecFactory.create(
                                commandId = commandId,
                                activeRuntime = activeRuntime,
                                workspace = workspace,
                                deepSeekHome = deepSeekHome,
                                harnessPatches = harnessComposition.patches,
                                launchSpec = launchSpec,
                                hostResolverConfig = dnsConfigWriter.refresh(),
                                sessionHome = scratch.home,
                                sessionGuestTemp = scratch.guestTemp,
                                sessionProotTemp = scratch.prootTemp,
                                resourceGuard = deepSeekResourceGuard(workspace, deepSeekHome, scratch.root),
                            ),
                        )
                    } catch (error: Throwable) {
                        paths.cleanupSessionScratch(commandId)
                        throw error
                    }
                }
                else -> error("未知的本地运行目标")
                }
            }
        }.onFailure { error ->
            ipc.broadcastFailure(commandId.ifBlank { null }, error.message ?: "启动本地运行任务失败")
        }
    }

    private fun deepSeekLaunchSpec(data: Bundle): DeepSeekRuntimeLaunchSpec = DeepSeekRuntimeLaunchSpec(
        workspaceId = data.getString(RuntimeIpc.KeyWorkspaceId).orEmpty(),
        workspaceProjectPath = data.getString(RuntimeIpc.KeyWorkspaceProjectPath).orEmpty(),
        providerBaseUrl = data.getString(RuntimeIpc.KeyProviderBaseUrl).orEmpty(),
        model = data.getString(RuntimeIpc.KeyModel).orEmpty(),
        modelContextWindow = data.getInt(RuntimeIpc.KeyModelContextWindow),
        autoCompactTokenLimit = data.takeIf {
            it.containsKey(RuntimeIpc.KeyAutoCompactTokenLimit)
        }?.getInt(RuntimeIpc.KeyAutoCompactTokenLimit),
        maxTokens = data.takeIf { it.containsKey(RuntimeIpc.KeyMaxTokens) }?.getInt(RuntimeIpc.KeyMaxTokens),
        ephemeral = data.getBoolean(RuntimeIpc.KeyEphemeral),
        deepSeekModelsJson = data.getString(RuntimeIpc.KeyDeepSeekModelsJson).orEmpty(),
        piAiProvidersJson = data.getString(RuntimeIpc.KeyPiAiProvidersJson).orEmpty(),
        workspaceToolsEnabled = data.getBoolean(RuntimeIpc.KeyWorkspaceToolsEnabled),
        workflowToolsEnabled = data.getBoolean(RuntimeIpc.KeyWorkflowToolsEnabled),
        collaborationToolsEnabled = data.getBoolean(RuntimeIpc.KeyCollaborationToolsEnabled),
    )

    private fun deepSeekResourceGuard(
        workspace: java.io.File,
        deepSeekHome: java.io.File,
        sessionScratch: java.io.File,
    ) = CompositeProcessResourceGuard(
        MinimumUsableSpaceGuard(paths.runtimeRoot, minimumUsableBytes = MinimumRuntimeFreeSpaceBytes),
        StartupOnlyProcessResourceGuard(
            CompositeProcessResourceGuard(
                FileTreeQuotaGuard(
                    workspace,
                    FileTreeQuota(
                        label = "DeepSeek 工作区",
                        maxFiles = 65_536,
                        maxEntries = 262_144,
                        maxDepth = CreatorWorkspaceLimits.MaxDirectoryDepth + 4,
                        maxSingleFileBytes = 2L * 1024L * 1024L * 1024L,
                        maxTotalBytes = 4L * 1024L * 1024L * 1024L,
                    ),
                    symbolicLinkPolicy = FileTreeSymbolicLinkPolicy.CountWithoutFollowing,
                ),
                FileTreeQuotaGuard(
                    deepSeekHome,
                    FileTreeQuota(
                        label = "DeepSeek 会话状态",
                        maxFiles = 65_536,
                        maxEntries = 131_072,
                        maxDepth = 96,
                        maxSingleFileBytes = 512L * 1024L * 1024L,
                        maxTotalBytes = 2L * 1024L * 1024L * 1024L,
                    ),
                    symbolicLinkPolicy = FileTreeSymbolicLinkPolicy.CountWithoutFollowing,
                ),
                FileTreeQuotaGuard(
                    sessionScratch,
                    FileTreeQuota(
                        label = "DeepSeek 临时目录",
                        maxFiles = 65_536,
                        maxEntries = 131_072,
                        maxDepth = 96,
                        maxSingleFileBytes = 512L * 1024L * 1024L,
                        maxTotalBytes = 2L * 1024L * 1024L * 1024L,
                    ),
                    symbolicLinkPolicy = FileTreeSymbolicLinkPolicy.CountWithoutFollowing,
                ),
            ),
        ),
    )

    private suspend fun sendLine(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        runCatching {
            val inlineLine = data.getString(RuntimeIpc.KeyLine)
            val spoolFile = data.getString(RuntimeIpc.KeyLineSpoolFile)
            require((inlineLine != null) xor (spoolFile != null)) { "运行时输入载荷无效" }
            supervisor.sendLine(
                commandId,
                spoolFile?.let(inputSpool::consume) ?: inlineLine.orEmpty(),
            )
        }.onFailure { error -> ipc.broadcastFailure(commandId, error.message ?: "写入本地进程失败") }
    }

    private suspend fun stopProcess(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        runCatching { supervisor.stop(commandId) }
            .onFailure { error -> ipc.broadcastFailure(commandId, error.message ?: "停止本地进程失败") }
    }

    private fun startRuntimeMaintenance(operation: RuntimeMaintenanceOperation) {
        healthRefreshJob?.cancel()
        val throttler = RuntimeInstallationProgressThrottle()
        val newJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                require(capabilities.get().supportsArm64Runtime) {
                    "当前设备不是受支持的 arm64-v8a 运行环境"
                }
                // installationJob is reserved before this lazy job starts. Holding the same
                // admission mutex used by startProcess makes the opposing checks atomic:
                // either the process becomes active first, or maintenance reservation wins.
                operationAdmissionMutex.withLock {
                    // The app-server is application-scoped now. Maintenance owns the boundary:
                    // retire it here and let the host lazily restart against the new runtime.
                    supervisor.stopActive()
                }
                if (operation == RuntimeMaintenanceOperation.Uninstall) {
                    runtimeInstaller.uninstall { installerProgress ->
                        val progress = installerProgress.toDomain()
                        latestInstallationProgress = progress
                        if (throttler.shouldEmit(progress)) ipc.broadcastInstallationProgress(progress, operation)
                    }
                } else {
                    runtimeInstaller.install(operation) { installerProgress ->
                        val progress = installerProgress.toDomain()
                        latestInstallationProgress = progress
                        if (throttler.shouldEmit(progress)) ipc.broadcastInstallationProgress(progress, operation)
                    }
                }
                val refreshed = healthInspector.inspect()
                capabilities.set(refreshed)
                latestInstallationProgress = null
                ipc.broadcastInstallationCompleted(refreshed, operation)
            } catch (_: CancellationException) {
                latestInstallationProgress = null
                ipc.broadcast(
                    RuntimeIpc.RuntimeInstallationCancelled,
                    Bundle().apply {
                        putString(RuntimeIpc.KeyMaintenanceOperation, operation.wireName)
                    },
                )
            } catch (error: Throwable) {
                latestInstallationProgress = null
                val refreshed = runCatching { healthInspector.inspect() }
                    .getOrElse { AndroidRuntimeCapabilityProbe.inspect(paths, catalog) }
                capabilities.set(refreshed)
                ipc.broadcastCapabilitiesChanged(refreshed)
                ipc.broadcastInstallationFailed(error.message ?: "本地运行时维护失败", operation)
            } finally {
                val currentJob = coroutineContext[Job]
                synchronized(installationLock) {
                    if (installationJob === currentJob) {
                        installationJob = null
                        maintenanceOperation = null
                    }
                }
            }
        }
        val accepted = synchronized(installationLock) {
            if (installationJob != null) {
                false
            } else {
                installationJob = newJob
                maintenanceOperation = operation
                true
            }
        }
        if (accepted) {
            newJob.start()
        } else {
            newJob.cancel()
            latestInstallationProgress?.let {
                ipc.broadcastInstallationProgress(it, maintenanceOperation ?: operation)
            }
        }
    }

    private fun cancelRuntimeInstallation() {
        synchronized(installationLock) { installationJob }?.cancel()
    }

    private fun refreshCapabilities(force: Boolean = false) {
        if (supervisor.hasActiveProcess || synchronized(installationLock) { installationJob != null }) {
            if (force) ipc.broadcastCapabilitiesChanged(capabilities.get())
            return
        }
        if (!force && healthRefreshJob?.isActive == true) return
        healthRefreshJob?.cancel()
        healthRefreshJob = serviceScope.launch {
            val refreshed = healthInspector.inspect()
            capabilities.set(refreshed)
            ipc.broadcastCapabilitiesChanged(refreshed)
        }
    }

    private companion object {
        const val DshDiagnosticTag = "ElecKoiDsh"
        val CommandId = Regex("^[A-Za-z0-9_-]{1,100}$")
        const val HarnessSurfaceHandoffMillis = 1_500L
        const val MinimumRuntimeFreeSpaceBytes = 256L * 1024L * 1024L
    }
}
