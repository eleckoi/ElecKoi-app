package com.eleckoi.android.feature.characters.modes.story.variables.ui

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schema
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.story.variables.model.VariableInitializationObjectId
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.model.VariableReadMode
import com.eleckoi.android.engine.story.variables.model.VariableValueType
import com.eleckoi.android.engine.story.variables.model.isInitializationObject
import com.eleckoi.android.engine.story.variables.model.variableTypeLabel
import com.eleckoi.android.foundation.design.components.DshTreeDisclosureGlyph
import com.eleckoi.android.foundation.design.components.dshTreeRowEntrance
import com.eleckoi.android.foundation.design.components.noRippleClickable

internal sealed interface VariableTreeNode {
    val id: String
    val title: String
    val depth: Int
    val ancestorEnabled: Boolean
    val effectiveEnabled: Boolean

    data class ObjectNode(
        val variableObject: VariableObjectConfig,
        override val depth: Int,
        override val ancestorEnabled: Boolean,
    ) : VariableTreeNode {
        override val id: String = variableObjectNodeId(variableObject.id)
        override val title: String = variableObject.name.ifBlank { "未命名变量组" }
        override val effectiveEnabled: Boolean = ancestorEnabled && (variableObject.isInitializationObject() || variableObject.enabled)
    }

    data class VariableNode(
        val variable: VariableItemConfig,
        override val depth: Int,
        override val ancestorEnabled: Boolean,
    ) : VariableTreeNode {
        override val id: String = variableItemNodeId(variable.id)
        override val title: String = variable.title.ifBlank { "未命名变量" }
        override val effectiveEnabled: Boolean = ancestorEnabled && variable.enabled
    }
}

internal fun variableTreeNodes(
    objects: List<VariableObjectConfig>,
    variables: List<VariableItemConfig>,
    expandedObjectIds: Set<String>,
    search: String,
    forceCollapsedObjectIds: Set<String> = emptySet(),
): List<VariableTreeNode> {
    val query = search.trim()
    val childrenByParent = objects.groupBy { it.parentId }
    val variablesByObject = variables.groupBy { it.objectId }
    val nodes = mutableListOf<VariableTreeNode>()

    fun variableMatches(variable: VariableItemConfig): Boolean {
        return query.isBlank() ||
            variable.title.contains(query, ignoreCase = true) ||
            variable.type.contains(query, ignoreCase = true) ||
            variable.defaultValue.contains(query, ignoreCase = true) ||
            variable.readMode.label.contains(query, ignoreCase = true)
    }

    fun objectMatches(variableObject: VariableObjectConfig): Boolean {
        if (query.isBlank()) return true
        if (variableObject.name.contains(query, ignoreCase = true)) return true
        if (variablesByObject[variableObject.id].orEmpty().any(::variableMatches)) return true
        return childrenByParent[variableObject.id].orEmpty().any(::objectMatches)
    }

    fun childNodes(parentId: String, depth: Int, ancestorEnabled: Boolean): List<VariableTreeNode> {
        val objectNodes = childrenByParent[parentId].orEmpty()
            .filter(::objectMatches)
            .map { variableObject ->
                VariableTreeNode.ObjectNode(
                    variableObject = variableObject,
                    depth = depth,
                    ancestorEnabled = ancestorEnabled,
                )
            }
        val variableNodes = variablesByObject[parentId].orEmpty()
            .filter(::variableMatches)
            .map { item ->
                VariableTreeNode.VariableNode(
                    variable = item,
                    depth = depth,
                    ancestorEnabled = ancestorEnabled,
                )
            }
        return (objectNodes + variableNodes).sortedWith(
            compareBy<VariableTreeNode> { node -> node.treeViewOrder }
                .thenBy { node -> if (node is VariableTreeNode.ObjectNode) 0 else 1 },
        )
    }

    fun addObject(variableObject: VariableObjectConfig, depth: Int, ancestorEnabled: Boolean) {
        if (!objectMatches(variableObject)) return
        val systemPinned = variableObject.isInitializationObject()
        val objectNode = VariableTreeNode.ObjectNode(
            variableObject = variableObject,
            depth = depth,
            ancestorEnabled = ancestorEnabled,
        )
        val expanded = !systemPinned && variableObject.id !in forceCollapsedObjectIds && (query.isNotBlank() || variableObject.id in expandedObjectIds)
        nodes += objectNode
        if (!expanded) return
        childNodes(variableObject.id, depth + 1, objectNode.effectiveEnabled).forEach { child ->
            when (child) {
                is VariableTreeNode.ObjectNode -> addObject(child.variableObject, depth + 1, child.ancestorEnabled)
                is VariableTreeNode.VariableNode -> nodes += child
            }
        }
    }

    childNodes("", 0, ancestorEnabled = true).forEach { child ->
        when (child) {
            is VariableTreeNode.ObjectNode -> addObject(child.variableObject, 0, child.ancestorEnabled)
            is VariableTreeNode.VariableNode -> nodes += child
        }
    }
    return nodes
}

