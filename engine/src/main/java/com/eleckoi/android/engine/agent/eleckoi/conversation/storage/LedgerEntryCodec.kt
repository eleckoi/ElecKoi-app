package com.eleckoi.android.engine.agent.eleckoi.conversation

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentResponseEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.ConversationSpeakerEntity
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal data class LedgerTurnRef(
    val sequence: Int,
    val turnId: String,
)

internal data class LedgerEntry(
    val turn: AgentTurnEntity,
    val speakers: List<ConversationSpeakerEntity>,
    val responses: List<AgentResponseEntity>,
    val parts: List<AgentContentPartEntity>,
) {
    /** Compatibility accessor for the current single-response generation API. */
    val response: AgentResponseEntity? get() = responses.singleOrNull()
}

internal fun ledgerEntries(
    conversationId: String,
    messages: List<LedgerMessage>,
    existingTurnId: (LedgerMessage) -> String? = { null },
): List<LedgerEntry> = buildList {
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        val role = message.role.lowercase()
        val pairedAssistants = if (role == KindUser) {
            messages.drop(index + 1).takeWhile { it.role.equals(KindAssistant, ignoreCase = true) }
        } else {
            emptyList()
        }
        val kind = when {
            message.id == OpeningMessageId -> KindOpening
            role == KindUser -> KindUser
            role == KindSystem -> KindSystem
            else -> KindAssistant
        }
        val turnId = existingTurnId(message) ?: stableLedgerId(
            "turn",
            conversationId,
            kind,
            message.id,
        )
        val turnSpeaker = message.toSpeakerEntity(conversationId)
        val turn = AgentTurnEntity(
            id = turnId,
            conversationId = conversationId,
            speakerId = turnSpeaker.id,
            sourceMessageId = message.id,
            kind = kind,
            provider = message.provider,
            model = message.model,
            createdAt = message.createdAt,
            variableStateJson = message.variableStateJson,
        )
        val turnParts = buildList {
            add(AgentContentPartEntity(
                conversationId = conversationId,
                ownerType = OwnerTurn,
                ownerId = turnId,
                partIndex = 0,
                kind = when (kind) {
                    KindUser -> PartUserText
                    KindOpening -> PartOpeningText
                    KindSystem -> PartSystemText
                    else -> PartAssistantText
                },
                text = message.content,
                payloadJson = "",
            ))
            if (message.inputImageAttachmentsJson != "[]") {
                add(AgentContentPartEntity(
                    conversationId = conversationId,
                    ownerType = OwnerTurn,
                    ownerId = turnId,
                    partIndex = size,
                    kind = PartInputImages,
                    text = "",
                    payloadJson = message.inputImageAttachmentsJson,
                ))
            }
        }
        val responseSpeakers = pairedAssistants.map { it.toSpeakerEntity(conversationId) }
        val responses = pairedAssistants.mapIndexed { responseIndex, assistant ->
            assistant.toResponseEntity(
                conversationId = conversationId,
                turnId = turnId,
                responseIndex = responseIndex,
                speakerInternalId = responseSpeakers[responseIndex].id,
            )
        }
        val responseParts = pairedAssistants.zip(responses).flatMap { (assistant, response) ->
            assistant.toResponseParts(conversationId, response.id)
        }
        add(LedgerEntry(turn, listOf(turnSpeaker) + responseSpeakers, responses, turnParts + responseParts))
        index += 1 + pairedAssistants.size
    }
}

internal fun LedgerMessage.toResponseEntity(
    conversationId: String,
    turnId: String,
    responseIndex: Int = 0,
    speakerInternalId: String = toSpeakerEntity(conversationId).id,
): AgentResponseEntity {
    val responseId = stableLedgerId(
        "response",
        conversationId,
        turnId,
        responseIndex.toString(),
    )
    return AgentResponseEntity(
        id = responseId,
        conversationId = conversationId,
        turnId = turnId,
        responseIndex = responseIndex,
        speakerId = speakerInternalId,
        sourceMessageId = id,
        status = if (pending) StatusPending else StatusCompleted,
        provider = provider,
        model = model,
        createdAt = createdAt,
        variableStateJson = variableStateJson,
        runtimeThreadId = runtimeThreadId,
        runtimeTurnId = runtimeTurnId,
        turnStartedAtMillis = turnStartedAtMillis,
        turnCompletedAtMillis = turnCompletedAtMillis,
    )
}

internal fun LedgerMessage.toSpeakerEntity(conversationId: String): ConversationSpeakerEntity {
    val normalizedRole = role.lowercase().ifBlank { KindAssistant }
    val kind = speakerKind.trim().ifBlank { normalizedRole }
    val sourceId = speakerId.trim().ifBlank { "role:$kind" }
    return ConversationSpeakerEntity(
        id = stableLedgerId("speaker", conversationId, sourceId),
        conversationId = conversationId,
        sourceSpeakerId = sourceId,
        kind = kind,
        displayName = speakerName.trim(),
        avatarAssetId = speakerAvatarAssetId.trim(),
    )
}

