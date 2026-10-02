package com.eleckoi.android.sdk.author.plugins

import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PluginViewportDeviceTest {
    @Test fun matchParentWebViewUsesNativeViewportUnits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        var view: WebView? = null
        var result: JsonObject? = null
        instrumentation.runOnMainSync {
            view = WebView(ApplicationProvider.getApplicationContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                layout(0, 0, 600, 800)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(webView: WebView, url: String) {
                        evaluateJavascript("JSON.stringify({vh:document.querySelector('#probe').getBoundingClientRect().height,inner:innerHeight})") { value ->
                            result = Json.parseToJsonElement(Json.parseToJsonElement(value).jsonPrimitive.content).jsonObject
                            finished.countDown()
                        }
                    }
                }
                loadDataWithBaseURL("https://eleckoi-plugin.local/", "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><div id='probe' style='height:100vh;width:1px'></div>", "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("WebView did not finish loading", finished.await(20, TimeUnit.SECONDS))
            assertTrue(result.toString(), result!!.getValue("vh").jsonPrimitive.double > 0)
            assertEquals(result!!.getValue("inner").jsonPrimitive.double, result!!.getValue("vh").jsonPrimitive.double, 1.0)
        } finally { instrumentation.runOnMainSync { view?.destroy() } }
    }
}
