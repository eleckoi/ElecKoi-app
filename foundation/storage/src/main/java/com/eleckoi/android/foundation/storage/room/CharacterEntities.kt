package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "characters",
    primaryKeys = ["id"],
)
data class CharacterEntity(
    val id: String,
    val name: String,
    val avatar: String,
    val squareImage: String = "",
    val coverImage: String,
    val groupName: String,
    val orderIndex: Int,
    val groupViewOrder: Int,
    val folder: String,
    val frontendBeautyEnabled: Boolean,
    val assistantName: String,
    val assistantAvatar: String,
    val profileAge: String = "",
    val profileSex: String = "",
    val profileHeight: String = "",
    val profileBirthday: String = "",
    val profileLike: String = "",
    val showOpening: Boolean,
    val chatBackground: String,
    val chatBackgroundOpacity: Float,
    val chatBackgroundBlur: Float,
    val chatBackgroundScrim: Float,
)

/**
 * Potentially large author text is kept out of the frequently reordered/renamed character row.
 * One field per row lets each piece of authoring content be updated independently.
 */
@Entity(
    tableName = "character_text_contents",
    primaryKeys = ["characterId", "kind"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId")],
)
data class CharacterTextContentEntity(
    val characterId: String,
    val kind: String,
    val content: String,
)

data class CharacterRecord(
    @androidx.room.Embedded val character: CharacterEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "characterId")
    val textContents: List<CharacterTextContentEntity>,
)

@Entity(
    tableName = "character_meta",
    primaryKeys = ["id"],
)
data class CharacterMetaEntity(
    val id: String = "default",
    val activeCharacterId: String,
    val groupsJson: String,
    val listAllExpanded: Boolean,
    val expandedGroupNamesJson: String,
)
