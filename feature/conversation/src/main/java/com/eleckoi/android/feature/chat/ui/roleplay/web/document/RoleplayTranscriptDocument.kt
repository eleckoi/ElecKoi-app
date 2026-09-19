package com.eleckoi.android.feature.chat.ui.roleplay.web.document

import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptContent
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptBootstrap
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptInteraction
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptMarkdown
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptProjection
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptRichContent
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptRuntimeCore
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptTurns
import com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime.RoleplayTranscriptUpdates

internal fun buildRoleplayTranscriptDocument(
    authorSdkSource: String,
    authorLibrariesHead: String = "",
): String {
    val encodedSdk = java.util.Base64.getEncoder()
        .encodeToString(authorSdkSource.toByteArray(Charsets.UTF_8))
    val encodedLibraries = java.util.Base64.getEncoder()
        .encodeToString(authorLibrariesHead.toByteArray(Charsets.UTF_8))
    return RoleplayTranscriptDocument
        .replace("__ELECKOI_AUTHOR_SDK_BASE64__", encodedSdk)
        .replace("__ELECKOI_AUTHOR_LIBRARIES_BASE64__", encodedLibraries)
}

private val RoleplayTranscriptDocument = buildString {
    append(RoleplayTranscriptMarkupStart)
    append(RoleplayTranscriptStyles)
    append(RoleplayTranscriptLayoutStyles)
    append(RoleplayTranscriptMarkupMiddle)
    append(RoleplayTranscriptRuntimeCore)
    append(RoleplayTranscriptMarkdown)
    append(RoleplayTranscriptRichContent)
    append(RoleplayTranscriptContent)
    append(RoleplayTranscriptTurns)
    append(RoleplayTranscriptProjection)
    append(RoleplayTranscriptBootstrap)
    append(RoleplayTranscriptUpdates)
    append(RoleplayTranscriptInteraction)
    append(RoleplayTranscriptMarkupEnd)
}.trimIndent()
