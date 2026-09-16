package com.eleckoi.android.feature.characters.model

import com.eleckoi.android.feature.characters.modes.story.model.StoryToolSettings

const val CharacterCoverAspectRatio = 3f / 4f

/** Persisted choice for the app's built-in image-free chat surface. */
const val AppDefaultChatBackground = "eleckoi://chat-background/app-default"

/** Persisted choice to follow the shared global chat image. */
const val GlobalChatBackground = "eleckoi://chat-background/global"

/** Persisted custom slot before the character has selected its own image. */
const val CustomChatBackground = "eleckoi://chat-background/custom"

/** 一个人的三张头像。角色和用户各有一套，同一个页面配置。 */
data class AvatarSet(
    val circle: String = "",
    val square: String = "",
    val portrait: String = "",
)

/**
 * 聊天的三种版式各用一张头像。三个槽位完全平级：都能单独换图、单独取景，谁也不跟随谁。
 * 全新资料第一次传图时三张一起裁好落盘；之后每个槽位都可以单独替换或留空。
 */
enum class AvatarSlot(
    val label: String,
    val scene: String,
    val aspect: Float,
    val circularFrame: Boolean,
    val outputWidth: Int,
    val fileNamePrefix: String,
    val ratioLabel: String,
) {
    Circle("圆形", "社交版式", 1f, true, 420, "avatar", "1:1"),
    Square("圆角正方形", "智能体版式", 1f, false, 420, "square", "1:1"),
    Portrait("立绘", "角色扮演版式", CharacterCoverAspectRatio, false, 900, "cover", "3:4"),
    ;

    fun pathIn(set: AvatarSet): String = when (this) {
        Circle -> set.circle
        Square -> set.square
        Portrait -> set.portrait
    }

    companion object {
        /**
         * 只有全新资料第一次传图时才从同一张原图补齐另外两个槽位。资料已经配置过以后，即使用户
         * 主动删除了某个槽位，替换另一张图也不能把它悄悄补回来。
         */
        fun emptySlotsBesides(set: AvatarSet, edited: AvatarSlot): List<AvatarSlot> =
            if (entries.all { it.pathIn(set).isBlank() }) entries.filter { it != edited } else emptyList()
    }
}

data class CharacterCard(
    val characterId: String = "",
    val characterName: String = "",
    val characterAvatar: String = "",
    val assistantName: String = "",
    // 三种版式各有一张成品图，各自按自己的比例裁好落盘。渲染时直接取对应的那张，不再靠放大
    // 一张圆形 PNG 去冒充另外两种。
    val assistantAvatar: String = "",
    val assistantSquare: String = "",
    val assistantCover: String = "",
    val profileAge: String = "",
    val profileSex: String = "",
    val profileHeight: String = "",
    val profileBirthday: String = "",
    val profileLike: String = "",
    /** NovelAI tags/phrases inserted verbatim for this character's identity and art style. */
    val imagePrompt: String = "",
    val opening: String = "",
    val showOpening: Boolean = false,
    val chatBackground: String = AppDefaultChatBackground,
    val chatBackgroundOpacity: Float = 0.72f,
    val chatBackgroundBlur: Float = 0f,
    val chatBackgroundScrim: Float = 0.22f,
    val userName: String = "用户",
    val userAvatar: String = "",
    val userSquare: String = "",
    val userPortrait: String = "",
) {
    val assistantAvatars: AvatarSet
        get() = AvatarSet(assistantAvatar, assistantSquare, assistantCover)

    /** Resolves the best available artwork whenever character-card background mode is selected. */
    val defaultChatBackground: String
        get() = assistantCover.ifBlank { assistantSquare }.ifBlank { assistantAvatar }

    val usesAppDefaultChatBackground: Boolean
        get() = chatBackground == AppDefaultChatBackground

    val userAvatars: AvatarSet
        get() = AvatarSet(userAvatar, userSquare, userPortrait)

    /**
     * 用户资料进角色卡只有这一条通路。之前三个构造点各自手抄 userName 和 userAvatar，加了方形和
     * 立绘之后就漏在了那儿——聊天里用户的立绘一直是空的。
     */
    fun withUser(user: UserProfile): CharacterCard = copy(
        userName = user.userName,
        userAvatar = user.userAvatar,
        userSquare = user.userSquare,
        userPortrait = user.userPortrait,
    )
}

data class CharacterSlot(
    val id: String,
    val name: String,
    val avatar: String,
    val squareImage: String = "",
    val coverImage: String = "",
    val group: String,
    val order: Int = 0,
    val groupViewOrder: Int = 0,
    val folder: String,
    val storyTools: StoryToolSettings = StoryToolSettings(),
    val persona: CharacterCard,
)

data class CharactersPayload(
    val activeCharacterId: String,
    val groups: List<String>,
    val items: List<CharacterSlot>,
    val listAllExpanded: Boolean = true,
    val expandedGroupNames: List<String> = emptyList(),
)

data class UserProfile(
    val userName: String = "用户",
    val userAvatar: String = "",
    val userSquare: String = "",
    val userPortrait: String = "",
    // 资料页顶部那张横幅，和聊天里的头像没关系。
    val userCover: String = "",
) {
    val avatars: AvatarSet
        get() = AvatarSet(userAvatar, userSquare, userPortrait)
}
