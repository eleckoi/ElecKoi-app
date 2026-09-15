package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import java.time.Instant

internal sealed interface PlacementGuideRow {
    val key: String

    data object Instructions : PlacementGuideRow {
        override val key: String = "fixed:instructions"
    }

    data object AfterInstructions : PlacementGuideRow {
        override val key: String = "anchor:after-instructions"
    }

    data class FixedGroup(val node: FixedPlacementNode) : PlacementGuideRow {
        override val key: String = "fixed-group:${node.name}"
    }

    data class Custom(val position: SettingLibraryPromptPosition) : PlacementGuideRow {
        override val key: String = "custom:${position.id}"
    }
}

internal enum class FixedPlacementNode(
    val label: String,
    val before: SettingLibraryPosition,
    val after: SettingLibraryPosition,
) {
    History("聊天记录", SettingLibraryPosition.BeforeHistory, SettingLibraryPosition.AfterHistory),
    LatestUserInput(
        "用户最新输入",
        SettingLibraryPosition.BeforeLatestUserInput,
        SettingLibraryPosition.AfterLatestUserInput,
    ),
    ToolFlow("工具调用流程", SettingLibraryPosition.BeforeToolFlow, SettingLibraryPosition.AfterToolFlow),
}

internal fun placementGuideRows(
    positions: List<SettingLibraryPromptPosition>,
    includeCustomPositions: Boolean = true,
): List<PlacementGuideRow> {
    if (!includeCustomPositions) {
        return listOf(PlacementGuideRow.Instructions, PlacementGuideRow.AfterInstructions) +
            FixedPlacementNode.entries.map(PlacementGuideRow::FixedGroup)
    }
    val byAnchor = positions.normalizedPositions().groupBy(SettingLibraryPromptPosition::anchor)
    fun MutableList<PlacementGuideRow>.customAt(anchor: SettingLibraryPosition) {
        byAnchor[anchor].orEmpty().forEach { add(PlacementGuideRow.Custom(it)) }
    }
    return buildList {
        add(PlacementGuideRow.Instructions)
        add(PlacementGuideRow.AfterInstructions)
        customAt(SettingLibraryPosition.AfterInstructions)
        customAt(SettingLibraryPosition.BeforeHistory)
        add(PlacementGuideRow.FixedGroup(FixedPlacementNode.History))
        customAt(SettingLibraryPosition.AfterHistory)
        customAt(SettingLibraryPosition.BeforeLatestUserInput)
        add(PlacementGuideRow.FixedGroup(FixedPlacementNode.LatestUserInput))
        customAt(SettingLibraryPosition.AfterLatestUserInput)
        customAt(SettingLibraryPosition.BeforeToolFlow)
        add(PlacementGuideRow.FixedGroup(FixedPlacementNode.ToolFlow))
        customAt(SettingLibraryPosition.AfterToolFlow)
    }
}

internal fun List<SettingLibraryPromptPosition>.normalizedPositions(): List<SettingLibraryPromptPosition> {
    val anchors = SettingLibraryPosition.entries.withIndex().associate { it.value to it.index }
    return sortedWith(
        compareBy<SettingLibraryPromptPosition> { anchors[it.anchor] ?: Int.MAX_VALUE }
            .thenBy(SettingLibraryPromptPosition::order)
            .thenBy(SettingLibraryPromptPosition::id),
    ).groupBy(SettingLibraryPromptPosition::anchor)
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
    val targetAnchor = when (target) {
        PlacementGuideRow.Instructions, PlacementGuideRow.AfterInstructions ->
            SettingLibraryPosition.AfterInstructions
        is PlacementGuideRow.FixedGroup -> if (movingDown) target.node.after else target.node.before
        is PlacementGuideRow.Custom -> target.position.anchor
    }
    val moved = moving.copy(anchor = targetAnchor, updatedAt = Instant.now().toString())
    val sameAnchor = without.filter { it.anchor == targetAnchor }.sortedBy { it.order }.toMutableList()
    val targetIndex = when (target) {
        is PlacementGuideRow.Custom -> sameAnchor.indexOfFirst { it.id == target.position.id }
            .let { if (it < 0) sameAnchor.size else it + if (movingDown) 1 else 0 }
        else -> if (movingDown) sameAnchor.size else 0
    }.coerceIn(0, sameAnchor.size)
    sameAnchor.add(targetIndex, moved)
    val replacedIds = sameAnchor.map(SettingLibraryPromptPosition::id)
    val replacement = sameAnchor.mapIndexed { index, position -> position.copy(order = index + 1) }
    return (without.filterNot { it.id in replacedIds } + replacement).normalizedPositions()
}
