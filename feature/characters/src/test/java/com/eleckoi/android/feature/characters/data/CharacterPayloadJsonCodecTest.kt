package com.eleckoi.android.feature.characters.data

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPayloadJsonCodecTest {
    @Test
    fun roundTripPreservesCharacterPayloadAndInjectsCurrentUserProfile() {
        val codec = CharacterPayloadJsonCodec {
            UserProfile(
                userName = "当前用户",
                userAvatar = "user-circle.png",
                userSquare = "user-square.jpg",
                userPortrait = "user-portrait.jpg",
            )
        }
        val slot = CharacterSlot(
            id = "character-round-trip",
            name = "测试角色",
            avatar = "assistant-circle.png",
            squareImage = "assistant-square.jpg",
            coverImage = "assistant-cover.jpg",
            group = "主角组",
            order = 3,
            groupViewOrder = 2,
            folder = "ignored-on-import",
            persona = CharacterCard(
                assistantName = "测试角色",
                assistantAvatar = "assistant-circle.png",
                profileAge = "20",
                opening = "你好",
                showOpening = true,
                chatBackground = "background.webp",
                chatBackgroundOpacity = 0.6f,
                chatBackgroundBlur = 8f,
                chatBackgroundScrim = 0.3f,
            ),
        )
        val payload = CharactersPayload(
            activeCharacterId = slot.id,
            groups = listOf("主角组"),
            items = listOf(slot),
            listAllExpanded = false,
            expandedGroupNames = listOf("主角组"),
        )

        val decoded = codec.decode(codec.encode(payload))
        val decodedSlot = decoded.items.single()

        assertEquals(slot.id, decoded.activeCharacterId)
        assertEquals(listOf("主角组"), decoded.groups)
        assertFalse(decoded.listAllExpanded)
        assertEquals("assistant-square.jpg", decodedSlot.squareImage)
        assertEquals("assistant-cover.jpg", decodedSlot.coverImage)
        assertEquals("当前用户", decodedSlot.persona.userName)
        assertEquals("user-portrait.jpg", decodedSlot.persona.userPortrait)
        assertEquals(8f, decodedSlot.persona.chatBackgroundBlur)
    }

    @Test
    fun payloadPoliciesNormalizeGroupsListsAndSqlSearchEscapes() {
        assertEquals(listOf("一组", "二组"), buildGroups(listOf(" 一组 ", "一组"), listOf(character("二组"))))
        assertEquals(listOf("一组", "二组"), "[\" 一组 \" , \"一组\", \"二组\", \"\"]".decodeStringList())
        assertEquals("%a\\%b\\_c\\\\d%", " a%b_c\\d ".toSqlLikePattern())
        assertEquals("", "   ".toSqlLikePattern())
        assertTrue(folderNameForCharacter("character-1234567890").endsWith("34567890"))
    }

    private fun character(group: String): CharacterSlot {
        return CharacterSlot(
            id = "character-policy",
            name = "角色",
            avatar = "",
            group = group,
            folder = "character_policy",
            persona = CharacterCard(),
        )
    }
}