internal fun LedgerMessage.toResponseParts(
    conversationId: String,
    responseId: String,
): List<AgentContentPartEntity> = buildList {
    add(
        AgentContentPartEntity(
            conversationId,
            OwnerResponse,
            responseId,
            size,
            PartAssistantText,
            content,
            "",
        ),
    )
    if (reasoningContent.isNotBlank()) {
        add(
            AgentContentPartEntity(
                conversationId,
                OwnerResponse,
                responseId,
                size,
                PartReasoning,
                reasoningContent,
                "",
            ),
        )
    }
    if (toolCallsJson != "[]") {
        add(
            AgentContentPartEntity(
                conversationId,
                OwnerResponse,
                responseId,
                size,
                PartToolCalls,
                "",
                toolCallsJson,
            ),
        )
    }
    if (imageAttachmentsJson != "[]") {
        add(
            AgentContentPartEntity(
                conversationId,
                OwnerResponse,
                responseId,
                size,
                PartImages,
                "",
                imageAttachmentsJson,
            ),
        )
    }
    if (surfaceTimelineJson.isNotBlank()) {
        add(
            AgentContentPartEntity(
                conversationId,
                OwnerResponse,
                responseId,
                size,
                PartSurfaceTimeline,
                "",
                surfaceTimelineJson,
            ),
        )
    }
    if (modelHistoryItems.isNotEmpty()) {
        add(
            AgentContentPartEntity(
                conversationId,
                OwnerResponse,
                responseId,
                size,
                PartModelHistoryItems,
                "",
                ElecKoiJson.encodeToString(modelHistoryItems),
            ),
        )
    }
}

internal fun AgentTurnEntity.toLedgerMessage(
    parts: List<AgentContentPartEntity>,
    speaker: ConversationSpeakerEntity? = null,
): LedgerMessage {
    val text = parts.firstOrNull { it.kind in TextPartKinds }?.text.orEmpty()
    val role = when (kind) {
        KindUser -> KindUser
        KindSystem -> KindSystem
        else -> KindAssistant
    }
    return LedgerMessage(
        id = sourceMessageId,
        role = role,
        content = text,
        provider = provider,
        model = model,
        createdAt = createdAt,
        variableStateJson = variableStateJson,
        inputImageAttachmentsJson = parts.firstOrNull { it.kind == PartInputImages }?.payloadJson ?: "[]",
        speakerId = speaker?.sourceSpeakerId.orEmpty(),
        speakerKind = speaker?.kind.orEmpty(),
        speakerName = speaker?.displayName.orEmpty(),
        speakerAvatarAssetId = speaker?.avatarAssetId.orEmpty(),
    )
}

internal fun AgentResponseEntity.toLedgerMessage(
    parts: List<AgentContentPartEntity>,
    speaker: ConversationSpeakerEntity? = null,
): LedgerMessage =
    LedgerMessage(
        id = sourceMessageId,
        role = KindAssistant,
        content = parts.firstOrNull { it.kind == PartAssistantText }?.text.orEmpty(),
        reasoningContent = parts.firstOrNull { it.kind == PartReasoning }?.text.orEmpty(),
        provider = provider,
        model = model,
        createdAt = createdAt,
        pending = status == StatusPending,
        variableStateJson = variableStateJson,
        toolCallsJson = parts.firstOrNull { it.kind == PartToolCalls }?.payloadJson ?: "[]",
        imageAttachmentsJson = parts.firstOrNull { it.kind == PartImages }?.payloadJson ?: "[]",
        surfaceTimelineJson = parts.firstOrNull { it.kind == PartSurfaceTimeline }?.payloadJson.orEmpty(),
        modelHistoryItems = parts.firstOrNull { it.kind == PartModelHistoryItems }
            ?.payloadJson
            ?.let { payload ->
                runCatching { ElecKoiJson.decodeFromString<List<String>>(payload) }
                    .getOrDefault(emptyList())
            }
            .orEmpty(),
        runtimeThreadId = runtimeThreadId,
        runtimeTurnId = runtimeTurnId,
        turnStartedAtMillis = turnStartedAtMillis,
        turnCompletedAtMillis = turnCompletedAtMillis,
        speakerId = speaker?.sourceSpeakerId.orEmpty(),
        speakerKind = speaker?.kind.orEmpty(),
        speakerName = speaker?.displayName.orEmpty(),
        speakerAvatarAssetId = speaker?.avatarAssetId.orEmpty(),
    )

internal fun encodeDisplayCacheChunks(messages: List<LedgerMessage>): List<String> =
    ElecKoiJson.encodeToString(messages).cursorWindowChunks()

