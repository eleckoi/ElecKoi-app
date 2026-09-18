package com.eleckoi.android.engine.workspace.runtime.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.eleckoi.android.engine.workspace.runtime.runtimeInstallationStageFromWire
import com.eleckoi.android.engine.workspace.runtime.localRuntimeHealthFromWire
import com.eleckoi.android.engine.workspace.runtime.maintenanceOperationFromWire
import com.eleckoi.android.engine.workspace.runtime.wireName
import com.eleckoi.android.engine.workspace.runtime.RuntimeInstallationInspector
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.RuntimeStorageUsageReader
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeCapabilities
import com.eleckoi.android.engine.workspace.runtime.model.DeepSeekRuntimeLaunchSpec
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeEvent
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeState
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeStorageUsage
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeStream
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeInstallationEvent
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeInstallationProgress
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeInstallationState
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeHealth
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeMaintenanceOperation
import com.eleckoi.android.engine.workspace.runtime.work.RuntimeBootstrapScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LocalRuntimeServiceClient(context: Context) : LocalRuntimeGateway {
    private val applicationContext = context.applicationContext
    private val connectMutex = Mutex()
    private val _state = MutableStateFlow<LocalRuntimeState>(LocalRuntimeState.Disconnected)
    private val _events = MutableSharedFlow<LocalRuntimeEvent>()
    private val _installationState = MutableStateFlow<RuntimeInstallationState>(RuntimeInstallationState.Idle)
    private val _installationEvents =
        MutableSharedFlow<RuntimeInstallationEvent>(
            extraBufferCapacity = InstallationEventBufferCapacity,
        )
    private val outputReassembler = RuntimeOutputReassembler()
    private val inputSpool by lazy { RuntimeInputSpool.create(applicationContext) }
    private val eventDispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventDispatcher = OrderedRuntimeEventDispatcher(
        scope = eventDispatchScope,
        deliver = ::deliverRuntimeEvent,
    )
    private val callbackMessenger = Messenger(CallbackHandler())
    private var serviceMessenger: Messenger? = null
    private var connectionDeferred: CompletableDeferred<Unit>? = null
    private var bound = false
    private var capabilities: LocalRuntimeCapabilities? = null

    override val state: StateFlow<LocalRuntimeState> = _state.asStateFlow()
    override val events: SharedFlow<LocalRuntimeEvent> = _events.asSharedFlow()
    override val installationState: StateFlow<RuntimeInstallationState> = _installationState.asStateFlow()
    override val installationEvents: SharedFlow<RuntimeInstallationEvent> = _installationEvents.asSharedFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceMessenger = binder?.let(::Messenger)
            if (serviceMessenger == null) {
                failConnection("本地运行时服务没有返回 Binder")
                return
            }
            runCatching { send(RuntimeIpc.RegisterClient, Bundle(), includeReplyTo = true) }
                .onFailure { failConnection(it.message ?: "注册本地运行时客户端失败") }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            capabilities = null
            bound = false
            failConnection("本地运行时服务已断开")
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)

        override fun onNullBinding(name: ComponentName?) = failConnection("本地运行时服务无法绑定")
    }

    override suspend fun connect() = connectMutex.withLock {
        if (_state.value is LocalRuntimeState.Ready || _state.value is LocalRuntimeState.Running) return@withLock
        capabilities?.takeIf { serviceMessenger != null }?.let { detected ->
            _state.value = LocalRuntimeState.Ready(detected)
            return@withLock
        }
        connectionDeferred?.let { pending ->
            withTimeout(ConnectionTimeoutMillis) { pending.await() }
            return@withLock
        }
        _state.value = LocalRuntimeState.Connecting
        val deferred = CompletableDeferred<Unit>()
        connectionDeferred = deferred
        val intent = Intent(applicationContext, LocalRuntimeService::class.java)
        bound = applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) failConnection("无法启动本地运行时服务")
        try {
            withTimeout(ConnectionTimeoutMillis) { deferred.await() }
        } finally {
            connectionDeferred = null
        }
    }

    override suspend fun startSystemProbe(commandId: String) {
        connect()
        send(
            RuntimeIpc.StartProcess,
            Bundle().apply {
                putString(RuntimeIpc.KeyCommandId, commandId)
                putString(RuntimeIpc.KeyTarget, RuntimeIpc.TargetProbe)
            },
        )
    }

    override suspend fun installRuntime() {
        // A user pressing "Install" is an interactive command, not deferred background upkeep.
        // Starting maintenance through the service immediately publishes Installing/Failed state
        // to every open client. WorkManager remains responsible only for automatic app bootstrap.
        RuntimeBootstrapScheduler.cancelPendingAutomaticInstall(applicationContext)
        maintainRuntime(RuntimeMaintenanceOperation.Install)
    }

    internal suspend fun installRuntimeDirectly() {
        maintainRuntime(RuntimeMaintenanceOperation.Install)
    }

    override suspend fun updateRuntime() {
        maintainRuntime(RuntimeMaintenanceOperation.Update)
    }

    override suspend fun repairRuntime() {
        maintainRuntime(RuntimeMaintenanceOperation.Repair)
    }

    override suspend fun uninstallRuntime() {
        maintainRuntime(RuntimeMaintenanceOperation.Uninstall)
    }

    override suspend fun refreshRuntimeStatus() {
        connect()
        send(RuntimeIpc.RefreshRuntimeStatus, Bundle())
    }

    override suspend fun readStorageUsage(): LocalRuntimeStorageUsage = withContext(Dispatchers.IO) {
        val paths = RuntimePaths(applicationContext)
        val manifest = RuntimeInstallationInspector.readActive(paths)
            ?: return@withContext LocalRuntimeStorageUsage.Unknown
        val installation = runCatching { paths.installation(manifest.installationDirectory) }
            .getOrNull() ?: return@withContext LocalRuntimeStorageUsage.Unknown
        RuntimeStorageUsageReader.measure(
            installation = installation,
            harnessRelativePaths = manifest.harnessEntrypoints.values + manifest.harnessConfigPaths.values,
        )
    }

    private suspend fun maintainRuntime(operation: RuntimeMaintenanceOperation) {
        connect()
        send(
            RuntimeIpc.InstallRuntime,
            Bundle().apply { putString(RuntimeIpc.KeyMaintenanceOperation, operation.wireName) },
        )
    }

    override suspend fun cancelRuntimeInstallation() {
        connect()
        send(RuntimeIpc.CancelRuntimeInstallation, Bundle())
    }

    override suspend fun startDeepSeekHarness(commandId: String, launchSpec: DeepSeekRuntimeLaunchSpec) {
        connect()
        send(
            RuntimeIpc.StartProcess,
            Bundle().apply {
                putString(RuntimeIpc.KeyCommandId, commandId)
                putString(RuntimeIpc.KeyTarget, RuntimeIpc.TargetDeepSeek)
                putString(RuntimeIpc.KeyWorkspaceId, launchSpec.workspaceId)
                putString(RuntimeIpc.KeyWorkspaceProjectPath, launchSpec.workspaceProjectPath)
                putString(RuntimeIpc.KeyProviderBaseUrl, launchSpec.providerBaseUrl)
                putString(RuntimeIpc.KeyModel, launchSpec.model)
                putInt(RuntimeIpc.KeyModelContextWindow, launchSpec.modelContextWindow)
                launchSpec.autoCompactTokenLimit?.let {
                    putInt(RuntimeIpc.KeyAutoCompactTokenLimit, it)
                }
                launchSpec.maxTokens?.let { putInt(RuntimeIpc.KeyMaxTokens, it) }
                putBoolean(RuntimeIpc.KeyEphemeral, launchSpec.ephemeral)
                putString(RuntimeIpc.KeyDeepSeekModelsJson, launchSpec.deepSeekModelsJson)
                putString(RuntimeIpc.KeyPiAiProvidersJson, launchSpec.piAiProvidersJson)
                putBoolean(RuntimeIpc.KeyWorkspaceToolsEnabled, launchSpec.workspaceToolsEnabled)
                putBoolean(RuntimeIpc.KeyWorkflowToolsEnabled, launchSpec.workflowToolsEnabled)
                putBoolean(
                    RuntimeIpc.KeyCollaborationToolsEnabled,
                    launchSpec.collaborationToolsEnabled,
                )
            },
        )
    }

    override suspend fun sendLine(commandId: String, line: String) {
        if (line.length <= RuntimeInputSpool.InlineLineMaxChars) {
            send(
                RuntimeIpc.SendLine,
                Bundle().apply {
                    putString(RuntimeIpc.KeyCommandId, commandId)
                    putString(RuntimeIpc.KeyLine, line)
                },
            )
            return
        }
        val spoolFile = withContext(Dispatchers.IO) { inputSpool.write(line) }
        try {
            send(
                RuntimeIpc.SendLine,
                Bundle().apply {
                    putString(RuntimeIpc.KeyCommandId, commandId)
                    putString(RuntimeIpc.KeyLineSpoolFile, spoolFile)
                },
            )
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { inputSpool.discard(spoolFile) }
            throw error
        }
    }

    override suspend fun stop(commandId: String) {
        send(
            RuntimeIpc.StopProcess,
            Bundle().apply { putString(RuntimeIpc.KeyCommandId, commandId) },
        )
    }

    override fun close() {
        if (serviceMessenger != null) {
            runCatching { send(RuntimeIpc.UnregisterClient, Bundle(), includeReplyTo = true) }
        }
        if (bound) runCatching { applicationContext.unbindService(serviceConnection) }
        serviceMessenger = null
        capabilities = null
        bound = false
        connectionDeferred?.cancel()
        connectionDeferred = null
        _state.value = LocalRuntimeState.Disconnected
        _installationState.value = RuntimeInstallationState.Idle
        outputReassembler.clear()
        eventDispatcher.close()
        eventDispatchScope.cancel()
    }

    private inner class CallbackHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                RuntimeIpc.Ready -> handleReady(message.data)
                RuntimeIpc.ProcessStarted -> handleStarted(message.data)
                RuntimeIpc.ProcessOutput -> handleOutput(message.data)
                RuntimeIpc.ProcessExited -> handleExited(message.data)
                RuntimeIpc.Failure -> handleFailure(message.data)
                RuntimeIpc.RuntimeInstallationProgress -> handleInstallationProgress(message.data)
                RuntimeIpc.RuntimeInstallationCompleted -> handleInstallationCompleted(message.data)
                RuntimeIpc.RuntimeInstallationCancelled -> handleInstallationCancelled(message.data)
                RuntimeIpc.RuntimeInstallationFailed -> handleInstallationFailed(message.data)
                RuntimeIpc.RuntimeCapabilitiesChanged -> handleCapabilitiesChanged(message.data)
                else -> super.handleMessage(message)
            }
        }
    }

    private fun handleReady(data: Bundle) {
        val detected = capabilitiesFrom(data)
        capabilities = detected
        _state.value = LocalRuntimeState.Ready(detected)
        _installationState.value = RuntimeInstallationState.Idle
        connectionDeferred?.complete(Unit)
    }

    private fun handleInstallationProgress(data: Bundle) {
        val stage = runtimeInstallationStageFromWire(data.getString(RuntimeIpc.KeyInstallStage))
            ?: return handleInstallationFailed("本地运行时返回了未知的安装阶段")
        val progress = RuntimeInstallationProgress(
            stage = stage,
            completedBytes = data.getLong(RuntimeIpc.KeyCompletedBytes),
            totalBytes = if (data.getBoolean(RuntimeIpc.KeyHasTotalBytes)) {
                data.getLong(RuntimeIpc.KeyTotalBytes)
            } else {
                null
            },
            processedEntries = data.getInt(RuntimeIpc.KeyProcessedEntries),
            componentId = data.getString(RuntimeIpc.KeyComponentId),
        )
        val operation = maintenanceOperationFromWire(data.getString(RuntimeIpc.KeyMaintenanceOperation))
            ?: return handleInstallationFailed("本地运行时返回了未知的维护操作")
        _installationState.value = RuntimeInstallationState.Installing(progress, operation)
        _installationEvents.tryEmit(RuntimeInstallationEvent.Progress(progress, operation))
    }

    private fun handleInstallationCompleted(data: Bundle) {
        val detected = capabilitiesFrom(data)
        val operation = maintenanceOperationFromWire(data.getString(RuntimeIpc.KeyMaintenanceOperation))
            ?: RuntimeMaintenanceOperation.Install
        capabilities = detected
        _state.value = when (val current = _state.value) {
            is LocalRuntimeState.Running -> current.copy(capabilities = detected)
            else -> LocalRuntimeState.Ready(detected)
        }
        _installationState.value = RuntimeInstallationState.Idle
        _installationEvents.tryEmit(RuntimeInstallationEvent.Completed(detected, operation))
    }

    private fun handleInstallationCancelled(data: Bundle = Bundle()) {
        val operation = maintenanceOperationFromWire(data.getString(RuntimeIpc.KeyMaintenanceOperation))
            ?: RuntimeMaintenanceOperation.Install
        _installationState.value = RuntimeInstallationState.Idle
        _installationEvents.tryEmit(RuntimeInstallationEvent.Cancelled(operation))
    }

    private fun handleInstallationFailed(data: Bundle) {
        val operation = maintenanceOperationFromWire(data.getString(RuntimeIpc.KeyMaintenanceOperation))
        handleInstallationFailed(
            data.getString(RuntimeIpc.KeyMessage).orEmpty().ifBlank { "本地运行时安装失败" },
            operation,
        )
    }

    private fun handleInstallationFailed(
        message: String,
        operation: RuntimeMaintenanceOperation? = null,
    ) {
        _installationState.value = RuntimeInstallationState.Failed(message, operation)
        _installationEvents.tryEmit(RuntimeInstallationEvent.Failed(message, operation))
    }

    private fun handleCapabilitiesChanged(data: Bundle) {
        val detected = capabilitiesFrom(data)
        capabilities = detected
        _state.value = when (val current = _state.value) {
            is LocalRuntimeState.Running -> current.copy(capabilities = detected)
            else -> LocalRuntimeState.Ready(detected)
        }
    }

    private fun handleStarted(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        outputReassembler.clear(commandId)
        val target = when (data.getString(RuntimeIpc.KeyTarget)) {
            RuntimeIpc.TargetDeepSeek -> LocalRuntimeTarget.DeepSeekHarness
            else -> LocalRuntimeTarget.SystemProbe
        }
        val detected = capabilities ?: return failConnection("本地运行时能力尚未就绪")
        _state.value = LocalRuntimeState.Running(commandId, target, detected)
        if (!dispatchEvent(LocalRuntimeEvent.ProcessStarted(commandId, target))) {
            failEventDelivery(EventPipelineUnavailableMessage)
        }
    }

    private fun handleOutput(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        val stream = if (data.getString(RuntimeIpc.KeyStream) == RuntimeIpc.StreamStderr) {
            LocalRuntimeStream.Stderr
        } else {
            LocalRuntimeStream.Stdout
        }
        val endOfLine = if (data.containsKey(RuntimeIpc.KeyEndOfLine)) {
            data.getBoolean(RuntimeIpc.KeyEndOfLine)
        } else {
            true
        }
        runCatching {
            outputReassembler.accept(
                commandId = commandId,
                stream = stream,
                fragment = data.getString(RuntimeIpc.KeyLine).orEmpty(),
                endOfLine = endOfLine,
            )
        }.onSuccess { line ->
            if (line != null) dispatchEvent(LocalRuntimeEvent.Output(commandId, stream, line))
        }.onFailure { error ->
            outputReassembler.clear(commandId)
            failConnection(error.message ?: "本地运行时输出重组失败")
        }
    }

    private fun handleExited(data: Bundle) {
        val commandId = data.getString(RuntimeIpc.KeyCommandId).orEmpty()
        outputReassembler.clear(commandId)
        val event = LocalRuntimeEvent.ProcessExited(
            commandId = commandId,
            exitCode = data.getInt(RuntimeIpc.KeyExitCode),
            cancelled = data.getBoolean(RuntimeIpc.KeyCancelled),
        )
        if (dispatchEvent(event)) {
            capabilities?.let { _state.value = LocalRuntimeState.Ready(it) }
        }
    }

    private fun handleFailure(data: Bundle) {
        val message = data.getString(RuntimeIpc.KeyMessage).orEmpty().ifBlank { "本地运行时失败" }
        val commandId = data.getString(RuntimeIpc.KeyCommandId)
        if (commandId == null) outputReassembler.clear() else outputReassembler.clear(commandId)
        val accepted = dispatchEvent(LocalRuntimeEvent.Failure(commandId, message))
        if (accepted) {
            _state.value = runtimeStateAfterOperationFailure(capabilities, message)
            if (capabilities == null) {
                connectionDeferred?.completeExceptionally(IllegalStateException(message))
            }
        }
    }

    private fun failConnection(message: String, emitEvent: Boolean = true) {
        outputReassembler.clear()
        _state.value = LocalRuntimeState.Failed(message)
        _installationState.value = RuntimeInstallationState.Idle
        connectionDeferred?.completeExceptionally(IllegalStateException(message))
        if (emitEvent) dispatchEvent(LocalRuntimeEvent.Failure(null, message))
    }

    private fun dispatchEvent(event: LocalRuntimeEvent): Boolean = eventDispatcher.dispatch(event)

    private suspend fun deliverRuntimeEvent(event: LocalRuntimeEvent) {
        // MutableSharedFlow with replay=0 discards values when nobody is listening. Waiting here
        // converts that otherwise silent loss into bounded queue pressure and an explicit,
        // fail-closed overflow if a process keeps producing output without a consumer.
        _events.subscriptionCount.first { subscriberCount -> subscriberCount > 0 }
        _events.emit(event)
    }

    /**
     * Losing a JSONL fragment or terminal process event would desynchronize the app-server
     * protocol. Stop the active child and make the failure visible through StateFlow; the
     * dispatcher emits a final Failure after draining all events that were accepted in order.
     */
    private fun failEventDelivery(message: String) {
        outputReassembler.clear()
        val running = _state.value as? LocalRuntimeState.Running
        _state.value = LocalRuntimeState.Failed(message)
        _installationState.value = RuntimeInstallationState.Idle
        connectionDeferred?.completeExceptionally(IllegalStateException(message))
        if (running != null && serviceMessenger != null) {
            runCatching {
                send(
                    RuntimeIpc.StopProcess,
                    Bundle().apply { putString(RuntimeIpc.KeyCommandId, running.commandId) },
                )
            }
        }
    }

    private fun capabilitiesFrom(data: Bundle): LocalRuntimeCapabilities {
        val health = localRuntimeHealthFromWire(data.getString(RuntimeIpc.KeyRuntimeHealth))
            ?: if (data.getBoolean(RuntimeIpc.KeyRuntimeInstalled)) {
                LocalRuntimeHealth.Healthy
            } else {
                LocalRuntimeHealth.NotInstalled
            }
        return LocalRuntimeCapabilities(
            abi = data.getString(RuntimeIpc.KeyAbi).orEmpty(),
            supportsArm64Runtime = data.getBoolean(RuntimeIpc.KeySupportsArm64),
            health = health,
            installedRuntimeVersion = data.getString(RuntimeIpc.KeyInstalledRuntimeVersion),
            availableRuntimeVersion = data.getString(RuntimeIpc.KeyAvailableRuntimeVersion),
            healthMessage = data.getString(RuntimeIpc.KeyHealthMessage),
        )
    }

    private fun send(what: Int, data: Bundle, includeReplyTo: Boolean = false) {
        val service = serviceMessenger ?: error("本地运行时服务尚未连接")
        service.send(
            Message.obtain(null, what).apply {
                this.data = data
                if (includeReplyTo) replyTo = callbackMessenger
            },
        )
    }

    private companion object {
        const val ConnectionTimeoutMillis = 10_000L
        const val InstallationEventBufferCapacity = 128
        const val EventPipelineUnavailableMessage =
            "本地运行时事件通道不可用，已停止进程以防止协议状态不同步"
    }
}
