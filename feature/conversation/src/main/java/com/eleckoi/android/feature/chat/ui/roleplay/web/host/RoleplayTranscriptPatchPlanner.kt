package com.eleckoi.android.feature.chat.ui.roleplay.web.host

import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptMessage
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.toJson
import org.json.JSONArray
import org.json.JSONObject

/** Builds a browser patch from the last browser-confirmed model and the newest candidate. */
internal object RoleplayTranscriptPatchPlanner {
    fun plan(
        baseline: RoleplayTranscriptModel,
        next: RoleplayTranscriptModel,
    ): JSONObject? {
        val patch = JSONObject()
        if (baseline.style != next.style) {
            patch.put("style", next.style.toJson())
        }
        if (baseline.frontendRendererEnabled != next.frontendRendererEnabled) {
            patch.put("frontendRendererEnabled", next.frontendRendererEnabled)
        }
        if (baseline.deleteMode != next.deleteMode) {
            patch.put("deleteMode", next.deleteMode)
        }
        if (baseline.deleteFromMessageId != next.deleteFromMessageId) {
            patch.put("deleteFromMessageId", next.deleteFromMessageId)
        }

        @Suppress("UNCHECKED_CAST")
        val baselineAppended = baseline.messages as? ImmutableAppendedList<RoleplayTranscriptMessage>
        @Suppress("UNCHECKED_CAST")
        val nextAppended = next.messages as? ImmutableAppendedList<RoleplayTranscriptMessage>
        val sharedPrefix = baselineAppended != null &&
            nextAppended != null &&
            baselineAppended.prefix === nextAppended.prefix
        val sameOrder = if (sharedPrefix) {
            checkNotNull(baselineAppended).tail.source.id ==
                checkNotNull(nextAppended).tail.source.id
        } else {
            baseline.messages.size == next.messages.size &&
                baseline.messages.indices.all { index ->
                    baseline.messages[index].source.id == next.messages[index].source.id
                }
        }
        if (!sameOrder) {
            patch.put("order", JSONArray().apply {
                next.messages.forEach { message -> put(message.source.id) }
            })
        }

        val changedMessages = JSONArray()
        if (sameOrder && sharedPrefix) {
            val previous = checkNotNull(baselineAppended).tail
            val message = checkNotNull(nextAppended).tail
            if (previous !== message && previous.revision != message.revision) {
                changedMessages.put(message.toJson())
            }
        } else if (sameOrder) {
            next.messages.forEachIndexed { index, message ->
                val previous = baseline.messages[index]
                if (previous !== message && previous.revision != message.revision) {
                    changedMessages.put(message.toJson())
                }
            }
        } else {
            val baselineById = baseline.messages.associateBy { message -> message.source.id }
            next.messages.forEach { message ->
                val previous = baselineById[message.source.id]
                if (previous == null || (previous !== message && previous.revision != message.revision)) {
                    changedMessages.put(message.toJson())
                }
            }
        }
        if (changedMessages.length() > 0) {
            patch.put("messages", changedMessages)
        }

        if (
            baseline.historyHasMore != next.historyHasMore ||
            baseline.historyLoading != next.historyLoading
        ) {
            patch.put(
                "meta",
                JSONObject()
                    .put("historyHasMore", next.historyHasMore)
                    .put("historyLoading", next.historyLoading),
            )
        }
        return patch.takeIf { it.length() > 0 }
    }
}
