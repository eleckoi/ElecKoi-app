package com.eleckoi.android.feature.characters.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarSlotTest {

    @Test
    fun `first upload fills the other two slots`() {
        val filled = AvatarSlot.emptySlotsBesides(AvatarSet(), AvatarSlot.Circle)

        assertEquals(listOf(AvatarSlot.Square, AvatarSlot.Portrait), filled)
    }

    @Test
    fun `retuning one slot leaves the slots that already have an image alone`() {
        val complete = AvatarSet("/c/avatar.png", "/c/square.jpg", "/c/cover.jpg")

        val filled = AvatarSlot.emptySlotsBesides(complete, AvatarSlot.Circle)

        assertEquals(emptyList<AvatarSlot>(), filled)
    }

    @Test
    fun `an intentionally empty slot stays empty when another image is replaced`() {
        // 只要资料已经配置过，空槽位就可能是用户主动删除的，不能再自动补图。
        val migrated = AvatarSet(circle = "/c/avatar.png", portrait = "/c/cover.jpg")

        val filled = AvatarSlot.emptySlotsBesides(migrated, AvatarSlot.Portrait)

        assertEquals(emptyList<AvatarSlot>(), filled)
    }

    @Test
    fun `character and user each read their own three images`() {
        val card = CharacterCard(
            assistantAvatar = "/c/avatar.png",
            assistantSquare = "/c/square.jpg",
            assistantCover = "/c/cover.jpg",
            userAvatar = "/u/avatar.png",
            userSquare = "/u/square.jpg",
            userPortrait = "/u/portrait.jpg",
        )

        assertEquals(AvatarSet("/c/avatar.png", "/c/square.jpg", "/c/cover.jpg"), card.assistantAvatars)
        assertEquals(AvatarSet("/u/avatar.png", "/u/square.jpg", "/u/portrait.jpg"), card.userAvatars)
    }

    @Test
    fun `withUser carries all three user images onto the card`() {
        val user = UserProfile(
            userName = "测试用户",
            userAvatar = "/u/avatar.png",
            userSquare = "/u/square.jpg",
            userPortrait = "/u/portrait.jpg",
            userCover = "/u/banner.jpg",
        )

        val card = CharacterCard(assistantName = "测试角色").withUser(user)

        assertEquals("测试用户", card.userName)
        assertEquals(AvatarSet("/u/avatar.png", "/u/square.jpg", "/u/portrait.jpg"), card.userAvatars)
    }

    @Test
    fun `each slot reads its own image`() {
        val set = AvatarSet("/c/avatar.png", "/c/square.jpg", "/c/cover.jpg")

        assertEquals("/c/avatar.png", AvatarSlot.Circle.pathIn(set))
        assertEquals("/c/square.jpg", AvatarSlot.Square.pathIn(set))
        assertEquals("/c/cover.jpg", AvatarSlot.Portrait.pathIn(set))
    }
}
