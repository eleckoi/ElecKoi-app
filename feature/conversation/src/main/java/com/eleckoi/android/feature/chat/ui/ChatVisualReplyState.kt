package com.eleckoi.android.feature.chat.ui

internal data class ChatVisualReplyKey(
    val messageId: String,
    val generation: Int,
)

internal data class ChatVisualReplyState(
    private val generationsByMessageId: Map<String, Int> = emptyMap(),
    val activeKey: ChatVisualReplyKey? = null,
    val visuallyComplete: Boolean = true,
) {
    val isCompleting: Boolean
        get() = activeKey != null && !visuallyComplete

    fun begin(key: ChatVisualReplyKey): ChatVisualReplyState {
        if (activeKey == key) return this
        return copy(
            generationsByMessageId = generationsByMessageId + (key.messageId to key.generation),
            activeKey = key,
            visuallyComplete = false,
        )
    }

    fun complete(key: ChatVisualReplyKey): ChatVisualReplyState {
        if (key != activeKey || visuallyComplete) return this
        return copy(visuallyComplete = true)
    }

    fun cancel(): ChatVisualReplyState {
        if (!isCompleting) return this
        return copy(visuallyComplete = true)
    }

    fun generationFor(messageId: String?): Int {
        return messageId?.let(generationsByMessageId::get) ?: 0
    }

    fun showStopButton(providerActive: Boolean): Boolean {
        return providerActive || isCompleting
    }
}

internal fun generationVisualReplyKey(
    presentation: ChatGenerationPresentation?,
    sessionId: String,
    latestAssistantMessageId: String?,
): ChatVisualReplyKey? {
    val generation = presentation ?: return null
    val messageId = latestAssistantMessageId ?: return null
    if (
        generation.sessionId != sessionId ||
        generation.assistantMessageId != messageId
    ) {
        return null
    }
    return ChatVisualReplyKey(
        messageId = messageId,
        generation = generation.generation,
    )
}
