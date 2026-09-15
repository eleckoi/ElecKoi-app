package com.eleckoi.android.feature.modelconfig.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.defaultApiFormatForProvider
import com.eleckoi.android.engine.generation.model.defaultImageSettings
import com.eleckoi.android.engine.generation.model.withProviderDefaults
import com.eleckoi.android.feature.modelconfig.ui.ModelTarget
import com.eleckoi.android.feature.modelconfig.ui.ModelTestState
import com.eleckoi.android.feature.modelconfig.ui.ModelTestStatus
import com.eleckoi.android.feature.modelconfig.ui.ModelTestStep
import com.eleckoi.android.feature.modelconfig.ui.normalizeProviderId

internal class ModelSettingsEditorState(
    initialForm: ModelConfig,
    initialDirty: Boolean,
    initialNewDraft: Boolean = false,
) {
    var form by mutableStateOf(initialForm)
    var dirty by mutableStateOf(initialDirty)
    var isNewDraft by mutableStateOf(initialNewDraft)
    var saveState by mutableStateOf("idle")
    var loadingModels by mutableStateOf(false)
    var testing by mutableStateOf(false)
    var testMessage by mutableStateOf("")
    var confirmDelete by mutableStateOf(false)
    var headersSheetOpen by mutableStateOf(false)
    var apiFormatSheetOpen by mutableStateOf(false)
    var modelPickerOpen by mutableStateOf(false)
    var testState by mutableStateOf<ModelTestState?>(null)
    var unsavedDialogOpen by mutableStateOf(false)
    private var pendingDraftAction: (() -> Unit)? = null

    val hasUnsavedChanges: Boolean
        get() = dirty || isNewDraft

    val saving: Boolean
        get() = saveState == "saving"

    fun syncFrom(configs: List<ModelConfig>, target: ModelTarget) {
        if (hasUnsavedChanges || saving) return
        val current = configs.firstOrNull { it.id == form.id }
        form = current ?: resolveInitialConfig(configs, target)
        // A draft becomes dirty only after an actual user edit. Merely opening the provider
        // picker must not persist an empty channel during the first Room synchronization.
        dirty = false
        isNewDraft = configs.none { it.id == form.id }
        saveState = "idle"
        testMessage = ""
    }

    fun update(next: ModelConfig) {
        form = next
        dirty = true
        saveState = "idle"
        testMessage = ""
    }

    fun markSaving() {
        saveState = "saving"
    }

    fun markSaved(saved: ModelConfig) {
        form = saved
        dirty = false
        isNewDraft = false
        saveState = "saved"
    }

    fun markSaveFailed(message: String) {
        saveState = "idle"
        testMessage = message
    }

    fun selectConfig(selected: ModelConfig) {
        form = selected
        dirty = false
        isNewDraft = false
        saveState = "idle"
        testMessage = ""
    }

    fun requestDraftReplacement(action: () -> Unit) {
        if (!hasUnsavedChanges) {
            action()
            return
        }
        pendingDraftAction = action
        unsavedDialogOpen = true
    }

    fun cancelDraftReplacement() {
        pendingDraftAction = null
        unsavedDialogOpen = false
    }

    fun discardDraftAndContinue() {
        val action = pendingDraftAction
        pendingDraftAction = null
        unsavedDialogOpen = false
        dirty = false
        isNewDraft = false
        action?.invoke()
    }

    fun savedDraftAndContinue(saved: ModelConfig) {
        val action = pendingDraftAction
        pendingDraftAction = null
        unsavedDialogOpen = false
        markSaved(saved)
        action?.invoke()
    }

    fun startFetchModels(): Boolean {
        if (loadingModels) return false
        loadingModels = true
        return true
    }

    fun finishFetchModels(result: Result<ModelConfig>) {
        loadingModels = false
        result.onSuccess { fetched ->
            form = fetched
            dirty = true
            saveState = "idle"
            testMessage = ""
            modelPickerOpen = true
        }.onFailure { error ->
            testMessage = error.message ?: "读取模型失败"
        }
    }

    fun startTestConnection(): Boolean {
        if (testing) return false
        testing = true
        testMessage = ""
        testState = ModelTestState(
            steps = listOf(
                ModelTestStep("地址与密钥", status = ModelTestStatus.Running),
                ModelTestStep("模型列表"),
                ModelTestStep("工具调用", hint = "tools"),
            ),
        )
        return true
    }

    private fun updateStep(index: Int, transform: (ModelTestStep) -> ModelTestStep) {
        val current = testState ?: return
        testState = current.copy(
            steps = current.steps.mapIndexed { itemIndex, step ->
                if (itemIndex == index) transform(step) else step
            },
        )
    }

    // The models call proves the address and key in one real request, so both rows settle together.
    fun finishConnectionStage(result: Result<ModelConfig>) {
        result.onSuccess { fetched ->
            updateStep(0) { it.copy(status = ModelTestStatus.Passed) }
            updateStep(1) {
                it.copy(status = ModelTestStatus.Passed, detail = "${fetched.modelOptions.size} 个")
            }
            updateStep(2) { it.copy(status = ModelTestStatus.Running) }
        }.onFailure { error ->
            updateStep(0) {
                it.copy(status = ModelTestStatus.Failed, detail = error.message?.take(24) ?: "失败")
            }
            testMessage = "当前接口格式测试失败，请尝试其他接口格式。"
            testState = testState?.copy(finished = true, formatFallbackSuggested = true)
            testing = false
        }
    }

    fun finishToolStage(result: Result<Unit>) {
        testing = false
        result.onSuccess {
            updateStep(2) { it.copy(status = ModelTestStatus.Passed, detail = "支持") }
            testState = testState?.copy(finished = true, toolsSupported = true)
        }.onFailure { error ->
            updateStep(2) {
                it.copy(status = ModelTestStatus.Failed, detail = error.message?.take(24) ?: "不支持")
            }
            testMessage = "当前接口格式未通过工具测试，请尝试其他接口格式。"
            testState = testState?.copy(
                finished = true,
                toolsSupported = false,
                formatFallbackSuggested = true,
            )
        }
    }

    fun dismissTest() {
        testState = null
        testMessage = ""
    }

    fun clearMessage() {
        testMessage = ""
    }

    fun prepareForDelete(): String {
        dirty = false
        isNewDraft = false
        saveState = "idle"
        testMessage = ""
        return form.id
    }

    fun startImageTestConnection(): Boolean {
        if (testing) return false
        testing = true
        testMessage = ""
        testState = ModelTestState(
            steps = listOf(ModelTestStep("请求低质量测试图", hint = "PNG", status = ModelTestStatus.Running)),
        )
        return true
    }

    fun finishImageTestConnection(result: Result<Unit>) {
        testing = false
        result.onSuccess {
            updateStep(0) { it.copy(status = ModelTestStatus.Passed, detail = "通过") }
            testState = testState?.copy(
                finished = true,
                completionMessage = "接口已返回有效 PNG；测试图仅在内存中校验，没有保存到本地。",
            )
        }.onFailure { error ->
            updateStep(0) {
                it.copy(status = ModelTestStatus.Failed, detail = error.message?.take(80) ?: "失败")
            }
            testMessage = error.message ?: "测试生图失败"
            testState = testState?.copy(
                finished = true,
                completionMessage = "测试生图失败，请检查地址、密钥和模型名称。",
            )
        }
    }
}

