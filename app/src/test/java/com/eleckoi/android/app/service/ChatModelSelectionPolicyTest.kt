package com.eleckoi.android.app.service

import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModelSelectionPolicyTest {
    private val configured = ModelConfig(id = "configured", model = "gpt-configured")
    private val blank = ModelConfig(id = "blank")
    private val collection = ModelConfigCollection(
        activeConfigId = blank.id,
        activeConfig = blank,
        configs = listOf(blank, configured),
    )

    @Test
    fun `validated fills a blank selected model from its config`() {
        val result = ChatModelSelectionPolicy.validated(
            ChatModelSelection(
                capability = "legacy",
                configId = configured.id,
                model = "",
            ),
            collection,
        )

        assertEquals(
            ChatModelSelection(
                capability = "chat",
                configId = configured.id,
                model = configured.model,
            ),
            result,
        )
    }

    @Test
    fun `validated rejects missing configs and configs without a model`() {
        assertNull(
            ChatModelSelectionPolicy.validated(
                ChatModelSelection(configId = "missing", model = "gpt"),
                collection,
            ),
        )
        assertNull(
            ChatModelSelectionPolicy.validated(
                ChatModelSelection(configId = blank.id),
                collection,
            ),
        )
    }

    @Test
    fun `bootstrap prefers the first config with a model`() {
        assertEquals(
            ChatModelSelection(
                capability = "chat",
                configId = configured.id,
                model = configured.model,
            ),
            ChatModelSelectionPolicy.bootstrap(collection),
        )
    }
}
