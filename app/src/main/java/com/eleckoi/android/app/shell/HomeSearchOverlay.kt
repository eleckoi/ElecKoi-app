package com.eleckoi.android.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.modelconfig.ui.ModelProviderMeta
import com.eleckoi.android.feature.modelconfig.ui.visibleModelProviders
import com.eleckoi.android.feature.modelconfig.ui.normalizeProviderId
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshSearchGlyph
import com.eleckoi.android.foundation.design.components.MobileConversationRow
import com.eleckoi.android.foundation.design.components.MobileEmptyState
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.formatShortDate
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import kotlinx.coroutines.delay

/** Full-home search adapted from the reference PR, using the categories this build can open. */
@Composable
internal fun HomeSearchOverlay(
    visible: Boolean,
    chats: List<ChatListItem>,
    characters: CharactersPayload?,
    modelConfigs: List<ModelConfig>,
    history: List<String>,
    appearance: AppearanceTheme,
    useCoverArtwork: Boolean,
    onDismiss: () -> Unit,
    onCommitTerm: (String) -> Unit,
    onForgetTerm: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenModel: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (!visible) {
            keyboardController?.hide()
            return@LaunchedEffect
        }
        query = ""
        delay(FocusSettleMillis)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(SearchEnterMillis, easing = FastOutSlowInEasing),
        ) { it },
        exit = slideOutVertically(
            animationSpec = tween(SearchExitMillis, easing = FastOutSlowInEasing),
        ) { it },
    ) {
        val key = query.trim()
        val chatHits = remember(chats, key) {
            if (key.isBlank()) emptyList() else filterMessageRootSearch(chats, key)
        }
        val characterHits = remember(characters, key) {
            if (key.isBlank()) emptyList() else filterHomeCharacters(characters?.items.orEmpty(), key)
        }
        val modelHits = remember(modelConfigs, key) {
            if (key.isBlank()) emptyList() else filterHomeModels(
                visibleModelProviders(modelConfigs),
                modelConfigs,
                key,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appearance.mobileSurface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            SearchTitleBar(
                appearance = appearance,
                onDismiss = {
                    keyboardController?.hide()
                    onDismiss()
                },
            )
            HomeSearchField(
                value = query,
                onValueChange = { query = it },
                appearance = appearance,
                focusRequester = focusRequester,
                onSubmit = {
                    key.takeIf(String::isNotBlank)?.let(onCommitTerm)
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SearchGutter),
            )

            when {
                key.isBlank() -> SearchHistory(
                    history = history,
                    appearance = appearance,
                    onPick = { query = it },
                    onForget = onForgetTerm,
                    onClear = onClearHistory,
                )

                chatHits.isEmpty() && characterHits.isEmpty() && modelHits.isEmpty() ->
                    MobileEmptyState("没有找到「$key」", appearance)

                else -> SearchResults(
                    chatHits = chatHits,
                    characterHits = characterHits,
                    modelHits = modelHits,
                    modelConfigs = modelConfigs,
                    appearance = appearance,
                    useCoverArtwork = useCoverArtwork,
                    onOpenChat = { chatId ->
                        onCommitTerm(key)
                        keyboardController?.hide()
                        onOpenChat(chatId)
                    },
                    onOpenCharacter = { characterId ->
                        onCommitTerm(key)
                        keyboardController?.hide()
                        onOpenCharacter(characterId)
                    },
                    onOpenModel = { providerId, configId ->
                        onCommitTerm(key)
                        keyboardController?.hide()
                        onOpenModel(providerId, configId)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchTitleBar(
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = SearchGutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "搜索",
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "取消搜索"
                    role = Role.Button
                }
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("取消", color = appearance.mobileMuted, fontSize = 14.5.sp)
        }
    }
}

@Composable
private fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    appearance: AppearanceTheme,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(appearance.mobileSearchBg)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DshSearchGlyph(tint = appearance.mobileMuted, iconSize = 16.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = "会话、角色、模型",
                    color = appearance.mobileSoft,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "搜索会话、角色或模型" },
                textStyle = TextStyle(color = appearance.mobileText, fontSize = 15.sp),
                cursorBrush = SolidColor(appearance.mobileBlue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSubmit() },
                ),
                singleLine = true,
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "清除搜索"
                        role = Role.Button
                    }
                    .noRippleClickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(
                    paths = AppIconPaths.X,
                    color = appearance.mobileMuted,
                    iconSize = 16.dp,
                    strokeWidth = 1.8f,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
private fun SearchResults(
    chatHits: List<ChatListItem>,
    characterHits: List<CharacterSlot>,
    modelHits: List<ModelProviderMeta>,
    modelConfigs: List<ModelConfig>,
    appearance: AppearanceTheme,
    useCoverArtwork: Boolean,
    onOpenChat: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenModel: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        if (chatHits.isNotEmpty()) {
            item("search-chats-header") {
                SearchSectionHeader("会话", chatHits.size, appearance)
            }
            items(chatHits, key = { "search-chat-${it.id}" }) { chat ->
                MobileConversationRow(
                    title = messageRootEntryTitle(chat),
                    subtitle = chat.summary.ifBlank { "还没有消息" },
                    avatarName = messageRootEntryTitle(chat),
                    avatarPath = chat.characterAvatar,
                    coverPath = chat.characterCover,
                    useCoverArtwork = useCoverArtwork,
                    sideText = formatShortDate(chat.updatedAt),
                    appearance = appearance,
                    onClick = { onOpenChat(chat.id) },
                )
            }
            item("search-chats-gap") { Spacer(modifier = Modifier.height(14.dp)) }
        }
        if (characterHits.isNotEmpty()) {
            item("search-characters-header") {
                SearchSectionHeader("角色", characterHits.size, appearance)
            }
            items(characterHits, key = { "search-character-${it.id}" }) { character ->
                MobileConversationRow(
                    title = character.persona.assistantName
                        .ifBlank { character.name }
                        .ifBlank { "未命名角色" },
                    subtitle = character.group.trim().ifBlank { "未分组" },
                    avatarName = character.name,
                    avatarPath = character.persona.assistantAvatar.ifBlank { character.avatar },
                    coverPath = character.persona.assistantCover
                        .ifBlank { character.coverImage }
                        .ifBlank { character.persona.assistantAvatar.ifBlank { character.avatar } },
                    useCoverArtwork = useCoverArtwork,
                    sideText = "",
                    appearance = appearance,
                    onClick = { onOpenCharacter(character.id) },
                )
            }
            item("search-characters-gap") { Spacer(modifier = Modifier.height(14.dp)) }
        }
        if (modelHits.isNotEmpty()) {
            item("search-models-header") {
                SearchSectionHeader("模型", modelHits.size, appearance)
            }
            items(modelHits, key = { "search-model-${it.id}" }) { provider ->
                val config = firstModelConfigForProvider(modelConfigs, provider.id)
                SearchModelRow(
                    provider = provider,
                    config = config,
                    appearance = appearance,
                    onClick = { onOpenModel(provider.id, config?.id.orEmpty()) },
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    label: String,
    count: Int,
    appearance: AppearanceTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = SearchGutter)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count 个结果",
            color = appearance.mobileMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SearchModelRow(
    provider: ModelProviderMeta,
    config: ModelConfig?,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .semantics(mergeDescendants = true) {}
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(horizontal = SearchGutter, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelProviderIcon(
            providerId = provider.id,
            initials = provider.initials,
            appearance = appearance,
            modifier = Modifier.size(45.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 9.dp),
        ) {
            Text(
                text = provider.label,
                color = appearance.mobileText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = config?.name.orEmpty().ifBlank { config?.model.orEmpty() }.ifBlank { provider.summary },
                color = appearance.mobileMuted.copy(alpha = 0.72f),
                fontSize = 12.5.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StrokeSvgIcon(
            paths = AppIconPaths.ChevronRight,
            color = appearance.mobileSoft,
            iconSize = 17.dp,
            strokeWidth = 1.8f,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistory(
    history: List<String>,
    appearance: AppearanceTheme,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = SearchGutter, end = 4.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近搜索",
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .noRippleClickable(onClick = onClear)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StrokeSvgIcon(
                        paths = AppIconPaths.Trash,
                        color = ElecKoiDanger,
                        iconSize = 13.dp,
                        strokeWidth = 1.8f,
                    )
                    Text("全部清除", color = ElecKoiDanger, fontSize = 12.5.sp)
                }
            }
        }
        if (history.isEmpty()) {
            MobileEmptyState("还没有搜索记录", appearance)
            return@Column
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SearchGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { term ->
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(appearance.mobileSearchBg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .noRippleClickable { onPick(term) }
                            .padding(start = 14.dp, end = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = term,
                            color = appearance.mobileText,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "删除最近搜索：$term"
                                role = Role.Button
                            }
                            .noRippleClickable { onForget(term) },
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeSvgIcon(
                            paths = AppIconPaths.X,
                            color = appearance.mobileSoft,
                            iconSize = 13.dp,
                            strokeWidth = 2f,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

internal fun filterHomeCharacters(
    characters: List<CharacterSlot>,
    query: String,
): List<CharacterSlot> {
    val key = query.trim()
    if (key.isBlank()) return emptyList()
    return characters.filter { character ->
        listOf(
            character.name,
            character.persona.assistantName,
            character.group,
        ).any { it.contains(key, ignoreCase = true) }
    }
}

internal fun filterHomeModels(
    providers: List<ModelProviderMeta>,
    configs: List<ModelConfig>,
    query: String,
): List<ModelProviderMeta> {
    val key = query.trim()
    if (key.isBlank()) return emptyList()
    return providers.filter { provider ->
        val providerTextMatches = listOf(provider.id, provider.label, provider.badge, provider.summary)
            .any { it.contains(key, ignoreCase = true) }
        providerTextMatches || configs
            .filter { normalizeProviderId(it.provider) == provider.id }
            .any { config ->
                listOf(config.name, config.model, config.baseUrl)
                    .any { it.contains(key, ignoreCase = true) }
            }
    }
}

private fun firstModelConfigForProvider(
    configs: List<ModelConfig>,
    providerId: String,
): ModelConfig? = configs.firstOrNull { normalizeProviderId(it.provider) == providerId }

private const val FocusSettleMillis = 100L
private const val SearchEnterMillis = 320
private const val SearchExitMillis = 240
private val SearchGutter = 16.dp
