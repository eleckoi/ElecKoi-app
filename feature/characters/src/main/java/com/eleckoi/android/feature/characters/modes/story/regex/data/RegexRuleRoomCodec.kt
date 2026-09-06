package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleVersion
import com.eleckoi.android.foundation.storage.room.RegexEnablementVersionEntity
import com.eleckoi.android.foundation.storage.room.RegexRuleFields
import com.eleckoi.android.foundation.storage.strings
import org.json.JSONArray

internal fun RegexRule.toRoomFields(): RegexRuleFields = RegexRuleFields(
    name, pattern, replacement, JSONArray(targets.map { it.name }).toString(),
    enabled, displayOnly, promptOnly, runOnEdit, order,
)

internal fun RegexRuleFields.toRule(id: String): RegexRule = RegexRule(
    id = id,
    name = name,
    pattern = pattern,
    replacement = replacement,
    targets = JSONArray(targetsJson).strings().map { RegexRuleTarget.valueOf(it) }.toSet(),
    enabled = enabled,
    displayOnly = displayOnly,
    promptOnly = promptOnly,
    runOnEdit = runOnEdit,
    order = sortIndex,
)

internal fun RegexRuleVersion.toRoomVersion(index: Int): RegexEnablementVersionEntity =
    RegexEnablementVersionEntity(
        id, name, index, JSONArray(globalEnabledIds).toString(), JSONArray(characterEnabledIds).toString(),
    )

internal fun RegexEnablementVersionEntity.toVersion(): RegexRuleVersion = RegexRuleVersion(
    id = id,
    name = name,
    globalEnabledIds = JSONArray(globalEnabledIdsJson).strings().toSet(),
    characterEnabledIds = JSONArray(characterEnabledIdsJson).strings().toSet(),
)
