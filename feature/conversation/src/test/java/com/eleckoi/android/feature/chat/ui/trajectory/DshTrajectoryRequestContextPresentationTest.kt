package com.eleckoi.android.feature.chat.ui.trajectory

import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextItem
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextKind
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshTrajectoryRequestContextPresentationTest {
    @Test
    fun `long tool result is truncated until explicitly expanded`() {
        val content = (1..20).joinToString("\n") { "第 $it 条很长的设定结果" }

        val presentation = requestContextPresentation(item(DshRequestContextKind.Tool, content))

        assertTrue(presentation.collapsible)
        assertTrue(presentation.preview.endsWith("…"))
        assertTrue(presentation.preview.length < content.length)
    }

    @Test
    fun `ordinary prompt stays readable without an unnecessary expander`() {
        val presentation = requestContextPresentation(
            item(DshRequestContextKind.Prompt, "角色设定正文".repeat(20)),
        )

        assertFalse(presentation.collapsible)
    }

    private fun item(kind: DshRequestContextKind, content: String) = DshRequestContextItem(
        order = 1,
        messageId = "message",
        role = DshRequestContextRole.User,
        kind = kind,
        title = "测试",
        source = "设定插入点 1",
        anchor = "beforeHistory",
        content = content,
    )
}
