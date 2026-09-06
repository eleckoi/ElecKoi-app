package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.engine.agent.api.AgentSettingLibraryTools
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.MessageRole

internal fun requiredSettingLibraryToolPrompt(
    draft: ChatDraft,
    assistantMessageId: String?,
    settingLibraryToolEnabled: Boolean,
): RequiredToolPrompt? {
    if (settingLibraryToolEnabled || assistantMessageId.isNullOrBlank()) return null
    val message = draft.session.messages.firstOrNull {
        it.id == assistantMessageId && it.role == MessageRole.Assistant
    } ?: return null
    if (!message.content.containsRawSettingLibraryToolInvocation()) return null
    return RequiredToolPrompt(
        characterId = draft.session.characterId,
        assistantMessageId = assistantMessageId,
    )
}

internal fun String.containsRawSettingLibraryToolInvocation(): Boolean {
    val normalized = lowercase()
    return "dsml" in normalized &&
        "tool_calls" in normalized &&
        "invoke" in normalized &&
        AgentSettingLibraryTools.any { toolName -> toolName.lowercase() in normalized }
}
