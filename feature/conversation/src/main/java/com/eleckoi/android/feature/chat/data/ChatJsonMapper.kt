package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.ChatToolCallRecord
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal fun chatHistoryJsonString(
    exportedAt: String,
    characterId: String,
    characterName: String,
    sessions: List<ChatSession>,
): String {
    return ElecKoiPrettyJson.encodeToString(
        ChatHistoryJson(
            exportedAt = exportedAt,
            characterId = characterId,
            characterName = characterName,
            sessionIds = sessions.map { it.id },
            sessions = sessions.map(ChatSessionJson::fromDomain),
        ),
    )
}

internal fun chatSessionsFromHistoryJson(value: String): List<ChatSession> {
    return ElecKoiJson.decodeFromString<ChatHistoryJson>(value).sessions.map { it.toDomain() }
}

internal fun characterPersonaJsonString(persona: CharacterCard): String {
    return ElecKoiJson.encodeToString(CharacterPersonaJson.fromDomain(persona))
}

internal fun characterPersonaFromJsonString(
    value: String,
    characterName: String,
    characterAvatar: String,
): CharacterCard {
    val persona = runCatching {
        ElecKoiJson.decodeFromString<CharacterPersonaJson>(value.ifBlank { "{}" })
    }.getOrDefault(CharacterPersonaJson())
    return persona.toDomain(characterName, characterAvatar)
}

internal fun toolCallsJsonString(calls: List<ChatToolCallRecord>): String {
    return ElecKoiJson.encodeToString(calls.map(ChatToolCallJson::fromDomain))
}

internal fun toolCallsFromJsonString(value: String): List<ChatToolCallRecord> {
    return runCatching {
        ElecKoiJson.decodeFromString<List<ChatToolCallJson>>(value.ifBlank { "[]" }).map { it.toDomain() }
    }.getOrDefault(emptyList())
}

internal fun imageAttachmentsJsonString(images: List<ChatImageAttachment>): String {
    return ElecKoiJson.encodeToString(images.map(ChatImageAttachmentJson::fromDomain))
}

internal fun imageAttachmentsFromJsonString(value: String): List<ChatImageAttachment> {
    return runCatching {
        ElecKoiJson.decodeFromString<List<ChatImageAttachmentJson>>(value.ifBlank { "[]" })
            .map(ChatImageAttachmentJson::toDomain)
    }.getOrDefault(emptyList())
}

internal fun inputImageAttachmentsJsonString(images: List<ChatUserImageAttachment>): String =
    ElecKoiJson.encodeToString(images.map(ChatUserImageAttachmentJson::fromDomain))

internal fun inputImageAttachmentsFromJsonString(value: String): List<ChatUserImageAttachment> =
    runCatching {
        ElecKoiJson.decodeFromString<List<ChatUserImageAttachmentJson>>(value.ifBlank { "[]" })
            .map(ChatUserImageAttachmentJson::toDomain)
    }.getOrDefault(emptyList())

@Serializable
private data class ChatHistoryJson(
    val format: String = "eleckoi.chat-history",
    val version: Int = 3,
    @SerialName("exported_at")
    val exportedAt: String = "",
    @SerialName("character_id")
    val characterId: String = "",
    @SerialName("character_name")
    val characterName: String = "",
    @SerialName("session_ids")
    val sessionIds: List<String> = emptyList(),
    val sessions: List<ChatSessionJson> = emptyList(),
)

