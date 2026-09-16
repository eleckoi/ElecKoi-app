package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import java.time.Instant

internal sealed interface PlacementGuideRow {
    val key: String

    data object Instructions : PlacementGuideRow {
        override val key: String = "fixed:instructions"
    }

    data class Slot(val slot: PlacementSlot) : PlacementGuideRow {
        override val key: String = "slot:${slot.name}"
    }

    data class FixedNode(val node: FixedPlacementNode) : PlacementGuideRow {
        override val key: String = "fixed-node:${node.name}"
    }

    data class Custom(val position: SettingLibraryPromptPosition) : PlacementGuideRow {
        override val key: String = "custom:${position.id}"
    }
}

internal enum class PlacementSlot(
    val label: String,
    val position: SettingLibraryPosition,
) {
    One(SettingLibraryPosition.InsertPoint1.label, SettingLibraryPosition.InsertPoint1),
    Two(SettingLibraryPosition.InsertPoint2.label, SettingLibraryPosition.InsertPoint2),
    Three(SettingLibraryPosition.InsertPoint3.label, SettingLibraryPosition.InsertPoint3),
    Four(SettingLibraryPosition.InsertPoint4.label, SettingLibraryPosition.InsertPoint4),
    Five(SettingLibraryPosition.InsertPoint5.label, SettingLibraryPosition.InsertPoint5),
}

internal enum class FixedPlacementNode(
    val label: String,
    val before: SettingLibraryPosition,
    val after: SettingLibraryPosition,
) {
    Cache(
        "缓存设定区",
        SettingLibraryPosition.InsertPoint1,
        SettingLibraryPosition.InsertPoint2,
    ),
    History("聊天记录", SettingLibraryPosition.InsertPoint2, SettingLibraryPosition.InsertPoint3),
    LatestUserInput(
        "用户最新输入",
        SettingLibraryPosition.InsertPoint3,
        SettingLibraryPosition.InsertPoint4,
    ),
    ToolFlow("工具调用流程", SettingLibraryPosition.InsertPoint4, SettingLibraryPosition.InsertPoint5),
}

internal fun placementGuideRows(
    positions: List<SettingLibraryPromptPosition>,
    includeCustomPositions: Boolean = true,
): List<PlacementGuideRow> {
    if (!includeCustomPositions) {
        return defaultPlacementGuideRows()
    }
    val byPlacement = positions.normalizedPositions().groupBy { it.anchor to it.side }
    fun MutableList<PlacementGuideRow>.customAt(
        anchor: SettingLibraryPosition,
        side: SettingLibraryPromptPositionSide,
    ) {
        byPlacement[anchor to side].orEmpty().forEach { add(PlacementGuideRow.Custom(it)) }
    }
    fun MutableList<PlacementGuideRow>.slot(slot: PlacementSlot) {
        customAt(slot.position, SettingLibraryPromptPositionSide.BeforeSettingPosition)
        add(PlacementGuideRow.Slot(slot))
        customAt(slot.position, SettingLibraryPromptPositionSide.AfterSettingPosition)
    }
    return buildList {
        add(PlacementGuideRow.Instructions)
        slot(PlacementSlot.One)
        add(PlacementGuideRow.FixedNode(FixedPlacementNode.Cache))
        slot(PlacementSlot.Two)
        add(PlacementGuideRow.FixedNode(FixedPlacementNode.History))
        slot(PlacementSlot.Three)
        add(PlacementGuideRow.FixedNode(FixedPlacementNode.LatestUserInput))
        slot(PlacementSlot.Four)
        add(PlacementGuideRow.FixedNode(FixedPlacementNode.ToolFlow))
        slot(PlacementSlot.Five)
    }
}

private fun defaultPlacementGuideRows(): List<PlacementGuideRow> = listOf(
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
)

internal fun List<SettingLibraryPromptPosition>.normalizedPositions(): List<SettingLibraryPromptPosition> {
    val anchors = SettingLibraryPosition.entries.withIndex().associate { it.value to it.index }
    return sortedWith(
        compareBy<SettingLibraryPromptPosition> { anchors[it.anchor] ?: Int.MAX_VALUE }
            .thenBy { it.side.ordinal }
            .thenBy(SettingLibraryPromptPosition::order)
            .thenBy(SettingLibraryPromptPosition::id),
    ).groupBy { it.anchor to it.side }
        .flatMap { (_, group) -> group.mapIndexed { index, position -> position.copy(order = index + 1) } }
}

internal fun movePromptPosition(
    positions: List<SettingLibraryPromptPosition>,
    movingId: String,
    target: PlacementGuideRow,
    movingDown: Boolean,
): List<SettingLibraryPromptPosition> {
    val moving = positions.firstOrNull { it.id == movingId } ?: return positions
    val without = positions.filterNot { it.id == movingId }.toMutableList()
    val (targetAnchor, targetSide) = when (target) {
        PlacementGuideRow.Instructions -> SettingLibraryPosition.InsertPoint1 to
            SettingLibraryPromptPositionSide.BeforeSettingPosition
        is PlacementGuideRow.Slot -> target.slot.position to if (movingDown) {
            SettingLibraryPromptPositionSide.AfterSettingPosition
        } else {
            SettingLibraryPromptPositionSide.BeforeSettingPosition
        }
        is PlacementGuideRow.FixedNode -> if (movingDown) {
            target.node.after to SettingLibraryPromptPositionSide.BeforeSettingPosition
        } else {
            target.node.before to SettingLibraryPromptPositionSide.AfterSettingPosition
        }
        is PlacementGuideRow.Custom -> target.position.anchor to target.position.side
    }
    val moved = moving.copy(
        anchor = targetAnchor,
        side = targetSide,
        updatedAt = Instant.now().toString(),
    )
    val samePlacement = without.filter {
        it.anchor == targetAnchor && it.side == targetSide
    }.sortedBy { it.order }.toMutableList()
    val targetIndex = when (target) {
        is PlacementGuideRow.Custom -> samePlacement.indexOfFirst { it.id == target.position.id }
            .let { if (it < 0) samePlacement.size else it + if (movingDown) 1 else 0 }
        PlacementGuideRow.Instructions -> 0
        is PlacementGuideRow.Slot -> if (movingDown) 0 else samePlacement.size
        is PlacementGuideRow.FixedNode -> if (movingDown) 0 else samePlacement.size
    }.coerceIn(0, samePlacement.size)
    samePlacement.add(targetIndex, moved)
    val replacedIds = samePlacement.map(SettingLibraryPromptPosition::id)
    val replacement = samePlacement.mapIndexed { index, position -> position.copy(order = index + 1) }
    return (without.filterNot { it.id in replacedIds } + replacement).normalizedPositions()
}
