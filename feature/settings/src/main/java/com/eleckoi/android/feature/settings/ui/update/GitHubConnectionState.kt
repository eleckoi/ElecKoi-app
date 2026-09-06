package com.eleckoi.android.feature.settings.ui.update

import java.net.URI

enum class GitHubConnectionSource(val title: String, val prefix: String) {
    Official("GitHub 官方", ""),
    Proxy("GH-Proxy 主站", "https://gh-proxy.org/"),
    Fastly("GH-Proxy Fastly", "https://cdn.gh-proxy.org/"),
    Dpik("GH-DPIK", "https://gh.dpik.top/"),
    Custom("自定义镜像", ""),
}

data class GitHubConnectionSettings(
    val source: GitHubConnectionSource = GitHubConnectionSource.Official,
    val customPrefix: String = "",
) {
    fun resolve(original: String): String = when (source) {
        GitHubConnectionSource.Official -> original
        GitHubConnectionSource.Custom -> requireNotNull(normalizeMirrorPrefix(customPrefix)) + original
        else -> source.prefix + original
    }
}

fun normalizeMirrorPrefix(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..65535)
    uri.toASCIIString().trimEnd('/') + "/"
}.getOrNull()

data class GitHubConnectionUiState(
    val settings: GitHubConnectionSettings = GitHubConnectionSettings(),
    val saving: Boolean = false,
    val testMode: GitHubConnectionTestMode = GitHubConnectionTestMode.Idle,
    val results: Map<GitHubConnectionSource, String> = emptyMap(),
    val error: String = "",
) {
    val testing: Boolean get() = testMode != GitHubConnectionTestMode.Idle
    val downloadTesting: Boolean get() = testMode == GitHubConnectionTestMode.Download
}

enum class GitHubConnectionTestMode {
    Idle,
    Api,
    Download,
}
