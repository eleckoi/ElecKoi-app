package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.modes.story.model.StoryToolSettings
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.objects
import com.eleckoi.android.foundation.storage.stringOrEmpty
import com.eleckoi.android.foundation.storage.strings
import org.json.JSONArray
import org.json.JSONObject

internal class CharacterPayloadJsonCodec(
    private val userProfile: () -> UserProfile,
) {
    fun decode(json: String): CharactersPayload {
        val value = runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("角色文件格式不正确", it) }
        val groups = value.optJSONArray("groups")
            ?.strings()
            .orEmpty()
            .map(::normalizeGroupName)
            .filter { it.isNotBlank() }
            .distinct()
        val items = value.optJSONArray("items")
            ?.objects()
            ?.map(::characterFromJson)
            ?.toList()
            .orEmpty()
        if (items.isEmpty()) throw ElecKoiDataException("角色文件里没有角色")
        val activeId = value.stringOrEmpty("active_character_id").ifBlank { items.first().id }
        val expandedGroupNames = if (value.has("expanded_group_names")) {
            value.optJSONArray("expanded_group_names")
                ?.strings()
                .orEmpty()
                .map(::normalizeGroupName)
                .filter { it in groups }
                .distinct()
        } else {
            groups
        }
        return CharactersPayload(
            activeCharacterId = activeId,
            groups = groups,
            items = items,
            listAllExpanded = value.optBoolean("list_all_expanded", true),
            expandedGroupNames = expandedGroupNames,
        )
    }

    fun encode(payload: CharactersPayload): String {
        return JSONObject()
            .put("active_character_id", payload.activeCharacterId)
            .put("groups", JSONArray(payload.groups))
            .put("list_all_expanded", payload.listAllExpanded)
            .put("expanded_group_names", JSONArray(payload.expandedGroupNames))
            .put("items", JSONArray(payload.items.map { characterSlotJson(it, includePersona = true) }))
            .toString(2)
    }

    private fun characterPersonaFrom(value: JSONObject, user: UserProfile): CharacterCard {
        val assistantName = value.stringOrEmpty("assistant_name")
        val assistantAvatar = value.stringOrEmpty("assistant_avatar")
        return CharacterCard(
            assistantName = assistantName,
            assistantAvatar = assistantAvatar,
            profileAge = value.stringOrEmpty("profile_age").take(16),
            profileSex = value.stringOrEmpty("profile_sex").take(16),
            profileHeight = value.stringOrEmpty("profile_height").take(16),
            profileBirthday = value.stringOrEmpty("profile_birthday").take(24),
            profileLike = value.stringOrEmpty("profile_like").take(80),
            imagePrompt = value.stringOrEmpty("image_prompt").take(4_000),
            opening = value.stringOrEmpty("opening"),
            showOpening = value.optBoolean("show_opening", false),
            chatBackground = value.stringOrEmpty("chat_background"),
            chatBackgroundOpacity = value.optDouble("chat_background_opacity", 0.72)
                .toFloat()
                .coerceIn(0f, 1f),
            chatBackgroundBlur = value.optDouble("chat_background_blur", 0.0)
                .toFloat()
                .coerceIn(0f, 24f),
            chatBackgroundScrim = value.optDouble("chat_background_scrim", 0.22)
                .toFloat()
                .coerceIn(0f, 1f),
        ).withUser(user)
    }

    private fun characterSlotJson(slot: CharacterSlot, includePersona: Boolean): JSONObject {
        return indexItemJson(slot).apply {
            if (includePersona) {
                put(
                    "persona",
                    JSONObject()
                        .put("assistant_name", slot.persona.assistantName)
                        .put("assistant_avatar", slot.persona.assistantAvatar)
                        .put("profile_age", slot.persona.profileAge)
                        .put("profile_sex", slot.persona.profileSex)
                        .put("profile_height", slot.persona.profileHeight)
                        .put("profile_birthday", slot.persona.profileBirthday)
                        .put("profile_like", slot.persona.profileLike)
                        .put("image_prompt", slot.persona.imagePrompt)
                        .put("opening", slot.persona.opening)
                        .put("show_opening", slot.persona.showOpening)
                        .put("chat_background", slot.persona.chatBackground)
                        .put("chat_background_opacity", slot.persona.chatBackgroundOpacity)
                        .put("chat_background_blur", slot.persona.chatBackgroundBlur)
                        .put("chat_background_scrim", slot.persona.chatBackgroundScrim),
                )
            }
        }
    }

    private fun characterFromJson(value: JSONObject): CharacterSlot {
        val persona = characterPersonaFrom(value.optJSONObject("persona") ?: JSONObject(), userProfile())
        val id = value.stringOrEmpty("id").ifBlank { "character-${newId(16)}" }
        val name = value.stringOrEmpty("name").ifBlank { persona.assistantName }.ifBlank { "未命名角色" }
        val avatar = value.stringOrEmpty("avatar").ifBlank { persona.assistantAvatar }
        val squareImage = value.stringOrEmpty("square_image")
        val coverImage = value.stringOrEmpty("cover_image")
        return CharacterSlot(
            id = id,
            name = name,
            avatar = avatar,
            squareImage = squareImage,
            coverImage = coverImage,
            group = normalizeGroupName(value.stringOrEmpty("group")),
            order = value.optInt("order", 0),
            groupViewOrder = value.optInt("group_view_order", 0),
            folder = folderNameForCharacter(id),
            storyTools = value.optJSONObject("story_tools")?.let { tools ->
                StoryToolSettings(
                    frontendBeautyEnabled = tools.optBoolean("frontend_beauty_enabled", false),
                )
            } ?: StoryToolSettings(),
            persona = persona.copy(
                characterId = id,
                characterName = name,
                characterAvatar = avatar,
                assistantName = persona.assistantName,
                assistantAvatar = persona.assistantAvatar.ifBlank { avatar },
                assistantSquare = squareImage,
                assistantCover = coverImage,
            ),
        )
    }

    private fun indexItemJson(slot: CharacterSlot): JSONObject {
        return JSONObject()
            .put("id", slot.id)
            .put("name", slot.name)
            .put("avatar", slot.avatar)
            .put("square_image", slot.squareImage)
            .put("cover_image", slot.coverImage)
            .put("group", slot.group)
            .put("order", slot.order)
            .put("group_view_order", slot.groupViewOrder)
            .put("folder", slot.folder)
            .put(
                "story_tools",
                JSONObject()
                    .put("frontend_beauty_enabled", slot.storyTools.frontendBeautyEnabled),
            )
            .put("persona", JSONObject())
    }
}
