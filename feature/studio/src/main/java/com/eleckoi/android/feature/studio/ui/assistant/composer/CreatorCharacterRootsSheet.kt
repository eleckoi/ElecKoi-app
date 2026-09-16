package com.eleckoi.android.feature.studio.ui.assistant.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon

@Composable
internal fun CreatorCharacterRootsSheet(
    workspace: CreatorWorkspace,
    rootCharacters: List<CharacterSlot>,
    directoryCharacters: List<CharacterSlot>,
    query: String,
    nextCursor: String,
    loading: Boolean,
    updating: Boolean,
    interactionEnabled: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onAttach: (String) -> Unit,
    onDetach: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onAccessChange: (String, CreatorWorkspaceRootAccess) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable(workspace.id) { mutableStateOf(false) }
    var newCharacterName by rememberSaveable(workspace.id) { mutableStateOf("") }
    val enabled = interactionEnabled && !updating
    val rootsByCharacter = workspace.characterRoots.associateBy { it.characterId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = appearance.mobileBg,
            contentColor = appearance.mobileText,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "创作角色",
                            color = appearance.mobileText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "一个主角色，可添加多个参考角色",
                            color = appearance.mobileMuted,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "关闭" },
                    ) {
                        StrokeSvgIcon(
                            paths = AppIconPaths.X,
                            color = appearance.mobileMuted,
                            iconSize = 19.dp,
                            strokeWidth = 1.8f,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                item(key = "mounted-heading") {
                    SectionHeading("当前范围", appearance)
                }
                if (workspace.characterRoots.isEmpty()) {
                    item(key = "mounted-empty") {
                        QuietMessage(
                            text = "还没有绑定角色。可以先添加参考角色，或直接新建一个主角色。",
                            appearance = appearance,
                        )
                    }
                } else {
                    items(workspace.characterRoots, key = { root -> root.id }) { root ->
                        CreatorRootRow(
                            root = root,
                            character = rootCharacters.firstOrNull { it.id == root.characterId },
                            isPrimary = root.id == workspace.primaryCharacterRootId,
                            enabled = enabled,
                            appearance = appearance,
                            onSetPrimary = { onSetPrimary(root.id) },
                            onAccessChange = { access -> onAccessChange(root.id, access) },
                            onDetach = { onDetach(root.id) },
                        )
                    }
                }

                item(key = "create-action") {
                    if (creating) {
                        NewCharacterRow(
                            value = newCharacterName,
                            enabled = enabled,
                            appearance = appearance,
                            onValueChange = { newCharacterName = it.take(80) },
                            onCancel = {
                                creating = false
                                newCharacterName = ""
                            },
                            onCreate = {
                                val name = newCharacterName.trim()
                                if (name.isNotEmpty()) {
                                    onCreate(name)
                                    creating = false
                                    newCharacterName = ""
                                }
                            },
                        )
                    } else {
                        TextButton(
                            onClick = { creating = true },
                            enabled = enabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = appearance.mobileBlue,
                                disabledContentColor = appearance.mobileMuted.copy(alpha = 0.38f),
                            ),
                            modifier = Modifier.height(44.dp),
                        ) {
                            StrokeSvgIcon(
                                paths = AppIconPaths.CharacterPlus,
                                color = if (enabled) appearance.mobileBlue else appearance.mobileMuted.copy(alpha = 0.38f),
                                iconSize = 18.dp,
                                strokeWidth = 1.75f,
                            )
                            Text("新建角色", fontSize = 14.sp, modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }

                item(key = "directory-heading") {
                    SectionHeading("角色目录", appearance, topPadding = 8.dp)
                }
                item(key = "directory-search") {
                    AppSearchField(
                        keyword = query,
                        placeholder = "搜索角色名称",
                        appearance = appearance,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp,
                        fontSize = 14.sp,
                        onKeywordChange = onQueryChange,
                    )
                }
                if (directoryCharacters.isEmpty() && !loading) {
                    item(key = "directory-empty") {
                        QuietMessage(
                            text = if (query.isBlank()) "还没有其他角色" else "没有找到匹配角色",
                            appearance = appearance,
                        )
                    }
                }
                items(directoryCharacters, key = { character -> character.id }) { character ->
                    CharacterDirectoryRow(
                        character = character,
                        attachedRoot = rootsByCharacter[character.id],
                        enabled = enabled,
                        appearance = appearance,
                        onAttach = { onAttach(character.id) },
                    )
                }
                if (loading) {
                    item(key = "directory-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = appearance.mobileBlue,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else if (nextCursor.isNotBlank()) {
                    item(key = "directory-more") {
                        TextButton(
                            onClick = onLoadMore,
                            colors = ButtonDefaults.textButtonColors(contentColor = appearance.mobileBlue),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Text("加载更多角色", fontSize = 14.sp)
                        }
                    }
                }
                }
            }
        }
    }
}
