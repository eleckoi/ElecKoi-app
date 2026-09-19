package com.eleckoi.android.feature.preferences

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

// Named after the conversation each mode is for, not after the shape it draws. People pick a layout
// because they want the chat to feel a certain way; "wide bubble" describes the result and leaves
// them guessing at the reason.
enum class ChatLayoutMode(val storageKey: String) {
    // Left/right columns, the reading experience of a messaging app.
    Social("social"),

    // Avatar and name on their own line, body running the full width — a tool talking back to you.
    Agent("agent"),

    // No bubble behind the text at all. Purpose-built for character roleplay: portrait avatars,
    // long prose, controls that stay out of the way until you reach for them.
    Roleplay("roleplay"),
    ;

    // Both of the non-social modes stack the avatar above a full-width body, which is the one
    // distinction the older rendering path knows how to make.
    val usesFullWidthBody: Boolean get() = this != Social

    val drawsBubbleBackground: Boolean get() = this != Roleplay

    companion object {
        val Default: ChatLayoutMode = Roleplay

        fun fromStorageKey(value: String?): ChatLayoutMode =
            entries.firstOrNull { it.storageKey == value } ?: Default

    }
}

// The avatar's silhouette. Corner rounding rides along with the shape instead of getting its own
// slider: three named shapes already cover what anyone wants, and a fourth knob would only let you
// build the two that look broken.
enum class ChatAvatarShape(val storageKey: String) {
    Circle("circle"),
    RoundedSquare("rounded_square"),

    // Standing-portrait proportions, 3:4. The avatar stops being an icon and starts being the
    // character, which is why the roleplay layout is the only one that can hold it — see
    // heightFor: in a chat row this is a third taller than the line it sits next to.
    Portrait("portrait"),
    ;

    val widthToHeight: Float get() = if (this == Portrait) 0.75f else 1f

    fun heightFor(width: Dp): Dp = width / widthToHeight

    fun shape(width: Dp): Shape = when (this) {
        Circle -> CircleShape
        RoundedSquare -> RoundedCornerShape(width * RoundedSquareCornerRatio)
        Portrait -> RoundedCornerShape(width * 0.14f)
    }

    fun isSupportedBy(mode: ChatLayoutMode): Boolean =
        this != Portrait || mode == ChatLayoutMode.Roleplay

    fun defaultSizeFor(mode: ChatLayoutMode): Float = when {
        mode == ChatLayoutMode.Roleplay && this == Portrait ->
            RoleplayLayoutDefaults.PortraitAvatarSize
        else -> mode.layoutDefaults.avatarSize
    }

    companion object {
        val Default: ChatAvatarShape = Circle
        const val RoundedSquareCornerRatio = 0.12f

        fun fromStorageKey(value: String?): ChatAvatarShape =
            entries.firstOrNull { it.storageKey == value } ?: Default
    }
}
