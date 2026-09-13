package com.eleckoi.android.feature.chat.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Resolves one generation's prompt macros against a mutable variable snapshot. */
internal class PromptMacroResolver(
    stateJson: String,
    private val random: () -> Double = Math::random,
    private val now: LocalDateTime = LocalDateTime.now(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val state = runCatching { JSONObject(stateJson.ifBlank { "{}" }) }
        .getOrDefault(JSONObject())

    val stateJson: String
        get() = state.toString(2)

    /**
     * Executes only state-changing macros and leaves value macros for the second pass. Calling
     * this for every prompt fragment first makes getvar independent of preset entry placement.
     */
    fun applyMutations(text: String): String = replaceMacros(text) { source, macro ->
        when (macro.name) {
            "setvar" -> {
                val path = macro.parts.firstOrNull().orEmpty().trim()
                if (path.isNotEmpty() && macro.parts.size > 1) {
                    writePath(path, parseValue(macro.parts.drop(1).joinToString("::")))
                }
                ""
            }
            "addvar" -> {
                val path = macro.parts.firstOrNull().orEmpty().trim()
                if (path.isNotEmpty() && macro.parts.size > 1) {
                    val addition = macro.parts.drop(1).joinToString("::").trim()
                    val current = readPath(path)
                    val currentNumber = current.asMacroNumber(default = 0.0)
                    val additionNumber = addition.toDoubleOrNull()
                    val next = if (currentNumber != null && additionNumber != null) {
                        normalizedNumber(currentNumber + additionNumber)
                    } else {
                        stringify(current) + addition
                    }
                    writePath(path, next)
                }
                ""
            }
            "incvar", "decvar" -> {
                val path = macro.parts.joinToString("::").trim()
                if (path.isEmpty()) return@replaceMacros ""
                val direction = if (macro.name.startsWith("dec")) -1.0 else 1.0
                val next = normalizedNumber((readPath(path).asMacroNumber(default = 0.0) ?: 0.0) + direction)
                writePath(path, next)
                stringify(next)
            }
            else -> source
        }
    }

    /** Replaces value macros after every prompt fragment has completed its mutation pass. */
    fun resolveValues(text: String): String = replaceMacros(text) { source, macro ->
        when (macro.name) {
            "setvar", "addvar", "incvar", "decvar" -> ""
            "getvar" -> stringify(readPath(macro.parts.joinToString("::").trim()))
            "random" -> randomChoice(macro.parts)
            "roll" -> roll(macro.parts.joinToString("::").trim())
            "newline" -> "\n"
            "noop", "trim" -> ""
            "time" -> now.format(
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
            )
            "isotime" -> now.format(TimeFormat)
            "date" -> now.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale),
            )
            "isodate" -> now.format(DateFormat)
            "weekday" -> now.format(DateTimeFormatter.ofPattern("EEEE", locale))
            else -> source
        }
    }

    private fun replaceMacros(text: String, replacement: (String, PromptMacro) -> String): String {
        if (text.isEmpty()) return text
        val output = StringBuilder(text.length)
        var sourceIndex = 0
        while (sourceIndex < text.length) {
            val macroStart = text.indexOf("{{", sourceIndex)
            if (macroStart < 0) {
                output.append(text, sourceIndex, text.length)
                break
            }
            val macroEnd = findMacroEnd(text, macroStart + 2)
            if (macroEnd < 0) {
                output.append(text, sourceIndex, text.length)
                break
            }
            output.append(text, sourceIndex, macroStart)
            val source = text.substring(macroStart, macroEnd + 2)
            val body = text.substring(macroStart + 2, macroEnd).trim()
            output.append(replacement(source, parseMacro(body)))
            sourceIndex = macroEnd + 2
        }
        return output.toString()
    }

    private fun randomChoice(rawParts: List<String>): String {
        val choices = if (rawParts.size > 1) rawParts else rawParts.flatMap(::splitChoices)
        val normalizedChoices = choices
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (normalizedChoices.isEmpty()) return ""
        val sample = random().takeIf(Double::isFinite)?.coerceIn(0.0, 0.999999999) ?: 0.0
        return normalizedChoices[
            (sample * normalizedChoices.size).toInt().coerceIn(0, normalizedChoices.lastIndex)
        ]
    }

    private fun roll(rawExpression: String): String {
        val expression = if (rawExpression.matches(Regex("\\d+"))) "1d$rawExpression" else rawExpression
        val match = DicePattern.matchEntire(expression) ?: return ""
        val count = (match.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 100)
        val sides = (match.groupValues[2].toIntOrNull() ?: 1).coerceIn(1, 100_000)
        var total = match.groupValues[3].toIntOrNull() ?: 0
        repeat(count) {
            val sample = random().takeIf(Double::isFinite)?.coerceIn(0.0, 0.999999999) ?: 0.0
            total += 1 + (sample * sides).toInt()
        }
        return total.toString()
    }

    private fun readPath(path: String): Any? {
        val segments = pathSegments(path)
        var current: Any? = state
        segments.forEach { segment ->
            current = (current as? JSONObject)?.opt(segment)
            if (current == null || current === JSONObject.NULL) return null
        }
        return current
    }

    private fun writePath(path: String, value: Any?) {
        val segments = pathSegments(path)
        if (segments.isEmpty()) return
        var current = state
        segments.dropLast(1).forEach { segment ->
            val next = current.optJSONObject(segment) ?: JSONObject().also { current.put(segment, it) }
            current = next
        }
        current.put(segments.last(), value ?: JSONObject.NULL)
    }

    private companion object {
        val DicePattern = Regex("""(?i)(\d+)?d(\d+)([+-]\d+)?""")
        val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val ForbiddenPathSegments = setOf("__proto__", "prototype", "constructor")

        fun findMacroEnd(text: String, bodyStart: Int): Int {
            var objectDepth = 0
            var quote: Char? = null
            var escaped = false
            var index = bodyStart
            while (index < text.length) {
                val character = text[index]
                if (quote != null) {
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == quote -> quote = null
                    }
                    index += 1
                    continue
                }
                when (character) {
                    '\'', '"' -> quote = character
                    '{' -> objectDepth += 1
                    '}' -> when {
                        objectDepth > 0 -> objectDepth -= 1
                        index + 1 < text.length && text[index + 1] == '}' -> return index
                    }
                }
                index += 1
            }
            return -1
        }

        fun parseMacro(body: String): PromptMacro {
            val parts = body.split("::").toMutableList()
            val first = parts.removeFirstOrNull().orEmpty().trim()
            val inline = Regex("""^([^\s:]+)[\s:]+(.+)$""").matchEntire(first)
            if (inline != null) parts.add(0, inline.groupValues[2])
            return PromptMacro(
                name = (inline?.groupValues?.get(1) ?: first).lowercase(),
                parts = parts,
            )
        }

        fun pathSegments(path: String): List<String> = path
            .replace(Regex("""\[\s*([^]]+)\s*]"""), ".$1")
            .split('.')
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotEmpty() && it !in ForbiddenPathSegments }

        fun parseValue(raw: String): Any? {
            val value = raw.trim()
            if (value.isEmpty()) return ""
            val looksLikeJson = value.equals("true", ignoreCase = true) ||
                value.equals("false", ignoreCase = true) ||
                value.equals("null", ignoreCase = true) ||
                value.matches(Regex("""-?(?:\d+\.?\d*|\.\d+)""")) ||
                (value.startsWith('{') && value.endsWith('}')) ||
                (value.startsWith('[') && value.endsWith(']'))
            if (!looksLikeJson) return value
            return runCatching { JSONTokener(value).nextValue() }.getOrDefault(value)
        }

        fun Any?.asMacroNumber(default: Double? = null): Double? = when (this) {
            null, JSONObject.NULL, "" -> default
            is Number -> toDouble().takeIf(Double::isFinite)
            else -> toString().toDoubleOrNull()?.takeIf(Double::isFinite)
        }

        fun normalizedNumber(value: Double): Number =
            if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                value.toLong()
            } else {
                value
            }

        fun stringify(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is String -> value
            is Number, is Boolean -> value.toString()
            is JSONObject, is JSONArray -> value.toString()
            else -> value.toString()
        }

        fun splitChoices(value: String): List<String> {
            if (',' !in value) return listOf(value)
            val escapedComma = "\u0000"
            return value.replace("\\,", escapedComma)
                .split(',')
                .map { it.replace(escapedComma, ",") }
        }
    }
}

private data class PromptMacro(
    val name: String,
    val parts: List<String>,
)
