package org.androidlabs.filewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.filewall.R

@Composable
fun PermissionsRow(
    isBlocked: Boolean,
    isReadAllowed: Boolean,
    isWriteAllowed: Boolean,
    isListOfFilesAllowed: Boolean,
    onBlockChanged: (Boolean) -> Unit,
    onReadChanged: (Boolean) -> Unit,
    onWriteChanged: (Boolean) -> Unit,
    onListOfFilesChanged: (Boolean) -> Unit
) {
    Column {

        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp),) {

            CheckboxText(
                text = stringResource(id = R.string.read),
                checked = isReadAllowed,
                onCheckedChange = onReadChanged,
                enabled = !isBlocked,
            )

            CheckboxText(
                text = stringResource(id = R.string.write),
                checked = isWriteAllowed,
                onCheckedChange = onWriteChanged,
                enabled = !isBlocked,
            )

            CheckboxText(
                text = stringResource(id = R.string.list_of_files),
                checked = isListOfFilesAllowed,
                onCheckedChange = onListOfFilesChanged,
                enabled = !isBlocked,
            )
        }

        CheckboxText(
            text = stringResource(id = R.string.block_long),
            checked = isBlocked,
            onCheckedChange = onBlockChanged
        )
    }
}