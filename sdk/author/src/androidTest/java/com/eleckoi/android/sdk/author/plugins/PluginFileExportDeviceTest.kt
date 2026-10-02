package com.eleckoi.android.sdk.author.plugins

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PluginFileExportDeviceTest {
    @Test fun savesUtf8JsonAndReportsCancellationAndWriteFailure() = runBlocking {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val text = """{"text":"中文 🐳", "items":[1,true,null]}"""
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "eleckoi-export-test-${System.nanoTime()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
        }))
        PluginFileExport.attach()
        try {
            suspend fun current() = withTimeout(10_000) { PluginFileExport.request.filterNotNull().first() }
            val saved = async { PluginFileExport.saveText("report.json", "application/json", text) }
            PluginFileExport.finish(current(), resolver, uri)
            val result = saved.await()
            assertTrue(result.getValue("saved").jsonPrimitive.boolean)
            assertEquals(text.toByteArray(Charsets.UTF_8).size, result.getValue("bytes").jsonPrimitive.int)
            assertEquals(text, resolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            assertNull(PluginFileExport.request.value)

            val cancelled = async { PluginFileExport.saveText("cancel.json", "application/json", text) }
            PluginFileExport.finish(current(), resolver, null)
            val cancellation = cancelled.await()
            assertFalse(cancellation.getValue("saved").jsonPrimitive.boolean)
            assertTrue(cancellation.getValue("cancelled").jsonPrimitive.boolean)

            // supervisorScope keeps an expected failure from cancelling the test parent.
            supervisorScope {
                val failed = async { PluginFileExport.saveText("fail.json", "application/json", text) }
                PluginFileExport.finish(current(), resolver, Uri.parse("content://eleckoi.missing-provider/report.json"))
                try { failed.await(); fail("Expected file write error") }
                catch (error: java.io.FileNotFoundException) { assertTrue(error.message!!.contains("provider")) }
            }
            assertNull(PluginFileExport.request.value)
        } finally {
            PluginFileExport.detach()
            resolver.delete(uri, null, null)
        }
    }
}
