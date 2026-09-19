package com.eleckoi.android.engine.story.variables.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface QuickJsRuntimeFactory {
    fun create(): QuickJsRuntime
}

internal interface QuickJsRuntime {
    suspend fun evaluate(script: String): String

    fun close()
}

internal class QuickJsRuntimeManager(
    private val runtimeFactory: QuickJsRuntimeFactory,
) {
    private val mutex = Mutex()

    suspend fun evaluate(script: String): String = mutex.withLock {
        val runtime = runtimeFactory.create()
        try {
            runtime.evaluate(script)
        } finally {
            runtime.closeQuietly()
        }
    }
}

internal object ProcessQuickJsRuntimeManager {
    val instance: QuickJsRuntimeManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuickJsRuntimeManager(
            runtimeFactory = QuickJsRuntimeFactory(::EmbeddedQuickJsRuntime),
        )
    }
}

private class EmbeddedQuickJsRuntime : QuickJsRuntime {
    private val quickJs = QuickJs.create(Dispatchers.Default).apply {
        evaluationTimeoutMillis = EvaluationTimeoutMillis
        memoryLimit = MemoryLimitBytes
        maxStackSize = MaxStackSizeBytes
    }

    override suspend fun evaluate(script: String): String {
        val result = quickJs.evaluate<Any?>(script, filename = ScriptFileName)
        return when (result) {
            null -> "null"
            is String -> result
            else -> result.toString()
        }
    }

    override fun close() {
        quickJs.close()
    }

    private companion object {
        const val EvaluationTimeoutMillis = 30_000L
        const val MemoryLimitBytes = 128L * 1024L * 1024L
        const val MaxStackSizeBytes = 4L * 1024L * 1024L
        const val ScriptFileName = "eleckoi-variable-runtime.js"
    }
}

private fun QuickJsRuntime.closeQuietly() {
    runCatching { close() }
}
