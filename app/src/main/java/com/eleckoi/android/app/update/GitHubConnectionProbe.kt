package com.eleckoi.android.app.update

import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class GitHubConnectionProbe(
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun measureApi(settings: GitHubConnectionSettings): String {
        val started = System.nanoTime()
        return try {
            releaseClient.latest(settings)
            "API ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms"
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            "API ${failureLabel(error)}"
        }
    }

    suspend fun measureDownload(settings: GitHubConnectionSettings, apk: AppReleaseApk?): String {
        val asset = apk ?: return "暂无安装包可测速"
        return try {
            val speed = sample(settings.resolve(asset.downloadUrl), asset.sizeBytes)
            if (speed >= 1024 * 1024) "下载 %.1f MiB/s".format(speed / (1024.0 * 1024.0))
            else "下载 %.0f KiB/s".format(speed / 1024.0)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            "下载 ${failureLabel(error)}"
        }
    }

    internal suspend fun sample(url: String, size: Long): Double = withContext(Dispatchers.IO) {
        require(size >= 4)
        val limit = minOf(size, SampleBytes)
        val request = Request.Builder().url(url)
            .header("Range", "bytes=0-${limit - 1}")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "ElecKoi-Android-Connection-Test")
            .build()
        val call = client.newCall(request)
        call.useResponse { response ->
            if (response.code != 200 && response.code != 206) {
                throw IOException("HTTP ${response.code}")
            }
            if (response.code == 206 && response.header("Content-Range") != "bytes 0-${limit - 1}/$size") {
                throw IOException("文件范围无效")
            }
            val input = response.body?.byteStream() ?: throw IOException("响应为空")
            val started = System.nanoTime()
            var read = 0L
            val signature = ByteArray(4)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read < limit) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - read).toInt())
                if (count < 0) throw IOException("文件响应提前结束")
                for (index in 0 until minOf(count, (4L - read).coerceAtLeast(0).toInt())) {
                    signature[read.toInt() + index] = buffer[index]
                }
                read += count
                if (read >= 4 && !signature.contentEquals(byteArrayOf(0x50, 0x4b, 3, 4))) {
                    throw IOException("安装包响应无效")
                }
            }
            // Stop immediately even when the server ignores Range and sends the entire APK.
            call.cancel()
            read * 1_000_000_000.0 / (System.nanoTime() - started).coerceAtLeast(1L)
        }
    }

    private fun failureLabel(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "超时"
        is java.net.UnknownHostException -> "域名解析失败"
        is IllegalArgumentException -> "响应无效"
        else -> error.message?.takeIf { it.startsWith("HTTP ") } ?: "连接失败"
    }

    private companion object {
        const val SampleBytes = 1024L * 1024L
    }
}
