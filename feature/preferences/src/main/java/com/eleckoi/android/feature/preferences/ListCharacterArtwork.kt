package com.eleckoi.android.feature.preferences

/** Artwork used for a character anywhere the mobile home presents a compact list row. */
enum class ListCharacterArtwork(val storageKey: String) {
    Cover("cover"),
    Avatar("avatar"),
    ;

    companion object {
        val Default: ListCharacterArtwork = Cover

        fun fromStorageKey(value: String?): ListCharacterArtwork =
            entries.firstOrNull { it.storageKey == value } ?: Default
    }
}
