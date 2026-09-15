package com.eleckoi.android.sdk.author

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorFrontendSdkTest {
    @Test
    fun `desktop aligned libraries load before the author sdk`() {
        val head = buildAuthorDocumentHead("window.ElecKoi = { api: { version: '0.1.0' } };")

        assertTrue(head.contains("jquery.min.js"))
        assertTrue(head.contains("pixi.min.js"))
        assertTrue(head.contains("tailwind-browser.global.js"))
        assertTrue(head.contains("vue.global.prod.js"))
        assertTrue(head.contains("yaml.global.js"))
        assertTrue(head.contains("zod.global.js"))
        assertTrue(head.contains("fontAwesome: '7.3.1'"))
        assertTrue(head.contains("zod: '4.1.11'"))
        assertTrue(head.indexOf("eleckoi:libraries-ready") < head.indexOf("eleckoi-author-api"))
    }

    @Test
    fun `author sdk source cannot close its host script element`() {
        val head = buildAuthorDocumentHead(
            "window.example = \"</script><script id='escape'>bad()</script>\";",
        )

        assertFalse(head.contains("</script><script id='escape'>"))
        assertTrue(head.contains("<\\/script>"))
    }
}
