package com.eleckoi.android.foundation.storage.room

import androidx.sqlite.db.SupportSQLiteDatabase
import com.eleckoi.android.foundation.storage.SettingLibraryEntryFormatMigration

/** Converts stored setting entries when the database moves to the selectable EJS format. */
internal object SettingLibraryEjsMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        migrateTable(db, "setting_entry_contents")
        migrateTable(db, "conversation_setting_changes", "WHERE `targetType` = 'entry'")
    }

    private fun migrateTable(db: SupportSQLiteDatabase, table: String, condition: String = "") {
        val changes = buildList {
            db.query("SELECT rowid, `payloadJson` FROM `$table` $condition").use { cursor ->
                while (cursor.moveToNext()) {
                    SettingLibraryEntryFormatMigration.migrateV3Entry(cursor.getString(1))
                        ?.let { add(cursor.getLong(0) to it) }
                }
            }
        }
        changes.forEach { (rowId, payload) ->
            db.execSQL(
                "UPDATE `$table` SET `payloadJson` = ? WHERE rowid = ?",
                arrayOf<Any>(payload, rowId),
            )
        }
    }

}
