package com.eleckoi.android.feature.characters.presets.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRuleEditorSheet
import com.eleckoi.android.feature.characters.modes.story.regex.ui.components.RegexRuleSection
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryEditorCardSpacing
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger

@Composable
internal fun AgentPresetRegexTab(
    rules: List<RegexRule>,
    appearance: AppearanceTheme,
    onUpdate: (List<RegexRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Pair<RegexRule, Boolean>?>(null) }
    var pendingDelete by remember { mutableStateOf<RegexRule?>(null) }
    var dragDraft by remember(rules) { mutableStateOf<List<RegexRule>?>(null) }
    val displayed = dragDraft ?: rules

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(StoryEditorCardSpacing),
    ) {
        item(key = "preset-regex") {
            RegexRuleSection(
                scope = RegexRuleScope.PromptPreset,
                rules = displayed,
                expanded = true,
                batchEditing = false,
                selectedIds = emptySet(),
                appearance = appearance,
                onToggleExpanded = {},
                onAdd = { editing = RegexRule() to true },
                onSelect = {},
                onToggle = { rule ->
                    onUpdate(rules.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it })
                },
                onEdit = { editing = it to false },
                onMove = { rule, direction -> onUpdate(rules.moveRule(rule.id, direction)) },
                onDelete = { pendingDelete = it },
                onDragStart = { dragDraft = rules },
                onDragMove = { ruleId, targetRuleId ->
                    val next = (dragDraft ?: rules).moveRuleTo(ruleId, targetRuleId)
                    val changed = next != dragDraft
                    dragDraft = next
                    changed
                },
                onDragStop = {
                    dragDraft?.let(onUpdate)
                    dragDraft = null
                },
            )
        }
    }

    editing?.let { (rule, isNew) ->
        RegexRuleEditorSheet(
            rule = rule,
            appearance = appearance,
            onDismiss = { editing = null },
            onSave = { updated ->
                onUpdate((rules.filterNot { it.id == updated.id } + updated).normalizeOrder())
                editing = null
            },
            onDelete = if (isNew) null else {{
                pendingDelete = rule
                editing = null
            }},
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条预设正则？") },
            text = { Text(rule.name.ifBlank { "删除后无法恢复。" }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(rules.filterNot { it.id == rule.id }.normalizeOrder())
                        pendingDelete = null
                    },
                ) { Text("删除", color = ElecKoiDanger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
            containerColor = appearance.mobileSurface,
            titleContentColor = appearance.mobileText,
            textContentColor = appearance.mobileMuted,
        )
    }
}

private fun List<RegexRule>.moveRule(id: String, direction: Int): List<RegexRule> {
    val from = indexOfFirst { it.id == id }
    val to = (from + direction).coerceIn(indices)
    if (from < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }.normalizeOrder()
}

private fun List<RegexRule>.moveRuleTo(id: String, targetId: String): List<RegexRule> {
    val from = indexOfFirst { it.id == id }
    val to = indexOfFirst { it.id == targetId }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }.normalizeOrder()
}

private fun List<RegexRule>.normalizeOrder(): List<RegexRule> = mapIndexed { index, rule ->
    rule.copy(order = index)
}
