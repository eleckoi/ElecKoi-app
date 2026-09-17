package com.eleckoi.android.feature.characters.presets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSource
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ImageModelParamsMode
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ModelPickerConfigKind
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ModelPickerContent
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ModelPickerLeadingChoice
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSwitch
import com.eleckoi.android.foundation.design.components.MobileBottomSheetDialog
import com.eleckoi.android.foundation.design.components.MobileBottomSheetHeader
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon

internal enum class ToolModelPickerKind { Subagent, Image }

@Composable
internal fun AddPresetToolSheet(
    groups: List<AgentToolGroupSnapshot>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    MobileBottomSheetDialog(
        appearance = appearance,
        onDismiss = onDismiss,
        sheetModifier = Modifier.fillMaxHeight(0.88f).imePadding(),
        showHandle = true,
    ) {
        MobileBottomSheetHeader(title = "添加工具", appearance = appearance, onDismiss = onDismiss)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups, key = AgentToolGroupSnapshot::id) { group ->
                Surface(
                    color = appearance.mobileBg,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onAdd(group.id) },
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, color = appearance.mobileText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${group.members.size} 个工具",
                                color = appearance.mobileMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Icon(Icons.Rounded.Add, contentDescription = "添加${group.name}", tint = appearance.mobileBlue)
                    }
                }
            }
            if (groups.isEmpty()) item {
                Text(
                    "没有可添加的工具组",
                    color = appearance.mobileMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
internal fun PresetToolDetailSheet(
    group: AgentToolGroupSnapshot,
    enabled: Boolean,
    configuration: AgentPresetToolConfiguration,
    roleplayPlan: AgentPresetRoleplayPlan,
    modelConfigs: List<ModelConfig>,
    allowRemove: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onRoleplayPlanChange: (AgentPresetRoleplayPlan) -> Unit,
    onSubagentModelChange: (String, String) -> Unit,
    onToolModelConfigChange: (String) -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    var modelPickerKind by rememberSaveable(group.id) { mutableStateOf<ToolModelPickerKind?>(null) }
    MobileBottomSheetDialog(
        appearance = appearance,
        onDismiss = onDismiss,
        sheetModifier = Modifier.fillMaxHeight(0.88f).imePadding(),
        showHandle = true,
    ) {
        val kind = modelPickerKind
        if (kind != null) {
            PresetToolModelPickerContent(
                kind = kind,
                groupId = group.id,
                configuration = configuration,
                modelConfigs = modelConfigs,
                appearance = appearance,
                onBack = { modelPickerKind = null },
                onDismiss = onDismiss,
                onSelect = { configId, model ->
                    when (kind) {
                        ToolModelPickerKind.Subagent -> onSubagentModelChange(configId, model)
                        ToolModelPickerKind.Image -> onToolModelConfigChange(configId)
                    }
                },
                onSaveModelConfig = onSaveModelConfig,
                onRefreshModels = onRefreshModels,
            )
        } else {
            ToolDetailSheetContent(
                group = group,
                enabled = enabled,
                configuration = configuration,
                roleplayPlan = roleplayPlan,
                modelConfigs = modelConfigs,
                allowRemove = allowRemove,
                showBack = false,
                appearance = appearance,
                onBack = onDismiss,
                onDismiss = onDismiss,
                onEnabledChange = onEnabledChange,
                onOpenWebSearchSettings = onOpenWebSearchSettings,
                onRoleplayPlanChange = onRoleplayPlanChange,
                onOpenModelPicker = { modelPickerKind = it },
                onRemove = onRemove,
            )
        }
    }
}

@Composable
internal fun ColumnScope.ToolDetailSheetContent(
    group: AgentToolGroupSnapshot,
    enabled: Boolean,
    configuration: AgentPresetToolConfiguration,
    roleplayPlan: AgentPresetRoleplayPlan,
    modelConfigs: List<ModelConfig>,
    allowRemove: Boolean,
    showBack: Boolean,
    interactionEnabled: Boolean = true,
    showPresetSpecificConfiguration: Boolean = true,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onRoleplayPlanChange: (AgentPresetRoleplayPlan) -> Unit,
    onOpenModelPicker: (ToolModelPickerKind) -> Unit,
    onRemove: () -> Unit,
) {
    val selectedSubagentConfig = modelConfigs.firstOrNull { it.id == configuration.subagentModelConfigId }
    val selectedImageConfig = modelConfigs.firstOrNull {
        it.id == configuration.toolModelConfigIds[group.id]
    }
    val isCreatorGroup = group.id == AgentToolRequestPolicy.BuiltInCreator
    val isImageConfigurationGroup = isCreatorGroup ||
        group.id == AgentToolRequestPolicy.BuiltInAutoIllustration
    MobileBottomSheetHeader(
        title = group.name,
        appearance = appearance,
        onDismiss = onDismiss,
        onBack = onBack.takeIf { showBack },
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isCreatorGroup) item("enable") {
            ToolDetailCard(appearance) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isImageConfigurationGroup) "启用自动配图" else "启用此工具组",
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    AppSwitch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        appearance = appearance,
                        enabled = interactionEnabled,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        if (group.description.isNotBlank()) item("description") {
            Text(
                group.description,
                color = appearance.mobileMuted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (group.id == AgentToolRequestPolicy.BuiltInWeb) {
            item("web-config-title") { SectionLabel("搜索配置", appearance) }
            item("web-config") {
                ToolDetailCard(appearance, onClick = onOpenWebSearchSettings) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(
                                appearance.mobileBlue.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = appearance.mobileBlue)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("联网搜索设置", color = appearance.mobileText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "选择模型原生或 Tavily，并配置 API Key 与结果数量",
                                color = appearance.mobileMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "打开联网搜索设置",
                            tint = appearance.mobileMuted,
                        )
                    }
                }
            }
        }
        if (
            showPresetSpecificConfiguration &&
            group.id == AgentToolRequestPolicy.BuiltInCollaboration
        ) {
            item("subagent-model-title") { SectionLabel("子 Agent 模型", appearance) }
            item("subagent-model") {
                ToolConfigurationSelector(
                    title = selectedSubagentConfig?.name?.ifBlank { "未命名配置" } ?: "跟随主模型",
                    subtitle = selectedSubagentConfig?.let {
                        configuration.subagentModel.ifBlank { it.model }.ifBlank { "未选择模型" }
                    } ?: "使用当前对话选择的模型与参数",
                    actionDescription = "选择子 Agent 模型",
                    appearance = appearance,
                    onClick = { onOpenModelPicker(ToolModelPickerKind.Subagent) },
                )
            }
        }
        if (isImageConfigurationGroup) {
            item("image-model-title") {
                SectionLabel(if (isCreatorGroup) "图片生成" else "绘图模型与参数", appearance)
            }
            item("image-model") {
                ToolConfigurationSelector(
                    title = selectedImageConfig?.name?.ifBlank { "未命名配置" } ?: "未配置绘图模型",
                    subtitle = selectedImageConfig?.model?.ifBlank { selectedImageConfig.provider }
                        ?: if (isCreatorGroup) {
                            "选择模型并设置输出尺寸与生成参数"
                        } else {
                            "选择模型并设置分镜、画幅与提示词"
                        },
                    actionDescription = "选择图片生成模型",
                    appearance = appearance,
                    onClick = { onOpenModelPicker(ToolModelPickerKind.Image) },
                )
            }
        }
        if (
            showPresetSpecificConfiguration &&
            group.id == AgentToolRequestPolicy.BuiltInRoleplayWorkflow
        ) {
            item("roleplay-plan-title") { SectionLabel("固定任务计划", appearance) }
            item("roleplay-plan") {
                AgentPresetRoleplayPlanEditor(
                    plan = roleplayPlan,
                    appearance = appearance,
                    onChange = onRoleplayPlanChange,
                )
            }
        }
        if (!isImageConfigurationGroup || isCreatorGroup) item("members-title") {
            SectionLabel(if (group.members.isEmpty()) "工具" else "包含 ${group.members.size} 个工具", appearance)
        }
        if (!isImageConfigurationGroup || isCreatorGroup) item("members") {
            ToolDetailCard(appearance) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (group.members.isEmpty()) {
                        Text("此工具组没有独立调用项", color = appearance.mobileMuted, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        group.members.take(12).forEach { member ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                                Text(
                                    member.name,
                                    color = appearance.mobileText,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (member.displayName.isNotBlank() && member.displayName != member.name) {
                                    Text(
                                        member.displayName,
                                        color = appearance.mobileText.copy(alpha = 0.75f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                                if (member.description.isNotBlank()) {
                                    Text(
                                        member.description,
                                        color = appearance.mobileMuted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item("source-title") { SectionLabel("来源", appearance) }
        item("source") {
            ToolDetailCard(appearance) {
                Text(
                    text = when (group.source) {
                        AgentToolGroupSource.BuiltIn -> "ElecKoi 内置"
                        AgentToolGroupSource.Mcp -> "MCP 服务器"
                        AgentToolGroupSource.Extension -> "扩展"
                    },
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (allowRemove) {
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = ElecKoiDanger),
            ) {
                StrokeSvgIcon(
                    paths = AppIconPaths.Trash,
                    color = ElecKoiDanger,
                    iconSize = 18.dp,
                    strokeWidth = 1.7f,
                )
                Spacer(Modifier.width(5.dp))
                Text("从预设移除")
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = appearance.mobileBlue),
        ) { Text("完成") }
    }
}

@Composable
internal fun ColumnScope.PresetToolModelPickerContent(
    kind: ToolModelPickerKind,
    groupId: String,
    configuration: AgentPresetToolConfiguration,
    modelConfigs: List<ModelConfig>,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
) {
    val isSubagent = kind == ToolModelPickerKind.Subagent
    val selectedConfigId = if (isSubagent) {
        configuration.subagentModelConfigId
    } else {
        configuration.toolModelConfigIds[groupId].orEmpty()
    }
    ModelPickerContent(
        configs = modelConfigs,
        selectedConfigId = selectedConfigId,
        selectedModel = configuration.subagentModel.takeIf { isSubagent }.orEmpty(),
        appearance = appearance,
        onBack = onBack,
        onDismiss = onDismiss,
        onSelect = onSelect,
        onSaveConfig = onSaveModelConfig,
        onRefreshModels = onRefreshModels,
        leadingChoice = ModelPickerLeadingChoice(
            title = if (isSubagent) "跟随主模型" else "不指定图片模型",
            subtitle = if (isSubagent) {
                "使用当前对话选择的模型与参数"
            } else {
                "工具启用时不会生成图片"
            },
            selected = selectedConfigId.isBlank(),
            onSelect = { onSelect("", "") },
        ),
        configKind = if (isSubagent) ModelPickerConfigKind.Chat else ModelPickerConfigKind.Image,
        imageParamsMode = if (groupId == AgentToolRequestPolicy.BuiltInCreator) {
            ImageModelParamsMode.OnDemand
        } else {
            ImageModelParamsMode.AutomaticIllustration
        },
        showCharacterImagePrompt = false,
    )
}

@Composable
private fun ToolConfigurationSelector(
    title: String,
    subtitle: String,
    actionDescription: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    ToolDetailCard(appearance, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = appearance.mobileText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = actionDescription,
                tint = appearance.mobileMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, appearance: AppearanceTheme) {
    Text(
        text,
        color = appearance.mobileMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
private fun ToolDetailCard(
    appearance: AppearanceTheme,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Surface(color = appearance.mobileBg, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(content = content)
    }
}
