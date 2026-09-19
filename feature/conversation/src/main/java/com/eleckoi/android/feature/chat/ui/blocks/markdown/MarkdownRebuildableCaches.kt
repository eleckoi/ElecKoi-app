package com.eleckoi.android.feature.chat.ui.blocks.markdown

import com.eleckoi.android.feature.chat.data.markdown.MarkdownDocumentCache
import com.eleckoi.android.feature.chat.data.markdown.MarkdownDocumentDiskCache
import com.eleckoi.android.feature.chat.data.markdown.MarkdownLiveDocumentHandoffCache
import com.eleckoi.android.feature.chat.data.stream.ChatContentBlockCache
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderBlockCache
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlanCache
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.code.CanvasCodePaintPool
import com.eleckoi.android.feature.chat.ui.blocks.markdown.render.mermaid.MermaidBitmapCache

/** Releases rebuildable renderer data only when Android reports real process memory pressure. */
object MarkdownRebuildableCaches {
    fun clear() {
        MarkdownDocumentCache.clear()
        MarkdownLiveDocumentHandoffCache.clear()
        ChatContentBlockCache.clear()
        MarkdownRenderPlanCache.clear()
        MarkdownRenderBlockCache.clear()
        MermaidBitmapCache.clear()
        CanvasCodePaintPool.clear()
    }

    /** Removes deleted chat text without making every other active conversation cold. */
    fun clearAfterConversationDeletion(sessionIds: Collection<String>) {
        val scopeKeys = sessionIds
            .asSequence()
            .filter(String::isNotBlank)
            .flatMap { sessionId -> sequenceOf("chat:$sessionId", "creation:$sessionId") }
            .toSet()
        if (scopeKeys.isEmpty()) return
        MarkdownDocumentCache.removeScopes(scopeKeys)
        MarkdownLiveDocumentHandoffCache.removeScopes(scopeKeys)
        ChatContentBlockCache.removeScopes(scopeKeys)
        MarkdownRenderPlanCache.removeScopes(scopeKeys)
        MarkdownRenderBlockCache.removeScopes(scopeKeys)
        // Mermaid caches are content-addressed rather than session-addressed and their keys can
        // contain the original diagram source, so deletion clears that small global tier too.
        MermaidBitmapCache.clear()
        // Disk entries are content-addressed and deliberately have no session identity. Deletion
        // therefore clears that rebuildable tier as a whole so deleted message text cannot remain.
        MarkdownDocumentDiskCache.clear()
    }
}
