package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    appearance: AppearanceTheme,
    confirmText: String = "确认",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = appearance.mobileText) },
        text = { Text(message, color = appearance.mobileMuted) },
        confirmButton = {
            DialogConfirmButton(confirmText, appearance, destructive = destructive, onClick = onConfirm)
        },
        dismissButton = { DialogDismissButton(dismissText, appearance, onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

/**
 * Shared guard for leaving an editor with an in-memory draft.
 *
 * The three explicit verbs mirror the desktop editor contract: save and continue, discard and
 * continue, or stay on the current page. Persistence remains owned by the caller.
 */
@Composable
fun UnsavedChangesDialog(
    title: String = "保存修改？",
    message: String = "离开前是否保存当前内容的修改？",
    appearance: AppearanceTheme,
    saving: Boolean = false,
    saveText: String = "保存",
    discardText: String = "不保存",
    cancelText: String = "取消",
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        title = { Text(title, color = appearance.mobileText) },
        text = { Text(message, color = appearance.mobileMuted) },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSave,
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appearance.mobileBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (saving) "保存中" else saveText, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !saving,
                    border = BorderStroke(1.dp, appearance.mobileLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appearance.mobileText),
                ) {
                    Text(discardText)
                }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                    border = BorderStroke(1.dp, appearance.mobileLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appearance.mobileText),
                ) {
                    Text(cancelText)
                }
            }
        },
        dismissButton = {},
        containerColor = appearance.mobileSurface,
    )
}

/**
 * A dialog action in the app's own colours.
 *
 * `TextButton` reads its content colour from `MaterialTheme`, which this app never configures — it
 * themes everything through [AppearanceTheme] instead. So every AlertDialog in the app was drawing
 * its actions in stock M3 pink next to UI that is otherwise blue. These two close that gap; reach
 * for them instead of a bare `TextButton` inside a dialog.
 */
@Composable
fun DialogConfirmButton(
    text: String,
    appearance: AppearanceTheme,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) ElecKoiDanger else appearance.mobileBlue,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DialogDismissButton(
    text: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = appearance.mobileMuted),
    ) {
        Text(text)
    }
}
