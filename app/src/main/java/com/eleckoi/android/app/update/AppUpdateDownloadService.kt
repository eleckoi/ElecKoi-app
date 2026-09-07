package com.eleckoi.android.app.update

import java.io.File
import java.io.FileOutputStream
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class AppUpdateDownloadService(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val downloadMutex = Mutex()

    suspend fun download(
        apk: AppReleaseApk,
        fileStem: String = "eleckoi-update",
        onProgress: (Long, Long) -> Unit,
        connection: GitHubConnectionSettings = GitHubConnectionSettings(),
        verify: suspend (File) -> Unit,
    ): File = downloadMutex.withLock {
        var target: File? = null
        var delivered = false
        try {
            val file = withContext(Dispatchers.IO) {
                require(apk.sizeBytes > 0L) { "APK 文件大小无效" }
                check(directory.isDirectory || directory.mkdirs()) { "无法创建更新目录" }
                directory.listFiles()?.filter {
                    it.isFile && it.name.startsWith("eleckoi-")
                }?.forEach { check(it.delete()) { "无法清理旧安装包" } }
                val partialFile = File(directory, "$fileStem.apk.part")
                val apkFile = File(directory, "$fileStem.apk")
                target = partialFile
                transfer(apk, connection.resolve(apk.downloadUrl), partialFile, onProgress)
                verify(partialFile)
                currentCoroutineContext().ensureActive()
                check(partialFile.renameTo(apkFile)) { "无法保存安装包" }
                target = apkFile
                apkFile
            }
            delivered = true
            file
        } finally {
            // Cancellation can discard the result while returning from the IO dispatcher.
            if (!delivered) {
                withContext(NonCancellable + Dispatchers.IO) {
                    target?.delete()
                }
            }
        }
    }

    suspend fun discard(file: File) = withContext(NonCancellable + Dispatchers.IO) {
        downloadMutex.withLock {
            file.delete()
            Unit
        }
    }

    private suspend fun transfer(
        apk: AppReleaseApk,
        downloadUrl: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) = coroutineScope {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "ElecKoi-Android-Update-Download")
            .build()
        val call = client.newCall(request)
        // A blocked socket read needs Call.cancel(), in addition to cooperative loop checks.
        val cancelRequest = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "GitHub APK 下载失败（HTTP ${response.code}）" }
                val body = response.body ?: error("GitHub APK 响应为空")
                var downloadedBytes = 0L
                var lastProgressAt = System.nanoTime()
                var lastProgressBytes = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            currentCoroutineContext().ensureActive()
                            check(count.toLong() <= apk.sizeBytes - downloadedBytes) {
                                "下载文件超出预期大小"
                            }
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            val now = System.nanoTime()
                            if (now - lastProgressAt >= TimeUnit.MILLISECONDS.toNanos(150)) {
                                val elapsed = now - lastProgressAt
                                val speed = (downloadedBytes - lastProgressBytes) * 1_000_000_000L /
                                    elapsed.coerceAtLeast(1L)
                                onProgress(downloadedBytes, speed)
                                lastProgressBytes = downloadedBytes
                                lastProgressAt = now
                            }
                        }
                    }
                }
                check(downloadedBytes == apk.sizeBytes) { "下载文件大小校验失败" }
                currentCoroutineContext().ensureActive()
                onProgress(downloadedBytes, 0L)
            }
        } finally {
            cancelRequest.cancel()
        }
    }
}
