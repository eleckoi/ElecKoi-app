package com.eleckoi.android.sdk.author

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorApiCatalogContractTest {
    @Test
    fun `desktop aligned public catalog exposes all 51 methods`() {
        val methods = AuthorApiCatalog.definitions.map(AuthorApiDefinition::method)

        assertEquals(51, methods.size)
        assertEquals(methods.size, methods.distinct().size)
        assertTrue("chat.getAgentTrajectory" in methods)
        assertTrue("settingLibrary.current" in methods)
        assertTrue("media.getMessageAttachments" in methods)
        assertTrue("audio.play" in methods)
        assertTrue("audio.setSettings" in methods)
    }
}
