package org.androidlabs.filewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.filewall.R
import org.androidlabs.filewall.provider.AccessResolution

@Composable
fun FolderAccessOptions(
    isBlocked: Boolean,
    accessResolution: AccessResolution?,
    onAccessResolutionChanged: (AccessResolution) -> Unit,
    modifier: Modifier = Modifier,
    isCentered: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CheckboxText(
            text = stringResource(id = R.string.access_subfolders),
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (isCentered == true) Arrangement.Center else Arrangement.Start,
            checked = accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS,
            enabled = true,//!isBlocked,
            onCheckedChange = { isChecked ->
                onAccessResolutionChanged(
                    if (isChecked)
                        AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
                    else
                        AccessResolution.ENTIRE_FOLDER
                )
            }
        )
    }
}