package com.eleckoi.android.feature.chat.ui.cache

import com.eleckoi.android.feature.chat.ui.blocks.markdown.MarkdownRebuildableCaches
import com.eleckoi.android.feature.chat.ui.roleplay.web.host.RoleplayRichHeightCache

/** Clears rebuildable UI data that is scoped to deleted ordinary-chat sessions. */
object ChatDeletionCaches {
    suspend fun clearAfterConversationDeletion(sessionIds: Collection<String>) {
        MarkdownRebuildableCaches.clearAfterConversationDeletion(sessionIds)
        RoleplayRichHeightCache.discardSessions(sessionIds)
    }
}
