package com.eleckoi.android.feature.modelconfig.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.ui.components.ModelActionButton
import com.eleckoi.android.feature.modelconfig.ui.displayName
import com.eleckoi.android.feature.modelconfig.ui.components.ModelField
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldDivider
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldGroup
import com.eleckoi.android.feature.modelconfig.ui.ModelProviderMeta
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSectionHeader
import com.eleckoi.android.feature.modelconfig.ui.components.ModelStackedNavigationField
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import kotlinx.coroutines.delay

@Composable
internal fun ModelConnectionSection(
    form: ModelConfig,
    provider: ModelProviderMeta,
    isImageProvider: Boolean,
    appearance: AppearanceTheme,
    scrollState: ScrollState,
    imeBottomPx: Int,
    onOpenApiFormat: () -> Unit,
    onUpdate: (ModelConfig) -> Unit,
) {
    var apiKeyVisible by remember(form.id) { mutableStateOf(false) }
    LaunchedEffect(apiKeyVisible, form.id) {
        if (apiKeyVisible) {
            delay(15_000)
            apiKeyVisible = false
        }
    }

    ModelSectionHeader("连接", appearance, actions = {})
    ModelFieldGroup(appearance) {
        if (!isImageProvider) {
            val officialDeepSeek = form.provider.trim().equals("deepseek", ignoreCase = true)
            ModelStackedNavigationField(
                label = "接口格式",
                value = when {
                    !officialDeepSeek -> form.apiFormat.displayName
                    form.apiFormat == ModelApiFormat.ChatCompletions -> "Chat Completions · DSH DeepSeek"
                    else -> "Responses API · DSH/pi-ai"
                },
                appearance = appearance,
                enabled = true,
                onClick = onOpenApiFormat,
            )
            ModelFieldDivider(appearance)
        }
        ModelField(
            label = "API 地址",
            value = form.baseUrl,
            placeholder = provider.baseUrlPlaceholder,
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
        ) { value ->
            onUpdate(
                if (isImageProvider) {
                    form.copy(baseUrl = value)
                } else {
                    form.copy(baseUrl = value, model = "", modelOptions = emptyList())
                },
            )
        }
        ModelFieldDivider(appearance)
        ModelField(
            label = "API Key",
            value = form.apiKey,
            placeholder = if (form.apiKeyNeedsReentry) {
                "请重新填写 API Key"
            } else {
                provider.apiKeyPlaceholder
            },
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            trailingIcon = AppIconPaths.Eye,
            trailingContentDescription = if (apiKeyVisible) "隐藏 API Key" else "显示 API Key 15 秒",
            secureEntry = true,
            secureEntryVisible = apiKeyVisible,
            onTrailingClick = { apiKeyVisible = !apiKeyVisible },
        ) { value ->
            onUpdate(
                if (isImageProvider) {
                    form.copy(apiKey = value, apiKeyNeedsReentry = false)
                } else {
                    form.copy(
                        apiKey = value,
                        model = "",
                        modelOptions = emptyList(),
                        apiKeyNeedsReentry = false,
                    )
                },
            )
        }
    }
    if (form.apiKeyNeedsReentry) {
        androidx.compose.material3.Text(
            "此前保存的 API Key 已无法解密；其他配置仍在，请重新填写后保存。",
            color = ElecKoiDanger,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
        )
    }
}

@Composable
internal fun ModelSelectionSection(
    form: ModelConfig,
    provider: ModelProviderMeta,
    appearance: AppearanceTheme,
    onOpenPicker: () -> Unit,
) {
    ModelSectionHeader("模型", appearance, actions = {})
    ModelFieldGroup(appearance) {
        ModelStackedNavigationField(
            label = "",
            value = form.model.ifBlank { provider.modelPlaceholder },
            appearance = appearance,
            onClick = onOpenPicker,
        )
    }
}

@Composable
internal fun ModelConnectionActions(
    loadingModels: Boolean,
    testing: Boolean,
    appearance: AppearanceTheme,
    onFetchModels: () -> Unit,
    onTestConnection: () -> Unit,
    showFetchModels: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showFetchModels) {
            ModelActionButton(
                text = if (loadingModels) "读取中" else "读取模型",
                icon = AppIconPaths.History,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onClick = onFetchModels,
            )
        }
        ModelActionButton(
            text = if (testing) "测试中" else "测试连接",
            icon = AppIconPaths.Plug,
            appearance = appearance,
            modifier = Modifier.weight(1f),
            primary = true,
            onClick = onTestConnection,
        )
    }
}
