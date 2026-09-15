package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.configuredContextWindowTokens
import com.eleckoi.android.engine.generation.model.isOfficialDeepSeekVisionModel
import com.eleckoi.android.engine.generation.reasoning.DshReasoningEfforts
import com.eleckoi.android.feature.modelconfig.ui.components.ModelInlineField
import com.eleckoi.android.feature.modelconfig.ui.displayName
import com.eleckoi.android.feature.modelconfig.ui.reasoning.ModelReasoningSelector
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.AppSwitch

@Composable
internal fun ModelParamsPage(
    selectedConfig: ModelConfig?,
    selectedModel: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onOpenApiFormat: () -> Unit,
    onSaveConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
) {
    val option = remember(selectedConfig, selectedModel) {
        selectedConfig?.modelOptions?.firstOrNull { it.id == selectedModel }
            ?: selectedModel.takeIf { it.isNotBlank() }?.let(::ModelOption)
    }
    val editable = selectedConfig != null && option != null

    var contextWindow by rememberSaveable(selectedConfig?.id, selectedModel, option?.contextWindowTokens) {
        mutableStateOf(option?.contextWindowTokens?.toString().orEmpty())
    }
    var autoCompact by rememberSaveable(selectedConfig?.id, selectedModel, option?.autoCompactTokenLimit) {
        mutableStateOf(option?.autoCompactTokenLimit?.toString().orEmpty())
    }
    var maxOutput by rememberSaveable(selectedConfig?.id, selectedModel, option?.maxOutputTokens) {
        mutableStateOf(option?.maxOutputTokens?.toString().orEmpty())
    }
    var temperature by rememberSaveable(selectedConfig?.id, selectedModel, option?.temperature) {
        mutableStateOf(option?.temperature?.samplingParameterText().orEmpty())
    }
    var topP by rememberSaveable(selectedConfig?.id, selectedModel, option?.topP) {
        mutableStateOf(option?.topP?.samplingParameterText().orEmpty())
    }
    var reasoningEffort by rememberSaveable(selectedConfig?.id, selectedModel, option?.reasoningEffort) {
        mutableStateOf(option?.reasoningEffort)
    }
    var supportsImageInput by rememberSaveable(selectedConfig?.id, selectedModel, option?.supportsImageInput) {
        mutableStateOf(option?.supportsImageInput == true)
    }
    var saveState by remember { mutableStateOf(ParamsSaveState.Idle) }
    val officialDeepSeekVision = remember(selectedConfig, selectedModel) {
        selectedConfig?.copy(model = selectedModel)?.isOfficialDeepSeekVisionModel() == true
    }
    val reasoningVariants = remember(selectedConfig, option) {
        if (selectedConfig == null || option == null) {
            emptyList()
        } else {
            DshReasoningEfforts.forModel(selectedConfig, option)
        }
    }

    val contextValue = contextWindow.toOptionalInt()
    val compactValue = autoCompact.toOptionalInt()
    val outputValue = maxOutput.toOptionalInt()
    val automaticContextWindow = selectedConfig
        ?.copy(model = selectedModel)
        ?.configuredContextWindowTokens()
    val effectiveContext = contextValue ?: option?.contextWindowTokens ?: automaticContextWindow

    val contextError = contextWindow.isNotBlank() && contextValue?.let {
        it in ModelOption.MinContextWindowTokens..ModelOption.MaxContextWindowTokens
    } != true
    val compactError = autoCompact.isNotBlank() && compactValue?.let {
        it in ModelOption.MinAutoCompactTokenLimit..ModelOption.MaxContextWindowTokens &&
            (effectiveContext == null || it <= effectiveContext)
    } != true
    val outputError = maxOutput.isNotBlank() && outputValue?.let {
        it >= ModelOption.MinMaxOutputTokens && (effectiveContext == null || it <= effectiveContext)
    } != true
    val temperatureValue = temperature.toSamplingValue()
    val topPValue = topP.toSamplingValue()
    val temperatureError = temperature.isNotBlank() && temperatureValue?.let {
        it in ModelOption.MinTemperature..ModelOption.MaxTemperature
    } != true
    val topPError = topP.isNotBlank() && topPValue?.let {
        it in ModelOption.MinTopP..ModelOption.MaxTopP
    } != true
    val hasError = contextError || compactError || outputError || temperatureError || topPError

    val pending = editable && !hasError && (
        contextValue != option.contextWindowTokens ||
            compactValue != option.autoCompactTokenLimit ||
            outputValue != option.maxOutputTokens ||
            temperatureValue != option.temperature ||
            topPValue != option.topP ||
            reasoningEffort != option.reasoningEffort ||
            (!officialDeepSeekVision && supportsImageInput != option.supportsImageInput)
        )

    // Nothing is written while a value is out of range: half-typed numbers routinely fail the
    // cross-field checks, and persisting them would fight the person still typing.
    LaunchedEffect(
        pending,
        contextValue,
        compactValue,
        outputValue,
        temperatureValue,
        topPValue,
        reasoningEffort,
        supportsImageInput,
        officialDeepSeekVision,
    ) {
        if (!pending) return@LaunchedEffect
        saveState = ParamsSaveState.Saving
        kotlinx.coroutines.delay(600)
        val config = selectedConfig
        val current = option
        val updated = current.copy(
            contextWindowTokens = contextValue,
            autoCompactTokenLimit = compactValue,
            maxOutputTokens = outputValue,
            temperature = temperatureValue,
            topP = topPValue,
            reasoningEffort = reasoningEffort,
            supportsImageInput = if (officialDeepSeekVision) {
                current.supportsImageInput
            } else {
                supportsImageInput
            },
        )
        val options = config.modelOptions.toMutableList().apply {
            val index = indexOfFirst { it.id == updated.id }
            if (index >= 0) this[index] = updated else add(updated)
        }
        onSaveConfig(config.copy(model = selectedModel, modelOptions = options)) { result ->
            saveState = if (result.isFailure) ParamsSaveState.Failed else ParamsSaveState.Saved
        }
    }
    LaunchedEffect(saveState) {
        if (saveState != ParamsSaveState.Saved) return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        saveState = ParamsSaveState.Idle
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 28.dp),
    ) {
        if (editable) {
            ParamsGroupLabel("连接", appearance)
            SheetGroupCard(appearance) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable(onClick = onOpenApiFormat)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("接口格式", modifier = Modifier.weight(1f), color = appearance.mobileText, fontSize = 15.sp)
                    Text(
                        option.apiFormatOverride?.displayName ?: "跟随连接",
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StrokeSvgIcon(
                        AppIconPaths.ChevronRight,
                        appearance.mobileSoft,
                        iconSize = 15.dp,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }

            ParamsGroupLabel("输入能力", appearance)
            SheetGroupCard(appearance) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("支持图片输入", color = appearance.mobileText, fontSize = 15.sp)
                        Text(
                            if (officialDeepSeekVision) {
                                "DeepSeek 官方视觉模型，已自动声明 input: [text, image]"
                            } else {
                                "向 DSH 声明 input: [text, image]"
                            },
                            color = appearance.mobileMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    AppSwitch(
                        checked = officialDeepSeekVision || supportsImageInput,
                        enabled = !officialDeepSeekVision,
                        appearance = appearance,
                        onCheckedChange = { supportsImageInput = it },
                    )
                }
            }
        }

        if (editable) {
            ParamsGroupLabel("推理", appearance)
            SheetGroupCard(appearance) {
                ModelReasoningSelector(
                    variants = reasoningVariants,
                    selectedVariant = reasoningEffort,
                    appearance = appearance,
                    onSelect = { reasoningEffort = it },
                )
            }
            Text(
                "档位由 DSH/pi-ai 按当前接口格式转换；默认不覆盖上游。",
                color = appearance.mobileSoft,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp),
            )
        }

        ParamsGroupLabel("上限", appearance)
        SheetGroupCard(appearance) {
            ModelInlineField(
                label = "上下文窗口",
                value = contextWindow,
                placeholder = if (editable) automaticContextWindow?.toString().orEmpty() else "先选择模型",
                appearance = appearance,
                isError = contextError,
                onChange = { contextWindow = it.onlyDigits() },
            )
            SheetGroupDivider(appearance)
            ModelInlineField(
                label = "自动压缩阈值",
                value = autoCompact,
                placeholder = if (editable) autoCompactHint(effectiveContext) else "先选择模型",
                appearance = appearance,
                isError = compactError,
                onChange = { autoCompact = it.onlyDigits() },
            )
            SheetGroupDivider(appearance)
            ModelInlineField(
                label = "单次最大输出",
                value = maxOutput,
                placeholder = if (editable) "上游默认" else "先选择模型",
                appearance = appearance,
                isError = outputError,
                onChange = { maxOutput = it.onlyDigits() },
            )
        }

        ParamsGroupLabel("采样", appearance)
        SheetGroupCard(appearance) {
            SamplingParameterField(
                label = "温度",
                detail = if (temperature.isBlank()) "留空不发送" else "0–2",
                value = temperature,
                enabled = editable,
                isError = temperatureError,
                appearance = appearance,
                onChange = { temperature = it.samplingInput() },
            )
            SheetGroupDivider(appearance)
            SamplingParameterField(
                label = "Top P",
                detail = if (topP.isBlank()) "留空不发送" else "0–1",
                value = topP,
                enabled = editable,
                isError = topPError,
                appearance = appearance,
                onChange = { topP = it.samplingInput() },
            )
        }

        val (note, noteColor) = paramsNote(
            editable = editable,
            contextError = contextError,
            compactError = compactError,
            outputError = outputError,
            temperatureError = temperatureError,
            topPError = topPError,
            saveState = saveState,
            appearance = appearance,
        )
        if (note.isNotEmpty()) {
            Text(
                note,
                color = noteColor,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp),
            )
        }
    }
}

