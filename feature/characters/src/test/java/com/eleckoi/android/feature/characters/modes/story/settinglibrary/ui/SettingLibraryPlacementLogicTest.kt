package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntryKind
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingLibraryPlacementLogicTest {
    @Test
    fun `fixed context triples stay indivisible in the placement guide`() {
        val positions = SettingLibraryPosition.entries.mapIndexed { index, anchor ->
            promptPosition("position-$index", anchor)
        }

        val rows = placementGuideRows(positions)

        assertEquals(
            listOf(
                FixedPlacementNode.History,
                FixedPlacementNode.LatestUserInput,
                FixedPlacementNode.ToolFlow,
            ),
            rows.filterIsInstance<PlacementGuideRow.FixedGroup>().map { it.node },
        )
        assertEquals(3, rows.count { it is PlacementGuideRow.FixedGroup })
        assertEquals(7, rows.count { it is PlacementGuideRow.Custom })
        assertEquals(1, rows.count { it is PlacementGuideRow.Instructions })
        assertEquals(1, rows.count { it is PlacementGuideRow.AfterInstructions })
    }

    @Test
    fun `ordinary setting picker exposes fixed contexts only`() {
        val rows = placementGuideRows(
            positions = listOf(promptPosition("preset-only", SettingLibraryPosition.AfterHistory)),
            includeCustomPositions = false,
        )

        assertEquals(
            FixedPlacementNode.entries.toList(),
            rows.filterIsInstance<PlacementGuideRow.FixedGroup>().map { it.node },
        )
        assertEquals(0, rows.count { it is PlacementGuideRow.Custom })
        assertEquals(1, rows.count { it is PlacementGuideRow.Instructions })
        assertEquals(1, rows.count { it is PlacementGuideRow.AfterInstructions })
    }

    @Test
    fun `system instructions and the following message boundary keep distinct roles`() {
        val entry = entry("moving", SettingLibraryPosition.AfterInstructions, order = 1)

        val inInstructions = movePositionEntry(
            entries = listOf(entry),
            entryId = entry.id,
            targetPosition = SettingLibraryPosition.Instructions,
        ).single()
        assertEquals(SettingLibraryInsertRole.System, inInstructions.insertRole)

        val afterInstructions = movePositionEntry(
            entries = listOf(inInstructions),
            entryId = entry.id,
            targetPosition = SettingLibraryPosition.AfterInstructions,
        ).single()
        assertEquals(SettingLibraryInsertRole.User, afterInstructions.insertRole)
    }

    @Test
    fun `dragging across a fixed context lands outside the whole triple`() {
        val beforeToolFlow = promptPosition("moving", SettingLibraryPosition.BeforeToolFlow)
        val afterToolFlow = movePromptPosition(
            positions = listOf(beforeToolFlow),
            movingId = beforeToolFlow.id,
            target = PlacementGuideRow.FixedGroup(FixedPlacementNode.ToolFlow),
            movingDown = true,
        )

        assertEquals(SettingLibraryPosition.AfterToolFlow, afterToolFlow.single().anchor)

        val movedBack = movePromptPosition(
            positions = afterToolFlow,
            movingId = beforeToolFlow.id,
            target = PlacementGuideRow.FixedGroup(FixedPlacementNode.ToolFlow),
            movingDown = false,
        )

        assertEquals(SettingLibraryPosition.BeforeToolFlow, movedBack.single().anchor)
    }

    @Test
    fun `a prompt position can be dragged beneath the final fixed context`() {
        val moving = promptPosition("moving", SettingLibraryPosition.AfterInstructions)

        val moved = movePromptPosition(
            positions = listOf(moving),
            movingId = moving.id,
            target = PlacementGuideRow.FixedGroup(FixedPlacementNode.ToolFlow),
            movingDown = true,
        )

        assertEquals(SettingLibraryPosition.AfterToolFlow, moved.single().anchor)
    }

    @Test
    fun `order scope is isolated by position`() {
        val entries = listOf(
            entry("after-a", SettingLibraryPosition.AfterHistory, order = 2),
            entry("after-b", SettingLibraryPosition.AfterHistory, order = 1),
            entry("before", SettingLibraryPosition.BeforeHistory, order = 1),
        )

        assertEquals(
            listOf("after-b", "after-a"),
            positionOrderScope(entries, SettingLibraryPosition.AfterHistory).map { it.id },
        )
    }

    @Test
    fun `order scope is isolated by custom prompt position`() {
        val entries = listOf(
            entry("custom-a-2", SettingLibraryPosition.AfterHistory, order = 2, promptPositionId = "custom-a"),
            entry("custom-a-1", SettingLibraryPosition.AfterHistory, order = 1, promptPositionId = "custom-a"),
            entry("custom-b", SettingLibraryPosition.AfterHistory, order = 1, promptPositionId = "custom-b"),
            entry("fixed-anchor", SettingLibraryPosition.AfterHistory, order = 1),
        )

        assertEquals(
            listOf("custom-a-1", "custom-a-2"),
            positionOrderScope(
                entries,
                SettingLibraryPosition.AfterHistory,
                promptPositionId = "custom-a",
            ).map { it.id },
        )
    }

    @Test
    fun `next order ignores fixed and current entries`() {
        val entries = listOf(
            entry("one", SettingLibraryPosition.BeforeHistory, order = 1),
            entry("current", SettingLibraryPosition.BeforeHistory, order = 8),
            entry(
                id = "fixed",
                position = SettingLibraryPosition.BeforeHistory,
                order = 99,
                kind = SettingLibraryEntryKind.Opening,
            ),
        )

        assertEquals(
            2,
            nextPositionOrder(
                entries = entries,
                position = SettingLibraryPosition.BeforeHistory,
                excludingEntryId = "current",
            ),
        )
    }

    @Test
    fun `moving a prompt across fixed nodes changes its position and normalizes both scopes`() {
        val entries = listOf(
            entry("source-a", SettingLibraryPosition.BeforeHistory, order = 1),
            entry("moving", SettingLibraryPosition.BeforeHistory, order = 9),
            entry("target-a", SettingLibraryPosition.BeforeToolFlow, order = 3),
            entry("target-b", SettingLibraryPosition.BeforeToolFlow, order = 8),
        )

        val moved = movePositionEntry(
            entries = entries,
            entryId = "moving",
            targetPosition = SettingLibraryPosition.BeforeToolFlow,
            relativeEntryId = "target-a",
            insertAfterRelative = true,
        )

        assertEquals(
            listOf("target-a", "moving", "target-b"),
            positionOrderScope(moved, SettingLibraryPosition.BeforeToolFlow).map { it.id },
        )
        assertEquals(listOf(1, 2, 3), positionOrderScope(moved, SettingLibraryPosition.BeforeToolFlow).map { it.order })
        assertEquals(1, moved.single { it.id == "source-a" }.order)
    }

    @Test
    fun `moving an entry into a custom position keeps its internal order separate`() {
        val entries = listOf(
            entry("moving", SettingLibraryPosition.AfterHistory, order = 4),
            entry(
                "custom-first",
                SettingLibraryPosition.BeforeToolFlow,
                order = 7,
                promptPositionId = "custom-position",
            ),
            entry("fixed-anchor", SettingLibraryPosition.BeforeToolFlow, order = 1),
        )

        val moved = movePositionEntry(
            entries = entries,
            entryId = "moving",
            targetPosition = SettingLibraryPosition.BeforeToolFlow,
            targetPromptPositionId = "custom-position",
        )

        assertEquals(
            listOf("custom-first", "moving"),
            positionOrderScope(
                moved,
                SettingLibraryPosition.BeforeToolFlow,
                promptPositionId = "custom-position",
            ).map { it.id },
        )
        assertEquals(listOf(1, 2), positionOrderScope(
            moved,
            SettingLibraryPosition.BeforeToolFlow,
            promptPositionId = "custom-position",
        ).map { it.order })
        assertEquals("custom-position", moved.single { it.id == "moving" }.promptPositionId)
        assertEquals(1, moved.single { it.id == "fixed-anchor" }.order)
    }

    private fun entry(
        id: String,
        position: SettingLibraryPosition,
        order: Int,
        kind: SettingLibraryEntryKind = SettingLibraryEntryKind.Normal,
        promptPositionId: String = "",
    ): SettingLibraryEntry {
        return SettingLibraryEntry(
            id = id,
            title = id,
            kind = kind,
            position = position,
            order = order,
            promptPositionId = promptPositionId,
            triggerMode = SettingLibraryTriggerMode.Always,
        )
    }

    private fun promptPosition(id: String, anchor: SettingLibraryPosition): SettingLibraryPromptPosition {
        return SettingLibraryPromptPosition(id = id, name = id, anchor = anchor, order = 1)
    }
}
