package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.foundation.storage.ElecKoiDataException

internal data class RegenerationTimeline(
    val messages: List<ChatMessage>,
    val prompt: String,
    val replacementMessageId: String?,
    val removedImagePaths: List<String>,
    val obsoleteRuntimeThreadIds: Set<String>,
    val retainedVariableStateJson: String?,
)

/** A terminal duration rendered from the local clock must never move behind event delivery. */
internal fun stableChatTurnCompletionAtMillis(
    providerCompletedAtMillis: Long,
    locallyObservedAtMillis: Long,
): Long = maxOf(providerCompletedAtMillis, locallyObservedAtMillis)

/** Restores Room's authoritative raw-message branch before a generation starts. */
internal fun authoritativeGenerationSession(
    persisted: ChatSession,
    activeMessages: List<ChatMessage>,
): ChatSession = persisted.copy(messages = activeMessages)

internal fun regenerationSessionVariableState(
    currentStateJson: String,
    retainedStateJson: String?,
    variablesConfigured: Boolean,
): String {
    if (retainedStateJson != null) return retainedStateJson
    if (variablesConfigured) {
        throw ElecKoiDataException(
            "这条历史分支缺少变量快照，无法安全回滚后重新生成",
        )
    }
    return currentStateJson
}
/** 保留目标回复对应的用户提问；目标本身是用户消息时，也可直接从该消息重新请求。 */
internal fun truncateForRegeneration(
    messages: List<ChatMessage>,
    targetMessageId: String,
    replacementMessage: String?,
    provider: String,
    model: String,
): RegenerationTimeline {
    val truncated = messages.toMutableList()
    val targetIndex = truncated.indexOfFirst { it.id == targetMessageId }
    if (targetIndex < 0) throw ElecKoiDataException("没有找到要重新生成的消息")
    val editingUserInput = replacementMessage != null
    val branchUserIndex = when (truncated[targetIndex].role) {
        MessageRole.Assistant -> (targetIndex - 1 downTo 0)
            .firstOrNull { truncated[it].role == MessageRole.User }
            ?: throw ElecKoiDataException("没有找到这条回复对应的用户输入")
        MessageRole.User -> targetIndex
        MessageRole.System -> throw ElecKoiDataException("这条消息不能重新生成")
    }
    val replacementMessageId = if (!editingUserInput) {
        truncated[targetIndex].takeIf { it.role == MessageRole.Assistant }?.id
    } else {
        null
    }
    replacementMessage?.trim()?.let { replacement ->
        if (replacement.isEmpty()) throw ElecKoiDataException("输入不能为空")
        truncated[branchUserIndex] = truncated[branchUserIndex].copy(
            content = replacement,
            provider = provider,
            model = model,
        )
    }
    val userText = truncated[branchUserIndex].content.trim()
    if (userText.isEmpty()) throw ElecKoiDataException("用户输入为空，不能重新生成")
    val removedImagePaths = truncated
        .drop(branchUserIndex + 1)
        .flatMap { message -> message.imageAttachments.map { image -> image.localPath } }
        .filter(String::isNotBlank)
    val obsoleteRuntimeThreadIds = truncated
        .map(ChatMessage::runtimeThreadId)
        .filter(String::isNotBlank)
        .toSet()
    val retainedMessages = truncated.take(branchUserIndex + 1).map { message ->
        // A DSH thread represents the whole branch. Regeneration invalidates that thread even
        // when some earlier messages remain, so the UI must not reopen its now-obsolete trace.
        message.copy(runtimeThreadId = "", runtimeTurnId = "")
    }
    return RegenerationTimeline(
        messages = retainedMessages,
        prompt = userText,
        replacementMessageId = replacementMessageId,
        removedImagePaths = removedImagePaths,
        obsoleteRuntimeThreadIds = obsoleteRuntimeThreadIds,
        retainedVariableStateJson = retainedMessages
            .asReversed()
            .firstNotNullOfOrNull { message ->
                message.variableStateJson.takeIf(String::isNotBlank)
            },
    )
}
