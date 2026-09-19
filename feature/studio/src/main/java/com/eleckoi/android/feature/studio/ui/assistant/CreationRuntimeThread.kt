package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem

internal fun List<CreationTimelineItem>.latestCreationRuntimeThreadId(): String = asReversed()
    .firstNotNullOfOrNull { item -> item.runtimeThreadId.takeIf(String::isNotBlank) }
    .orEmpty()