@Serializable
private data class ChatSessionJson(
    val id: String = "",
    @SerialName("workspace_id")
    val workspaceId: String = "",
    val title: String = "",
    @SerialName("character_id")
    val characterId: String = "",
    @SerialName("character_name")
    val characterName: String = "",
    @SerialName("character_avatar")
    val characterAvatar: String = "",
    @SerialName("permission_mode")
    val permissionMode: String = AgentPermissionMode.AskForApproval.name,
    @SerialName("character_persona")
    val characterPersona: CharacterPersonaJson = CharacterPersonaJson(),
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("initial_variable_state_json")
    val initialVariableStateJson: String = "",
    @SerialName("variable_state_json")
    val variableStateJson: String = "",
    @SerialName("generation_stats")
    val generationStats: ChatSessionGenerationStatsJson? = null,
    val messages: List<ChatMessageJson> = emptyList(),
) {
    fun toDomain(): ChatSession {
        val domainMessages = messages.map { it.toDomain() }
        return ChatSession(
            id = id,
            workspaceId = workspaceId,
            title = title.ifBlank { characterName.ifBlank { "新对话" } },
            characterId = characterId,
            characterName = characterName,
            characterAvatar = characterAvatar,
            characterPersona = characterPersona.toDomain(characterName, characterAvatar),
            permissionMode = AgentPermissionMode.entries.firstOrNull {
                it.name.equals(permissionMode, ignoreCase = true)
            } ?: AgentPermissionMode.AskForApproval,
            messages = domainMessages,
            createdAt = createdAt,
            updatedAt = updatedAt,
            initialVariableStateJson = initialVariableStateJson,
            variableStateJson = variableStateJson,
            generationStats = generationStats?.takeIf { it.version == 1 }?.toDomain()
                ?: inferLegacyGenerationStats(domainMessages),
        )
    }

    companion object {
        fun fromDomain(session: ChatSession): ChatSessionJson {
            return ChatSessionJson(
                id = session.id,
                workspaceId = session.workspaceId,
                title = session.title,
                characterId = session.characterId,
                characterName = session.characterName,
                characterAvatar = session.characterAvatar,
                permissionMode = session.permissionMode.name,
                characterPersona = CharacterPersonaJson.fromDomain(session.characterPersona),
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                initialVariableStateJson = session.initialVariableStateJson,
                variableStateJson = session.variableStateJson,
                generationStats = ChatSessionGenerationStatsJson.fromDomain(session.generationStats),
                messages = session.messages.map(ChatMessageJson::fromDomain),
            )
        }
    }
}

private fun inferLegacyGenerationStats(
    messages: List<com.eleckoi.android.feature.chat.model.ChatMessage>,
): ChatSessionGenerationStats {
    val runtimeThreadId = messages.asReversed()
        .firstOrNull { it.role == MessageRole.Assistant && it.runtimeThreadId.isNotBlank() }
        ?.runtimeThreadId
        .orEmpty()
    if (runtimeThreadId.isBlank()) return ChatSessionGenerationStats()
    val matching = messages.filter {
        it.role == MessageRole.Assistant && it.runtimeThreadId == runtimeThreadId
    }
    return ChatSessionGenerationStats(
        runtimeThreadId = runtimeThreadId,
        metrics = matching.fold(com.eleckoi.android.feature.chat.model.ChatGenerationMetrics()) { total, message ->
            total + message.generationMetrics
        },
        contextWindowUsage = matching.asReversed()
            .firstNotNullOfOrNull { it.contextWindowUsage },
    )
}

@Serializable
private data class CharacterPersonaJson(
    @SerialName("assistant_name")
    val assistantName: String = "",
    @SerialName("assistant_avatar")
    val assistantAvatar: String = "",
    @SerialName("assistant_cover")
    val assistantCover: String = "",
    @SerialName("image_prompt")
    val imagePrompt: String = "",
    val opening: String = "",
    @SerialName("show_opening")
    val showOpening: Boolean = false,
    @SerialName("chat_background")
    val chatBackground: String = "",
    @SerialName("chat_background_opacity")
    val chatBackgroundOpacity: Float = 0.72f,
    @SerialName("chat_background_blur")
    val chatBackgroundBlur: Float = 0f,
    @SerialName("chat_background_scrim")
    val chatBackgroundScrim: Float = 0.22f,
) {
    fun toDomain(characterName: String, characterAvatar: String): CharacterCard {
        return CharacterCard(
            characterId = "",
            characterName = characterName,
            characterAvatar = characterAvatar,
            assistantName = assistantName.ifBlank { characterName },
            assistantAvatar = assistantAvatar.ifBlank { characterAvatar },
            assistantCover = assistantCover,
            imagePrompt = imagePrompt,
            opening = opening,
            showOpening = showOpening,
            chatBackground = chatBackground,
            chatBackgroundOpacity = chatBackgroundOpacity.coerceIn(0f, 1f),
            chatBackgroundBlur = chatBackgroundBlur.coerceIn(0f, 24f),
            chatBackgroundScrim = chatBackgroundScrim.coerceIn(0f, 1f),
        )
    }

    companion object {
        fun fromDomain(persona: CharacterCard): CharacterPersonaJson {
            return CharacterPersonaJson(
                assistantName = persona.assistantName,
                assistantAvatar = persona.assistantAvatar,
                assistantCover = persona.assistantCover,
                imagePrompt = persona.imagePrompt,
                opening = persona.opening,
                showOpening = persona.showOpening,
                chatBackground = persona.chatBackground,
                chatBackgroundOpacity = persona.chatBackgroundOpacity,
                chatBackgroundBlur = persona.chatBackgroundBlur,
                chatBackgroundScrim = persona.chatBackgroundScrim,
            )
        }
    }
}
