package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isRoleplayPlanEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryEditorStateTest {
    @Test
    fun `new setting really defaults to agent tool and can be enabled immediately`() {
        val state = SettingLibraryEditorState(SettingLibrary(characterId = "character"))

        state.addEntry()
        val created = state.entries.single { !it.isOpeningEntry() && !it.isRoleplayPlanEntry() }

        assertEquals(SettingLibraryTriggerMode.AgentTool, created.triggerMode)
        assertEquals(null, created.position)
        assertEquals("新建设定", created.title)
        assertEquals(fileNodeId(created.id), state.selectedTreeNodeId)
        assertEquals(created.id, state.editorEntryId)
        state.updateEntry(created.id) { it.copy(enabled = true) }
        assertTrue(state.entries.single { it.id == created.id }.enabled)
    }

    @Test
    fun `new settings receive unique editable titles inside the same folder`() {
        val state = SettingLibraryEditorState(SettingLibrary(characterId = "character"))

        state.addEntry()
        state.editorEntryId = null
        state.focusTreeNode(RootNodeId)
        state.addEntry()

        val created = state.entries.filterNot { it.isOpeningEntry() || it.isRoleplayPlanEntry() }
        assertEquals(listOf("新建设定", "新建设定 2"), created.map { it.title })
        assertEquals(created.last().id, state.editorEntryId)
    }

    @Test
    fun `agent tool entry can be enabled without an insertion position`() {
        val state = stateWith(
            SettingLibraryEntry(
                id = "language",
                title = "语言与行为",
                triggerMode = SettingLibraryTriggerMode.AgentTool,
                position = null,
                enabled = false,
            ),
        )

        state.updateEntry("language") { it.copy(enabled = true) }

        assertTrue(state.entries.single().enabled)
    }

    @Test
    fun `automatic entry still requires an insertion position`() {
        val state = stateWith(
            SettingLibraryEntry(
                id = "always",
                title = "常驻",
                triggerMode = SettingLibraryTriggerMode.Always,
                position = null,
                enabled = false,
            ),
        )

        state.updateEntry("always") { it.copy(enabled = true) }

        assertFalse(state.entries.single().enabled)
    }

    @Test
    fun `new version copies the selected version without removing existing versions`() {
        val firstEntry = SettingLibraryEntry(id = "first-entry", title = "第一版", content = "第一版正文")
        val secondEntry = SettingLibraryEntry(id = "second-entry", title = "第二版", content = "第二版正文")
        val first = SettingLibraryVersion(
            id = "first",
            name = "初稿",
            entries = listOf(settingLibraryOpeningEntry(), firstEntry),
        )
        val second = SettingLibraryVersion(
            id = "second",
            name = "当前稿",
            entries = listOf(settingLibraryOpeningEntry(), secondEntry),
        )
        val source = SettingLibrary(
            characterId = "character",
            name = second.name,
            entries = second.entries,
            activeVersionId = second.id,
            versions = listOf(first, second),
        )
        val state = SettingLibraryEditorState(source)

        state.createLibraryVersion(name = "基于初稿", sourceVersionId = first.id)

        assertEquals(3, state.versions.size)
        assertEquals(listOf("first", "second"), state.versions.take(2).map { it.id })
        assertEquals("基于初稿", state.libraryName)
        assertEquals("第一版正文", state.entries.single { it.id == firstEntry.id }.content)

        state.updateEntry(firstEntry.id) { it.copy(content = "只修改副本") }

        assertEquals(
            "第一版正文",
            state.versions.first { it.id == first.id }.entries.single { it.id == firstEntry.id }.content,
        )
    }

    @Test
    fun `blank version keeps history and starts with pinned entries`() {
        val source = SettingLibrary(
            characterId = "character",
            name = "当前稿",
            entries = listOf(settingLibraryOpeningEntry(), SettingLibraryEntry(id = "setting", title = "设定")),
            activeVersionId = "current",
            versions = listOf(
                SettingLibraryVersion(
                    id = "current",
                    name = "当前稿",
                    entries = listOf(settingLibraryOpeningEntry(), SettingLibraryEntry(id = "setting", title = "设定")),
                ),
            ),
        )
        val state = SettingLibraryEditorState(source)

        state.createLibraryVersion(name = "空白稿", sourceVersionId = null)

        assertEquals(2, state.versions.size)
        assertEquals("current", state.versions.first().id)
        assertEquals(2, state.entries.size)
        assertTrue(state.entries[0].isOpeningEntry())
        assertTrue(state.entries[1].isRoleplayPlanEntry())
    }

    @Test
    fun `copying a folder preserves its descendant hierarchy and entries`() {
        val parent = SettingLibraryGroup(id = "parent", name = "世界")
        val child = SettingLibraryGroup(id = "child", name = "城市", parentId = parent.id)
        val entry = SettingLibraryEntry(id = "city-setting", title = "王都", groupId = child.id)
        val state = SettingLibraryEditorState(
            SettingLibrary(
                characterId = "character",
                entries = listOf(entry),
                groups = listOf(parent, child),
            ),
        )
        state.focusTreeNode(folderNodeId(parent.id))

        assertEquals("已复制", state.copySelectedTreeNode())
        state.focusTreeNode(RootNodeId)
        assertEquals("已粘贴", state.pasteTreeClipboard())

        val copiedParent = state.groups.single { it.id != parent.id && it.parentId.isBlank() }
        val copiedChild = state.groups.single { it.id != child.id && it.parentId == copiedParent.id }
        assertEquals("世界 副本", copiedParent.name)
        assertTrue(state.entries.any { it.id != entry.id && it.groupId == copiedChild.id && it.title == entry.title })
        assertTrue(state.entries.any { it.id == entry.id && it.groupId == child.id })
    }

    @Test
    fun `cutting a folder moves the original subtree and clears clipboard`() {
        val source = SettingLibraryGroup(id = "source", name = "待移动")
        val target = SettingLibraryGroup(id = "target", name = "目标")
        val state = SettingLibraryEditorState(
            SettingLibrary(characterId = "character", groups = listOf(source, target)),
        )
        state.focusTreeNode(folderNodeId(source.id))

        assertEquals("已剪切", state.cutSelectedTreeNode())
        state.focusTreeNode(folderNodeId(target.id))
        assertEquals("已粘贴", state.pasteTreeClipboard())

        assertEquals(target.id, state.groups.single { it.id == source.id }.parentId)
        assertEquals(2, state.groups.size)
        assertFalse(state.hasTreeClipboard())
    }

    private fun stateWith(entry: SettingLibraryEntry): SettingLibraryEditorState =
        SettingLibraryEditorState(
            SettingLibrary(
                characterId = "character",
                entries = listOf(entry),
            ),
        )
}
