package com.eleckoi.android.feature.characters.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.foundation.design.AppearanceTheme

/** The full grouped, reorderable character directory hosted directly in the drawer. */
@Composable
fun CharacterDrawerList(
    characters: CharactersPayload?,
    appearance: AppearanceTheme,
    useCoverArtwork: Boolean,
    onToggleAllCharactersExpanded: () -> Unit,
    onToggleCharacterGroupExpanded: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        CharacterGroupedList(
            characters = characters?.items.orEmpty(),
            groups = buildCharacterGroups(characters),
            payload = characters,
            keyword = "",
            listAllExpanded = characters?.listAllExpanded ?: true,
            expandedGroupNames = characters?.expandedGroupNames.orEmpty().toSet(),
            appearance = appearance,
            useCoverArtwork = useCoverArtwork,
            onToggleGroup = { group, _ ->
                if (group == ALL_CHARACTERS) {
                    onToggleAllCharactersExpanded()
                } else {
                    onToggleCharacterGroupExpanded(group)
                }
            },
            onOpenCharacter = onOpenCharacter,
            onSaveCharacters = onSaveCharacters,
        )
    }
}