internal fun decodeDisplayCacheChunks(chunks: List<String>): List<LedgerMessage>? = runCatching {
    ElecKoiJson.decodeFromString<List<LedgerMessage>>(chunks.joinToString(separator = ""))
}.getOrNull()

internal fun List<AgentContentPartEntity>.toStorageChunks(): List<AgentContentPartEntity> =
    flatMap { part ->
        val textChunks = part.text.cursorWindowChunks()
        val payloadChunks = part.payloadJson.cursorWindowChunks()
        List(maxOf(textChunks.size, payloadChunks.size)) { index ->
            part.copy(
                text = textChunks.getOrElse(index) { "" },
                payloadJson = payloadChunks.getOrElse(index) { "" },
                chunkIndex = index,
            )
        }
    }

internal data class ContentPartWritePlan(
    val upserts: List<AgentContentPartEntity>,
    val deletes: List<AgentContentPartEntity>,
)

/**
 * Plans a row-level replacement. A growing response normally rewrites only its last partial chunk
 * and appends a new row when that chunk becomes full.
 */
internal fun contentPartWritePlan(
    current: List<AgentContentPartEntity>,
    incoming: List<AgentContentPartEntity>,
): ContentPartWritePlan {
    val currentByKey = current.associateBy(AgentContentPartEntity::storageKey)
    val incomingByKey = incoming.associateBy(AgentContentPartEntity::storageKey)
    return ContentPartWritePlan(
        upserts = incoming.filter { row -> currentByKey[row.storageKey()] != row },
        deletes = current.filter { row -> row.storageKey() !in incomingByKey },
    )
}

internal fun List<AgentContentPartEntity>.mergeStorageChunks(): List<AgentContentPartEntity> =
    groupBy { part ->
        ContentPartKey(part.ownerType, part.ownerId, part.partIndex)
    }.values.map { chunks ->
        val ordered = chunks.sortedBy(AgentContentPartEntity::chunkIndex)
        ordered.first().copy(
            text = ordered.joinToString(separator = "", transform = AgentContentPartEntity::text),
            payloadJson = ordered.joinToString(
                separator = "",
                transform = AgentContentPartEntity::payloadJson,
            ),
            chunkIndex = 0,
        )
    }

/** Splits on a UTF-16 boundary that never separates a surrogate pair; each row stays well below 2 MB. */
internal fun String.cursorWindowChunks(): List<String> {
    if (isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var start = 0
    while (start < length) {
        var end = minOf(start + CursorWindowChunkCharacters, length)
        if (
            end < length &&
            end > start &&
            this[end - 1].isHighSurrogate() &&
            this[end].isLowSurrogate()
        ) {
            end -= 1
        }
        result += substring(start, end)
        start = end
    }
    return result
}

private data class ContentPartKey(
    val ownerType: String,
    val ownerId: String,
    val partIndex: Int,
)

private data class ContentPartStorageKey(
    val ownerType: String,
    val ownerId: String,
    val partIndex: Int,
    val chunkIndex: Int,
)

private fun AgentContentPartEntity.storageKey() = ContentPartStorageKey(
    ownerType = ownerType,
    ownerId = ownerId,
    partIndex = partIndex,
    chunkIndex = chunkIndex,
)

internal fun stableLedgerId(prefix: String, vararg values: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return buildString {
        append(prefix)
        values.forEach { value ->
            append('.')
            append(encoder.encodeToString(value.toByteArray(Charsets.UTF_8)))
        }
    }
}

internal const val OpeningMessageId = "opening"
internal const val OwnerTurn = "turn"
internal const val OwnerResponse = "response"
internal const val KindUser = "user"
internal const val KindAssistant = "assistant"
internal const val KindSystem = "system"
internal const val KindOpening = "opening"
internal const val StatusPending = "pending"
internal const val StatusCompleted = "completed"
internal const val PartUserText = "user_text"
internal const val PartAssistantText = "assistant_text"
internal const val PartOpeningText = "opening_text"
internal const val PartSystemText = "system_text"
internal const val PartReasoning = "reasoning"
internal const val PartToolCalls = "tool_calls"
internal const val PartImages = "images"
internal const val PartInputImages = "input_images"
internal const val PartSurfaceTimeline = "surface_timeline"
internal const val PartModelHistoryItems = "model_history_items"
const val SurfaceRole = "role"
const val SurfaceAssistant = "assistant"
private val TextPartKinds = setOf(PartUserText, PartAssistantText, PartOpeningText, PartSystemText)
internal const val DisplayCacheTurnLimit = 12
internal const val DisplayCacheRendererVersion = 2
internal const val MaxDisplayCacheChunks = 64
internal const val CursorWindowChunkCharacters = 64 * 1024

