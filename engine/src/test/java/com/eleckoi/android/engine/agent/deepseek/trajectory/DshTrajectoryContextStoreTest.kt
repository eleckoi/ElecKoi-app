package com.eleckoi.android.engine.agent.deepseek.trajectory

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DshTrajectoryContextStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stores stable definitions once and activates them for each exact turn`() {
        var now = 1_000L
        val store = DshTrajectoryContextStore(temporaryFolder.root) { now }
        val persistent = AgentContextInjection(
            id = "cache",
            anchor = AgentContextAnchor.BeforeHistory,
            role = AgentContextRole.User,
            activation = AgentContextActivation.Immediate,
            content = "稳定设定",
            traceTitle = "缓存设定 · 世界观",
            traceSource = "设定插入点 1",
        )
        val conditional = AgentContextInjection(
            id = "conditional",
            anchor = AgentContextAnchor.AfterToolFlow,
            role = AgentContextRole.System,
            activation = AgentContextActivation.AfterToolCall("read"),
            content = "仅工具后出现",
        )

        store.recordTurn("session", "session:1", listOf(persistent, conditional))
        now = 2_000L
        store.recordTurn("session", "session:2", listOf(persistent, conditional))

        val activations = store.read("session")
        val rows = File(temporaryFolder.root, "session.jsonl").readLines()
        assertEquals(listOf(1, 2), activations.map(DshTrajectoryContextActivation::turn))
        assertEquals(listOf("缓存设定 · 世界观"), activations.first().entries.map { it.title })
        assertEquals(1, rows.count { it.contains("context/definition") })
        assertEquals(2, rows.count { it.contains("context/activation") })
        assertFalse(rows.any { it.contains("仅工具后出现") })
    }
}
