package com.eleckoi.android.engine.workspace.runtime.process

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeGuestCommandOutputTest {
    @Test
    fun `full output mode retains paths beyond the default tail window`() {
        val paths = (1..20_000).joinToString("\n") { "group/entry-$it" }
        val output = ByteArrayInputStream(paths.toByteArray(Charsets.UTF_8))
            .readBoundedOutput(retainFullOutput = true)

        assertEquals(paths, output)
    }
}
