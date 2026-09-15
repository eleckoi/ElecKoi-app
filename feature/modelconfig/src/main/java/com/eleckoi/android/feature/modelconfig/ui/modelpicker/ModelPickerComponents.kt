package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun SheetGroupCard(appearance: AppearanceTheme, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(appearance.mobileBg),
        content = { content() },
    )
}

@Composable
fun SheetGroupDivider(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(0.5.dp)
            .background(appearance.mobileLine),
    )
}

// Its own page rather than an inline expansion: a config can list dozens of models, and dozens of
// rows unfolding inside a card push everything else off the sheet.

enum class ParamsSaveState { Idle, Saving, Saved, Failed }

enum class ImageModelParamsMode { OnDemand, AutomaticIllustration }

@Composable
fun ParamsGroupLabel(text: String, appearance: AppearanceTheme) {
    Text(
        text,
        color = appearance.mobileMuted,
        fontSize = 11.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 7.dp),
    )
}

// The note says the one thing that is currently true: what is wrong, or that the write happened,
// or how the placeholders should be read.
fun String.onlyDigits(): String = filter(Char::isDigit).take(7)
