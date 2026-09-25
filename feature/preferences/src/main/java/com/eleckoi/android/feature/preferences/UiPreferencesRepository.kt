package com.eleckoi.android.feature.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException

private val Context.uiPreferencesDataStore by preferencesDataStore(name = "ui_preferences")

class UiPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.uiPreferencesDataStore
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storedPreferencesFlow: Flow<UiPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences.toUiPreferences() }

    val preferencesFlow: StateFlow<UiPreferences> = storedPreferencesFlow.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = UiPreferences(),
    )

    /** Exact persisted snapshot for suspend workflows; [preferencesFlow] serves non-blocking UI reads. */
    suspend fun read(): UiPreferences = storedPreferencesFlow.first()

    /**
     * Portable snapshot of the DataStore values. The type is stored next to every value because
     * DataStore's key declarations are intentionally private to this feature.
     */
    suspend fun exportSnapshotJson(): String {
        val values = dataStore.data.first().asMap().entries.associate { (key, value) ->
            key.name to buildJsonObject {
                when (value) {
                    is Boolean -> {
                        put("type", "boolean")
                        put("value", value)
                    }
                    is Float -> {
                        put("type", "float")
                        put("value", value)
                    }
                    is Double -> {
                        put("type", "double")
                        put("value", value)
                    }
                    is Int -> {
                        put("type", "int")
                        put("value", value)
                    }
                    is Long -> {
                        put("type", "long")
                        put("value", value)
                    }
                    is String -> {
                        put("type", "string")
                        put("value", value)
                    }
                    is Set<*> -> {
                        put("type", "string_set")
                        put("value", buildJsonArray {
                            value.filterIsInstance<String>().forEach { add(JsonPrimitive(it)) }
                        })
                    }
                    else -> error("不支持的偏好类型：${value::class.qualifiedName}")
                }
            }
        }
        return ElecKoiPrettyJson.encodeToString(
            buildJsonObject {
                put("format", "eleckoi.ui-preferences")
                put("version", 1)
                put("values", JsonObject(values))
            },
        )
    }

    /** Restores a complete snapshot and deliberately rejects unknown value types. */
    suspend fun restoreSnapshotJson(json: String) {
        val root = ElecKoiJson.parseToJsonElement(json).jsonObject
        require(root["format"]?.jsonPrimitive?.content == "eleckoi.ui-preferences") {
            "偏好备份格式不正确"
        }
        require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "不支持的偏好备份版本" }
        val values = root["values"]?.jsonObject ?: error("偏好备份缺少 values")
        dataStore.edit { preferences ->
            preferences.clear()
            values.forEach { (name, encoded) ->
                val value = encoded.jsonObject["value"] ?: error("偏好缺少值：$name")
                when (encoded.jsonObject["type"]?.jsonPrimitive?.content) {
                    "boolean" -> preferences[booleanPreferencesKey(name)] =
                        value.jsonPrimitive.booleanOrNull ?: error("偏好值无效：$name")
                    "float" -> preferences[floatPreferencesKey(name)] =
                        value.jsonPrimitive.floatOrNull ?: error("偏好值无效：$name")
                    "double" -> preferences[doublePreferencesKey(name)] =
                        value.jsonPrimitive.doubleOrNull ?: error("偏好值无效：$name")
                    "int" -> preferences[intPreferencesKey(name)] =
                        value.jsonPrimitive.intOrNull ?: error("偏好值无效：$name")
                    "long" -> preferences[longPreferencesKey(name)] =
                        value.jsonPrimitive.longOrNull ?: error("偏好值无效：$name")
                    "string" -> preferences[stringPreferencesKey(name)] =
                        value.jsonPrimitive.contentOrNull ?: error("偏好值无效：$name")
                    "string_set" -> preferences[stringSetPreferencesKey(name)] =
                        value.jsonArray.map { it.jsonPrimitive.content }
                            .toSet()
                    else -> error("不支持的偏好类型：$name")
                }
            }
        }
    }

    // Writes land in one of three explicit profile buckets. No current-layout value is shared.
    private suspend fun writeChatMetric(
        agent: Preferences.Key<Float>,
        social: Preferences.Key<Float>,
        roleplay: Preferences.Key<Float>,
        value: Float,
    ): UiPreferences {
        dataStore.edit { preferences ->
            val mode = currentLayoutMode(preferences)
            preferences[profileKey(agent, social, roleplay, mode)] = value
        }
        return read()
    }

    private fun currentLayoutMode(preferences: Preferences): ChatLayoutMode =
        ChatLayoutMode.fromStorageKey(preferences[ChatLayoutModeKey])

    suspend fun setPinnedChatIds(ids: List<String>): UiPreferences {
        dataStore.edit { preferences ->
            preferences[PinnedChatIdsJson] = encodeStringList(ids)
        }
        return read()
    }

    suspend fun setHiddenChatIds(ids: List<String>): UiPreferences {
        dataStore.edit { preferences ->
            preferences[HiddenChatIdsJson] = encodeStringList(ids)
        }
        return read()
    }

    suspend fun setSearchHistory(terms: List<String>): UiPreferences {
        dataStore.edit { preferences ->
            preferences[SearchHistoryJson] = encodeStringList(terms)
        }
        return read()
    }

    /**
     * A hidden message-home entry is a presentation preference, not conversation deletion.
     * New activity in that exact conversation makes it relevant again, so restore only its ID
     * while preserving every other hidden entry.
     */
    suspend fun restoreChatEntry(sessionId: String): UiPreferences {
        val normalizedId = sessionId.trim()
        if (normalizedId.isBlank()) return read()
        dataStore.edit { preferences ->
            val hidden = preferences[HiddenChatIdsJson]
                ?.let(::decodeStringList)
                .orEmpty()
            val restored = hidden.restoreChatEntry(normalizedId)
            if (restored != hidden) {
                preferences[HiddenChatIdsJson] = encodeStringList(restored)
            }
        }
        return read()
    }

    suspend fun setActiveChatSessionId(
        characterId: String,
        sessionId: String,
    ): UiPreferences {
        dataStore.edit { preferences ->
            val next = ActiveChatSessionSelection(
                lastSessionId = preferences[LastActiveChatSessionId].orEmpty(),
                sessionIdsByContext = preferences[ActiveChatSessionIdsJson]
                    ?.let(::decodeStringMap)
                    .orEmpty(),
            ).remember(characterId, sessionId)
            preferences[LastActiveChatSessionId] = next.lastSessionId
            preferences[ActiveChatSessionIdsJson] = encodeStringMap(next.sessionIdsByContext)
        }
        return read()
    }

    suspend fun removeActiveChatSessionId(sessionId: String): UiPreferences {
        return removeChatSessionIds(listOf(sessionId))
    }

    suspend fun removeChatSessionIds(sessionIds: Collection<String>): UiPreferences {
        val deleted = sessionIds.filter(String::isNotBlank).toSet()
        if (deleted.isEmpty()) return read()
        dataStore.edit { preferences ->
            val next = ActiveChatSessionSelection(
                lastSessionId = preferences[LastActiveChatSessionId].orEmpty(),
                sessionIdsByContext = preferences[ActiveChatSessionIdsJson]
                    ?.let(::decodeStringMap)
                    .orEmpty(),
            ).forgetAll(deleted)
            preferences[LastActiveChatSessionId] = next.lastSessionId
            preferences[ActiveChatSessionIdsJson] = encodeStringMap(next.sessionIdsByContext)
            listOf(PinnedChatIdsJson, HiddenChatIdsJson).forEach { key ->
                preferences[key] = encodeStringList(
                    preferences[key]?.let(::decodeStringList).orEmpty().filterNot { it in deleted },
                )
            }
        }
        return read()
    }

    suspend fun activeChatSessionId(characterId: String): String {
        return read().activeChatSessionId(characterId)
    }

    suspend fun lastActiveChatSessionId(): String = read().lastActiveChatSessionId

    suspend fun setPinnedCreatorWorkspaceIds(ids: List<String>): UiPreferences {
        dataStore.edit { preferences ->
            preferences[PinnedCreatorWorkspaceIdsJson] = encodeStringList(ids)
        }
        return read()
    }

    suspend fun removeCreatorWorkspaceIds(workspaceIds: Collection<String>) {
        val deleted = workspaceIds.toSet()
        dataStore.edit { preferences ->
            preferences[PinnedCreatorWorkspaceIdsJson] = encodeStringList(
                preferences[PinnedCreatorWorkspaceIdsJson]?.let(::decodeStringList).orEmpty()
                    .filterNot { it in deleted },
            )
            preferences[CreatorWorkspaceExpansionOverridesJson] = encodeBooleanMap(
                preferences[CreatorWorkspaceExpansionOverridesJson]?.let(::decodeBooleanMap).orEmpty()
                    .filterKeys { it !in deleted },
            )
            if (preferences[LastCreatorWorkspaceId] in deleted) preferences.remove(LastCreatorWorkspaceId)
        }
    }

    suspend fun setCreatorWorkspaceExpansionOverrides(
        overrides: Map<String, Boolean>,
    ): UiPreferences {
        dataStore.edit { preferences ->
            preferences[CreatorWorkspaceExpansionOverridesJson] = encodeBooleanMap(overrides)
        }
        return read()
    }

    suspend fun setLastCreatorWorkspaceId(workspaceId: String): UiPreferences {
        dataStore.edit { preferences ->
            preferences[LastCreatorWorkspaceId] = workspaceId.trim()
        }
        return read()
    }

    suspend fun setCreatorAssistantEnabledToolGroupIds(groupIds: Set<String>): UiPreferences {
        dataStore.edit { preferences ->
            preferences[CreatorAssistantEnabledToolGroupIds] = groupIds
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()
        }
        return read()
    }

    suspend fun setCreatorAssistantImageModelConfigId(configId: String): UiPreferences {
        dataStore.edit { preferences ->
            preferences[CreatorAssistantImageModelConfigId] = configId.trim()
        }
        return read()
    }

    suspend fun setHistorySaveMode(mode: String): UiPreferences {
        dataStore.edit { preferences ->
            preferences[HistorySaveMode] = normalizeHistoryMode(mode)
        }
        return read()
    }

    suspend fun setDefaultChatModel(configId: String, model: String): UiPreferences {
        dataStore.edit { preferences ->
            preferences[DefaultChatConfigId] = configId.trim()
            preferences[DefaultChatModel] = model.trim()
        }
        return read()
    }

    suspend fun setListCharacterArtwork(artwork: ListCharacterArtwork): UiPreferences {
        dataStore.edit { preferences ->
            preferences[SidebarCharacterArtwork] = artwork.storageKey
        }
        return read()
    }

    suspend fun setAppearanceMode(mode: AppearanceMode): UiPreferences {
        dataStore.edit { preferences -> preferences[AppearanceModeKey] = mode.storageKey }
        return read()
    }

    suspend fun setNewCharacterBackground(background: NewCharacterBackground): UiPreferences {
        dataStore.edit { preferences ->
            preferences[NewCharacterBackgroundKey] = background.storageKey
        }
        return read()
    }

    suspend fun setAssistantBubbleEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences ->
            val mode = currentLayoutMode(preferences)
            preferences[
                profileKey(
                    AssistantBubbleEnabledAgent,
                    AssistantBubbleEnabledSocial,
                    AssistantBubbleEnabledRoleplay,
                    mode,
                ),
            ] = resolveAssistantBubbleEnabled(mode, enabled)
        }
        return read()
    }

    // Remove only the selected profile. The active layout and the other two tuned profiles survive.
    suspend fun resetChatLayoutPreferences(mode: ChatLayoutMode): UiPreferences {
        dataStore.edit { preferences ->
            preferences.remove(profileKey(ChatAvatarShapeAgent, ChatAvatarShapeSocial, ChatAvatarShapeRoleplay, mode))
            preferences.remove(
                profileKey(
                    AssistantBubbleEnabledAgent,
                    AssistantBubbleEnabledSocial,
                    AssistantBubbleEnabledRoleplay,
                    mode,
                ),
            )
            preferences.remove(profileKey(ChatBubbleCornerRadiusAgent, ChatBubbleCornerRadiusSocial, ChatBubbleCornerRadiusRoleplay, mode))
            preferences.remove(profileKey(ChatAvatarSizeAgent, ChatAvatarSizeSocial, ChatAvatarSizeRoleplay, mode))
            preferences.remove(profileKey(ChatNameFontSizeAgent, ChatNameFontSizeSocial, ChatNameFontSizeRoleplay, mode))
            preferences.remove(profileKey(ChatNameAvatarSpacingAgent, ChatNameAvatarSpacingSocial, ChatNameAvatarSpacingRoleplay, mode))
            preferences.remove(profileKey(ChatAreaHorizontalPaddingAgent, ChatAreaHorizontalPaddingSocial, ChatAreaHorizontalPaddingRoleplay, mode))
            preferences.remove(profileKey(ChatReplySpacingAgent, ChatReplySpacingSocial, ChatReplySpacingRoleplay, mode))
            preferences.remove(profileKey(ChatTurnSpacingAgent, ChatTurnSpacingSocial, ChatTurnSpacingRoleplay, mode))
            preferences.remove(profileKey(ChatMessageFontSizeAgent, ChatMessageFontSizeSocial, ChatMessageFontSizeRoleplay, mode))
            preferences.remove(profileKey(ChatLineHeightMultiplierAgent, ChatLineHeightMultiplierSocial, ChatLineHeightMultiplierRoleplay, mode))
            preferences.remove(profileKey(ChatLetterSpacingAgent, ChatLetterSpacingSocial, ChatLetterSpacingRoleplay, mode))
            preferences.remove(profileKey(ChatParagraphSpacingAgent, ChatParagraphSpacingSocial, ChatParagraphSpacingRoleplay, mode))
            preferences.remove(
                profileKey(
                    ChatTimelineThinkingAnimationAgent,
                    ChatTimelineThinkingAnimationSocial,
                    ChatTimelineThinkingAnimationRoleplay,
                    mode,
                ),
            )
            if (mode == ChatLayoutMode.Roleplay) {
                preferences.remove(ChatRoleplayCardPanel)
                preferences.remove(ChatRoleplayTimestampsEnabled)
                preferences.remove(ChatRoleplayMessageFloorsEnabled)
            }
        }
        return read()
    }

    suspend fun setChatTimelineThinkingAnimation(
        animation: ChatTimelineThinkingAnimation,
    ): UiPreferences {
        dataStore.edit { preferences ->
            val mode = currentLayoutMode(preferences)
            preferences[
                profileKey(
                    ChatTimelineThinkingAnimationAgent,
                    ChatTimelineThinkingAnimationSocial,
                    ChatTimelineThinkingAnimationRoleplay,
                    mode,
                ),
            ] = animation.storageKey
        }
        return read()
    }

    suspend fun setChatLayoutMode(mode: ChatLayoutMode): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatLayoutModeKey] = mode.storageKey
        }
        return read()
    }

    suspend fun setChatReasoningDisplayMode(mode: ChatReasoningDisplayMode): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatReasoningDisplayModeKey] = mode.storageKey
        }
        return read()
    }

    suspend fun setChatToolTimelineStyle(style: ChatToolTimelineStyle): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatToolTimelineStyleKey] = style.storageKey
        }
        return read()
    }

    suspend fun setChatGenerationStatsEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatGenerationStatsEnabled] = enabled
        }
        return read()
    }

    suspend fun setChatCodeBlockStyle(style: ChatCodeBlockStyle): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatCodeBlockStyleKey] = style.storageKey
        }
        return read()
    }

    suspend fun setChatCodeBlockWrapEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatCodeBlockWrapEnabled] = enabled
        }
        return read()
    }

    suspend fun setChatCodeBlockShowAllEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences ->
            preferences[ChatCodeBlockShowAllEnabled] = enabled
        }
        return read()
    }

    suspend fun setChatAvatarShape(shape: ChatAvatarShape): UiPreferences {
        dataStore.edit { preferences ->
            val mode = currentLayoutMode(preferences)
            preferences[
                profileKey(ChatAvatarShapeAgent, ChatAvatarShapeSocial, ChatAvatarShapeRoleplay, mode),
            ] = shape.storageKey
        }
        return read()
    }

    suspend fun setChatRoleplayCardPanel(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences -> preferences[ChatRoleplayCardPanel] = enabled }
        return read()
    }

    suspend fun setChatRoleplayTimestampsEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences -> preferences[ChatRoleplayTimestampsEnabled] = enabled }
        return read()
    }

    suspend fun setChatRoleplayMessageFloorsEnabled(enabled: Boolean): UiPreferences {
        dataStore.edit { preferences -> preferences[ChatRoleplayMessageFloorsEnabled] = enabled }
        return read()
    }

    suspend fun setChatBubbleCornerRadius(value: Float): UiPreferences =
        writeChatMetric(
            ChatBubbleCornerRadiusAgent,
            ChatBubbleCornerRadiusSocial,
            ChatBubbleCornerRadiusRoleplay,
            value.coerceIn(0f, 24f),
        )

    suspend fun setChatAvatarSize(value: Float): UiPreferences =
        writeChatMetric(
            ChatAvatarSizeAgent,
            ChatAvatarSizeSocial,
            ChatAvatarSizeRoleplay,
            value.coerceIn(ChatLayoutDefaults.AvatarSizeMin, ChatLayoutDefaults.AvatarSizeMax),
        )

    suspend fun setChatNameFontSize(value: Float): UiPreferences =
        writeChatMetric(
            ChatNameFontSizeAgent,
            ChatNameFontSizeSocial,
            ChatNameFontSizeRoleplay,
            value.coerceIn(
                ChatLayoutDefaults.NameFontSizeMin,
                ChatLayoutDefaults.NameFontSizeMax,
            ),
        )

    suspend fun setChatNameAvatarSpacing(value: Float): UiPreferences =
        writeChatMetric(
            ChatNameAvatarSpacingAgent,
            ChatNameAvatarSpacingSocial,
            ChatNameAvatarSpacingRoleplay,
            value.coerceIn(0f, 20f),
        )

    suspend fun setChatAreaHorizontalPadding(value: Float): UiPreferences =
        writeChatMetric(
            ChatAreaHorizontalPaddingAgent,
            ChatAreaHorizontalPaddingSocial,
            ChatAreaHorizontalPaddingRoleplay,
            value.coerceIn(0f, 32f),
        )

    suspend fun setChatReplySpacing(value: Float): UiPreferences =
        writeChatMetric(
            ChatReplySpacingAgent,
            ChatReplySpacingSocial,
            ChatReplySpacingRoleplay,
            value.coerceIn(0f, 32f),
        )

    suspend fun setChatTurnSpacing(value: Float): UiPreferences =
        writeChatMetric(
            ChatTurnSpacingAgent,
            ChatTurnSpacingSocial,
            ChatTurnSpacingRoleplay,
            value.coerceIn(0f, 32f),
        )

    suspend fun setChatMessageFontSize(value: Float): UiPreferences =
        writeChatMetric(
            ChatMessageFontSizeAgent,
            ChatMessageFontSizeSocial,
            ChatMessageFontSizeRoleplay,
            value.coerceIn(ChatLayoutDefaults.MessageFontSizeMin, ChatLayoutDefaults.MessageFontSizeMax),
        )

    suspend fun setChatLineHeightMultiplier(value: Float): UiPreferences =
        writeChatMetric(
            ChatLineHeightMultiplierAgent,
            ChatLineHeightMultiplierSocial,
            ChatLineHeightMultiplierRoleplay,
            value.coerceIn(0.8f, 1.6f),
        )

    suspend fun setChatLetterSpacing(value: Float): UiPreferences =
        writeChatMetric(
            ChatLetterSpacingAgent,
            ChatLetterSpacingSocial,
            ChatLetterSpacingRoleplay,
            value.coerceIn(-1f, 4f),
        )

    suspend fun setChatParagraphSpacing(value: Float): UiPreferences =
        writeChatMetric(
            ChatParagraphSpacingAgent,
            ChatParagraphSpacingSocial,
            ChatParagraphSpacingRoleplay,
            value.coerceIn(0f, 24f),
        )

    suspend fun appearanceTheme(): AppearanceTheme = read().appearanceTheme

    suspend fun saveAppearanceTheme(theme: AppearanceTheme): AppearanceTheme {
        dataStore.edit { preferences ->
            preferences.writeAppearanceTheme(theme)
        }
        return theme
    }

    suspend fun resetAppearanceTheme(): AppearanceTheme {
        val defaults = AppearanceTheme()
        saveAppearanceTheme(defaults)
        return defaults
    }

}
