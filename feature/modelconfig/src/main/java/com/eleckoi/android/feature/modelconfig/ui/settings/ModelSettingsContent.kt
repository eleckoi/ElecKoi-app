package com.eleckoi.android.feature.modelconfig.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.ui.components.ModelField
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldDivider
import com.eleckoi.android.feature.modelconfig.ui.components.ModelFieldGroup
import com.eleckoi.android.feature.modelconfig.ui.ModelProviderMeta
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSectionAction
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSectionHeader
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSectionNote
import com.eleckoi.android.feature.modelconfig.ui.components.ModelVersionSelector
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ModelProviderIcon

@Composable
internal fun ColumnScope.ModelSettingsContent(
    state: ModelSettingsEditorState,
    provider: ModelProviderMeta,
    providerConfigs: List<ModelConfig>,
    canDeleteConfig: Boolean,
    isImageProvider: Boolean,
    appearance: AppearanceTheme,
    onCreateConfig: (String) -> Unit,
    onDeleteConfig: () -> Unit,
    onFetchModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onTestConnection: (ModelConfig, (Result<Unit>) -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val keyboardClearance = with(density) { imeBottomPx.toDp() }
    val scrollState = rememberScrollState()
    val form = state.form

    Column(
        modifier = Modifier
            .weight(1f)
            .background(appearance.mobileBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 16.dp + keyboardClearance),
    ) {
        ModelProviderSummary(provider, appearance)
        ModelConfigurationSection(
            form = form,
            provider = provider,
            providerConfigs = providerConfigs,
            canDeleteConfig = canDeleteConfig,
            isImageProvider = isImageProvider,
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            onCreateConfig = { providerId ->
                state.requestDraftReplacement { onCreateConfig(providerId) }
            },
            onDeleteConfig = onDeleteConfig,
            onSelectConfig = { selected ->
                state.requestDraftReplacement { state.selectConfig(selected) }
            },
            onUpdate = state::update,
        )
        ModelConnectionSection(
            form = form,
            provider = provider,
            isImageProvider = isImageProvider,
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            onOpenApiFormat = { state.apiFormatSheetOpen = true },
            onUpdate = state::update,
        )
        ModelSelectionSection(
            form = form,
            provider = provider,
            appearance = appearance,
            onOpenPicker = { state.modelPickerOpen = true },
        )
        if (isImageProvider) {
            ModelImageSettingsSection(
                form = form,
                appearance = appearance,
                scrollState = scrollState,
                imeBottomPx = imeBottomPx,
                onUpdate = state::update,
            )
            ModelConnectionActions(
                loadingModels = false,
                testing = state.testing,
                appearance = appearance,
                onFetchModels = {},
                onTestConnection = {
                    if (state.startImageTestConnection()) {
                        onTestConnection(form, state::finishImageTestConnection)
                    }
                },
                showFetchModels = false,
            )
        } else {
            ModelConnectionActions(
                loadingModels = state.loadingModels,
                testing = state.testing,
                appearance = appearance,
                onFetchModels = {
                    if (state.startFetchModels()) {
                        onFetchModels(form, state::finishFetchModels)
                    }
                },
                onTestConnection = {
                    if (state.startTestConnection()) {
                        onFetchModels(form) { fetchResult ->
                            state.finishConnectionStage(fetchResult)
                            if (fetchResult.isSuccess) {
                                val fetched = fetchResult.getOrThrow()
                                onTestConnection(fetched) { toolResult ->
                                    state.finishToolStage(toolResult)
                                }
                            }
                        }
                    }
                },
            )
            ModelCapabilitySections(
                form = form,
                appearance = appearance,
                scrollState = scrollState,
                imeBottomPx = imeBottomPx,
                onUpdate = state::update,
            )
        }
        ModelNetworkSection(
            form = form,
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            onOpenHeaders = { state.headersSheetOpen = true },
            onUpdate = state::update,
        )
    }
}

@Composable
private fun ModelProviderSummary(provider: ModelProviderMeta, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appearance.mobileSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelProviderIcon(provider.id, provider.initials, appearance, Modifier.size(34.dp))
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(provider.label, color = appearance.mobileText, fontSize = 15.sp)
            Text(
                provider.badge,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ModelConfigurationSection(
    form: ModelConfig,
    provider: ModelProviderMeta,
    providerConfigs: List<ModelConfig>,
    canDeleteConfig: Boolean,
    isImageProvider: Boolean,
    appearance: AppearanceTheme,
    scrollState: androidx.compose.foundation.ScrollState,
    imeBottomPx: Int,
    onCreateConfig: (String) -> Unit,
    onDeleteConfig: () -> Unit,
    onSelectConfig: (ModelConfig) -> Unit,
    onUpdate: (ModelConfig) -> Unit,
) {
    ModelSectionHeader("配置", appearance) {
        if (!isImageProvider) {
            ModelSectionAction("新建", AppIconPaths.Plus, appearance) { onCreateConfig(provider.id) }
        }
        if (canDeleteConfig) {
            ModelSectionAction(
                "删除",
                AppIconPaths.Trash,
                appearance,
                danger = true,
                onClick = onDeleteConfig,
            )
        }
    }
    ModelFieldGroup(appearance) {
        if (!isImageProvider) {
            ModelVersionSelector(
                configs = providerConfigs.ifEmpty { listOf(form) },
                currentId = form.id,
                appearance = appearance,
                onSelect = onSelectConfig,
            )
            ModelFieldDivider(appearance)
        }
        ModelField(
            label = "名称",
            value = form.name,
            placeholder = "待命名",
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
        ) { onUpdate(form.copy(name = it)) }
    }
}
