package org.androidlabs.filewall.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.androidlabs.filewall.R


@Composable
fun DurationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    title: String,
    entries: List<String>,
    currentIndex: Int,
    onConfirm: (selectedIndex: Int) -> Unit
) {
    if (showDialog) {
        var selectedIndex by remember { mutableStateOf(currentIndex) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title,style = MaterialTheme.typography.titleLarge) },
            text = {
                SpinnerComponent(
                    entries = entries,
                    value = entries[selectedIndex],
                    onChange = { newValue ->
                        selectedIndex = entries.indexOf(newValue)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton( onClick = {
                    onConfirm(selectedIndex)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}