package org.androidlabs.filewall.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.androidlabs.filewall.R
import org.androidlabs.filewall.ui.theme.LocalCustomColorsPalette

@Composable
fun DeleteRuleAlert(
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.delete_rule)) },
        text = {
            Text(stringResource(id = R.string.remove_rule))
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalCustomColorsPalette.current.redDark,
                    contentColor = Color.White
                ),
                onClick = onDelete
            ) {
                Text(stringResource(id = R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

