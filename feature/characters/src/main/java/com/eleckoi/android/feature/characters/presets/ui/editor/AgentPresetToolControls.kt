package com.eleckoi.android.feature.characters.presets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schema
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.AppSwitch

@Composable
internal fun AgentPresetToolsTab(
    preset: AgentPreset,
    availableGroups: List<AgentToolGroupSnapshot>,
    modelConfigs: List<ModelConfig>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onUpdate: (AgentPreset) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
) {
    var query by rememberSaveable(preset.id) { mutableStateOf("") }
    var addOpen by rememberSaveable(preset.id) { mutableStateOf(false) }
    var detailGroupId by rememberSaveable(preset.id) { mutableStateOf("") }
    val configuration = preset.toolConfiguration.normalized()
    val groupsById = remember(availableGroups) { availableGroups.associateBy(AgentToolGroupSnapshot::id) }
    val included = configuration.includedGroupIds.mapNotNull(groupsById::get)
    val visible = included.filter { group ->
        val needle = query.trim()
        needle.isBlank() || group.searchableText().contains(needle, ignoreCase = true)
    }
    val modalOpen = addOpen || detailGroupId.isNotBlank()

    LaunchedEffect(modalOpen) { onModalVisibilityChange(modalOpen) }
    DisposableEffect(Unit) { onDispose { onModalVisibilityChange(false) } }

    Column(modifier = modifier.fillMaxSize().background(appearance.mobileBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSearchField(
                keyword = query,
                placeholder = "搜索工具",
                appearance = appearance,
                modifier = Modifier.weight(1f),
                height = 48.dp,
                clearContentDescription = "清除预设工具搜索",
                onKeywordChange = { query = it },
            )
            Button(
                onClick = { addOpen = true },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appearance.mobileBlue,
                    contentColor = appearance.mobileAccentFg,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isNotEmpty()) item(key = "heading") {
                Text(
                    text = "预设自带",
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                )
            }
            items(visible, key = AgentToolGroupSnapshot::id) { group ->
                PresetToolCard(
                    group = group,
                    enabled = group.id in configuration.enabledGroupIds,
                    appearance = appearance,
                    onOpen = { detailGroupId = group.id },
                    onEnabledChange = { enabled -> onUpdate(preset.withToolEnabled(group.id, enabled)) },
                )
            }
            if (visible.isEmpty()) item(key = "empty") {
                ToolEmptyState(
                    text = if (query.isBlank()) "还没有添加工具" else "没有匹配的工具",
                    appearance = appearance,
                )
            }
        }
    }

    if (addOpen) {
        AddPresetToolSheet(
            groups = availableGroups.filter { it.id !in configuration.includedGroupIds },
            appearance = appearance,
            onDismiss = { addOpen = false },
            onAdd = { groupId ->
                onUpdate(preset.withToolIncluded(groupId))
                addOpen = false
            },
        )
    }
    availableGroups.firstOrNull { it.id == detailGroupId }?.let { group ->
        PresetToolDetailSheet(
            group = group,
            enabled = group.id in configuration.enabledGroupIds,
            configuration = configuration,
            roleplayPlan = preset.roleplayPlan,
            modelConfigs = modelConfigs,
            allowRemove = true,
            appearance = appearance,
            onDismiss = { detailGroupId = "" },
            onEnabledChange = { enabled -> onUpdate(preset.withToolEnabled(group.id, enabled)) },
            onOpenWebSearchSettings = {
                detailGroupId = ""
                onOpenWebSearchSettings()
            },
            onRoleplayPlanChange = { roleplayPlan -> onUpdate(preset.copy(roleplayPlan = roleplayPlan)) },
            onSubagentModelChange = { configId, model ->
                onUpdate(preset.withSubagentModel(configId, model))
            },
            onToolModelConfigChange = { configId ->
                onUpdate(preset.withToolModelConfig(group.id, configId))
            },
            onSaveModelConfig = onSaveModelConfig,
            onRemove = {
                onUpdate(preset.withToolRemoved(group.id))
                detailGroupId = ""
            },
        )
    }
}

