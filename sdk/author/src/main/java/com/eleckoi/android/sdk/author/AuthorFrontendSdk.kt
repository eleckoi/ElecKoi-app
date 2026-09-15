package com.eleckoi.android.sdk.author

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.IOException

object AuthorFrontendSdk {
    @Volatile
    private var cachedSource: String? = null

    fun source(context: Context): String {
        cachedSource?.let { return it }
        return synchronized(this) {
            cachedSource ?: context.applicationContext.assets
                .open(AssetPath)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .also { source ->
                    require(source.isNotBlank()) { "ElecKoi 作者前端 SDK 为空" }
                    cachedSource = source
                }
        }
    }

    /**
     * Runtime injected before an author's own scripts. Keep this list and its versions aligned
     * with the desktop author runtime so one frontend package sees the same browser globals.
     */
    fun documentHead(context: Context): String = buildAuthorDocumentHead(source(context))

    /** Same browser library contract used by desktop, for hosts that build nested documents. */
    fun librariesHead(): String = RuntimeLibrariesHead

    /** Serves only the SDK-owned browser runtime; project files never share this asset root. */
    fun runtimeResource(context: Context, requestedPath: String): WebResourceResponse? {
        val path = requestedPath.trimStart('/')
        if (path == SdkRuntimeFile) {
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                context.applicationContext.assets.open(AssetPath),
            )
        }
        if (!path.startsWith(LibraryRuntimePrefix)) return null
        val relativePath = path.removePrefix(LibraryRuntimePrefix)
        if (relativePath !in LibraryResourcePaths) return null
        return try {
            WebResourceResponse(
                runtimeMimeType(relativePath),
                runtimeEncoding(relativePath),
                context.applicationContext.assets.open("$LibraryAssetRoot/$relativePath"),
            )
        } catch (_: IOException) {
            null
        }
    }

    private const val AssetPath = "frontend/preview/eleckoi.js"
    private const val SdkRuntimeFile = "eleckoi.js"
    private const val LibraryAssetRoot = "frontend/runtime-libraries"
    private const val LibraryRuntimePrefix = "author-libraries/"
    private const val LibraryUrlRoot = "/eleckoi-runtime/author-libraries"

    private val LibraryResourcePaths = setOf(
        "jquery.min.js",
        "jquery-ui.min.js",
        "jquery-ui-touch-punch.min.js",
        "lodash.min.js",
        "pixi.min.js",
        "showdown.min.js",
        "tailwind-browser.global.js",
        "toastr.min.css",
        "toastr.min.js",
        "vue.global.prod.js",
        "vue-router.global.prod.js",
        "yaml.global.js",
        "zod.global.js",
        "fontawesome/css/all.min.css",
        "fontawesome/js/all.min.js",
        "fontawesome/webfonts/fa-brands-400.woff2",
        "fontawesome/webfonts/fa-regular-400.woff2",
        "fontawesome/webfonts/fa-solid-900.woff2",
        "fontawesome/webfonts/fa-v4compatibility.woff2",
        "jquery-ui/jquery-ui.min.css",
        "jquery-ui/images/ui-icons_444444_256x240.png",
        "jquery-ui/images/ui-icons_555555_256x240.png",
        "jquery-ui/images/ui-icons_777620_256x240.png",
        "jquery-ui/images/ui-icons_777777_256x240.png",
        "jquery-ui/images/ui-icons_cc0000_256x240.png",
        "jquery-ui/images/ui-icons_ffffff_256x240.png",
    )

    private fun runtimeMimeType(path: String): String = when {
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".woff2") -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun runtimeEncoding(path: String): String? = when {
        path.endsWith(".js") || path.endsWith(".css") -> "UTF-8"
        else -> null
    }

    internal val RuntimeLibrariesHead = """
<link rel="stylesheet" href="$LibraryUrlRoot/jquery-ui/jquery-ui.min.css" data-eleckoi-author-libraries>
<link rel="stylesheet" href="$LibraryUrlRoot/toastr.min.css" data-eleckoi-author-libraries>
<link rel="stylesheet" href="$LibraryUrlRoot/fontawesome/css/all.min.css" data-eleckoi-author-libraries>
<script src="$LibraryUrlRoot/jquery.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/jquery-ui.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/jquery-ui-touch-punch.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/lodash.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/pixi.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/showdown.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/toastr.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/vue.global.prod.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/vue-router.global.prod.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/fontawesome/js/all.min.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/tailwind-browser.global.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/yaml.global.js" data-eleckoi-author-libraries></script>
<script src="$LibraryUrlRoot/zod.global.js" data-eleckoi-author-libraries></script>
<script data-eleckoi-author-libraries>
window.ElecKoiLibraries = Object.freeze({
  ready: true,
  versions: Object.freeze({
    fontAwesome: '7.3.1',
    jquery: '3.7.1',
    jqueryUi: '1.13.3',
    jqueryUiTouchPunch: '0.2.3',
    lodash: '4.17.21',
    pixi: '8.20.1',
    showdown: '2.1.0',
    tailwindCss: '4.3.3',
    toastr: '2.1.4',
    vue: '3.5.42',
    vueRouter: '4.6.3',
    yaml: '2.9.0',
    zod: '4.1.11'
  })
});
window.dispatchEvent(new CustomEvent('eleckoi:libraries-ready', { detail: window.ElecKoiLibraries }));
</script>
""".trimIndent()
}

internal fun buildAuthorDocumentHead(authorApiSource: String): String = buildString {
    append(AuthorFrontendSdk.RuntimeLibrariesHead)
    append("\n<script id=\"eleckoi-author-api\">")
    append(authorApiSource.replace(ClosingScriptTag) { "<\\/script" })
    append("</script>")
}

private val ClosingScriptTag = Regex("(?i)</script")
