package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun RootSearchPage(
    query: String,
    placeholder: String,
    accentColor: Color,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable (AppearanceTheme) -> Unit,
) {
    val appearance = remember(accentColor) {
        AppearanceTheme(
            mobileBlue = accentColor,
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileSurface)
            .navigationBarsPadding(),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietBackButton(
                    color = appearance.mobileText,
                    onClick = {
                        keyboardController?.hide()
                        onBack()
                    },
                    modifier = Modifier.size(48.dp),
                    iconSize = 23.dp,
                )
                AppSearchField(
                    keyword = query,
                    placeholder = placeholder,
                    appearance = appearance,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    fontSize = 16.sp,
                    iconSize = 20.dp,
                    inputModifier = Modifier.focusRequester(focusRequester),
                    onSearch = { keyboardController?.hide() },
                    onKeywordChange = onQueryChange,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                content(appearance)
            }
        }
    }
}
