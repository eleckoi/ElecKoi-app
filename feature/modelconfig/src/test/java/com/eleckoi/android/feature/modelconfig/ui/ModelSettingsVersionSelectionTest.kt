package com.eleckoi.android.feature.modelconfig.ui

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.feature.modelconfig.ui.settings.addAndSelectModel
import com.eleckoi.android.feature.modelconfig.ui.settings.resolveInitialConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSettingsVersionSelectionTest {
    @Test
    fun `DeepSeek interface picker exposes all three DSH supported protocols`() {
        assertEquals(
            listOf(
                ModelApiFormat.ChatCompletions,
                ModelApiFormat.Responses,
                ModelApiFormat.AnthropicMessages,
            ),
            apiFormatsForProvider("deepseek"),
        )
    }

    @Test
    fun `Room refresh keeps the version selected inside the editor`() {
        val original = config(id = "config-123", name = "123")
        val selected = config(id = "config-deepseek", name = "DeepSeek", model = "deepseek-v4-flash")
        val state = ModelSettingsEditorState(original, initialDirty = false)

        state.selectConfig(selected)
        state.syncFrom(
            configs = listOf(original, selected.copy(model = "deepseek-v4-pro")),
            target = ModelTarget(providerId = "custom", configId = original.id),
        )

        assertEquals(selected.id, state.form.id)
        assertEquals("deepseek-v4-pro", state.form.model)
    }

    @Test
    fun `model library opens and summarizes the active provider version`() {
        val original = config(id = "config-123", name = "123")
        val selected = config(id = "config-deepseek", name = "DeepSeek", model = "deepseek-v4-pro")
        val configs = listOf(original, selected)

        assertEquals(
            selected,
            firstConfigForProvider(configs, "custom", preferredConfigId = selected.id),
        )
        assertEquals(
            "DeepSeek · deepseek-v4-pro",
            latestConfigSummary(
                configs,
                providerMeta("custom"),
                preferredConfigId = selected.id,
            ),
        )
    }

    @Test
    fun `DeepSeek library row keeps the concise official API summary`() {
        val configured = ModelConfig(
            id = "config-deepseek",
            name = "未命名",
            provider = "deepseek",
            model = "deepseek-v4-flash",
        )

        assertEquals(
            "官方 API",
            latestConfigSummary(listOf(configured), providerMeta("deepseek"), configured.id),
        )
        assertEquals(
            "官方 API",
            latestConfigSummary(emptyList(), providerMeta("deepseek")),
        )
    }

    @Test
    fun `opening an unconfigured provider uses its creation format`() {
        assertEquals(
            ModelApiFormat.Responses,
            resolveInitialConfig(emptyList(), ModelTarget(providerId = "custom")).apiFormat,
        )
    }

    @Test
    fun `new draft stays selected instead of falling back to the first saved config`() {
        val existing = config(id = "config-existing", name = "existing-provider")
        val target = ModelConfig(id = "config-draft", provider = "custom")
            .toDraftModelTarget()

        val draft = resolveInitialConfig(listOf(existing), target)

        assertEquals("config-draft", draft.id)
        assertEquals("", draft.name)
        assertEquals("", target.configId)
        assertEquals("config-draft", target.draftId)
    }

    @Test
    fun `opening a new provider does not mark the untouched draft dirty`() {
        val target = ModelConfig(id = "config-draft", provider = "zhipu").toDraftModelTarget()
        val state = ModelSettingsEditorState(
            initialForm = resolveInitialConfig(emptyList(), target),
            initialDirty = false,
            initialNewDraft = true,
        )

        state.syncFrom(emptyList(), target)

        assertFalse(state.dirty)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun `reading models opens the picker without leaving inline status text`() {
        val state = ModelSettingsEditorState(config(id = "config", name = "DeepSeek"), initialDirty = false)
        val fetched = state.form.copy(
            modelOptions = listOf(ModelOption(id = "deepseek-v4-flash")),
        )

        state.finishFetchModels(Result.success(fetched))

        assertTrue(state.modelPickerOpen)
        assertEquals("", state.testMessage)
        assertEquals(fetched.modelOptions, state.form.modelOptions)
        assertTrue(state.dirty)
        assertEquals("idle", state.saveState)
    }

    @Test
    fun `unsaved edit blocks replacement until the user chooses an action`() {
        val state = ModelSettingsEditorState(config(id = "config", name = "DeepSeek"), initialDirty = false)
        var replaced = false

        state.update(state.form.copy(name = "edited"))
        state.requestDraftReplacement { replaced = true }

        assertTrue(state.unsavedDialogOpen)
        assertFalse(replaced)

        state.discardDraftAndContinue()

        assertFalse(state.unsavedDialogOpen)
        assertTrue(replaced)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `saving a new draft turns it into a persisted selection before continuing`() {
        val draft = config(id = "draft", name = "new")
        val saved = draft.copy(name = "saved")
        val state = ModelSettingsEditorState(
            initialForm = draft,
            initialDirty = false,
            initialNewDraft = true,
        )
        var continued = false

        state.requestDraftReplacement { continued = true }
        state.savedDraftAndContinue(saved)

        assertEquals(saved, state.form)
        assertFalse(state.hasUnsavedChanges)
        assertEquals("saved", state.saveState)
        assertTrue(continued)
    }

    @Test
    fun `reading model failure stays out of layout and is dismissible`() {
        val state = ModelSettingsEditorState(config(id = "config", name = "DeepSeek"), initialDirty = false)

        state.finishFetchModels(Result.failure(IllegalStateException("读取失败")))

        assertFalse(state.modelPickerOpen)
        assertEquals("读取失败", state.testMessage)
        state.clearMessage()
        assertEquals("", state.testMessage)
    }

    @Test
    fun `manual model is selected and added to the reusable model list`() {
        val fetched = ModelOption(id = "glm-5.3-flash")
        val config = ModelConfig(modelOptions = listOf(fetched))

        val added = config.addAndSelectModel("  private-glm  ")
        val selectedExisting = added.addAndSelectModel("glm-5.3-flash")

        assertEquals("private-glm", added.model)
        assertTrue(added.modelOptions.first().isUserAdded)
        assertFalse(added.modelOptions.last().isUserAdded)
        assertEquals(listOf("private-glm", "glm-5.3-flash"), added.modelOptions.map { it.id })
        assertEquals("glm-5.3-flash", selectedExisting.model)
        assertEquals(2, selectedExisting.modelOptions.size)
    }

    @Test
    fun `connection failure suggests another format without changing the selected format`() {
        val state = ModelSettingsEditorState(
            ModelConfig(apiFormat = ModelApiFormat.Responses),
            initialDirty = false,
        )

        assertTrue(state.startTestConnection())
        state.finishConnectionStage(Result.failure(IllegalStateException("unsupported endpoint")))

        assertEquals(ModelApiFormat.Responses, state.form.apiFormat)
        assertTrue(state.testState?.formatFallbackSuggested == true)
        assertEquals("当前接口格式测试失败，请尝试其他接口格式。", state.testMessage)
    }

    @Test
    fun `connection failure gives the same generic format suggestion for Chat`() {
        val option = ModelOption(
            id = "chat-model",
            apiFormatOverride = ModelApiFormat.ChatCompletions,
        )
        val state = ModelSettingsEditorState(
            ModelConfig(
                model = option.id,
                modelOptions = listOf(option),
                apiFormat = ModelApiFormat.Responses,
            ),
            initialDirty = false,
        )

        assertTrue(state.startTestConnection())
        state.finishConnectionStage(Result.failure(IllegalStateException("connection failed")))

        assertEquals(ModelApiFormat.Responses, state.form.apiFormat)
        assertTrue(state.testState?.formatFallbackSuggested == true)
        assertEquals("当前接口格式测试失败，请尝试其他接口格式。", state.testMessage)
    }

    @Test
    fun `connection test uses fetched capabilities without changing the editor draft`() {
        val original = ModelConfig(
            id = "config",
            name = "unsaved name",
            model = "old-model",
            modelOptions = listOf(ModelOption("old-model")),
        )
        val state = ModelSettingsEditorState(original, initialDirty = true)
        val fetched = original.copy(
            model = "new-model",
            modelOptions = listOf(ModelOption("new-model")),
        )

        assertTrue(state.startTestConnection())
        state.finishConnectionStage(Result.success(fetched))
        state.finishToolStage(Result.success(Unit))

        assertEquals(original, state.form)
        assertTrue(state.dirty)
        assertEquals("idle", state.saveState)
        assertEquals("1 个", state.testState?.steps?.get(1)?.detail)
        assertTrue(state.testState?.toolsSupported == true)
    }

    @Test
    fun `connection test does not turn a saved config into an unsaved edit`() {
        val original = ModelConfig(
            id = "config",
            model = "old-model",
            modelOptions = listOf(ModelOption("old-model")),
        )
        val state = ModelSettingsEditorState(original, initialDirty = false)

        assertTrue(state.startTestConnection())
        state.finishConnectionStage(
            Result.success(original.copy(modelOptions = listOf(ModelOption("remote-model")))),
        )
        state.finishToolStage(Result.failure(IllegalStateException("tools unsupported")))

        assertEquals(original, state.form)
        assertFalse(state.hasUnsavedChanges)
    }

    private fun config(
        id: String,
        name: String,
        model: String = "",
    ): ModelConfig = ModelConfig(
        id = id,
        name = name,
        provider = "custom",
        model = model,
    )
}