@Composable
fun AgentPresetQuickToolsDialog(
    preset: AgentPreset,
    availableGroups: List<AgentToolGroupSnapshot>,
    modelConfigs: List<ModelConfig>,
    appearance: AppearanceTheme,
    onUpdate: (AgentPreset) -> Unit,
    onManage: () -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = preset.toolConfiguration.normalized()
    val groupsById = remember(availableGroups) { availableGroups.associateBy(AgentToolGroupSnapshot::id) }
    val included = configuration.includedGroupIds.mapNotNull(groupsById::get)
    AgentToolQuickDialog(
        stateKey = preset.id,
        title = "预设工具",
        groups = included,
        configuration = configuration,
        roleplayPlan = preset.roleplayPlan,
        modelConfigs = modelConfigs,
        appearance = appearance,
        onEnabledChange = { groupId, enabled -> onUpdate(preset.withToolEnabled(groupId, enabled)) },
        onOpenWebSearchSettings = onOpenWebSearchSettings,
        onRoleplayPlanChange = { roleplayPlan -> onUpdate(preset.copy(roleplayPlan = roleplayPlan)) },
        onSubagentModelChange = { configId, model -> onUpdate(preset.withSubagentModel(configId, model)) },
        onToolModelConfigChange = { groupId, configId ->
            onUpdate(preset.withToolModelConfig(groupId, configId))
        },
        onSaveModelConfig = onSaveModelConfig,
        onManage = onManage,
        onDismiss = onDismiss,
    )
}

@Composable
fun AgentToolQuickDialog(
    stateKey: String,
    title: String,
    groups: List<AgentToolGroupSnapshot>,
    configuration: AgentPresetToolConfiguration,
    roleplayPlan: AgentPresetRoleplayPlan,
    modelConfigs: List<ModelConfig>,
    appearance: AppearanceTheme,
    interactionEnabled: Boolean = true,
    showPresetSpecificConfiguration: Boolean = true,
    onEnabledChange: (String, Boolean) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onRoleplayPlanChange: (AgentPresetRoleplayPlan) -> Unit,
    onSubagentModelChange: (String, String) -> Unit,
    onToolModelConfigChange: (String, String) -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onManage: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var detailGroupId by rememberSaveable(stateKey) { mutableStateOf("") }
    val detailGroup = groups.firstOrNull { it.id == detailGroupId }

    PresetBottomSheetDialog(
        appearance = appearance,
        onDismiss = if (detailGroup == null) onDismiss else ({ detailGroupId = "" }),
    ) {
        if (detailGroup != null) {
            ToolDetailSheetContent(
                group = detailGroup,
                enabled = detailGroup.id in configuration.enabledGroupIds,
                configuration = configuration,
                roleplayPlan = roleplayPlan,
                modelConfigs = modelConfigs,
                allowRemove = false,
                showBack = true,
                interactionEnabled = interactionEnabled,
                showPresetSpecificConfiguration = showPresetSpecificConfiguration,
                appearance = appearance,
                onBack = { detailGroupId = "" },
                onDismiss = onDismiss,
                onEnabledChange = { enabled -> onEnabledChange(detailGroup.id, enabled) },
                onOpenWebSearchSettings = {
                    onDismiss()
                    onOpenWebSearchSettings()
                },
                onRoleplayPlanChange = onRoleplayPlanChange,
                onSubagentModelChange = onSubagentModelChange,
                onToolModelConfigChange = { configId ->
                    onToolModelConfigChange(detailGroup.id, configId)
                },
                onSaveModelConfig = onSaveModelConfig,
                onRemove = {},
            )
        } else {
            QuickToolListContent(
                title = title,
                groups = groups,
                enabledGroupIds = configuration.enabledGroupIds,
                interactionEnabled = interactionEnabled,
                appearance = appearance,
                onOpen = { detailGroupId = it },
                onEnabledChange = onEnabledChange,
                onManage = onManage,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun QuickToolListContent(
    title: String,
    groups: List<AgentToolGroupSnapshot>,
    enabledGroupIds: Set<String>,
    interactionEnabled: Boolean,
    appearance: AppearanceTheme,
    onOpen: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onManage: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    SheetHeader(title = title, appearance = appearance, onDismiss = onDismiss)
    if (groups.isEmpty()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ToolEmptyState("当前没有可用工具", appearance)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 520.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups, key = AgentToolGroupSnapshot::id) { group ->
                PresetToolCard(
                    group = group,
                    enabled = group.id in enabledGroupIds,
                    interactionEnabled = interactionEnabled,
                    appearance = appearance,
                    compact = true,
                    onOpen = { onOpen(group.id) },
                    onEnabledChange = { onEnabledChange(group.id, it) },
                )
            }
        }
    }
    if (onManage != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onManage,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = appearance.mobileBlue),
            ) {
                Text("管理预设工具")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
internal fun PresetToolCard(
    group: AgentToolGroupSnapshot,
    enabled: Boolean,
    interactionEnabled: Boolean = true,
    appearance: AppearanceTheme,
    compact: Boolean = false,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        color = appearance.mobileSurface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (enabled) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 68.dp else 78.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolGroupIcon(group.id, enabled, appearance, compact = false)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
                    Text(
                        group.name,
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                        fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!compact && group.description.isNotBlank()) {
                        Text(
                            group.description,
                            color = appearance.mobileMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "配置${group.name}",
                    tint = appearance.mobileMuted.copy(alpha = 0.62f),
                    modifier = Modifier.size(22.dp),
                )
            }
            AppSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                appearance = appearance,
                enabled = interactionEnabled,
                modifier = Modifier.padding(start = 6.dp, end = 12.dp).semantics {
                    contentDescription = "${group.name}工具开关"
                },
            )
        }
    }
}

