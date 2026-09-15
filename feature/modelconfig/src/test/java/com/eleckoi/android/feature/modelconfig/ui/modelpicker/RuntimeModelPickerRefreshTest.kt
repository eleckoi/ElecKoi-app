package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.feature.modelconfig.ui.modelOptionsKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeModelPickerRefreshTest {
    @Test
    fun `freshly read models replace the visible catalog without changing saved selection`() {
        val persisted = ModelConfig(
            id = "config",
            provider = "custom",
            baseUrl = "https://example.com/v1",
            apiKey = "secret",
            model = "old-model",
            modelOptions = listOf(ModelOption("old-model")),
        )
        val refreshed = persisted.copy(
            model = "new-model",
            modelOptions = listOf(ModelOption("new-model"), ModelOption("added-model")),
        )

        val result = applyRefreshedModelCatalogs(
            configs = listOf(persisted),
            catalogs = mapOf(
                persisted.id to RefreshedModelCatalog(modelOptionsKey(persisted), refreshed),
            ),
        ).single()

        assertEquals("old-model", result.model)
        assertEquals(listOf("new-model", "added-model"), result.modelOptions.map { it.id })
    }

    @Test
    fun `changing connection fields invalidates a previously read catalog`() {
        val persisted = ModelConfig(
            id = "config",
            provider = "custom",
            baseUrl = "https://old.example.com/v1",
            apiKey = "secret",
            modelOptions = listOf(ModelOption("old-model")),
        )
        val refreshed = persisted.copy(modelOptions = listOf(ModelOption("fresh-model")))
        val changed = persisted.copy(baseUrl = "https://new.example.com/v1")

        val result = applyRefreshedModelCatalogs(
            configs = listOf(changed),
            catalogs = mapOf(
                persisted.id to RefreshedModelCatalog(modelOptionsKey(persisted), refreshed),
            ),
        ).single()

        assertEquals(listOf("old-model"), result.modelOptions.map { it.id })
    }
}
