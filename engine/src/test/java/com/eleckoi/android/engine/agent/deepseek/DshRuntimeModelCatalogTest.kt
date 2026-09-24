package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.config.withFetchedModelsForCapabilityLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DshRuntimeModelCatalogTest {
    @Test
    fun `new unsaved configuration can enter capability catalog after reading models`() {
        val draft = ModelConfig(provider = "custom")
            .withFetchedModelsForCapabilityLookup(listOf(ModelOption(id = "model-a")))

        val catalog = DshRuntimeModelCatalog.merge(emptyList(), listOf(draft))

        assertTrue(draft.id.startsWith("config-"))
        assertEquals("model-a", catalog.single().model)
        assertEquals(draft.id, catalog.single().id)
        assertEquals(draft.id, draft.withFetchedModelsForCapabilityLookup(draft.modelOptions).id)
    }

    @Test
    fun `keeps every stored route and replaces the active configuration by id`() {
        val stored = listOf(
            ModelConfig(id = "root", provider = "openai", model = "stored-root"),
            ModelConfig(id = "other", provider = "custom", model = "other-model"),
        )
        val selected = ModelConfig(id = "root", provider = "openai", model = "active-root")

        val merged = DshRuntimeModelCatalog.merge(stored, listOf(selected))

        assertEquals(listOf("root", "other"), merged.map(ModelConfig::id))
        assertEquals("active-root", merged.first().model)
        assertEquals("other-model", merged.last().model)
    }

    @Test
    fun `drops incomplete stored rows but rejects an incomplete active selection`() {
        val stored = listOf(
            ModelConfig(id = "", model = "orphan"),
            ModelConfig(id = "blank-model", model = ""),
            ModelConfig(id = "valid", model = "model"),
        )

        assertEquals(
            listOf("valid"),
            DshRuntimeModelCatalog.merge(stored, emptyList()).map(ModelConfig::id),
        )
        assertThrows(IllegalArgumentException::class.java) {
            DshRuntimeModelCatalog.merge(stored, listOf(ModelConfig(id = "active", model = "")))
        }
    }
}
