package com.eleckoi.android.engine.immersive.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveWebSecurityTest {
    @Test
    fun `allows only exact local asset origin and registered paths`() {
        val host = ImmersiveWebSecurity.isolatedHost(
            AuthorFrontendStoragePrincipal.publishedProject("project-1"),
        )
        assertTrue(
            ImmersiveWebSecurity.isAllowedLocalResource(
                scheme = "https",
                host = host,
                port = -1,
                path = "/frontend-project/index.html",
                userInfo = null,
                expectedHost = host,
            ),
        )
        assertTrue(
            ImmersiveWebSecurity.isAllowedLocalResource(
                scheme = "https",
                host = host,
                port = -1,
                path = "/eleckoi-media/character-avatar",
                userInfo = null,
                expectedHost = host,
            ),
        )
        assertTrue(
            ImmersiveWebSecurity.isAllowedLocalResource(
                scheme = "https",
                host = host,
                port = -1,
                path = "/eleckoi-runtime/eleckoi.js",
                userInfo = null,
                expectedHost = host,
            ),
        )
    }

    @Test
    fun `blocks external and lookalike resource origins`() {
        val host = testHost()
        assertFalse(allowed(host = "example.com"))
        assertFalse(allowed(host = "$host.example.com"))
        assertFalse(allowed(scheme = "http"))
        assertFalse(allowed(port = 443))
        assertFalse(allowed(path = "/unregistered/index.html"))
        assertFalse(allowed(userInfo = "attacker"))
    }

    @Test
    fun `principal host is deterministic valid and isolated across projects and scopes`() {
        val projectA = AuthorFrontendStoragePrincipal.publishedProject("same-stable-id")
        val projectAReload = AuthorFrontendStoragePrincipal.publishedProject("same-stable-id")
        val projectB = AuthorFrontendStoragePrincipal.publishedProject("other-project")
        val workspaceA = AuthorFrontendStoragePrincipal.creationWorkspace("same-stable-id")

        val hostA = ImmersiveWebSecurity.isolatedHost(projectA)
        assertEquals(hostA, ImmersiveWebSecurity.isolatedHost(projectAReload))
        assertNotEquals(hostA, ImmersiveWebSecurity.isolatedHost(projectB))
        assertNotEquals(hostA, ImmersiveWebSecurity.isolatedHost(workspaceA))
        assertTrue(hostA.length <= 253)
        assertTrue(hostA.matches(Regex("^[a-z0-9.-]+$")))
        assertTrue(hostA.endsWith(".invalid"))
        assertEquals("https://$hostA", ImmersiveWebSecurity.isolatedOrigin(projectA))
    }

    private fun allowed(
        scheme: String = "https",
        host: String = testHost(),
        port: Int = -1,
        path: String = "/frontend-project/index.html",
        userInfo: String? = null,
    ) = ImmersiveWebSecurity.isAllowedLocalResource(
        scheme = scheme,
        host = host,
        port = port,
        path = path,
        userInfo = userInfo,
        expectedHost = testHost(),
    )

    private fun testHost() = ImmersiveWebSecurity.isolatedHost(
        AuthorFrontendStoragePrincipal.publishedProject("test-project"),
    )
}
