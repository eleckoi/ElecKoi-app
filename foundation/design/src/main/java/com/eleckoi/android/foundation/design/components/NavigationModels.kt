package com.eleckoi.android.foundation.design.components

enum class RootTab(val label: String, val icon: NavIconKind) {
    Messages("消息", NavIconKind.Messages),
    Characters("角色", NavIconKind.Characters),
    Presets("预设", NavIconKind.Presets),
    Models("模型", NavIconKind.Models),
}

enum class BottomTab(
    val label: String,
    val icon: NavIconKind,
) {
    Messages("消息", NavIconKind.Messages),
    Characters("角色", NavIconKind.Characters),
    Models("模型", NavIconKind.Models),
    Presets("预设", NavIconKind.Presets),
    ;

    fun rootTab(): RootTab = when (this) {
        Messages -> RootTab.Messages
        Characters -> RootTab.Characters
        Presets -> RootTab.Presets
        Models -> RootTab.Models
    }

    companion object {
        val MobileBarTabs = listOf(Messages, Characters, Presets, Models)

        fun from(rootTab: RootTab): BottomTab = when (rootTab) {
            RootTab.Messages -> Messages
            RootTab.Characters -> Characters
            RootTab.Presets -> Presets
            RootTab.Models -> Models
        }
    }
}

fun formatShortDate(value: String): String {
    return value.substringAfter('T', value).take(5).ifBlank { "" }
}
