package com.eleckoi.android.engine.workspace.runtime.service

import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeStream
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeTarget
import com.eleckoi.android.engine.workspace.runtime.process.SupervisedProcessEvent

/** Debug-only, bounded stderr projection for the isolated DSH child process. */
internal class RuntimeProcessDiagnosticLogger(
    private val enabled: Boolean,
    private val sink: (String) -> Unit,
) {
    private var deepSeekCommandId: String? = null
    private var emittedLines = 0
    private var emittedChars = 0

    @Synchronized
    fun record(event: SupervisedProcessEvent) {
        if (!enabled) return
        when (event) {
            is SupervisedProcessEvent.Started -> if (event.target == LocalRuntimeTarget.DeepSeekHarness) {
                deepSeekCommandId = event.commandId
                emittedLines = 0
                emittedChars = 0
            }
            is SupervisedProcessEvent.Output -> if (
                event.commandId == deepSeekCommandId &&
                event.stream == LocalRuntimeStream.Stderr &&
                event.line.isNotBlank() &&
                emittedLines < MaxLines &&
                emittedChars < MaxChars
            ) {
                val sanitized = sanitizeRuntimeDiagnosticLine(event.line)
                    .take((MaxChars - emittedChars).coerceAtMost(MaxLineChars))
                if (sanitized.isNotBlank()) {
                    sink(sanitized)
                    emittedLines += 1
                    emittedChars += sanitized.length
                }
            }
            is SupervisedProcessEvent.Exited -> if (event.commandId == deepSeekCommandId) {
                deepSeekCommandId = null
            }
            is SupervisedProcessEvent.Failed -> Unit
        }
    }

    private companion object {
        const val MaxLines = 64
        const val MaxLineChars = 4 * 1024
        const val MaxChars = 32 * 1024
    }
}

internal fun sanitizeRuntimeDiagnosticLine(value: String): String = value
    .replace(BearerSecret, "$1<redacted>")
    .replace(NamedSecret, "$1$2<redacted>")
    .replace(LoopbackScope, "$1<scope>$2")
    .replace(UrlQuery, "$1?<redacted>")

private val BearerSecret = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+")
private val NamedSecret = Regex(
    "(?i)(authorization|api[-_ ]?key|token|password|secret)(\\s*[=:]\\s*)[^\\s,;]+",
)
private val LoopbackScope = Regex("(http://127\\.0\\.0\\.1:\\d{1,5}/)[A-Za-z0-9_-]{24,128}(/)")
private val UrlQuery = Regex("(https?://[^?\\s]+)\\?[^\\s]+")
