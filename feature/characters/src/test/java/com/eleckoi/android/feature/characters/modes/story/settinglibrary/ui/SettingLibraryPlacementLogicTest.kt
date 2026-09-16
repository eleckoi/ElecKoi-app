package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntryKind
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingLibraryPlacementLogicTest {
    @Test
    fun `new preset position defaults above its setting insertion point`() {
        val position = SettingLibraryPromptPosition(
            id = "default-position",
            name = "默认位置",
            anchor = SettingLibraryPosition.InsertPoint1,
        )

        assertEquals(SettingLibraryPromptPositionSide.BeforeSettingPosition, position.side)
    }

    @Test
    fun `placement guide exposes five numbered slots and fixed context nodes`() {
        val positions = SettingLibraryPosition.entries.mapIndexed { index, anchor ->
            promptPosition("position-$index", anchor)
        }

        val rows = placementGuideRows(positions)

        assertEquals(
            listOf(
                FixedPlacementNode.Cache,
                FixedPlacementNode.History,
                FixedPlacementNode.LatestUserInput,
                FixedPlacementNode.ToolFlow,
            ),
            rows.filterIsInstance<PlacementGuideRow.FixedNode>().map { it.node },
        )
        assertEquals(4, rows.count { it is PlacementGuideRow.FixedNode })
        assertEquals(5, rows.count { it is PlacementGuideRow.Slot })
        assertEquals(5, rows.count { it is PlacementGuideRow.Custom })
        assertEquals(1, rows.count { it is PlacementGuideRow.Instructions })
    }

    @Test
    fun `ordinary setting picker exposes fixed contexts only`() {
        val rows = placementGuideRows(
            positions = listOf(promptPosition("preset-only", SettingLibraryPosition.InsertPoint2)),
            includeCustomPositions = false,
        )

        assertEquals(
            listOf(
                PlacementGuideRow.Instructions,
                PlacementGuideRow.Slot(PlacementSlot.One),
                PlacementGuideRow.FixedNode(FixedPlacementNode.Cache),
                PlacementGuideRow.Slot(PlacementSlot.Two),
                PlacementGuideRow.FixedNode(FixedPlacementNode.History),
                PlacementGuideRow.Slot(PlacementSlot.Three),
                PlacementGuideRow.FixedNode(FixedPlacementNode.LatestUserInput),
                PlacementGuideRow.Slot(PlacementSlot.Four),
                PlacementGuideRow.FixedNode(FixedPlacementNode.ToolFlow),
                PlacementGuideRow.Slot(PlacementSlot.Five),
            ),
            rows,
        )
        assertEquals(
            FixedPlacementNode.entries.toList(),
            rows.filterIsInstance<PlacementGuideRow.FixedNode>().map { it.node },
        )
        assertEquals(0, rows.count { it is PlacementGuideRow.Custom })
        assertEquals(5, rows.count { it is PlacementGuideRow.Slot })
        assertEquals(1, rows.count { it is PlacementGuideRow.Instructions })
    }

    @Test
    fun `custom preset positions can sit on either side of a setting insertion point`() {
        val before = promptPosition(
            id = "preset-before",
            anchor = SettingLibraryPosition.InsertPoint2,
            side = SettingLibraryPromptPositionSide.BeforeSettingPosition,
        )
        val after = promptPosition(
            id = "preset-after",
            anchor = SettingLibraryPosition.InsertPoint2,
            side = SettingLibraryPromptPositionSide.AfterSettingPosition,
        )

        val rows = placementGuideRows(listOf(after, before))
        val beforeIndex = rows.indexOfFirst { it is PlacementGuideRow.Custom && it.position.id == before.id }
        val slotIndex = rows.indexOf(PlacementGuideRow.Slot(PlacementSlot.Two))
        val afterIndex = rows.indexOfFirst { it is PlacementGuideRow.Custom && it.position.id == after.id }

        assertEquals(true, beforeIndex < slotIndex)
        assertEquals(true, slotIndex < afterIndex)
    }

    @Test
    fun `dragging across a setting insertion point changes the custom side`() {
        val moving = promptPosition(
            id = "moving",
            anchor = SettingLibraryPosition.InsertPoint2,
            side = SettingLibraryPromptPositionSide.AfterSettingPosition,
        )

        val before = movePromptPosition(
            positions = listOf(moving),
            movingId = moving.id,
            target = PlacementGuideRow.Slot(PlacementSlot.Two),
            movingDown = false,
        ).single()
        val after = movePromptPosition(
            positions = listOf(before),
            movingId = moving.id,
            target = PlacementGuideRow.Slot(PlacementSlot.Two),
            movingDown = true,
        ).single()

        assertEquals(SettingLibraryPromptPositionSide.BeforeSettingPosition, before.side)
        assertEquals(SettingLibraryPromptPositionSide.AfterSettingPosition, after.side)
    }

    @Test
    fun `system instructions and the following message boundary keep distinct roles`() {
        val entry = entry("moving", SettingLibraryPosition.InsertPoint1, order = 1)

        val inInstructions = movePositionEntry(
            entries = listOf(entry),
            entryId = entry.id,
            targetPosition = SettingLibraryPosition.Instructions,
        ).single()
        assertEquals(SettingLibraryInsertRole.System, inInstructions.insertRole)

        val afterInstructions = movePositionEntry(
            entries = listOf(inInstructions),
            entryId = entry.id,
            targetPosition = SettingLibraryPosition.InsertPoint1,
        ).single()
        assertEquals(SettingLibraryInsertRole.User, afterInstructions.insertRole)
    }

    @Test
    fun `dragging across a fixed context lands outside the whole triple`() {
        val beforeToolFlow = promptPosition("moving", SettingLibraryPosition.InsertPoint4)
        val afterToolFlow = movePromptPosition(
            positions = listOf(beforeToolFlow),
            movingId = beforeToolFlow.id,
            target = PlacementGuideRow.FixedNode(FixedPlacementNode.ToolFlow),
            movingDown = true,
        )

        assertEquals(SettingLibraryPosition.InsertPoint5, afterToolFlow.single().anchor)

        val movedBack = movePromptPosition(
            positions = afterToolFlow,
            movingId = beforeToolFlow.id,
            target = PlacementGuideRow.FixedNode(FixedPlacementNode.ToolFlow),
            movingDown = false,
        )

        assertEquals(SettingLibraryPosition.InsertPoint4, movedBack.single().anchor)
    }

    @Test
    fun `a prompt position can be dragged beneath the final fixed context`() {
        val moving = promptPosition("moving", SettingLibraryPosition.InsertPoint1)

        val moved = movePromptPosition(
            positions = listOf(moving),
            movingId = moving.id,
            target = PlacementGuideRow.FixedNode(FixedPlacementNode.ToolFlow),
            movingDown = true,
        )

        assertEquals(SettingLibraryPosition.InsertPoint5, moved.single().anchor)
    }

    @Test
    fun `order scope is isolated by position`() {
        val entries = listOf(
            entry("after-a", SettingLibraryPosition.InsertPoint2, order = 2),
            entry("after-b", SettingLibraryPosition.InsertPoint2, order = 1),
            entry("before", SettingLibraryPosition.InsertPoint1, order = 1),
        )

        assertEquals(
            listOf("after-b", "after-a"),
            positionOrderScope(entries, SettingLibraryPosition.InsertPoint2).map { it.id },
        )
    }

    @Test
    fun `order scope is isolated by custom prompt position`() {
        val entries = listOf(
            entry("custom-a-2", SettingLibraryPosition.InsertPoint2, order = 2, promptPositionId = "custom-a"),
            entry("custom-a-1", SettingLibraryPosition.InsertPoint2, order = 1, promptPositionId = "custom-a"),
            entry("custom-b", SettingLibraryPosition.InsertPoint2, order = 1, promptPositionId = "custom-b"),
            entry("fixed-anchor", SettingLibraryPosition.InsertPoint2, order = 1),
        )

        assertEquals(
            listOf("custom-a-1", "custom-a-2"),
            positionOrderScope(
                entries,
                SettingLibraryPosition.InsertPoint2,
                promptPositionId = "custom-a",
            ).map { it.id },
        )
    }

    @Test
    fun `next order ignores fixed and current entries`() {
        val entries = listOf(
            entry("one", SettingLibraryPosition.InsertPoint1, order = 1),
            entry("current", SettingLibraryPosition.InsertPoint1, order = 8),
            entry(
                id = "fixed",
                position = SettingLibraryPosition.InsertPoint1,
                order = 99,
                kind = SettingLibraryEntryKind.Opening,
            ),
        )

        assertEquals(
            2,
            nextPositionOrder(
                entries = entries,
                position = SettingLibraryPosition.InsertPoint1,
                excludingEntryId = "current",
            ),
        )
    }

    @Test
    fun `moving a prompt across fixed nodes changes its position and normalizes both scopes`() {
        val entries = listOf(
            entry("source-a", SettingLibraryPosition.InsertPoint1, order = 1),
            entry("moving", SettingLibraryPosition.InsertPoint1, order = 9),
            entry("target-a", SettingLibraryPosition.InsertPoint3, order = 3),
            entry("target-b", SettingLibraryPosition.InsertPoint3, order = 8),
        )

        val moved = movePositionEntry(
            entries = entries,
            entryId = "moving",
            targetPosition = SettingLibraryPosition.InsertPoint3,
            relativeEntryId = "target-a",
            insertAfterRelative = true,
        )

        assertEquals(
            listOf("target-a", "moving", "target-b"),
            positionOrderScope(moved, SettingLibraryPosition.InsertPoint3).map { it.id },
        )
        assertEquals(listOf(1, 2, 3), positionOrderScope(moved, SettingLibraryPosition.InsertPoint3).map { it.order })
        assertEquals(1, moved.single { it.id == "source-a" }.order)
    }

    @Test
    fun `moving an entry into a custom position keeps its internal order separate`() {
        val entries = listOf(
            entry("moving", SettingLibraryPosition.InsertPoint2, order = 4),
            entry(
                "custom-first",
                SettingLibraryPosition.InsertPoint3,
                order = 7,
                promptPositionId = "custom-position",
            ),
            entry("fixed-anchor", SettingLibraryPosition.InsertPoint3, order = 1),
        )

        val moved = movePositionEntry(
            entries = entries,
            entryId = "moving",
            targetPosition = SettingLibraryPosition.InsertPoint3,
            targetPromptPositionId = "custom-position",
        )

        assertEquals(
            listOf("custom-first", "moving"),
            positionOrderScope(
                moved,
                SettingLibraryPosition.InsertPoint3,
                promptPositionId = "custom-position",
            ).map { it.id },
        )
        assertEquals(listOf(1, 2), positionOrderScope(
            moved,
            SettingLibraryPosition.InsertPoint3,
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

    private fun promptPosition(
        id: String,
        anchor: SettingLibraryPosition,
        side: SettingLibraryPromptPositionSide = SettingLibraryPromptPositionSide.AfterSettingPosition,
    ): SettingLibraryPromptPosition {
        return SettingLibraryPromptPosition(id = id, name = id, anchor = anchor, side = side, order = 1)
    }
}
