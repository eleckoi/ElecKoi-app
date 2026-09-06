package com.eleckoi.android.app.update

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response

internal suspend fun <T> Call.useResponse(block: suspend (Response) -> T): T = coroutineScope {
    currentCoroutineContext().ensureActive()
    // Closing the socket also interrupts execute() and blocked body reads.
    val cancelRequest = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@useResponse.cancel()
        }
    }
    try {
        execute().use { response ->
            val result = block(response)
            currentCoroutineContext().ensureActive()
            result
        }
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        cancelRequest.cancel()
    }
}
