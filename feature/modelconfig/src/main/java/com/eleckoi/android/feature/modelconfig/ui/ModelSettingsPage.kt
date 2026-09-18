package com.eleckoi.android.feature.modelconfig.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.feature.modelconfig.ui.settings.ModelSettingsContent
import com.eleckoi.android.feature.modelconfig.ui.settings.addAndSelectModel
import com.eleckoi.android.feature.modelconfig.ui.settings.removeManualModel
import com.eleckoi.android.feature.modelconfig.ui.settings.modelPickerItems
import com.eleckoi.android.feature.modelconfig.ui.settings.rememberModelSettingsEditorState
import com.eleckoi.android.feature.modelconfig.ui.components.ModelSettingsHeader
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.ErrorDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.UnsavedChangesDialog

internal typealias ModelSettingsEditorState =
    com.eleckoi.android.feature.modelconfig.ui.settings.ModelSettingsEditorState

@Composable
fun ModelSettingsPage(
    models: ModelConfigCollection?,
    target: ModelTarget,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onSave: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onCreateConfig: (String) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onFetchModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onTestConnection: (ModelConfig, (Result<Unit>) -> Unit) -> Unit,
) {
    val configs = models?.configs.orEmpty()
    val editorState = rememberModelSettingsEditorState(configs, target)
    val context = LocalContext.current
    val form = editorState.form
    val provider = providerMeta(form.provider)
    val isImageProvider = form.isImageGenerationConfig()
    val providerConfigs = configs
        .filter { normalizeProviderId(it.provider) == normalizeProviderId(form.provider) }
        .let { list ->
            if (list.any { it.id == form.id } || form.id.isBlank()) list else list + form
        }

    fun saveCurrent(onSaved: (ModelConfig) -> Unit = {}) {
        if (editorState.saving) return
        editorState.markSaving()
        onSave(editorState.form) { result ->
            result.onSuccess { saved ->
                editorState.markSaved(saved)
                Toast.makeText(context, "模型配置已保存", Toast.LENGTH_SHORT).show()
                onSaved(saved)
            }.onFailure { error ->
                editorState.markSaveFailed(error.message ?: "模型配置保存失败")
            }
        }
    }

    fun requestBack() = editorState.requestDraftReplacement(onBack)

    BackHandler(onBack = ::requestBack)

    PinnedStatusScaffold(
        appearance = appearance,
        imeAware = false,
        backgroundColor = appearance.mobileBg,
    ) {
        ModelSettingsHeader(
            title = "模型配置",
            appearance = appearance,
            onBack = ::requestBack,
            actionText = if (editorState.saving) "保存中" else if (editorState.saveState == "saved") "已保存" else "保存",
            actionEnabled = editorState.hasUnsavedChanges && !editorState.saving,
            onAction = { saveCurrent() },
        )
        ModelSettingsContent(
            state = editorState,
            provider = provider,
            providerConfigs = providerConfigs,
            canDeleteConfig = configs.any { it.id == form.id },
            isImageProvider = isImageProvider,
            appearance = appearance,
            onCreateConfig = onCreateConfig,
            onDeleteConfig = { editorState.confirmDelete = true },
            onFetchModels = onFetchModels,
            onTestConnection = onTestConnection,
        )
    }

    editorState.testState?.let { state ->
        ModelConnectionTestDialog(
            state = state,
            modelLabel = editorState.form.model,
            appearance = appearance,
            onDismiss = editorState::dismissTest,
        )
    }

    if (editorState.headersSheetOpen) {
        ModelHeadersSheet(
            headers = editorState.form.customHeaders,
            appearance = appearance,
            onClose = { editorState.headersSheetOpen = false },
            onConfirm = { headers ->
                editorState.headersSheetOpen = false
                editorState.update(editorState.form.copy(customHeaders = headers))
            },
        )
    }

    if (editorState.apiFormatSheetOpen && !isImageProvider) {
        ModelApiFormatSheet(
            selected = editorState.form.apiFormat,
            appearance = appearance,
            formats = apiFormatsForProvider(editorState.form.provider),
            onClose = { editorState.apiFormatSheetOpen = false },
            onSelect = { format ->
                editorState.apiFormatSheetOpen = false
                if (format != null) {
                    editorState.update(editorState.form.copy(apiFormat = format))
                }
            },
        )
    }

    if (editorState.modelPickerOpen) {
        ModelPickerSheet(
            items = modelPickerItems(editorState.form),
            activeModel = editorState.form.model,
            appearance = appearance,
            onClose = { editorState.modelPickerOpen = false },
            onSelect = { model ->
                editorState.modelPickerOpen = false
                editorState.update(editorState.form.addAndSelectModel(model))
            },
            onAdd = { model -> editorState.update(editorState.form.addAndSelectModel(model)) },
            onDelete = { model -> editorState.update(editorState.form.removeManualModel(model)) },
        )
    }

    if (editorState.testState == null && editorState.testMessage.isNotBlank()) {
        ErrorDialog(
            message = editorState.testMessage,
            appearance = appearance,
            onDismiss = editorState::clearMessage,
        )
    }

    if (editorState.confirmDelete) {
        val deletesProviderEntry = providerConfigs.size <= 1 && !isFixedModelProvider(form.provider)
        ConfirmDialog(
            title = if (deletesProviderEntry) "删除渠道？" else "删除配置？",
            message = when {
                providerConfigs.size > 1 -> "只删除当前配置，其他配置不受影响。"
                deletesProviderEntry -> "将删除当前配置，并从模型页移除这个渠道入口。"
                else -> "将删除当前配置；模型页的固定入口会保留。"
            },
            appearance = appearance,
            confirmText = "确认删除",
            destructive = true,
            onDismiss = { editorState.confirmDelete = false },
            onConfirm = {
                editorState.confirmDelete = false
                onDeleteConfig(editorState.prepareForDelete())
                onBack()
            },
        )
    }

    if (editorState.unsavedDialogOpen) {
        UnsavedChangesDialog(
            title = "保存修改？",
            message = "离开前是否保存当前模型配置的修改？",
            appearance = appearance,
            saving = editorState.saving,
            onSave = {
                saveCurrent(editorState::savedDraftAndContinue)
            },
            onDiscard = editorState::discardDraftAndContinue,
            onCancel = editorState::cancelDraftReplacement,
        )
    }
}