@Composable
private fun ToolGroupIcon(
    groupId: String,
    enabled: Boolean,
    appearance: AppearanceTheme,
    compact: Boolean,
) {
    Box(
        modifier = Modifier.size(if (compact) 30.dp else 44.dp).background(
            color = if (enabled) {
                appearance.mobileBlue.copy(alpha = 0.10f)
            } else {
                appearance.mobileText.copy(alpha = 0.055f)
            },
            shape = RoundedCornerShape(if (compact) 9.dp else 14.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            toolGroupIcon(groupId),
            contentDescription = null,
            tint = if (enabled) appearance.mobileBlue else appearance.mobileMuted,
            modifier = Modifier.size(if (compact) 17.dp else 22.dp),
        )
    }
}

private fun toolGroupIcon(groupId: String): ImageVector = when (groupId) {
    AgentToolRequestPolicy.BuiltInVariables -> Icons.Rounded.Schema
    AgentToolRequestPolicy.BuiltInSettingLibrary -> Icons.AutoMirrored.Rounded.MenuBook
    AgentToolRequestPolicy.BuiltInWeb -> Icons.Rounded.Public
    AgentToolRequestPolicy.BuiltInAutoIllustration -> Icons.Rounded.Image
    AgentToolRequestPolicy.BuiltInCollaboration -> Icons.Rounded.Groups
    AgentToolRequestPolicy.BuiltInRemoteDsh -> Icons.Rounded.Computer
    AgentToolRequestPolicy.BuiltInWorkflow,
    AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
    -> Icons.Rounded.Checklist
    AgentToolRequestPolicy.BuiltInCreator -> Icons.Rounded.AutoAwesome
    AgentToolRequestPolicy.BuiltInWorkspace -> Icons.Rounded.Folder
    else -> Icons.Rounded.Extension
}

@Composable
internal fun SheetHeader(
    title: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = appearance.mobileText)
            }
        } else Spacer(Modifier.size(48.dp))
        Text(
            text = title,
            color = appearance.mobileText,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = appearance.mobileText)
        }
    }
}

@Composable
private fun ToolEmptyState(text: String, appearance: AppearanceTheme) {
    Surface(color = appearance.mobileSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = appearance.mobileMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
        )
    }
}

private fun AgentToolGroupSnapshot.searchableText(): String = buildString {
    append(name)
    append(' ')
    append(description)
    members.forEach { append(' ').append(it.displayName).append(' ').append(it.name) }
}

private fun AgentPreset.withToolEnabled(groupId: String, enabled: Boolean): AgentPreset {
    val current = toolConfiguration.normalized()
    val included = if (groupId in current.includedGroupIds) current.includedGroupIds else current.includedGroupIds + groupId
    val enabledIds = if (enabled) current.enabledGroupIds + groupId else current.enabledGroupIds - groupId
    return copy(toolConfiguration = current.copy(includedGroupIds = included, enabledGroupIds = enabledIds))
}

private fun AgentPreset.withToolIncluded(groupId: String): AgentPreset {
    val current = toolConfiguration.normalized()
    return copy(toolConfiguration = current.copy(
        includedGroupIds = (current.includedGroupIds + groupId).distinct(),
        enabledGroupIds = current.enabledGroupIds + groupId,
    ))
}

private fun AgentPreset.withToolRemoved(groupId: String): AgentPreset {
    val current: AgentPresetToolConfiguration = toolConfiguration.normalized()
    return copy(toolConfiguration = current.copy(
        includedGroupIds = current.includedGroupIds - groupId,
        enabledGroupIds = current.enabledGroupIds - groupId,
        toolModelConfigIds = current.toolModelConfigIds - groupId,
    ))
}

private fun AgentPreset.withSubagentModel(configId: String, model: String): AgentPreset {
    val current = toolConfiguration.normalized()
    return copy(
        toolConfiguration = current.copy(
            subagentModelConfigId = configId,
            subagentModel = model,
        ).normalized(),
    )
}

private fun AgentPreset.withToolModelConfig(groupId: String, configId: String): AgentPreset {
    val current = toolConfiguration.normalized()
    val next = if (configId.isBlank()) {
        current.toolModelConfigIds - groupId
    } else {
        current.toolModelConfigIds + (groupId to configId)
    }
    return copy(toolConfiguration = current.copy(toolModelConfigIds = next).normalized())
}