@Composable
private fun paramsNote(
    editable: Boolean,
    contextError: Boolean,
    compactError: Boolean,
    outputError: Boolean,
    temperatureError: Boolean,
    topPError: Boolean,
    saveState: ParamsSaveState,
    appearance: AppearanceTheme,
): Pair<String, Color> = when {
    !editable -> "先在「模型」页选一个模型，参数是跟着模型走的。" to appearance.mobileSoft
    contextError -> "上下文窗口需要在 ${ModelOption.MinContextWindowTokens} 到 ${ModelOption.MaxContextWindowTokens} 之间。" to ElecKoiDanger
    compactError -> "自动压缩阈值不能超过上下文窗口。" to ElecKoiDanger
    outputError -> "单次最大输出不能超过上下文窗口。" to ElecKoiDanger
    temperatureError -> "温度需要在 ${ModelOption.MinTemperature} 到 ${ModelOption.MaxTemperature} 之间。" to ElecKoiDanger
    topPError -> "Top P 需要在 ${ModelOption.MinTopP} 到 ${ModelOption.MaxTopP} 之间。" to ElecKoiDanger
    saveState == ParamsSaveState.Failed -> "保存失败，改一下再试。" to ElecKoiDanger
    saveState == ParamsSaveState.Saving -> "保存中…" to appearance.mobileMuted
    saveState == ParamsSaveState.Saved -> "已保存" to appearance.mobileMuted
    else -> "" to appearance.mobileSoft
}

