package com.eleckoi.android.feature.chat.ui.author

import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatToolCallRecord
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.model.content.ToolCallState
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.sdk.author.AuthorAgentProcessSnapshot
import com.eleckoi.android.sdk.author.AuthorChatDraftSnapshot
import com.eleckoi.android.sdk.author.AuthorChatListItemSnapshot
import com.eleckoi.android.sdk.author.AuthorChatSessionSnapshot
import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorMediaResourceSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningOptionSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningStateSnapshot
import com.eleckoi.android.sdk.author.AuthorSettingLibrarySnapshot
import com.eleckoi.android.sdk.author.authorMediaResourceUrl
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

internal fun ChatDraft.toAuthorSnapshot(): AuthorChatDraftSnapshot {
    val publicOpenings = openingOptions.map { option ->
        AuthorOpeningSnapshot(
            id = option.id,
            title = option.title,
            content = option.content,
            displayContent = option.content,
            initialVariableStateJson = option.initialVariableStateJson,
        )
    }
    val openingState = AuthorOpeningStateSnapshot(
        items = openingOptions.map { option ->
            AuthorOpeningOptionSnapshot(
                id = option.id,
                title = option.title,
                content = option.content,
                displayContent = option.content,
                initialVariableStateJson = option.initialVariableStateJson,
            )
        },
        selectedId = selectedOpeningOptionId,
        selectionEnabled = openingSelectionEnabled,
    )
    val sessionSnapshot = session.toAuthorSnapshot().let { snapshot ->
        snapshot.copy(
            messages = snapshot.messages.map { message ->
                if (message.id == OpeningMessageId) {
                    message.copy(
                        openingOptions = publicOpenings,
                        selectedOpeningId = selectedOpeningOptionId,
                    )
                } else {
                    message
                }
            },
        )
    }
    return AuthorChatDraftSnapshot(
        session = sessionSnapshot,
        selectedConfigId = selectedModelConfig.id,
        selectedModel = selectedModel,
        settingLibrary = settingLibrary?.toAuthorSnapshot(),
        openings = openingState,
    )
}

private fun SettingLibrary.toAuthorSnapshot(): AuthorSettingLibrarySnapshot {
    val publicDocument = buildJsonObject {
        put("characterId", characterId)
        put("name", name)
        put("entries", buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("iconId", entry.iconId)
                    put("kind", entry.kind.storageValue)
                    put("groupId", entry.groupId)
                    put("content", entry.content)
                    put("openingMessages", buildJsonArray {
                        entry.openingMessages.forEach { opening ->
                            add(buildJsonObject {
                                put("id", opening.id)
                                put("title", opening.title)
                                put("content", opening.content)
                                put("initialVariableStateJson", opening.initialVariableStateJson)
                            })
                        }
                    })
                    put("defaultOpeningMessageId", entry.defaultOpeningMessageId)
                    put("agentSelectionHint", entry.agentSelectionHint)
                    put("agentReadStrategy", entry.agentReadStrategy.storageValue)
                    put("agentReadCondition", entry.agentReadCondition)
                    put("dynamicMode", entry.dynamicMode.storageValue)
                    put("keywords", buildJsonArray { entry.keywords.forEach { add(it) } })
                    put("keywordScanDepth", entry.keywordScanDepth)
                    put("conditionKeywords", buildJsonArray { entry.conditionKeywords.forEach { add(it) } })
                    put("keywordCondition", entry.keywordCondition.storageValue)
                    put("keywordUseRegex", entry.keywordUseRegex)
                    put("keywordIgnoreCase", entry.keywordIgnoreCase)
                    put("keywordWholeWord", entry.keywordWholeWord)
                    put("keywordRecursionDepth", entry.keywordRecursionDepth)
                    val triggerMode = entry.triggerMode
                    if (triggerMode == null) {
                        put("triggerMode", kotlinx.serialization.json.JsonNull)
                    } else {
                        put("triggerMode", triggerMode.storageValue)
                    }
                    put("enabled", entry.enabled)
                    val position = entry.position
                    if (position == null) {
                        put("position", kotlinx.serialization.json.JsonNull)
                    } else {
                        put("position", position.storageValue)
                    }
                    put("promptPositionId", entry.promptPositionId)
                    put("insertRole", entry.insertRole.storageValue)
                    put("order", entry.order)
                    put("viewOrder", entry.viewOrder)
                    put("groupViewOrder", entry.groupViewOrder)
                    put("treeViewOrder", entry.treeViewOrder)
                    put("createdAt", entry.createdAt)
                    put("updatedAt", entry.updatedAt)
                })
            }
        })
        put("groups", buildJsonArray {
            groups.forEach { group ->
                add(buildJsonObject {
                    put("id", group.id)
                    put("name", group.name)
                    put("parentId", group.parentId)
                    put("order", group.order)
                    put("treeViewOrder", group.treeViewOrder)
                    put("createdAt", group.createdAt)
                    put("updatedAt", group.updatedAt)
                })
            }
        })
        put("promptPositions", buildJsonArray {
            promptPositions.forEach { position ->
                add(buildJsonObject {
                    put("id", position.id)
                    put("name", position.name)
                    put("anchor", position.anchor.storageValue)
                    put("order", position.order)
                    put("createdAt", position.createdAt)
                    put("updatedAt", position.updatedAt)
                })
            }
        })
    }
    return AuthorSettingLibrarySnapshot(
        characterId = characterId,
        name = name,
        entryCount = entries.size,
        groupCount = groups.size,
        versionCount = versions.size,
        activeVersionId = activeVersionId,
        document = publicDocument,
    )
}

