package com.eleckoi.android.feature.characters.ui.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.characters.ui.list.ALL_CHARACTERS
import com.eleckoi.android.feature.characters.ui.list.CharacterBatchAction
import com.eleckoi.android.feature.characters.ui.list.characterGroup

@Composable
internal fun CharacterWaterfallSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    action: CharacterBatchAction,
    appearance: AppearanceTheme,
    onToggleSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selectedCount == 0) "选择角色" else "已选 $selectedCount",
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        WaterfallTextAction(if (allSelected) "取消全选" else "全选", appearance, onClick = onToggleSelectAll)
        if (selectedCount > 0) {
            val label = if (action == CharacterBatchAction.Export) "导出" else "删除"
            val color = if (action == CharacterBatchAction.Export) appearance.mobileBlue else ElecKoiDanger
            WaterfallTextAction(label, appearance, color = color, onClick = onConfirm)
        }
        WaterfallTextAction("取消", appearance, color = appearance.mobileMuted, onClick = onCancel)
    }
}

@Composable
private fun WaterfallTextAction(
    text: String,
    appearance: AppearanceTheme,
    color: Color = appearance.mobileText,
    onClick: () -> Unit,
) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
internal fun CharacterWaterfallGroupChips(
    groups: List<String>,
    characters: CharactersPayload,
    selectedGroup: String,
    appearance: AppearanceTheme,
    onSelectGroup: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        item("chip-$ALL_CHARACTERS") {
            CharacterWaterfallGroupChip(
                text = "全部",
                count = characters.items.size,
                active = selectedGroup == ALL_CHARACTERS,
                appearance = appearance,
                onClick = { onSelectGroup(ALL_CHARACTERS) },
            )
        }
        items(groups, key = { "chip-$it" }) { group ->
            CharacterWaterfallGroupChip(
                text = group,
                count = characters.items.count { characterGroup(it) == group },
                active = selectedGroup == group,
                appearance = appearance,
                onClick = { onSelectGroup(group) },
            )
        }
    }
}

// No shell. Two bordered 34dp pills spent a whole band of the screen on what is a filter, and the
// count on an inactive group mostly announced that it is empty — it shows on the active one, where
// it describes what you are looking at.
@Composable
private fun CharacterWaterfallGroupChip(
    text: String,
    count: Int,
    active: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) appearance.mobileText.copy(alpha = 0.07f) else Color.Transparent)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (active) appearance.mobileText else appearance.mobileMuted,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (active) {
            Text(
                count.toString(),
                color = appearance.mobileSoft,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
