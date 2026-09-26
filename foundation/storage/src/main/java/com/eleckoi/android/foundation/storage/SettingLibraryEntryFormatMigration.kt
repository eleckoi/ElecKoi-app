package com.eleckoi.android.foundation.storage

import org.json.JSONObject

/** Upgrades one version-three setting entry to the selectable EJS format. */
object SettingLibraryEntryFormatMigration {
    fun migrateV3Entry(payload: String): String? {
        val entry = runCatching { JSONObject(payload) }
            .getOrElse { error -> throw IllegalStateException("设定条目 JSON 已损坏，无法完成数据迁移。", error) }
        val oldStrategy = entry.optString("agent_read_strategy")
        val oldMode = entry.optString("dynamic_mode")
        val oldContentMode = entry.optString("content_mode")
        val newStrategy = if (oldStrategy == "variable_condition") "normal" else oldStrategy
        val newMode = if (oldMode == "ejs_controller") "standard" else oldMode
        val newContentMode = if (
            oldMode == "ejs_controller" ||
            (oldMode != "ejs_reference" && oldStrategy != "required" &&
                entry.optString("trigger_mode") == "agent_tool" &&
                entry.optString("content").contains("<%"))
        ) "ejs" else "plain_text"
        if (newStrategy == oldStrategy && newMode == oldMode && newContentMode == oldContentMode) return null
        entry.put("agent_read_strategy", newStrategy)
        entry.put("dynamic_mode", newMode)
        entry.put("content_mode", newContentMode)
        return entry.toString()
    }
}
