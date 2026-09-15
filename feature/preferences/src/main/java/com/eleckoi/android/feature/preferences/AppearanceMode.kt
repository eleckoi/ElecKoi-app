package com.eleckoi.android.feature.preferences

/** Matches the PC appearance contract and its persisted values. */
enum class AppearanceMode(val storageKey: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }

    companion object {
        val Default = Light

        fun fromStorageKey(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageKey == value } ?: Default
    }
}

/** Default background assigned only when a newly created/imported character has no explicit one. */
enum class NewCharacterBackground(val storageKey: String) {
    App("app"),
    Character("character"),
    ;

    companion object {
        // PC baseline: characters use their artwork until the user selects the app-colour default.
        val Default = Character

        fun fromStorageKey(value: String?): NewCharacterBackground =
            entries.firstOrNull { it.storageKey == value } ?: Default
    }
}
