package com.eleckoi.android.foundation.storage.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexRuleChunkReaderTest {
    @Test
    fun `large regex text is reconstructed from bounded cursor rows`() {
        val source = RegexRuleFields(
            name = "large rule",
            pattern = "p".repeat(RegexRuleCursorChunkCharacters + 19),
            replacement = "r".repeat(RegexRuleCursorChunkCharacters * 3 + 7),
            targetsJson = "[\"display\"]",
            enabled = true,
            displayOnly = true,
            promptOnly = false,
            runOnEdit = true,
            sortIndex = 4,
        )
        val metadata = source.metadata()
        var reads = 0

        val actual = readRegexRuleFields(metadata) { start, length ->
            reads += 1
            assertEquals(RegexRuleCursorChunkCharacters, length)
            source.chunk(start, length)
        }

        assertEquals(source, actual)
        assertEquals(4, reads)
    }

    @Test
    fun `empty regex text does not issue a content query`() {
        val source = RegexRuleFields("", "", "", "", false, false, false, false, 0)

        val actual = readRegexRuleFields(source.metadata()) { _, _ ->
            throw AssertionError("empty content must not be queried")
        }

        assertEquals(source, actual)
    }

    @Test
    fun `each projected row stays bounded when every text field is large`() {
        val source = RegexRuleFields(
            name = "n".repeat(RegexRuleCursorChunkCharacters * 2),
            pattern = "p".repeat(RegexRuleCursorChunkCharacters * 2),
            replacement = "r".repeat(RegexRuleCursorChunkCharacters * 2),
            targetsJson = "t".repeat(RegexRuleCursorChunkCharacters * 2),
            enabled = true,
            displayOnly = false,
            promptOnly = true,
            runOnEdit = false,
            sortIndex = 1,
        )

        readRegexRuleFields(source.metadata()) { start, length ->
            val chunk = source.chunk(start, length)
            assertTrue(chunk.nameChunk.length <= RegexRuleCursorChunkCharacters)
            assertTrue(chunk.patternChunk.length <= RegexRuleCursorChunkCharacters)
            assertTrue(chunk.replacementChunk.length <= RegexRuleCursorChunkCharacters)
            assertTrue(chunk.targetsJsonChunk.length <= RegexRuleCursorChunkCharacters)
            chunk
        }
    }

    private fun RegexRuleFields.metadata() = RegexRuleReadMetadata(
        id = "rule",
        enabled = enabled,
        displayOnly = displayOnly,
        promptOnly = promptOnly,
        runOnEdit = runOnEdit,
        sortIndex = sortIndex,
        nameLength = name.length,
        patternLength = pattern.length,
        replacementLength = replacement.length,
        targetsJsonLength = targetsJson.length,
    )

    private fun RegexRuleFields.chunk(start: Int, length: Int): RegexRuleTextChunk {
        val zeroBasedStart = start - 1
        return RegexRuleTextChunk(
            nameChunk = name.slice(zeroBasedStart, length),
            patternChunk = pattern.slice(zeroBasedStart, length),
            replacementChunk = replacement.slice(zeroBasedStart, length),
            targetsJsonChunk = targetsJson.slice(zeroBasedStart, length),
        )
    }

    private fun String.slice(start: Int, length: Int): String {
        if (start >= this.length) return ""
        return substring(start, (start + length).coerceAtMost(this.length))
    }
}
