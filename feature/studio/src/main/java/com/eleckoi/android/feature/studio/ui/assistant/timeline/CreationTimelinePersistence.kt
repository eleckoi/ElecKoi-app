package com.eleckoi.android.feature.studio.ui.assistant.timeline

import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorConversationInputImage
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineKind
import com.eleckoi.android.engine.workspace.model.CreatorConversationWorkItemType
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.feature.studio.ui.assistant.CreationModelChoice
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.conversation.timeline.normalizeCreationWorkspacePaths
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment

internal fun ModelConfig.toCreationModelChoices(): List<CreationModelChoice> {
    val configured = modelOptions.mapNotNull { option ->
        val id = option.id.trim()
        if (id.isBlank()) null else CreationModelChoice(id, id)
    }
    val current = model.trim()
    val currentChoice = current.takeIf(String::isNotBlank)?.let { id ->
        CreationModelChoice(id, id)
    }
    return buildList {
        currentChoice?.let(::add)
        addAll(configured)
    }.distinctBy(CreationModelChoice::id)
}

internal fun List<CreatorWorkspace>.replaceWorkspace(
    updated: CreatorWorkspace,
): List<CreatorWorkspace> =
    map { workspace -> if (workspace.id == updated.id) updated else workspace }

internal fun List<CreatorConversationTimelineItem>.toUiTimeline(): List<CreationTimelineItem> =
    map { item ->
        CreationTimelineItem(
            id = item.id,
            kind = when (item.kind) {
                CreatorConversationTimelineKind.User -> CreationTimelineKind.User
                CreatorConversationTimelineKind.Assistant -> CreationTimelineKind.Assistant
                CreatorConversationTimelineKind.Tool -> CreationTimelineKind.Tool
            },
            text = item.text,
            detail = item.detail,
            running = false,
            failed = item.failed,
            workItemType = item.workItemType?.toAgentWorkItemType(),
            runtimeThreadId = item.runtimeThreadId,
            turnId = item.turnId,
            createdAtMillis = item.createdAtMillis,
            turnStartedAtMillis = item.turnStartedAtMillis,
            completedAtMillis = item.completedAtMillis,
            fileChanges = item.fileChanges,
            paths = normalizeCreationWorkspacePaths(item.paths),
            diff = item.diff,
            turnDiffObserved = item.turnDiffObserved,
            messagePhase = item.messagePhase,
            phaseHeader = item.phaseHeader,
            toolName = item.toolName,
            toolArguments = item.toolArguments,
            delegatedModel = item.delegatedModel,
            childTimeline = item.childTimeline.toUiTimeline(),
            delegatedSessionId = item.delegatedSessionId,
            rawCommand = item.rawCommand,
            commandActions = item.commandActions,
            inputImages = item.inputImages.map(CreatorConversationInputImage::toUiImage),
            modelHistoryItems = item.modelHistoryItems,
        )
    }

internal fun List<CreationTimelineItem>.toStoredTimeline(): List<CreatorConversationTimelineItem> =
    map { item ->
        CreatorConversationTimelineItem(
            id = item.id,
            kind = when (item.kind) {
                CreationTimelineKind.User -> CreatorConversationTimelineKind.User
                CreationTimelineKind.Assistant -> CreatorConversationTimelineKind.Assistant
                CreationTimelineKind.Tool -> CreatorConversationTimelineKind.Tool
            },
            text = item.text,
            detail = item.detail,
            failed = item.failed,
            workItemType = item.workItemType?.toStoredWorkItemType(),
            runtimeThreadId = item.runtimeThreadId,
            turnId = item.turnId,
            createdAtMillis = item.createdAtMillis,
            turnStartedAtMillis = item.turnStartedAtMillis,
            completedAtMillis = item.completedAtMillis,
            fileChanges = item.fileChanges,
            paths = normalizeCreationWorkspacePaths(item.paths),
            diff = item.diff,
            turnDiffObserved = item.turnDiffObserved,
            messagePhase = item.messagePhase,
            phaseHeader = item.phaseHeader,
            toolName = item.toolName,
            toolArguments = item.toolArguments,
            delegatedModel = item.delegatedModel,
            childTimeline = item.childTimeline.toStoredTimeline(),
            delegatedSessionId = item.delegatedSessionId,
            rawCommand = item.rawCommand,
            commandActions = item.commandActions,
            inputImages = item.inputImages.map(ChatUserImageAttachment::toStoredImage),
            modelHistoryItems = item.modelHistoryItems,
        )
    }

private fun CreatorConversationInputImage.toUiImage() = ChatUserImageAttachment(
    id = id,
    localPath = localPath,
    mediaType = mediaType,
    displayName = displayName,
    creatorMediaReference = creatorMediaReference,
    bytes = bytes.coerceAtLeast(0L),
    imageWidth = imageWidth.coerceAtLeast(0),
    imageHeight = imageHeight.coerceAtLeast(0),
)

private fun ChatUserImageAttachment.toStoredImage() = CreatorConversationInputImage(
    id = id,
    localPath = localPath,
    mediaType = mediaType,
    displayName = displayName,
    creatorMediaReference = creatorMediaReference,
    bytes = bytes,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
)

private fun CreatorConversationWorkItemType.toAgentWorkItemType(): AgentWorkItemType = when (this) {
    CreatorConversationWorkItemType.Request -> AgentWorkItemType.Request
    CreatorConversationWorkItemType.Reasoning -> AgentWorkItemType.Reasoning
    CreatorConversationWorkItemType.Command -> AgentWorkItemType.Command
    CreatorConversationWorkItemType.FileChange -> AgentWorkItemType.FileChange
    CreatorConversationWorkItemType.Tool -> AgentWorkItemType.Tool
    CreatorConversationWorkItemType.Action -> AgentWorkItemType.Action
    CreatorConversationWorkItemType.ContextCompaction -> AgentWorkItemType.ContextCompaction
    CreatorConversationWorkItemType.Unknown -> AgentWorkItemType.Unknown
    CreatorConversationWorkItemType.AssistantMessage -> AgentWorkItemType.AssistantMessage
    CreatorConversationWorkItemType.UserMessage -> AgentWorkItemType.UserMessage
}

private fun AgentWorkItemType.toStoredWorkItemType(): CreatorConversationWorkItemType = when (this) {
    AgentWorkItemType.Request -> CreatorConversationWorkItemType.Request
    AgentWorkItemType.Reasoning -> CreatorConversationWorkItemType.Reasoning
    AgentWorkItemType.Command -> CreatorConversationWorkItemType.Command
    AgentWorkItemType.FileChange -> CreatorConversationWorkItemType.FileChange
    AgentWorkItemType.Tool -> CreatorConversationWorkItemType.Tool
    AgentWorkItemType.Action -> CreatorConversationWorkItemType.Action
    AgentWorkItemType.ContextCompaction -> CreatorConversationWorkItemType.ContextCompaction
    AgentWorkItemType.Unknown -> CreatorConversationWorkItemType.Unknown
    AgentWorkItemType.AssistantMessage -> CreatorConversationWorkItemType.AssistantMessage
    AgentWorkItemType.UserMessage -> CreatorConversationWorkItemType.UserMessage
}
