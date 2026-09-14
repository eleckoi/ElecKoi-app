package com.eleckoi.android.engine.immersive.project

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontendHtmlProjectFilesTest {
    @Test
    fun `creates and reads a single file html project`() = withTemporaryDirectory { directory ->
        val imported = FrontendHtmlProjectFiles.create("<h1>Theme</h1>", directory)

        assertEquals("index.html", imported.entryFile)
        assertEquals(listOf("index.html"), imported.files)
        assertEquals("<h1>Theme</h1>", FrontendHtmlProjectFiles.readEntry(directory, imported.entryFile))
    }

    @Test
    fun `replaces only the selected entry file`() = withTemporaryDirectory { directory ->
        FrontendHtmlProjectFiles.create("<p>Before</p>", directory)
        val stylesheet = directory.resolve("theme.css").apply { writeText("body {}") }

        FrontendHtmlProjectFiles.replaceEntry(directory, "index.html", "<p>After</p>")

        assertEquals("<p>After</p>", FrontendHtmlProjectFiles.readEntry(directory, "index.html"))
        assertEquals("body {}", stylesheet.readText())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".replacement") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an entry outside the project directory`() = withTemporaryDirectory { directory ->
        FrontendHtmlProjectFiles.readEntry(directory, "../index.html")
    }

    private fun withTemporaryDirectory(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("eleckoi-html-theme-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
