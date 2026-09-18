package com.eleckoi.android.engine.workspace.runtime.service

internal object RuntimeIpc {
    const val RegisterClient = 1
    const val UnregisterClient = 2
    const val Ready = 3
    const val StartProcess = 10
    const val SendLine = 11
    const val StopProcess = 12
    const val InstallRuntime = 13
    const val CancelRuntimeInstallation = 14
    const val RefreshRuntimeStatus = 15
    const val ProcessStarted = 20
    const val ProcessOutput = 21
    const val ProcessExited = 22
    const val Failure = 23
    const val RuntimeInstallationProgress = 24
    const val RuntimeInstallationCompleted = 25
    const val RuntimeInstallationCancelled = 26
    const val RuntimeInstallationFailed = 27
    const val RuntimeCapabilitiesChanged = 28

    const val KeyCommandId = "command_id"
    const val KeyTarget = "target"
    const val KeyWorkspaceId = "workspace_id"
    const val KeyWorkspaceProjectPath = "workspace_project_path"
    const val KeyProviderBaseUrl = "provider_base_url"
    const val KeyModel = "model"
    const val KeyModelContextWindow = "model_context_window"
    const val KeyAutoCompactTokenLimit = "auto_compact_token_limit"
    const val KeyMaxTokens = "max_tokens"
    const val KeyEphemeral = "ephemeral"
    const val KeyDeepSeekModelsJson = "deepseek_models_json"
    const val KeyPiAiProvidersJson = "pi_ai_providers_json"
    const val KeyWorkspaceToolsEnabled = "workspace_tools_enabled"
    const val KeyWorkflowToolsEnabled = "workflow_tools_enabled"
    const val KeyCollaborationToolsEnabled = "collaboration_tools_enabled"
    const val KeyLine = "line"
    const val KeyLineSpoolFile = "line_spool_file"
    const val KeyEndOfLine = "end_of_line"
    const val KeyStream = "stream"
    const val KeyExitCode = "exit_code"
    const val KeyCancelled = "cancelled"
    const val KeyMessage = "message"
    const val KeyAbi = "abi"
    const val KeySupportsArm64 = "supports_arm64"
    const val KeyRuntimeInstalled = "runtime_installed"
    const val KeyRuntimeHealth = "runtime_health"
    const val KeyInstalledRuntimeVersion = "installed_runtime_version"
    const val KeyAvailableRuntimeVersion = "available_runtime_version"
    const val KeyHealthMessage = "health_message"
    const val KeyMaintenanceOperation = "maintenance_operation"
    const val KeyInstallStage = "install_stage"
    const val KeyCompletedBytes = "completed_bytes"
    const val KeyTotalBytes = "total_bytes"
    const val KeyHasTotalBytes = "has_total_bytes"
    const val KeyProcessedEntries = "processed_entries"
    const val KeyComponentId = "component_id"

    const val TargetProbe = "system_probe"
    const val TargetDeepSeek = "deepseek_harness"
    const val StreamStdout = "stdout"
    const val StreamStderr = "stderr"
}
