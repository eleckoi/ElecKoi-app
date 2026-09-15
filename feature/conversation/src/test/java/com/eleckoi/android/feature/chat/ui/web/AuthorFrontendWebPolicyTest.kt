package com.eleckoi.android.feature.chat.ui.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorFrontendWebPolicyTest {
    @Test
    fun `authored frontends keep the same browser resource schemes as desktop`() {
        listOf("https", "http", "data", "blob", "about").forEach { scheme ->
            assertTrue(scheme, isDesktopAllowedAuthorFrontendResource(scheme))
        }

        listOf("file", "content", "javascript", null).forEach { scheme ->
            assertFalse(scheme, isDesktopAllowedAuthorFrontendResource(scheme))
        }
    }

    @Test
    fun `external navigation follows the desktop https-only rule`() {
        assertTrue(isDesktopAllowedExternalNavigation("https"))
        listOf("http", "mailto", "tel", "file", "content", null).forEach { scheme ->
            assertFalse(scheme, isDesktopAllowedExternalNavigation(scheme))
        }
    }
}
