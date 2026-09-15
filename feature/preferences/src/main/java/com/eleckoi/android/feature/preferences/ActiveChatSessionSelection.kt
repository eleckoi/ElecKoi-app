package com.eleckoi.android.feature.preferences

internal data class ActiveChatSessionSelection(
    val lastSessionId: String = "",
    val sessionIdsByContext: Map<String, String> = emptyMap(),
) {
    fun sessionIdFor(characterId: String): String {
        val key = characterId.trim()
        if (key.isBlank()) return ""
        return sessionIdsByContext[key].orEmpty()
    }

    fun remember(
        characterId: String,
        sessionId: String,
    ): ActiveChatSessionSelection {
        val characterKey = characterId.trim()
        val normalizedSessionId = sessionId.trim()
        if (characterKey.isBlank() || normalizedSessionId.isBlank()) return this
        return copy(
            lastSessionId = normalizedSessionId,
            sessionIdsByContext = sessionIdsByContext + (characterKey to normalizedSessionId),
        )
    }

    fun forget(sessionId: String): ActiveChatSessionSelection {
        return forgetAll(setOf(sessionId))
    }

    fun forgetAll(sessionIds: Collection<String>): ActiveChatSessionSelection {
        val deleted = sessionIds.map(String::trim).filter(String::isNotBlank).toSet()
        return copy(
            lastSessionId = lastSessionId.takeUnless { it in deleted }.orEmpty(),
            sessionIdsByContext = sessionIdsByContext.filterValues { it !in deleted },
        )
    }
}