@Composable
private fun SamplingParameterField(
    label: String,
    detail: String,
    value: String,
    enabled: Boolean,
    isError: Boolean,
    appearance: AppearanceTheme,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, color = appearance.mobileText, fontSize = 14.sp)
            Text(
                detail,
                color = if (isError) ElecKoiDanger else appearance.mobileMuted,
                fontSize = 11.sp,
            )
        }
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            placeholder = "留空",
            modifier = Modifier.width(120.dp).padding(start = 8.dp),
            enabled = enabled,
            textStyle = TextStyle(
                color = when {
                    isError -> ElecKoiDanger
                    enabled -> appearance.mobileText
                    else -> appearance.mobileSoft
                },
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

private fun autoCompactHint(contextTokens: Int?): String {
    contextTokens ?: return "上下文的 90%"
    return (contextTokens.toLong() * 9 / 10).toString()
}

private fun String.toOptionalInt(): Int? = trim().takeIf(String::isNotEmpty)?.toIntOrNull()

internal fun Double.samplingParameterText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

internal fun String.samplingInput(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = normalized.indexOf('.')
    return if (dot < 0) normalized.take(4) else {
        normalized.take(dot + 1) + normalized.drop(dot + 1).replace(".", "").take(3)
    }
}

internal fun String.toSamplingValue(): Double? = trim().toDoubleOrNull()
