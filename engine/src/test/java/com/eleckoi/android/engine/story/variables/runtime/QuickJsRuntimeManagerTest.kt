package com.eleckoi.android.engine.story.variables.runtime

import com.eleckoi.android.engine.story.variables.runtime.script.VariableRuntimeScripts
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsRuntimeManagerTest {
    @Test
    fun `embedded quickjs evaluates modern syntax and asynchronous results`() = runBlocking {
        val result = ProcessQuickJsRuntimeManager.instance.evaluate(
            """
            const values = [1, 2, 3].map(value => value * 2);
            await Promise.resolve(JSON.stringify({ values, total: values.reduce((sum, value) => sum + value, 0) }));
            """.trimIndent(),
        )

        assertEquals("{\"values\":[2,4,6],\"total\":12}", result)
    }

    @Test
    fun `embedded quickjs renders the production ejs runtime`() = runBlocking {
        val input = JSONObject()
            .put("state", JSONObject().put("name", "QuickJS"))
            .put("messages", emptyList<Any>())
            .put(
                "sources",
                listOf(
                    JSONObject()
                        .put("id", "root")
                        .put("controller_id", "controller")
                        .put("title", "root")
                        .put("path", "root")
                        .put("content", "Hello <%= getvar('name') %>"),
                ),
            )
            .put("target_ids", listOf("root"))
        val result = ProcessQuickJsRuntimeManager.instance.evaluate(
            VariableRuntimeScripts.helpers + "\n" + VariableRuntimeScripts.ejsRuntime +
                "\nawait __eleckoiRenderTemplates($input);",
        )

        assertEquals("Hello QuickJS", JSONObject(result).getJSONObject("rendered").getString("root"))
    }

    @Test
    fun `variable preview ejs and chat validation are serialized`() = runBlocking {
        val createdCount = AtomicInteger()
        val closedCount = AtomicInteger()
        val activeEvaluations = AtomicInteger()
        val maxConcurrentEvaluations = AtomicInteger()
        val manager = QuickJsRuntimeManager(
            runtimeFactory = QuickJsRuntimeFactory {
                RecordingRuntime(
                    closedCount = closedCount,
                    activeEvaluations = activeEvaluations,
                    maxConcurrentEvaluations = maxConcurrentEvaluations,
                ).also { createdCount.incrementAndGet() }
            },
        )
        val scripts = List(20) { index ->
            listOf(
                "variable-preview-$index",
                "ejs-render-$index",
                "chat-state-validation-$index",
            )
        }.flatten()

        val results = scripts.map { script ->
            async(Dispatchers.Default) { manager.evaluate(script) }
        }.awaitAll()

        assertEquals(scripts.toSet(), results.toSet())
        assertEquals(scripts.size, createdCount.get())
        assertEquals(scripts.size, closedCount.get())
        assertEquals(1, maxConcurrentEvaluations.get())
    }

    @Test
    fun `runtime is closed after script failure and next evaluation can continue`() = runBlocking {
        val runtimes = ArrayDeque(
            listOf(
                RecordingRuntime(failure = IllegalStateException("broken script")),
                RecordingRuntime(),
            ),
        )
        val manager = QuickJsRuntimeManager(
            runtimeFactory = QuickJsRuntimeFactory { runtimes.removeFirst() },
        )

        val failure = runCatching { manager.evaluate("broken") }.exceptionOrNull()
        val result = manager.evaluate("healthy")

        assertEquals("broken script", failure?.message)
        assertEquals("healthy", result)
        assertTrue(runtimes.isEmpty())
    }

    private class RecordingRuntime(
        private val closedCount: AtomicInteger = AtomicInteger(),
        private val activeEvaluations: AtomicInteger = AtomicInteger(),
        private val maxConcurrentEvaluations: AtomicInteger = AtomicInteger(),
        private val failure: Throwable? = null,
    ) : QuickJsRuntime {
        override suspend fun evaluate(script: String): String {
            val active = activeEvaluations.incrementAndGet()
            maxConcurrentEvaluations.updateMaximum(active)
            return try {
                failure?.let { throw it }
                delay(2)
                script
            } finally {
                activeEvaluations.decrementAndGet()
            }
        }

        override fun close() {
            closedCount.incrementAndGet()
        }
    }
}

private fun AtomicInteger.updateMaximum(candidate: Int) {
    while (true) {
        val current = get()
        if (candidate <= current || compareAndSet(current, candidate)) return
    }
}
