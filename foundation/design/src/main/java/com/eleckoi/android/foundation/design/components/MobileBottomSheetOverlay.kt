package com.eleckoi.android.foundation.design.components

import android.view.WindowManager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.overlayScrim

/** Shared root-level sheet motion so feature panels do not invent their own transitions. */
@Composable
fun MobileBottomSheetOverlay(
    visible: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    sheetModifier: Modifier = Modifier,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.overlayScrim())
                    .noRippleClickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { height -> height },
            exit = slideOutVertically(tween(180)) { height -> height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sheetModifier)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(appearance.mobileSurface)
                    .navigationBarsPadding()
                    .noRippleClickable {},
            ) {
                if (showHandle) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Spacer(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(appearance.mobileSoft.copy(alpha = 0.78f)),
                        )
                    }
                }
                content()
            }
        }
    }
}

/** Window-level host for sheets opened below nested screen content such as tabs or lists. */
@Composable
fun MobileBottomSheetDialog(
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    sheetModifier: Modifier = Modifier,
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        CompositionLocalProvider(LocalContentColor provides appearance.mobileText) {
            MobileBottomSheetOverlay(
                visible = visible,
                appearance = appearance,
                onDismiss = onDismiss,
                sheetModifier = sheetModifier,
                showHandle = showHandle,
                content = content,
            )
        }
    }
}
