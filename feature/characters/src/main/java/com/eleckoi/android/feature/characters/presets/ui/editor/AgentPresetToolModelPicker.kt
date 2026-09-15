package com.eleckoi.android.feature.characters.presets.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.isChatModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ModelIdentityIcon
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ImageModelParamsMode
import com.eleckoi.android.feature.modelconfig.ui.modelpicker.ImageModelParamsPage

internal enum class ToolModelPickerKind { Subagent, Image }

private data class ToolModelChoice(
    val key: String,
    val configId: String,
    val model: String,
    val providerId: String,
    val title: String,
    val subtitle: String,
)

@Composable
internal fun ToolModelPickerContent(
    kind: ToolModelPickerKind,
    configs: List<ModelConfig>,
    selectedConfigId: String,
    selectedModel: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
    onSaveConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
) {
    val choices = remember(kind, configs) { toolModelChoices(kind, configs) }
    var localSelectedConfigId by rememberSaveable(kind, selectedConfigId) { mutableStateOf(selectedConfigId) }
    var imageParamsConfigId by rememberSaveable(kind) { mutableStateOf("") }
    val imageParamsConfig = configs.firstOrNull { it.id == imageParamsConfigId && it.isImageGenerationConfig() }
    if (kind == ToolModelPickerKind.Image && imageParamsConfig != null) {
        SheetHeader(
            title = "绘图模型与参数",
            appearance = appearance,
            onDismiss = onDismiss,
            onBack = { imageParamsConfigId = "" },
        )
        ImageModelParamsPage(
            selectedConfig = imageParamsConfig,
            characterImagePrompt = "",
            appearance = appearance,
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 610.dp),
            onSaveConfig = onSaveConfig,
            onCharacterImagePromptChange = { _, callback -> callback(Result.success("")) },
            mode = ImageModelParamsMode.AutomaticIllustration,
            showCharacterImagePrompt = false,
        )
        return
    }
    val resolvedSelectedModel = if (kind == ToolModelPickerKind.Subagent) {
        selectedModel.ifBlank { configs.firstOrNull { it.id == selectedConfigId }?.model.orEmpty() }
    } else {
        ""
    }
    val leadingTitle = if (kind == ToolModelPickerKind.Subagent) "跟随主模型" else "不指定图片模型"
    val leadingSubtitle = if (kind == ToolModelPickerKind.Subagent) {
        "使用当前对话选择的模型与参数"
    } else {
        "工具启用时不会生成图片"
    }

    SheetHeader(
        title = if (kind == ToolModelPickerKind.Subagent) "选择子 Agent 模型" else "选择图片生成模型",
        appearance = appearance,
        onDismiss = onDismiss,
        onBack = onBack,
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 610.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("leading") {
            ToolModelChoiceCard(
                title = leadingTitle,
                subtitle = leadingSubtitle,
                selected = localSelectedConfigId.isBlank(),
                kind = kind,
                modelName = "",
                providerId = "",
                appearance = appearance,
                onClick = {
                    localSelectedConfigId = ""
                    onSelect("", "")
                },
            )
        }
        if (choices.isNotEmpty()) {
            item("configured-heading") {
                Text(
                    text = if (kind == ToolModelPickerKind.Subagent) "已配置的聊天模型" else "已配置的图片模型",
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                )
            }
        }
        items(choices, key = ToolModelChoice::key) { choice ->
            ToolModelChoiceCard(
                title = choice.title,
                subtitle = choice.subtitle,
                selected = choice.configId == localSelectedConfigId &&
                    (kind == ToolModelPickerKind.Image || choice.model == resolvedSelectedModel),
                kind = kind,
                modelName = choice.model,
                providerId = choice.providerId,
                appearance = appearance,
                onClick = {
                    localSelectedConfigId = choice.configId
                    onSelect(choice.configId, choice.model)
                    if (kind == ToolModelPickerKind.Image) imageParamsConfigId = choice.configId
                },
            )
        }
        if (choices.isEmpty()) {
            item("empty") {
                Surface(
                    color = appearance.mobileBg,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (kind == ToolModelPickerKind.Subagent) {
                            "还没有可用的聊天模型，请先到模型页面完成配置。"
                        } else {
                            "还没有图片生成模型，请先到模型页面完成配置。"
                        },
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolModelChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    kind: ToolModelPickerKind,
    modelName: String,
    providerId: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) appearance.mobileBlue.copy(alpha = 0.1f) else appearance.mobileBg,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (modelName.isBlank()) {
                Icon(
                    imageVector = if (kind == ToolModelPickerKind.Subagent) Icons.Rounded.SmartToy else Icons.Rounded.Image,
                    contentDescription = null,
                    tint = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                    modifier = Modifier.size(23.dp),
                )
            } else {
                ModelIdentityIcon(
                    modelName = modelName,
                    providerId = providerId,
                    appearance = appearance,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    title,
                    color = appearance.mobileText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = appearance.mobileBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun toolModelChoices(kind: ToolModelPickerKind, configs: List<ModelConfig>): List<ToolModelChoice> {
    return when (kind) {
        ToolModelPickerKind.Image -> configs
            .filter(ModelConfig::isImageGenerationConfig)
            .map { config ->
                ToolModelChoice(
                    key = config.id,
                    configId = config.id,
                    model = config.model.trim(),
                    providerId = config.provider.trim(),
                    title = config.name.trim().ifBlank { "未命名配置" },
                    subtitle = listOf(config.provider.trim(), config.model.trim())
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                        .ifBlank { "未填写模型" },
                )
            }

        ToolModelPickerKind.Subagent -> configs
            .filter(ModelConfig::isChatModelConfig)
            .flatMap { config ->
                val modelNames = buildList {
                    config.model.trim().takeIf(String::isNotBlank)?.let(::add)
                    config.modelOptions.mapTo(this) { it.id.trim() }
                }.filter(String::isNotBlank).distinct()
                modelNames.map { model ->
                    val displayName = config.modelOptions.firstOrNull { it.id.trim() == model }
                        ?.name
                        ?.trim()
                        .orEmpty()
                        .ifBlank { model }
                    ToolModelChoice(
                        key = "${config.id}:$model",
                        configId = config.id,
                        model = model,
                        providerId = config.provider.trim(),
                        title = displayName,
                        subtitle = listOf(config.name.trim().ifBlank { "未命名配置" }, config.provider.trim())
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                    )
                }
            }
    }
}
