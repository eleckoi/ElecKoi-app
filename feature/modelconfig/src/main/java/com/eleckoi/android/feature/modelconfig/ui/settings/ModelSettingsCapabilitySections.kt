package com.eleckoi.android.feature.modelconfig.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.configuredContextWindowTokens
import com.eleckoi.android.engine.generation.model.isOfficialDeepSeekVisionModel
import com.eleckoi.android.engine.generation.reasoning.DshReasoningEfforts
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldDivider
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldGroup
import com.eleckoi.android.feature.modelconfig.ui.components.ModelInlineField
import com.eleckoi.android.feature.modelconfig.ui.components.ModelNavigationRow
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSectionHeader
import com.eleckoi.android.feature.modelconfig.ui.reasoning.ModelReasoningSelector
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.AppSwitch

@Composable
internal fun ModelCapabilitySections(
    form: ModelConfig,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onUpdate: (ModelConfig) -> Unit,
) {
    val activeModelOption = form.modelOptions.firstOrNull { it.id == form.model.trim() }
    val reasoningVariants = activeModelOption
        ?.let { DshReasoningEfforts.forModel(form, it) }
        .orEmpty()

    ModelInputCapabilitySection(form, activeModelOption, appearance, onUpdate)
    if (form.model.isNotBlank()) {
        ModelSectionHeader("推理", appearance, actions = {})
        ModelFieldGroup(appearance) {
            ModelReasoningSelector(
                variants = reasoningVariants,
                selectedVariant = activeModelOption?.reasoningEffort,
                appearance = appearance,
            ) { selected ->
                onUpdate(
                    form.updateActiveModelOption { option ->
                        option.copy(reasoningEffort = selected)
                    },
                )
            }
        }
    }
    ModelLimitSection(
        form = form,
        activeModelOption = activeModelOption,
        appearance = appearance,
        scrollState = scrollState,
        imeBottomPx = imeBottomPx,
        onUpdate = onUpdate,
    )
}

