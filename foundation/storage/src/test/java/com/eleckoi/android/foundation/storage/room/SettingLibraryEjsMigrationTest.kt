package com.eleckoi.android.foundation.storage.room

import com.eleckoi.android.foundation.storage.SettingLibraryEntryFormatMigration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingLibraryEjsMigrationTest {
    @Test
    fun `old controller becomes selectable without changing its EJS body`() {
        val body = "<%- await getwi('场景') %>"
        val migrated = JSONObject(requireNotNull(SettingLibraryEntryFormatMigration.migrateV3Entry(
            entry(body, "variable_condition", "ejs_controller"),
        )))

        assertEquals("normal", migrated.getString("agent_read_strategy"))
        assertEquals("standard", migrated.getString("dynamic_mode"))
        assertEquals("ejs", migrated.getString("content_mode"))
        assertEquals(body, migrated.getString("content"))
    }

    @Test
    fun `old reference remains independent and plain while keeping its enable switch`() {
        val source = JSONObject(entry("章节正文", "variable_condition", "ejs_reference"))
            .put("enabled", false)
        val migrated = JSONObject(requireNotNull(SettingLibraryEntryFormatMigration.migrateV3Entry(source.toString())))

        assertEquals("normal", migrated.getString("agent_read_strategy"))
        assertEquals("ejs_reference", migrated.getString("dynamic_mode"))
        assertEquals("plain_text", migrated.getString("content_mode"))
        assertEquals(false, migrated.getBoolean("enabled"))
    }

    @Test
    fun `migration does not change required entries or their bodies`() {
        val migrated = JSONObject(requireNotNull(SettingLibraryEntryFormatMigration.migrateV3Entry(
            entry("<%= getvar('关系.阶段') %>", "required", "standard"),
        )))
        assertEquals("required", migrated.getString("agent_read_strategy"))
        assertEquals("plain_text", migrated.getString("content_mode"))
        assertEquals("<%= getvar('关系.阶段') %>", migrated.getString("content"))
        assertEquals("plain_text", JSONObject(requireNotNull(SettingLibraryEntryFormatMigration.migrateV3Entry(
            entry("普通正文", "required", "standard"),
        ))).getString("content_mode"))
        assertNull(SettingLibraryEntryFormatMigration.migrateV3Entry(migrated.toString()))
    }

    private fun entry(content: String, strategy: String, mode: String) = JSONObject()
        .put("content", content)
        .put("agent_read_strategy", strategy)
        .put("dynamic_mode", mode)
        .put("enabled", true)
        .toString()
}
