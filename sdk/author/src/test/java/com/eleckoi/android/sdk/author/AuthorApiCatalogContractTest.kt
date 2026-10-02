package com.eleckoi.android.sdk.author

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorApiCatalogContractTest {
    @Test
    fun `public catalog preserves baseline methods and adds plugin capabilities without duplicates`() {
        val methods = AuthorApiCatalog.definitions.map(AuthorApiDefinition::method)

        assertTrue(methods.size > 51)
        assertTrue("storage.transaction" in methods)
        assertTrue("generation.invoke" in methods)
        assertTrue("worldbooks.bind" in methods)
        assertEquals(methods.size, methods.distinct().size)
        assertTrue("chat.getAgentTrajectory" in methods)
        assertTrue("settingLibrary.current" in methods)
        assertTrue("media.getMessageAttachments" in methods)
        assertTrue("audio.play" in methods)
        assertTrue("audio.setSettings" in methods)
    }
}
