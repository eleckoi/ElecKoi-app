package com.eleckoi.android.engine.agent.deepseek.trajectory

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DshRequestContextStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `restores ordered request snapshots from deduplicated definitions and skips bad rows`() {
        File(temporaryFolder.root, "session_one.jsonl").writeText(
            sequenceOf(
                "not-json",
                definition("system-key", "system", "system", "系统提示词", "", "SYSTEM"),
                definition("tool-key", "user", "tool", "工具结果", "", "TOOL RESULT"),
                request(10, listOf(2 to "tool-key", 1 to "system-key")),
                """{"type":"definition","key":"broken","role":"nope","kind":"tool","content":"ignored"}""",
                request(11, listOf(1 to "system-key", 2 to "missing")),
                request(10, listOf(1 to "system-key")),
            ).joinToString("\n", postfix = "\n"),
        )

        val snapshots = DshRequestContextStore(temporaryFolder.root).read("session:one")

        assertEquals(listOf(10L, 11L), snapshots.map(DshRequestContextSnapshot::requestSeq))
        assertEquals(listOf("SYSTEM"), snapshots[0].items.map(DshRequestContextItem::content))
        assertEquals(listOf("SYSTEM"), snapshots[1].items.map(DshRequestContextItem::content))
        assertEquals(DshRequestContextRole.System, snapshots[0].items.single().role)
        assertEquals(DshRequestContextKind.System, snapshots[0].items.single().kind)
    }

    private fun definition(
        key: String,
        role: String,
        kind: String,
        title: String,
        anchor: String,
        content: String,
    ): String =
        """{"type":"definition","key":"$key","messageId":"$key-message","role":"$role","kind":"$kind","title":"$title","source":"测试","anchor":"$anchor","content":"$content"}"""

    private fun request(seq: Int, items: List<Pair<Int, String>>): String =
        """{"type":"request","requestSeq":$seq,"turn":1,"step":$seq,"timeMillis":$seq,"items":[${
            items.joinToString(",") { (order, key) -> """{"order":$order,"key":"$key"}""" }
        }]}"""
}
