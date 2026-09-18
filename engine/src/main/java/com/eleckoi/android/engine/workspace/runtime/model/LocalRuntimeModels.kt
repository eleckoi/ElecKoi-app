package com.eleckoi.android.engine.workspace.runtime.model

enum class LocalRuntimeTarget {
    SystemProbe,
    DeepSeekHarness,
}

enum class LocalRuntimeStream {
    Stdout,
    Stderr,
}

/** Secrets stay Android-side; the guest receives only a short-lived loopback route. */
data class DeepSeekRuntimeLaunchSpec(
    val workspaceId: String,
    val workspaceProjectPath: String = "",
    val providerBaseUrl: String,
    val model: String,
    val modelContextWindow: Int,
    val autoCompactTokenLimit: Int? = null,
    val maxTokens: Int? = null,
    val ephemeral: Boolean = false,
    /** Advisory catalog consumed by the official dsh-llm-deepseek plugin. */
    val deepSeekModelsJson: String = "[]",
    /** Literal provider dictionary consumed by the official dsh-llm-pi-ai plugin. */
    val piAiProvidersJson: String = "{}",
    val workspaceToolsEnabled: Boolean = false,
    val workflowToolsEnabled: Boolean = false,
    val collaborationToolsEnabled: Boolean = false,
)

data class LocalRuntimeCapabilities(
    val abi: String,
    val supportsArm64Runtime: Boolean,
    val health: LocalRuntimeHealth,
    val installedRuntimeVersion: String? = null,
    val availableRuntimeVersion: String? = null,
    val healthMessage: String? = null,
) {
    constructor(
        abi: String,
        supportsArm64Runtime: Boolean,
        runtimeInstalled: Boolean,
    ) : this(
        abi = abi,
        supportsArm64Runtime = supportsArm64Runtime,
        health = if (runtimeInstalled) LocalRuntimeHealth.Healthy else LocalRuntimeHealth.NotInstalled,
    )

    val runtimeInstalled: Boolean
        get() = health == LocalRuntimeHealth.Healthy || health == LocalRuntimeHealth.UpdateAvailable

    val isUsable: Boolean
        get() = supportsArm64Runtime && runtimeInstalled
}

enum class LocalRuntimeHealth {
    Unsupported,
    NotInstalled,
    Checking,
    Healthy,
    UpdateAvailable,
    NeedsRepair,
}

enum class RuntimeMaintenanceOperation {
    Install,
    Update,
    Repair,
    Uninstall,
}

enum class RuntimeInstallationStage {
    Checking,
    DownloadingRootfs,
    DownloadingHarness,
    DownloadingNode,
    DownloadingPnpm,
    ExtractingRootfs,
    ExtractingHarness,
    ExtractingNode,
    ExtractingPnpm,
    ProvisioningPackages,
    Verifying,
    Activating,
    Removing,
    Cleaning,
}

data class RuntimeInstallationProgress(
    val stage: RuntimeInstallationStage,
    val completedBytes: Long = 0,
    val totalBytes: Long? = null,
    val processedEntries: Int = 0,
    val componentId: String? = null,
)

sealed interface RuntimeInstallationState {
    data object Idle : RuntimeInstallationState
    data class Installing(
        val progress: RuntimeInstallationProgress,
        val operation: RuntimeMaintenanceOperation = RuntimeMaintenanceOperation.Install,
    ) : RuntimeInstallationState
    data class Failed(
        val message: String,
        val operation: RuntimeMaintenanceOperation? = null,
    ) : RuntimeInstallationState
}

sealed interface RuntimeInstallationEvent {
    data class Progress(
        val progress: RuntimeInstallationProgress,
        val operation: RuntimeMaintenanceOperation = RuntimeMaintenanceOperation.Install,
    ) : RuntimeInstallationEvent
    data class Completed(
        val capabilities: LocalRuntimeCapabilities,
        val operation: RuntimeMaintenanceOperation = RuntimeMaintenanceOperation.Install,
    ) : RuntimeInstallationEvent
    data class Cancelled(
        val operation: RuntimeMaintenanceOperation = RuntimeMaintenanceOperation.Install,
    ) : RuntimeInstallationEvent
    data class Failed(
        val message: String,
        val operation: RuntimeMaintenanceOperation? = null,
    ) : RuntimeInstallationEvent
}

sealed interface LocalRuntimeState {
    data object Disconnected : LocalRuntimeState
    data object Connecting : LocalRuntimeState
    data class Ready(val capabilities: LocalRuntimeCapabilities) : LocalRuntimeState
    data class Running(
        val commandId: String,
        val target: LocalRuntimeTarget,
        val capabilities: LocalRuntimeCapabilities,
    ) : LocalRuntimeState
    data class Failed(val message: String) : LocalRuntimeState
}

sealed interface LocalRuntimeEvent {
    data class ProcessStarted(
        val commandId: String,
        val target: LocalRuntimeTarget,
    ) : LocalRuntimeEvent

    data class Output(
        val commandId: String,
        val stream: LocalRuntimeStream,
        val line: String,
    ) : LocalRuntimeEvent

    data class ProcessExited(
        val commandId: String,
        val exitCode: Int,
        val cancelled: Boolean,
    ) : LocalRuntimeEvent

    data class Failure(
        val commandId: String?,
        val message: String,
    ) : LocalRuntimeEvent
}
