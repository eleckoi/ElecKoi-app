package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSettingLibraryCatalogTest {

    @Test
    fun `rebuilding group paths updates descendants and entries`() {
        val root = SettingLibraryAgentGroup(
            id = "root",
            name = "People",
            parentId = "",
            path = "People",
        )
        val child = SettingLibraryAgentGroup(
            id = "child",
            name = "Friends",
            parentId = "root",
            path = "People/Friends",
        )
        val entry = SettingLibraryAgentEntry(
            id = "entry",
            title = "Rin",
            groupId = "child",
            groupPath = "People/Friends",
            path = "People/Friends/Rin",
            content = "A trusted friend",
        )

        val rebuilt = SettingLibraryToolCatalog(
            entries = listOf(entry),
            groups = listOf(root, child),
        ).rebuildPaths(
            nextGroups = listOf(
                root.copy(name = "Characters", path = ""),
                child.copy(path = ""),
            ),
        )

        assertEquals("Characters", rebuilt.groups.first { it.id == "root" }.path)
        assertEquals("Characters/Friends", rebuilt.groups.first { it.id == "child" }.path)
        assertEquals("Characters/Friends/Rin", rebuilt.entries.single().path)
        assertEquals("Characters/Friends", rebuilt.entries.single().groupPath)
    }

    @Test
    fun `circular group ancestry remains finite and deterministic`() {
        val first = SettingLibraryAgentGroup("first", "One", "second", "")
        val second = SettingLibraryAgentGroup("second", "Two", "first", "")

        val rebuilt = SettingLibraryToolCatalog(emptyList(), listOf(first, second))
            .rebuildPaths(listOf(first, second))

        assertEquals(listOf("One/Two/One", "One/Two"), rebuilt.groups.map { it.path })
    }
}