@Composable
internal fun rememberModelSettingsEditorState(
    configs: List<ModelConfig>,
    target: ModelTarget,
): ModelSettingsEditorState {
    val state = remember(target) {
        ModelSettingsEditorState(
            initialForm = resolveInitialConfig(configs, target),
            // Match the reference implementation: opening a provider is not itself an edit.
            // This prevents an accidental tap from leaving a blank provider in the library.
            initialDirty = false,
            initialNewDraft = target.draftId.isNotBlank(),
        )
    }
    LaunchedEffect(configs, target) {
        // Refresh the version currently on screen without snapping back to the navigation target.
        state.syncFrom(configs, target)
    }
    return state
}

internal fun resolveInitialConfig(configs: List<ModelConfig>, target: ModelTarget): ModelConfig {
    if (target.draftId.isNotBlank()) {
        val provider = normalizeProviderId(target.providerId)
        return ModelConfig(
            id = target.draftId,
            provider = provider,
            apiFormat = defaultApiFormatForProvider(provider),
            imageSettings = defaultImageSettings(provider),
        ).withProviderDefaults()
    }
    val existing = configs.firstOrNull { it.id == target.configId }
        ?: configs.firstOrNull {
            normalizeProviderId(it.provider) == normalizeProviderId(target.providerId)
        }
    val initial = existing ?: run {
        val provider = normalizeProviderId(target.providerId)
        ModelConfig(
            provider = provider,
            apiFormat = defaultApiFormatForProvider(provider),
            imageSettings = defaultImageSettings(provider),
        )
    }
    return initial.withProviderDefaults()
}
