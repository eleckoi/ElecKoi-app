package com.eleckoi.android.sdk.author.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginStoreDeviceTest {
    @Test fun deletingWorldbookRemovesAllBindingsAndPreservesOtherBooksAfterRestart() {
        val store = PluginStore(ApplicationProvider.getApplicationContext<Context>())
        val name = "book-${System.nanoTime()}"
        val retained = "$name-other"
        val scopes = listOf("global-$name", "character:$name", "chat:$name")
        val book = buildJsonObject { put("entries", buildJsonArray {}) }
        try {
            store.put("worldbooks", name, book)
            store.put("worldbooks", retained, book)
            scopes.forEach { store.put("bindings", it, buildJsonArray { add(name); add(retained) }) }
            assertTrue(store.deleteWorldbook(name))
            val restarted = PluginStore(ApplicationProvider.getApplicationContext<Context>())
            assertEquals(JsonNull, restarted.get("worldbooks", name))
            assertEquals(book, restarted.get("worldbooks", retained))
            scopes.forEach { assertEquals(buildJsonArray { add(retained) }, restarted.get("bindings", it)) }
            assertFalse(restarted.deleteWorldbook(name))
        } finally {
            store.delete("worldbooks", name)
            store.delete("worldbooks", retained)
            scopes.forEach { store.delete("bindings", it) }
        }
    }
    @Test fun multiMegabyteJsonAndUnicodeReadBackAfterRestartAndEnumeration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PluginStore(context)
        val scope = "large-report-${System.nanoTime()}"
        val value = buildJsonObject {
            // More than a default CursorWindow, with supplementary Unicode at chunk edges.
            put("payload", ("a".repeat(128 * 1024 - 1) + "🐳中文").repeat(25))
            put("end", "完整报告结束")
        }
        try {
            store.put(scope, "probe-report", value)
            store.put(scope, "small", JsonPrimitive("still readable"))
            val restarted = PluginStore(context)
            assertEquals(value, restarted.get(scope, "probe-report"))
            assertEquals(value, restarted.list(scope).getValue("probe-report"))
            assertEquals(JsonPrimitive("still readable"), restarted.list(scope).getValue("small"))
            assertEquals(JsonNull, restarted.get(scope, "missing"))
            store.put(scope, "probe-report", JsonPrimitive("updated"))
            assertEquals(JsonPrimitive("updated"), restarted.get(scope, "probe-report"))
        } finally {
            store.delete(scope, "probe-report")
            store.delete(scope, "small")
        }
    }
    @Test fun privateDatabasesPersistAndTransactionsRollback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PluginStore(context)
        val owner = "test-${System.nanoTime()}"
        fun statements(sql: String, params: JsonArray = JsonArray(emptyList())) = buildJsonArray {
            add(buildJsonObject { put("sql", sql); put("params", params) })
        }
        store.sql(owner, "memory.db", statements("CREATE TABLE memories(id INTEGER PRIMARY KEY, text TEXT)"), false)
        store.sql(owner, "memory.db", statements("INSERT INTO memories(id,text) VALUES(?,?)", buildJsonArray { add(1); add("人物 'A'") }), false)
        val restarted = PluginStore(context)
        val rows = restarted.sql(owner, "memory.db", statements("SELECT * FROM memories"), false).first().jsonObject.getValue("rows").jsonArray
        assertEquals("人物 'A'", rows.single().jsonObject.getValue("text").jsonPrimitive.content)
        val transaction = buildJsonArray {
            add(buildJsonObject { put("sql", "INSERT INTO memories VALUES(2,'temporary')") })
            add(buildJsonObject { put("sql", "INSERT INTO missing_table VALUES(1)") })
        }
        assertNotNull(runCatching { restarted.sql(owner, "memory.db", transaction, true) }.exceptionOrNull())
        assertEquals(1, restarted.sql(owner, "memory.db", statements("SELECT * FROM memories"), false).single().jsonObject.getValue("rows").jsonArray.size)
        store.put("settings:$owner", "value", buildJsonObject { put("model", "test") })
        assertEquals("test", PluginStore(context).get("settings:$owner", "value").jsonObject.getValue("model").jsonPrimitive.content)
        assertNotNull(runCatching { restarted.sql("another:$owner", "memory.db", statements("SELECT * FROM memories"), false) }.exceptionOrNull())
    }
}
