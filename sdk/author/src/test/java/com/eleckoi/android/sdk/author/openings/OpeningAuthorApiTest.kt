package com.eleckoi.android.sdk.author.openings

import com.eleckoi.android.sdk.author.AuthorOpeningOptionSnapshot
import com.eleckoi.android.sdk.author.AuthorOpeningStateSnapshot
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OpeningAuthorApiTest {
    @Test
    fun `list exposes PC opening contract`() {
        val result = state().toListJson()

        val items = result["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("opening-1", items[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("第一幕", items[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("1", items[0].jsonObject["initialVariableState"]!!.jsonObject["scene"]!!.jsonPrimitive.content)
    }

    @Test
    fun `current returns null when selected id is missing`() {
        val result = state().copy(selectedId = "missing").toCurrentJson()

        assertSame(kotlinx.serialization.json.JsonNull, result)
    }

    private fun state() = AuthorOpeningStateSnapshot(
        items = listOf(
            AuthorOpeningOptionSnapshot(
                id = "opening-1",
                title = "第一幕",
                content = "第一幕",
                initialVariableStateJson = "{\"scene\":1}",
            ),
            AuthorOpeningOptionSnapshot(id = "opening-2", title = "第二幕", content = "第二幕"),
        ),
        selectedId = "opening-2",
        selectionEnabled = true,
    )
}
