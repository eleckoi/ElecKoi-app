package com.eleckoi.android.feature.chat.ui.trajectory

import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage

/** Resolves trajectory ownership from the authoritative draft, never a stale display projection. */
internal fun ChatDraft?.trajectoryRuntimeThreadId(): String =
    resolveTrajectoryRuntimeThreadId(
        generationThreadId = this?.session?.generationStats?.runtimeThreadId.orEmpty(),
        messages = this?.session?.messages.orEmpty(),
    )

internal fun resolveTrajectoryRuntimeThreadId(
    generationThreadId: String,
    messages: List<ChatMessage>,
): String = generationThreadId.ifBlank {
    messages.asReversed()
        .firstOrNull { message -> message.runtimeThreadId.isNotBlank() }
        ?.runtimeThreadId
        .orEmpty()
}
