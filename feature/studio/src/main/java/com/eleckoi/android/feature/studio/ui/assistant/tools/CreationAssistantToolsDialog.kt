package com.eleckoi.android.feature.studio.ui.assistant.tools

import androidx.compose.runtime.Composable
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.feature.characters.presets.ui.editor.AgentToolQuickDialog
import com.eleckoi.android.foundation.design.AppearanceTheme

/**
 * Uses the same tool browser as role conversations while keeping creator state independent from
 * the active role preset. Only the durable values supplied by the creator ViewModel are changed.
 */
@Composable
internal fun CreationAssistantToolsDialog(
    groups: List<AgentToolGroupSnapshot>,
    enabledGroupIds: Set<String>,
    imageModelConfigId: String,
    modelConfigs: List<ModelConfig>,
    enabled: Boolean,
    appearance: AppearanceTheme,
    onEnabledChange: (String, Boolean) -> Unit,
    onImageModelConfigChange: (String) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val toolModelConfigIds = imageModelConfigId
        .takeIf(String::isNotBlank)
        ?.let { mapOf(AgentToolRequestPolicy.BuiltInCreator to it) }
        .orEmpty()
    AgentToolQuickDialog(
        stateKey = "creator-assistant-tools",
        title = "创作助手工具",
        groups = groups,
        configuration = AgentPresetToolConfiguration(
            includedGroupIds = groups.map(AgentToolGroupSnapshot::id),
            enabledGroupIds = enabledGroupIds,
            toolModelConfigIds = toolModelConfigIds,
        ),
        roleplayPlan = AgentPresetRoleplayPlan(),
        modelConfigs = modelConfigs,
        appearance = appearance,
        interactionEnabled = enabled,
        showPresetSpecificConfiguration = false,
        onEnabledChange = onEnabledChange,
        onOpenWebSearchSettings = onOpenWebSearchSettings,
        onRoleplayPlanChange = {},
        onSubagentModelChange = { _, _ -> },
        onToolModelConfigChange = { groupId, configId ->
            if (groupId == AgentToolRequestPolicy.BuiltInCreator) {
                onImageModelConfigChange(configId)
            }
        },
        onSaveModelConfig = onSaveModelConfig,
        onRefreshModels = onRefreshModels,
        onDismiss = onDismiss,
    )
}
