package com.eleckoi.android.feature.chat.ui.roleplay.web.model

import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayRendererFailureTest {
    @Test
    fun copiedDetailsKeepTheCompleteStructuredDiagnostic() {
        val failure = RoleplayRendererFailure(
            kind = RoleplayRendererFailureKind.JavaScript,
            message = "synthetic renderer failure",
            context = linkedMapOf(
                "执行阶段" to "synthetic-phase",
                "事务 ID" to "7",
            ),
            stackTrace = "Error: synthetic renderer failure\n    at render (example.js:1:1)",
            rawPayload = "{\"type\":\"rendererError\",\"transactionId\":7}",
        )

        val details = failure.copyDetails()

        assertTrue(details.contains("错误代码：javascript_error"))
        assertTrue(details.contains("错误信息：synthetic renderer failure"))
        assertTrue(details.contains("执行阶段：synthetic-phase"))
        assertTrue(details.contains("事务 ID：7"))
        assertTrue(details.contains("at render (example.js:1:1)"))
        assertTrue(details.contains("\"transactionId\":7"))
    }
}