internal fun ChatSession.toAuthorSnapshot() = AuthorChatSessionSnapshot(
    id = id,
    title = title,
    characterId = characterId,
    characterName = characterName,
    characterAvatar = characterAvatar,
    characterPersona = buildJsonObject {
        put("characterId", characterPersona.characterId)
        put("characterName", characterPersona.characterName)
        put("assistantName", characterPersona.assistantName)
        put("assistantAvatar", characterPersona.assistantAvatar)
        put("assistantSquare", characterPersona.assistantSquare)
        put("assistantCover", characterPersona.assistantCover)
        put("userName", characterPersona.userName)
        put("userAvatar", characterPersona.userAvatar)
        put("userSquare", characterPersona.userSquare)
        put("userPortrait", characterPersona.userPortrait)
    },
    messages = messages.mapIndexed { index, message ->
        message.toAuthorSnapshot(conversationId = id, sequence = index)
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
    variableStateJson = variableStateJson,
)

internal fun ChatListItem.toAuthorSnapshot() = AuthorChatListItemSnapshot(
    id = id,
    title = title,
    characterId = characterId,
    characterName = characterName,
    characterAvatar = characterAvatar,
    summary = summary,
    updatedAt = updatedAt,
    messageCount = messageCount,
    createdAt = createdAt,
)

internal fun ChatMessage.toAuthorSnapshot(
    conversationId: String = "",
    sequence: Int? = null,
) = AuthorMessageSnapshot(
    id = id,
    conversationId = conversationId,
    role = role.name.lowercase(),
    content = content,
    displayContent = content,
    reasoningContent = reasoningContent,
    provider = provider,
    model = model,
    createdAt = createdAt,
    pending = pending,
    status = if (pending) "streaming" else "complete",
    variableStateJson = variableStateJson,
    turnId = runtimeTurnId,
    speakerId = speakerId,
    speakerName = speakerName,
    speakerAvatar = speakerAvatarAssetId,
    sequence = sequence,
    runtimeThreadId = runtimeThreadId,
    turnStartedAtMillis = turnStartedAtMillis,
    turnCompletedAtMillis = turnCompletedAtMillis,
    modelHistoryItems = modelHistoryItems,
    process = toolCalls.flatMap { call -> call.toAuthorProcess(parentId = null) },
    attachments = inputImageAttachments.map { attachment ->
        AuthorMediaResourceSnapshot(
            id = attachment.id,
            type = "image",
            url = authorMediaResourceUrl(id, attachment.id),
            mimeType = attachment.mediaType,
            name = attachment.displayName.ifBlank { "图片" },
            size = attachment.bytes,
            width = attachment.imageWidth.takeIf { it > 0 },
            height = attachment.imageHeight.takeIf { it > 0 },
            sourcePath = attachment.localPath,
        )
    },
)

private fun ChatToolCallRecord.toAuthorProcess(parentId: String?): List<AuthorAgentProcessSnapshot> {
    val processId = callId.ifBlank { name }
    val item = AuthorAgentProcessSnapshot(
        id = processId,
        kind = when {
            narrative -> "narrative"
            workItemType == AgentWorkItemType.Reasoning -> "reasoning"
            workItemType == AgentWorkItemType.Command -> "command"
            workItemType == AgentWorkItemType.FileChange -> "file_change"
            workItemType == AgentWorkItemType.ContextCompaction -> "compaction"
            delegatedModel.isNotBlank() || childCalls.isNotEmpty() -> "subagent"
            workItemType == AgentWorkItemType.Action -> "action"
            else -> "tool"
        },
        status = when (state) {
            ToolCallState.Pending, ToolCallState.Running -> "running"
            ToolCallState.Succeeded -> "complete"
            ToolCallState.Failed -> "error"
        },
        toolName = toolName.ifBlank { name },
        arguments = arguments,
        summary = name,
        detail = result,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        parentId = parentId,
        delegatedModel = delegatedModel.takeIf(String::isNotBlank),
    )
    return listOf(item) + childCalls.flatMap { child -> child.toAuthorProcess(parentId = processId) }
}
