package com.eleckoi.android.app.update

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class AppRelease(
    val tagName: String,
    val title: String,
    val pageUrl: String,
    val notes: String,
    val publishedAt: String,
)

internal sealed interface LatestReleaseResult {
    data class Published(val release: AppRelease) : LatestReleaseResult
    data object NonePublished : LatestReleaseResult
}

internal class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: String = LatestReleaseEndpoint,
) {
    suspend fun latest(): LatestReleaseResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ElecKoi-Android-Update-Check")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext LatestReleaseResult.NonePublished
            if (!response.isSuccessful) {
                throw IOException("GitHub 更新检查失败（HTTP ${response.code}）")
            }
            val body = response.body?.string().orEmpty()
            LatestReleaseResult.Published(parseRelease(body))
        }
    }

    companion object {
        private const val RepositorySlug = "eleckoi/ElecKoi-app"

        const val RepositoryUrl = "https://github.com/$RepositorySlug"
        const val ReleasesUrl = "$RepositoryUrl/releases"
        const val LatestReleaseEndpoint = "https://api.github.com/repos/$RepositorySlug/releases/latest"

        internal fun parseRelease(json: String): AppRelease {
            val root = ElecKoiJson.parseToJsonElement(json).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val page = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            require(tag.isNotBlank()) { "GitHub Release 缺少 tag_name" }
            require(page.startsWith("$ReleasesUrl/")) {
                "GitHub Release 地址无效"
            }
            return AppRelease(
                tagName = tag,
                title = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    .ifBlank { tag },
                pageUrl = page,
                notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            )
        }
    }
}