private val VariableTreeNode.treeViewOrder: Int
    get() = when (this) {
        is VariableTreeNode.ObjectNode -> if (variableObject.id == VariableInitializationObjectId) Int.MIN_VALUE else variableObject.treeViewOrder
        is VariableTreeNode.VariableNode -> variable.treeViewOrder
    }

@Composable
internal fun VariableTreeNodeRow(
    node: VariableTreeNode,
    selected: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    reorderModifier: Modifier = Modifier,
    expanded: Boolean,
    horizontalScrollState: ScrollState,
    appearance: AppearanceTheme,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onObjectEnabledChange: (VariableObjectConfig, Boolean) -> Unit,
    onVariableEnabledChange: (VariableItemConfig, Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    val enabled = node.effectiveEnabled
    var lastClickAt by remember(node.id) { mutableLongStateOf(0L) }
    Row(
        modifier = modifier
            .dshTreeRowEntrance(enabled = node.depth > 0)
            .horizontalScroll(horizontalScrollState)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .fillMaxWidth()
            .widthIn(min = 300.dp + (node.depth * 14).dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected || dragging) appearance.mobileSurface else appearance.mobileBg)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) appearance.mobileBlue else appearance.mobileBg,
                shape = RoundedCornerShape(8.dp),
            )
            .alpha(if (enabled) 1f else 0.42f)
            .then(reorderModifier)
            .noRippleClickable {
                val now = SystemClock.uptimeMillis()
                if (now - lastClickAt <= DoubleClickMillis) {
                    lastClickAt = 0L
                    onOpen()
                } else {
                    lastClickAt = now
                    onSelect()
                }
            }
            .padding(start = (4 + node.depth * 18).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (node) {
            is VariableTreeNode.ObjectNode -> {
                val initialization = node.variableObject.isInitializationObject()
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(if (initialization) Modifier else Modifier.noRippleClickable(onClick = onToggle)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (initialization) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = appearance.mobileMuted.copy(alpha = 0.74f),
                            modifier = Modifier.size(15.dp),
                        )
                    } else {
                        DshTreeDisclosureGlyph(
                            expanded = expanded,
                            tint = appearance.mobileMuted,
                            iconSize = 14.dp,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(3.dp))
                VariableObjectGlyph(initialization, node.effectiveEnabled, appearance)
            }
            is VariableTreeNode.VariableNode -> {
                Spacer(modifier = Modifier.width(27.dp))
                VariableGlyph(type = node.variable.type, enabled = node.variable.enabled, appearance = appearance)
            }
        }
        Text(
            node.title,
            modifier = Modifier.weight(1f).padding(start = 7.dp, end = 8.dp),
            color = appearance.mobileText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (node) {
            is VariableTreeNode.ObjectNode -> {
                if (!node.variableObject.isInitializationObject()) {
                    Text(
                        ObjectTypeLabel,
                        color = appearance.mobileMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    SmallVariableEnabledSwitch(
                        checked = node.effectiveEnabled,
                        appearance = appearance,
                        enabled = node.ancestorEnabled,
                        onClick = { onObjectEnabledChange(node.variableObject, !node.variableObject.enabled) },
                    )
                }
            }
            is VariableTreeNode.VariableNode -> {
                if (node.variable.readMode == VariableReadMode.Required) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "必读",
                        tint = appearance.mobileBlue.copy(alpha = 0.72f),
                        modifier = Modifier.padding(end = 7.dp).size(13.dp),
                    )
                }
                Text(
                    variableTypeLabel(node.variable.type),
                    color = appearance.mobileMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                SmallVariableEnabledSwitch(
                    checked = node.effectiveEnabled,
                    appearance = appearance,
                    enabled = node.ancestorEnabled,
                    onClick = { onVariableEnabledChange(node.variable, !node.variable.enabled) },
                )
            }
        }
    }
}

private val DoubleClickMillis = (ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getTapTimeout()).toLong()
private const val ObjectTypeLabel = "object"

@Composable
private fun VariableObjectGlyph(initialization: Boolean, enabled: Boolean, appearance: AppearanceTheme) {
    Icon(
        imageVector = if (initialization) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.Schema,
        contentDescription = null,
        tint = when {
            initialization -> appearance.mobileBlue.copy(alpha = 0.68f)
            enabled -> appearance.mobileBlue.copy(alpha = 0.64f)
            else -> appearance.mobileMuted.copy(alpha = 0.72f)
        },
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun VariableGlyph(type: String, enabled: Boolean, appearance: AppearanceTheme) {
    val typeIcon = VariableValueType.entries.firstOrNull { it.raw == type }?.let(::variableTypeIcon)
    Icon(
        imageVector = typeIcon ?: Icons.Rounded.DataObject,
        contentDescription = null,
        tint = if (enabled) appearance.mobileBlue.copy(alpha = 0.72f) else appearance.mobileMuted.copy(alpha = 0.72f),
        modifier = Modifier.size(19.dp),
    )
}

@Composable
private fun SmallVariableEnabledSwitch(
    checked: Boolean,
    appearance: AppearanceTheme,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) appearance.mobileBlue.copy(alpha = 0.86f) else appearance.mobileMuted.copy(alpha = 0.26f))
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(appearance.mobileSurface),
        )
    }
}
