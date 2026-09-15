package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.hasRenderableContent

/** One pass owns the screen-wide message aggregates that used to rescan on every live frame. */
internal data class ChatMessagePresentationScan(
    val renderableMessages: List<ChatMessage>,
)

internal fun scanChatMessages(messages: List<ChatMessage>): ChatMessagePresentationScan {
    val renderable = ArrayList<ChatMessage>(messages.size)
    messages.forEach { message ->
        if (message.hasRenderableContent()) renderable += message
    }
    return ChatMessagePresentationScan(
        renderableMessages = renderable,
    )
}

/** Reuses aggregate results for a stable history prefix while only the streamed tail changes. */
internal class ChatMessagePresentationScanCache(
    private val maxStableLists: Int = 4,
) {
    private data class Entry(
        val source: List<ChatMessage>,
        val scan: ChatMessagePresentationScan,
    )

    private val stableLists = ArrayDeque<Entry>()

    init {
        require(maxStableLists > 0) { "消息扫描缓存容量必须大于 0" }
    }

    fun scan(messages: List<ChatMessage>): ChatMessagePresentationScan {
        @Suppress("UNCHECKED_CAST")
        val appended = messages as? ImmutableAppendedList<ChatMessage>
        if (appended == null) return scanStable(messages)

        val prefix = scanStable(appended.prefix)
        val tail = appended.tail
        return ChatMessagePresentationScan(
            renderableMessages = if (tail.hasRenderableContent()) {
                ImmutableAppendedList(prefix.renderableMessages, tail)
            } else {
                prefix.renderableMessages
            },
        )
    }

    private fun scanStable(messages: List<ChatMessage>): ChatMessagePresentationScan {
        val iterator = stableLists.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.source === messages) {
                iterator.remove()
                stableLists.addLast(entry)
                return entry.scan
            }
        }
        val scan = scanChatMessages(messages)
        stableLists.addLast(Entry(messages, scan))
        while (stableLists.size > maxStableLists) stableLists.removeFirst()
        return scan
    }
}

/** Keeps the streamed tail shape when the synthetic opening row is hidden above a Paging gap. */
internal class ChatVisibleMessageWindowCache {
    private var sourcePrefix: List<ChatMessage>? = null
    private var hiddenCount: Int = -1
    private var visiblePrefix: List<ChatMessage> = emptyList()

    fun project(messages: List<ChatMessage>, hiddenPrefixCount: Int): List<ChatMessage> {
        if (hiddenPrefixCount == 0) return messages
        @Suppress("UNCHECKED_CAST")
        val appended = messages as? ImmutableAppendedList<ChatMessage>
        if (appended == null) {
            return messages.subList(hiddenPrefixCount.coerceAtMost(messages.size), messages.size)
        }
        if (sourcePrefix !== appended.prefix || hiddenCount != hiddenPrefixCount) {
            sourcePrefix = appended.prefix
            hiddenCount = hiddenPrefixCount
            visiblePrefix = appended.prefix.subList(
                hiddenPrefixCount.coerceAtMost(appended.prefix.size),
                appended.prefix.size,
            )
        }
        return ImmutableAppendedList(visiblePrefix, appended.tail)
    }
}
