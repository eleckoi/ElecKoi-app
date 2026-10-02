package com.eleckoi.android.sdk.author.plugins

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

/** One registry and one independent SQLite file per plugin. SQL errors propagate to the caller. */
class PluginStore(context: Context) {
    private val root = File(context.filesDir, "author-plugins").apply { mkdirs() }
    fun resource(pluginId: String, path: String): android.webkit.WebResourceResponse? {
        val value = get("resources:$pluginId", path) as? JsonPrimitive ?: return null
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substringAfterLast('.', "")) ?: "application/octet-stream"
        return android.webkit.WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript")) "UTF-8" else null,
            java.io.ByteArrayInputStream(android.util.Base64.decode(value.content, android.util.Base64.DEFAULT)))
    }
    private val registry = SQLiteDatabase.openOrCreateDatabase(File(root, "registry.sqlite"), null).apply {
        execSQL("CREATE TABLE IF NOT EXISTS documents (scope TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(scope,key))")
    }

    @Synchronized fun get(scope: String, key: String): JsonElement {
        // CursorWindow holds each selected row, not just the part consumed by getString.
        // Read bounded SQL substrings so existing multi-MB reports/resources remain readable.
        val initial = registry.rawQuery(
            "SELECT substr(value,1,?),length(value) FROM documents WHERE scope=? AND key=?",
            arrayOf(JsonReadChunkCharacters.toString(), scope, key),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return JsonNull
            cursor.getString(0) to cursor.getInt(1)
        }
        if (initial.second <= JsonReadChunkCharacters) return Json.parseToJsonElement(initial.first)
        val text = StringBuilder(initial.first)
        var position = JsonReadChunkCharacters + 1
        while (position <= initial.second) {
            registry.rawQuery(
                "SELECT substr(value,?,?) FROM documents WHERE scope=? AND key=?",
                arrayOf(position.toString(), JsonReadChunkCharacters.toString(), scope, key),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "插件存储记录在读取时被删除：$scope/$key" }
                text.append(cursor.getString(0))
            }
            // SQLite counts Unicode code points; do not advance by Kotlin UTF-16 length.
            position += JsonReadChunkCharacters
        }
        return Json.parseToJsonElement(text.toString())
    }

    @Synchronized fun put(scope: String, key: String, value: JsonElement) {
        // Android 8.1 (minSdk 27) ships SQLite before UPSERT support.
        registry.execSQL("INSERT OR REPLACE INTO documents(scope,key,value) VALUES(?,?,?)", arrayOf(scope, key, value.toString()))
    }

    @Synchronized fun delete(scope: String, key: String): Int = registry.delete("documents", "scope=? AND key=?", arrayOf(scope, key))

    /** A deleted named book must not leave bindings that make the next generation fail. */
    @Synchronized fun deleteWorldbook(name: String): Boolean {
        registry.beginTransaction()
        try {
            val deleted = delete("worldbooks", name) > 0
            list("bindings").forEach { (scope, value) ->
                val names = value.jsonArray
                val retained = names.filterNot { it.jsonPrimitive.content == name }
                if (retained.size != names.size) put("bindings", scope, JsonArray(retained))
            }
            registry.setTransactionSuccessful()
            return deleted
        } finally {
            registry.endTransaction()
        }
    }

    @Synchronized fun list(scope: String): JsonObject {
        val keys = registry.rawQuery("SELECT key FROM documents WHERE scope=? ORDER BY key", arrayOf(scope)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return buildJsonObject { keys.forEach { key -> put(key, get(scope, key)) } }
    }

    fun sql(pluginId: String, name: String, statements: JsonArray, transaction: Boolean): JsonArray {
        val identity = MessageDigest.getInstance("SHA-256").digest("$pluginId/$name".toByteArray()).joinToString("") { "%02x".format(it) }
        return SQLiteDatabase.openOrCreateDatabase(File(root, "$identity.sqlite"), null).use { db ->
            if (transaction) db.beginTransaction()
            try {
                val results = statements.map { item ->
                    val stmt = item.jsonObject
                    val args = (stmt["params"] as? JsonArray)?.map { if (it is JsonNull) null else it.jsonPrimitive.content }?.toTypedArray() ?: emptyArray()
                    val sql = stmt.getValue("sql").jsonPrimitive.content
                    val rows = db.rawQuery(sql, args).use { cursor -> cursor.toJsonRows() }
                    buildJsonObject {
                        put("rows", rows)
                        db.rawQuery("SELECT changes(), last_insert_rowid()", null).use { cursor ->
                            cursor.moveToFirst(); put("changes", cursor.getInt(0)); put("lastInsertId", cursor.getLong(1))
                        }
                    }
                }
                if (transaction) db.setTransactionSuccessful()
                JsonArray(results)
            } finally { if (transaction) db.endTransaction() }
        }
    }
}

private const val JsonReadChunkCharacters = 128 * 1024

private fun Cursor.toJsonRows(): JsonArray = buildJsonArray {
    while (moveToNext()) add(buildJsonObject {
        columnNames.forEachIndexed { index, name -> put(name, when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(android.util.Base64.encodeToString(getBlob(index), android.util.Base64.NO_WRAP))
            else -> JsonPrimitive(getString(index))
        }) }
    })
}
