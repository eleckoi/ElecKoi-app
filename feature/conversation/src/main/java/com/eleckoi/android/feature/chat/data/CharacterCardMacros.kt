package com.eleckoi.android.feature.chat.data

internal data class CharacterCardMacroValues(
    val userName: String,
    val characterName: String,
)

private val CharacterCardMacroPattern = Regex(
    pattern = """\{\{\s*(user|char)\s*\}\}""",
    option = RegexOption.IGNORE_CASE,
)

internal fun String.resolveCharacterCardMacros(values: CharacterCardMacroValues): String {
    if (isEmpty()) return this
    val matches = CharacterCardMacroPattern.findAll(this).toList()
    if (matches.isEmpty()) return com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline.expand(this)
    val resolved = StringBuilder(length)
    var sourceIndex = 0
    matches.forEach { match ->
        resolved.append(this, sourceIndex, match.range.first)
        val replacement = when (match.groupValues[1].lowercase()) {
            "user" -> values.userName
            "char" -> values.characterName
            else -> match.value
        }
        if (replacement.firstOrNull()?.isCjkTextBoundary() == true) {
            resolved.trimCjkBoundarySpacing()
        }
        resolved.append(replacement)
        sourceIndex = match.range.last + 1
        if (replacement.lastOrNull()?.isCjkTextBoundary() == true) {
            val nextTextIndex = nextNonHorizontalSpace(sourceIndex)
            if (nextTextIndex > sourceIndex && getOrNull(nextTextIndex)?.isCjkTextBoundary() == true) {
                sourceIndex = nextTextIndex
            }
        }
    }
    resolved.append(this, sourceIndex, length)
    return com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline.expand(resolved.toString())
}

private fun StringBuilder.trimCjkBoundarySpacing() {
    var boundary = length
    while (boundary > 0 && this[boundary - 1].isHorizontalMacroSpacing()) boundary--
    if (boundary < length && boundary > 0 && this[boundary - 1].isCjkTextBoundary()) {
        setLength(boundary)
    }
}

private fun String.nextNonHorizontalSpace(startIndex: Int): Int {
    var index = startIndex
    while (index < length && this[index].isHorizontalMacroSpacing()) index++
    return index
}

private fun Char.isHorizontalMacroSpacing(): Boolean =
    this == ' ' || this == '\t' || this == '\u00A0' || this == '\u3000'

private fun Char.isCjkTextBoundary(): Boolean = when (this) {
    in '\u3000'..'\u30FF',
    in '\u3400'..'\u4DBF',
    in '\u4E00'..'\u9FFF',
    in '\uAC00'..'\uD7AF',
    in '\uF900'..'\uFAFF',
    in '\uFF01'..'\uFF65',
    -> true
    else -> false
}
