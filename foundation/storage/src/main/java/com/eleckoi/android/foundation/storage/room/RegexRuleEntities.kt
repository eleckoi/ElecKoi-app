package com.eleckoi.android.foundation.storage.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Rule content is stored once in its owning scope. Preset rules remain owned by preset versions. */
data class RegexRuleFields(
    val name: String,
    val pattern: String,
    val replacement: String,
    val targetsJson: String,
    val enabled: Boolean,
    val displayOnly: Boolean,
    val promptOnly: Boolean,
    val runOnEdit: Boolean,
    val sortIndex: Int,
)

@Entity(tableName = "global_regex_rules", primaryKeys = ["id"], indices = [Index("sortIndex")])
data class GlobalRegexRuleEntity(val id: String, @Embedded val rule: RegexRuleFields)

@Entity(
    tableName = "character_regex_rules",
    primaryKeys = ["characterId", "id"],
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"], childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["characterId", "sortIndex"])],
)
data class CharacterRegexRuleEntity(
    val characterId: String,
    val id: String,
    @Embedded val rule: RegexRuleFields,
)

/** A named enablement selection, not a historical copy of rule content. */
@Entity(tableName = "regex_enablement_versions", primaryKeys = ["id"])
data class RegexEnablementVersionEntity(
    val id: String,
    val name: String,
    val sortIndex: Int,
    val globalEnabledIdsJson: String,
    val characterEnabledIdsJson: String,
)

@Entity(
    tableName = "regex_state", primaryKeys = ["singletonId"],
    foreignKeys = [ForeignKey(
        entity = RegexEnablementVersionEntity::class,
        parentColumns = ["id"], childColumns = ["activeVersionId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("activeVersionId")],
)
data class RegexStateEntity(
    val singletonId: Int = 0,
    val activeVersionId: String?,
    /** Monotonic business revision; avoids scanning every rule body to detect changes. */
    val revision: Long = 0L,
)
