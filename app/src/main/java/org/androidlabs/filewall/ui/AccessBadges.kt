package org.androidlabs.filewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.androidlabs.filewall.R
import org.androidlabs.filewall.database.RuleItem
import org.androidlabs.filewall.ui.theme.LocalCustomColorsPalette

@Composable
fun AccessBadges(
    rule: RuleItem
) {
    val noAccess = rule.noAccess

    @Composable
    fun sharedInfo(hasAccess: Boolean) {
        val color =
            if (hasAccess) LocalCustomColorsPalette.current.green else LocalCustomColorsPalette.current.red
        if (rule.subfoldersAccess) {
            Badge(title = stringResource(id = R.string.subfolders), color = color)
        }

        if (rule.isListOfFilesAllowed) {
            Badge(title = stringResource(id = R.string.list_of_files), color = color)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (noAccess) {
            Badge(
                title = stringResource(id = R.string.no_access),
                color = LocalCustomColorsPalette.current.red
            )
        } else {
            if (rule.readAccess) {
                Badge(
                    title = stringResource(id = R.string.read),
                    color = LocalCustomColorsPalette.current.green
                )
            }

            if (rule.writeAccess) {
                Badge(
                    title = stringResource(id = R.string.write),
                    color = LocalCustomColorsPalette.current.green
                )
            }
        }

        sharedInfo(hasAccess = !noAccess)
    }
}