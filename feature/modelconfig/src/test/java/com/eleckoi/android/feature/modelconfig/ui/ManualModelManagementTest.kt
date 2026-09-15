package com.eleckoi.android.feature.modelconfig.ui

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.feature.modelconfig.ui.settings.addAndSelectModel
import com.eleckoi.android.feature.modelconfig.ui.settings.modelPickerItems
import com.eleckoi.android.feature.modelconfig.ui.settings.removeManualModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ManualModelManagementTest {
    private val providerModel = ModelOption("provider-model", temperature = 0.6)
    private val manualModel = ModelOption("manual-model", isUserAdded = true)

    @Test
    fun `provider models cannot be removed or converted to manual by selecting them`() {
        val original = ModelConfig(model = providerModel.id, modelOptions = listOf(providerModel))
        val selected = original.addAndSelectModel(providerModel.id)

        assertFalse(selected.modelOptions.single().isUserAdded)
        assertEquals(original, selected.removeManualModel(providerModel.id))
    }

    @Test
    fun `deleting selected manual model selects a remaining model`() {
        val original = ModelConfig(
            model = manualModel.id,
            modelOptions = listOf(manualModel, providerModel),
        )

        val removed = original.removeManualModel(manualModel.id)

        assertEquals(providerModel.id, removed.model)
        assertEquals(listOf(providerModel), modelPickerItems(removed))
    }

    @Test
    fun `deleting inactive manual model keeps current selection and settings`() {
        val original = ModelConfig(
            model = providerModel.id,
            modelOptions = listOf(manualModel, providerModel),
        )
        val removed = original.removeManualModel(manualModel.id)

        assertEquals(providerModel.id, removed.model)
        assertEquals(listOf(providerModel), removed.modelOptions)
    }

    @Test
    fun `deleting last manual model leaves an empty list without selected-model resurrection`() {
        val original = ModelConfig().addAndSelectModel("private-only")
        val removed = original.removeManualModel("private-only")

        assertEquals("", removed.model)
        assertEquals(emptyList<ModelOption>(), modelPickerItems(removed))
    }

    @Test
    fun `adding to a config with only a current model retains that original option`() {
        val added = ModelConfig(model = "original").addAndSelectModel("manual")

        assertEquals(listOf("manual", "original"), added.modelOptions.map { it.id })
        assertFalse(added.modelOptions.last().isUserAdded)
    }
}
