package com.eleckoi.android.feature.modelconfig.ui.settings

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSettingsEditorReadOnlyTest {
    @Test
    fun `fetching models and testing connection do not create unsaved changes`() {
        val original = ModelConfig(model = "example-model")
        val editor = ModelSettingsEditorState(original, initialDirty = false)
        val fetched = original.copy(modelOptions = listOf(ModelOption("example-model")))

        assertTrue(editor.startFetchModels())
        editor.finishFetchModels(Result.success(fetched))
        assertFalse(editor.hasUnsavedChanges)

        assertTrue(editor.startTestConnection())
        editor.finishConnectionStage(Result.success(fetched))
        editor.finishToolStage(Result.success(Unit))
        assertFalse(editor.hasUnsavedChanges)

        editor.update(editor.form.copy(model = "another-model"))
        assertTrue(editor.hasUnsavedChanges)
        editor.update(fetched)
        assertFalse(editor.hasUnsavedChanges)
    }

    @Test
    fun `reading models in a new untouched draft does not prompt to save`() {
        val editor = ModelSettingsEditorState(
            ModelConfig(),
            initialDirty = false,
            initialNewDraft = true,
        )

        editor.finishFetchModels(Result.success(ModelConfig(
            modelOptions = listOf(ModelOption("available-model")),
        )))

        assertFalse(editor.hasUnsavedChanges)
    }

    @Test
    fun `explicitly selecting the fetched first model can be saved`() {
        val editor = ModelSettingsEditorState(ModelConfig(), initialDirty = false)
        val fetched = ModelConfig(
            model = "available-model",
            modelOptions = listOf(ModelOption("available-model")),
        )

        editor.finishFetchModels(Result.success(fetched))
        assertFalse(editor.hasUnsavedChanges)

        editor.selectModel(fetched)
        assertTrue(editor.hasUnsavedChanges)
    }
}
