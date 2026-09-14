package com.eleckoi.android.engine.immersive.security

import android.net.Uri
import java.security.MessageDigest

@JvmInline
value class AuthorFrontendStoragePrincipal private constructor(
    internal val canonicalValue: String,
) {
    companion object {
        fun publishedProject(projectId: String): AuthorFrontendStoragePrincipal =
            create("published-project", projectId)

        fun creationWorkspace(workspaceId: String): AuthorFrontendStoragePrincipal =
            create("creation-workspace", workspaceId)

        private fun create(scope: String, stableId: String): AuthorFrontendStoragePrincipal {
            require(stableId.isNotBlank()) { "Author frontend storage principal id cannot be blank" }
            return AuthorFrontendStoragePrincipal("$scope\u0000$stableId")
        }
    }
}

object ImmersiveWebSecurity {
    private const val IsolatedHostPrefix = "eg-"
    private const val HashHexLength = 40
    private val HostPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

    fun isolatedHost(principal: AuthorFrontendStoragePrincipal): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(principal.canonicalValue.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(HashHexLength)
        // `.invalid` is a reserved non-network TLD. Each hash is therefore its own
        // registrable site instead of sharing a cookie parent with another project.
        return "$IsolatedHostPrefix$digest.$VirtualTopLevelDomain".also { host ->
            check(host.length <= 253 && HostPattern.matches(host)) {
                "Generated author frontend host is not a valid DNS host"
            }
        }
    }

    fun isolatedOrigin(principal: AuthorFrontendStoragePrincipal): String =
        "https://${isolatedHost(principal)}"

    fun isAllowedLocalResource(uri: Uri, expectedHost: String): Boolean = isAllowedLocalResource(
        scheme = uri.scheme,
        host = uri.host,
        port = uri.port,
        path = uri.path,
        userInfo = uri.userInfo,
        expectedHost = expectedHost,
    )

    fun isAllowedLocalResource(
        scheme: String?,
        host: String?,
        port: Int,
        path: String?,
        userInfo: String?,
        expectedHost: String,
    ): Boolean {
        if (scheme != "https") return false
        if (host != expectedHost || !HostPattern.matches(expectedHost)) return false
        if (port != -1 || userInfo != null) return false
        return path?.startsWith(ProjectPath) == true ||
            path?.startsWith(RuntimePath) == true ||
            path?.startsWith(MediaPath) == true
    }
}

internal const val VirtualTopLevelDomain = "invalid"
const val ProjectPath = "/frontend-project/"
const val RuntimePath = "/eleckoi-runtime/"
const val MediaPath = "/eleckoi-media/"