@Composable
private fun ModelInputCapabilitySection(
    form: ModelConfig,
    activeModelOption: ModelOption?,
    appearance: AppearanceTheme,
    onUpdate: (ModelConfig) -> Unit,
) {
    val officialDeepSeekVision = form.isOfficialDeepSeekVisionModel()
    ModelSectionHeader("输入能力", appearance, actions = {})
    ModelFieldGroup(appearance) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("支持图片输入", color = appearance.mobileText, fontSize = 14.sp)
            }
            AppSwitch(
                checked = officialDeepSeekVision || activeModelOption?.supportsImageInput == true,
                enabled = form.model.isNotBlank() && !officialDeepSeekVision,
                appearance = appearance,
                onCheckedChange = { enabled ->
                    onUpdate(
                        form.updateActiveModelOption { option ->
                            option.copy(supportsImageInput = enabled)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ModelLimitSection(
    form: ModelConfig,
    activeModelOption: ModelOption?,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onUpdate: (ModelConfig) -> Unit,
) {
    val automaticContextWindow = form.configuredContextWindowTokens()
    var temperatureText by remember(form.id, form.model) {
        mutableStateOf(activeModelOption?.temperature?.parameterText().orEmpty())
    }
    var topPText by remember(form.id, form.model) {
        mutableStateOf(activeModelOption?.topP?.parameterText().orEmpty())
    }

    ModelSectionHeader("参数", appearance, actions = {})
    ModelFieldGroup(appearance) {
        ModelParameterField(
            label = "上下文窗口",
            detail = "= ${automaticContextWindow.compactParameterNumber()}",
            value = activeModelOption?.contextWindowTokens?.toString().orEmpty(),
            placeholder = if (form.model.isBlank()) {
                "先选模型"
            } else {
                "自动 $automaticContextWindow"
            },
            appearance = appearance,
            keyboardType = KeyboardType.Number,
        ) { value ->
            onUpdate(
                form.updateActiveModelOption { option ->
                    option.copy(contextWindowTokens = value.optionalTokenCount())
                },
            )
        }
        ModelFieldDivider(appearance)
        ModelParameterField(
            label = "自动压缩阈值",
            detail = "= ${(
                activeModelOption?.autoCompactTokenLimit
                    ?: automaticContextWindow.toLong()
                        .times(ModelOption.AgentDefaultAutoCompactPercent)
                        .div(100L)
                        .toInt()
                ).compactParameterNumber()} · 占上下文 80%",
            value = activeModelOption?.autoCompactTokenLimit?.toString().orEmpty(),
            placeholder = if (form.model.isBlank()) {
                "先选模型"
            } else {
                val contextWindow = automaticContextWindow
                val automaticLimit = contextWindow.toLong() *
                    ModelOption.AgentDefaultAutoCompactPercent / 100L
                "自动 $automaticLimit"
            },
            appearance = appearance,
            keyboardType = KeyboardType.Number,
        ) { value ->
            onUpdate(
                form.updateActiveModelOption { option ->
                    option.copy(autoCompactTokenLimit = value.optionalTokenCount())
                },
            )
        }
        ModelFieldDivider(appearance)
        ModelParameterField(
            label = "单次最大输出",
            detail = activeModelOption?.maxOutputTokens
                ?.let { "= ${it.compactParameterNumber()}" }
                ?: "由上游决定",
            value = activeModelOption?.maxOutputTokens?.toString().orEmpty(),
            placeholder = if (form.model.isBlank()) "先选模型" else "自动",
            appearance = appearance,
            keyboardType = KeyboardType.Number,
        ) { value ->
            onUpdate(
                form.updateActiveModelOption { option ->
                    option.copy(maxOutputTokens = value.optionalTokenCount())
                },
            )
        }
        ModelFieldDivider(appearance)
        ModelParameterField(
            label = "温度",
            detail = temperatureText.takeIf(String::isNotBlank)?.let { "= $it" } ?: "留空不发送",
            value = temperatureText,
            placeholder = "留空",
            appearance = appearance,
            keyboardType = KeyboardType.Decimal,
            fieldEnabled = form.model.isNotBlank(),
        ) { value ->
            temperatureText = value.decimalInput()
            val parsed = temperatureText.toDoubleOrNull()
            if (temperatureText.isBlank() || parsed != null) {
                onUpdate(
                    form.updateActiveModelOption { option ->
                        option.copy(temperature = parsed)
                    },
                )
            }
        }
        ModelFieldDivider(appearance)
        ModelParameterField(
            label = "Top P",
            detail = topPText.takeIf(String::isNotBlank)?.let { "= $it" } ?: "留空不发送",
            value = topPText,
            placeholder = "留空",
            appearance = appearance,
            keyboardType = KeyboardType.Decimal,
            fieldEnabled = form.model.isNotBlank(),
        ) { value ->
            topPText = value.decimalInput()
            val parsed = topPText.toDoubleOrNull()
            if (topPText.isBlank() || parsed != null) {
                onUpdate(
                    form.updateActiveModelOption { option ->
                        option.copy(topP = parsed)
                    },
                )
            }
        }
    }
    val limitHint = modelLimitHint(activeModelOption)
    if (limitHint.isNotBlank()) {
        Text(
            text = limitHint,
            color = ElecKoiDanger,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
        )
    }
}

@Composable
private fun ModelParameterField(
    label: String,
    detail: String,
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    keyboardType: KeyboardType,
    fieldEnabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, color = appearance.mobileText, fontSize = 14.sp)
            Text(detail, color = appearance.mobileMuted, fontSize = 11.sp)
        }
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            placeholder = placeholder,
            modifier = Modifier.width(132.dp).padding(start = 8.dp),
            enabled = fieldEnabled,
            textStyle = TextStyle(
                color = if (fieldEnabled) appearance.mobileText else appearance.mobileSoft,
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

private fun Number.compactParameterNumber(): String {
    val value = toLong()
    return when {
        value >= 1_000_000 && value % 1_000_000 == 0L -> "${value / 1_000_000}M"
        value >= 1_000 && value % 1_000 == 0L -> "${value / 1_000}K"
        value >= 1_000 -> "${value / 1_000.0}K"
        else -> value.toString()
    }
}

private fun Double.parameterText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun String.decimalInput(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = normalized.indexOf('.')
    return if (dot < 0) normalized.take(4) else {
        normalized.take(dot + 1) + normalized.drop(dot + 1).replace(".", "").take(3)
    }
}

@Composable
internal fun ModelNetworkSection(
    form: ModelConfig,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onOpenHeaders: () -> Unit,
    onUpdate: (ModelConfig) -> Unit,
) {
    ModelSectionHeader("网络", appearance, actions = {})
    ModelFieldGroup(appearance) {
        ModelInlineField(
            label = "代理",
            value = form.proxyUrl,
            placeholder = "可选",
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            keyboardType = KeyboardType.Uri,
        ) { value ->
            onUpdate(form.copy(proxyUrl = value))
        }
        ModelFieldDivider(appearance)
        ModelNavigationRow(
            label = "自定义请求头",
            value = if (form.customHeaders.isEmpty()) "可选" else "${form.customHeaders.size} 条",
            valueMuted = form.customHeaders.isEmpty(),
            appearance = appearance,
            onClick = onOpenHeaders,
        )
    }
}
