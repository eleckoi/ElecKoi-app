package com.eleckoi.android.feature.characters.presets.data.policy

internal fun uniqueAgentPresetName(requested: String, existingNames: List<String>): String {
    val base = requested.take(60)
    val names = existingNames.map(String::trim).toSet()
    if (base !in names) return base
    var suffix = 2
    while ("$base $suffix" in names) suffix += 1
    return "$base $suffix".take(60)
}
