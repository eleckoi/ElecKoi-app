package com.eleckoi.android.app.update

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSettings
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class AppRelease(
    val tagName: String,
    val title: String,
    val pageUrl: String,
    val notes: String,
    val publishedAt: String,
    val apk: AppReleaseApk? = null,
) {
    fun hasSameDownload(other: AppRelease?): Boolean =
        tagName == other?.tagName && apk == other.apk
}

internal data class AppReleaseApk(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

internal sealed interface LatestReleaseResult {
    data class Published(val release: AppRelease) : LatestReleaseResult
    data object NonePublished : LatestReleaseResult
}

internal class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = LatestReleaseEndpoint,
) {
    suspend fun latest(
        connection: GitHubConnectionSettings = GitHubConnectionSettings(),
    ): LatestReleaseResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(connection.resolve(endpoint))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ElecKoi-Android-Update-Check")
            .build()
        client.newCall(request).useResponse { response ->
            // A mirror's 404 can indicate an unsupported API route.
            if (response.code == 404 && connection.source == GitHubConnectionSource.Official) {
                return@useResponse LatestReleaseResult.NonePublished
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub 更新检查失败（HTTP ${response.code}）")
            }
            val body = response.body?.string().orEmpty()
            try {
                LatestReleaseResult.Published(parseRelease(body))
            } catch (error: IllegalArgumentException) {
                throw IOException("版本接口响应无效", error)
            }
        }
    }

    companion object {
        const val RepositoryUrl = "https://github.com/eleckoi/ElecKoi"
        const val ReleasesUrl = "$RepositoryUrl/releases"
        const val LatestReleaseEndpoint = "https://api.github.com/repos/eleckoi/ElecKoi/releases/latest"
        private val Sha256Pattern = Regex("[0-9a-fA-F]{64}")

        internal fun parseRelease(json: String): AppRelease {
            val root = ElecKoiJson.parseToJsonElement(json).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val page = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            require(tag.isNotBlank()) { "GitHub Release 缺少 tag_name" }
            require(page.startsWith("$ReleasesUrl/")) {
                "GitHub Release 地址无效"
            }
            val apk = root["assets"]?.jsonArray
                ?.mapNotNull { asset ->
                    val objectValue = asset.jsonObject
                    val name = objectValue["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    val downloadUrl = objectValue["browser_download_url"]
                        ?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    val digest = objectValue["digest"]?.jsonPrimitive?.contentOrNull
                        .orEmpty().trim().removePrefix("sha256:")
                        .takeIf { it.matches(Sha256Pattern) }
                    val size = objectValue["size"]?.jsonPrimitive?.contentOrNull
                        ?.toLongOrNull() ?: 0L
                    if (
                        name.endsWith("-arm64.apk", ignoreCase = true) &&
                        downloadUrl.startsWith("$ReleasesUrl/download/") &&
                        size > 0L
                    ) {
                        AppReleaseApk(
                            name = name,
                            downloadUrl = downloadUrl,
                            sizeBytes = size,
                            sha256 = digest?.lowercase(),
                        )
                    } else {
                        null
                    }
                }
                ?.minByOrNull(AppReleaseApk::name)
            return AppRelease(
                tagName = tag,
                title = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    .ifBlank { tag },
                pageUrl = page,
                notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                apk = apk,
            )
        }
    }
}
