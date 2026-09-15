package com.eleckoi.android.sdk.author

enum class AuthorApiPermission(val wireName: String) {
    AppRead("app.read"),
    ContextRead("context.read"),
    VariablesRead("variables.read"),
    VariablesWrite("variables.write"),
    OpeningsRead("openings.read"),
    OpeningsWrite("openings.write"),
    MessagesRead("messages.read"),
    MessagesWrite("messages.write"),
    ChatRead("chat.read"),
    ChatSend("chat.send"),
    ChatWrite("chat.write"),
    CharacterRead("character.read"),
    SettingLibraryRead("setting_library.read"),
    MediaRead("media.read"),
    AudioRead("audio.read"),
    AudioWrite("audio.write"),
    InputRead("input.read"),
    InputWrite("input.write"),
    EventsRead("events.read"),
    ;

    companion object {
        val previewReadOnly: Set<AuthorApiPermission> = entries.toSet()
            .filterNot { it.wireName.endsWith(".write") || it == ChatSend }
            .toSet()
        val previewLocalFull: Set<AuthorApiPermission> = entries.toSet()
        val inlineMessageReadOnly: Set<AuthorApiPermission> = setOf(
            AppRead,
            ContextRead,
            VariablesRead,
            MessagesRead,
        )
        val inlineMessageInteractive: Set<AuthorApiPermission> = inlineMessageReadOnly + setOf(
            OpeningsRead,
            OpeningsWrite,
            ChatSend,
        )

        fun fromWireName(value: String): AuthorApiPermission? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
