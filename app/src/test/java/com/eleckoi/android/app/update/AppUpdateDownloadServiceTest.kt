package com.eleckoi.android.app.update

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateDownloadServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File get() = temporaryFolder.root

    @Test(timeout = 10_000)
    fun verificationFailureRemovesTheDownloadedFile() = runBlocking {
        withServer(CompleteResponse) { url, _ ->
            val result = runCatching {
                AppUpdateDownloadService(directory).download(asset(url), { _, _ -> }) {
                    error("invalid APK")
                }
            }
            assertEquals("invalid APK", result.exceptionOrNull()?.message)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test(timeout = 10_000)
    fun cancellingAStalledBodyClosesTheSocketAndRemovesPartialFiles() = runBlocking {
        withServer(
            "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\na",
            awaitDisconnect = true,
        ) { url, responseSent ->
            val job = launch {
                AppUpdateDownloadService(directory).download(asset(url), { _, _ -> }) {
                    error("cancelled download reached verification")
                }
            }
            responseSent.await()
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    private fun asset(url: String) = AppReleaseApk("app-arm64.apk", url, 3L)

    private suspend fun withServer(
        response: String,
        awaitDisconnect: Boolean = false,
        block: suspend (String, CompletableDeferred<Unit>) -> Unit,
    ) = coroutineScope {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 4_000
            val responseSent = CompletableDeferred<Unit>()
            val serving = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 4_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                    responseSent.complete(Unit)
                    if (awaitDisconnect) assertEquals(-1, reader.read())
                }
            }
            block("http://127.0.0.1:${server.localPort}/app.apk", responseSent)
            serving.join()
        }
    }

    private companion object {
        const val CompleteResponse = "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"
    }
}
