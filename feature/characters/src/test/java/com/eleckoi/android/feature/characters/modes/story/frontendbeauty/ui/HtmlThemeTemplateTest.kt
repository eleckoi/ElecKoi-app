package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlThemeTemplateTest {
    @Test
    fun `default theme exercises the interactive chat api`() {
        val template = templateFile().readText(Charsets.UTF_8)

        listOf(
            "api.messages.list()",
            "api.messages.regenerate(id)",
            "api.messages.edit(editingMessage.id, text)",
            "api.messages.editAndRegenerate(editingMessage.id, text)",
            "api.chat.send(text)",
            "api.chat.stopGeneration()",
            "api.events.on(\"message.delta\"",
            "api.events.on(\"messages.changed\"",
            "api.events.on(\"generation.completed\"",
        ).forEach { apiUse -> assertTrue("Missing $apiUse", template.contains(apiUse)) }
        assertTrue(template.contains("if (api) {"))
        assertTrue(template.contains("requestAnimationFrame(flushStreamingMessages)"))
        assertTrue(template.contains("updateMessageNode(article, message)"))
        assertTrue(template.contains("replaceWith(renderMessageActions(message))"))
        assertFalse(template.contains("color-mix("))
    }

    private fun templateFile(): File = File(
        requireNotNull(System.getProperty("user.dir")),
        "src/main/assets/frontend/templates/chat-theme.html",
    )
}
