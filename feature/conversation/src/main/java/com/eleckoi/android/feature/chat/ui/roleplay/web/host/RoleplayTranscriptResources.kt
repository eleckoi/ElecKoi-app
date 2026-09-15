package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

internal data class TranscriptAsset(
    val path: String,
    val mimeType: String,
    val encoding: String? = null,
)

internal val TranscriptAssets = buildMap {
    listOf(
        "whale-maid-thinking.png",
        "whale-maid-thinking-half.png",
        "whale-maid-thinking-closed.png",
        "whale-maid-thinking-head.png",
        "whale-maid-thinking-head-half.png",
        "whale-maid-thinking-head-closed.png",
    ).forEach { name ->
        put(name, TranscriptAsset(path = "model-icons/$name", mimeType = "image/png"))
    }
    put(
        "web-runtime/showdown-2.1.0.min.js",
        TranscriptAsset(
            path = "web-runtime/showdown-2.1.0.min.js",
            mimeType = "application/javascript",
            encoding = "UTF-8",
        ),
    )
    put(
        "web-runtime/dompurify-3.3.2.min.js",
        TranscriptAsset(
            path = "web-runtime/dompurify-3.3.2.min.js",
            mimeType = "application/javascript",
            encoding = "UTF-8",
        ),
    )
    put(
        "web-runtime/tanstack-virtual-core-3.17.8.min.js",
        TranscriptAsset(
            path = "web-runtime/tanstack-virtual-core-3.17.8.min.js",
            mimeType = "application/javascript",
            encoding = "UTF-8",
        ),
    )
}

internal fun blockedResourceResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

internal fun missingMediaResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    404,
    "Not Found",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

internal const val NativeObjectName = "ElecKoiTranscript"
internal const val MaxBridgeMessageBytes = 32 * 1024 * 1024
internal const val MaxNativeCommandBytes = 32 * 1024 * 1024
internal const val IngressChunkCharacters = 48 * 1024
