package org.androidlabs.filewall.accesslogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.ui.asDisplayPath
import org.androidlabs.filewall.R
import org.androidlabs.filewall.database.AccessLogType
import org.androidlabs.filewall.provider.AccessReason
import org.androidlabs.filewall.ui.AccessBadges
import org.androidlabs.filewall.ui.Badge
import org.androidlabs.filewall.ui.InfoView
import org.androidlabs.filewall.ui.theme.LocalCustomColorsPalette
import java.text.SimpleDateFormat

@Composable
fun AccessLogRow(
    modifier: Modifier = Modifier,
    log: ListAccessLogItem,
    timeFormatter: SimpleDateFormat,
    isSelected: Boolean,
    onDelete: (ruleId: Long) -> Unit
) {
    val rule = log.log.rule
    val ruleId = log.log.ruleId
    val isRequest = log.log.type == AccessLogType.REQUEST

    val context = LocalContext.current
    val accessResultLabel = stringResource(id = R.string.access_result)
    val pathLabel = stringResource(
        id = if (log.log.isDirectory) R.string.folder_access else R.string.file_access
    )
    val accessAtLabel = stringResource(id = R.string.access_at)
    val resolutionLabel = stringResource(id = R.string.access_resolution)
    val durationLabel = stringResource(id = R.string.access_duration)

    val borderShape = RoundedCornerShape(4.dp)
    val customColorsPalette = LocalCustomColorsPalette.current

    val (reasonColor, reasonTitle) = when (log.log.reason) {
        AccessReason.READ -> customColorsPalette.readColor to "Read"
        AccessReason.CREATE -> customColorsPalette.createColor to "Create"
        AccessReason.DELETE -> customColorsPalette.deleteColor to "Delete"
        AccessReason.RENAME -> customColorsPalette.renameColor to "Rename"
        AccessReason.COPY -> customColorsPalette.copyColor to "Copy"
        AccessReason.MOVE -> customColorsPalette.moveColor to "Move"
        AccessReason.WRITE -> customColorsPalette.writeColor to "Write"
        AccessReason.READWRITE -> customColorsPalette.readWriteColor to "Read/Write"
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
            alpha = 0.2f
        ),
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, borderShape)
            .border(1.dp, borderColor, borderShape)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // One focus stop for the whole entry. Without this the row reached TalkBack as
            // a run of separate TextViews - "Files", "Read", "Access result", each chip on
            // its own, "Folder", "./", "Access at", the timestamp - which is the screen a
            // user goes to when a rule is not working. The delete button below stays its
            // own stop, since it is a separate action.
            // An empty semantics block groups the nodes but leaves them separate for
            // TalkBack, so the phrase is spelled out here instead.
            val spoken = remember(log, reasonTitle, isRequest) {
                val badges = log.accessResultText(context)
                listOfNotNull(
                    log.appName,
                    if (isRequest) "$reasonTitle request" else reasonTitle,
                    "$accessResultLabel $badges".takeIf { !isRequest && badges.isNotEmpty() },
                    "$pathLabel ${log.getPath()}",
                    "$accessAtLabel ${timeFormatter.format(log.log.createdAt)}",
                    rule?.let { "$resolutionLabel ${it.accessResolution.title}" },
                    rule?.let { "$durationLabel ${it.accessDuration.title}" }
                ).joinToString(", ")
            }

            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = spoken
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = rememberDrawablePainter(log.appIcon),
                    contentDescription = log.appName,
                    modifier = Modifier.size(48.dp)
                )

                Column {
                    Text(
                        text = log.appName,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isRequest) {
                            Badge(
                                title = "$reasonTitle ${stringResource(R.string.request)}",
                                color = reasonColor
                            )
                        } else {
                            Badge(title = reasonTitle, color = reasonColor)
                        }
                    }
                }
            }

            if (!isRequest) {
                Column {
                    Text(stringResource(id = R.string.access_result))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (rule != null) {
                            AccessBadges(rule)
                        } else if (log.log.type == AccessLogType.DENIED) {
                            Badge(
                                title = stringResource(id = R.string.denied),
                                color = LocalCustomColorsPalette.current.red
                            )
                        } else if (log.log.type == AccessLogType.PROTECTION_DISABLED) {
                            Badge(
                                title = stringResource(id = R.string.allowed_protection_disabled),
                                color = LocalCustomColorsPalette.current.warningColor
                            )
                        }

                        // The entry outlives the rule it names, so say that rather than
                        // leaving the result blank as though nothing had been decided.
                        if (rule?.deletedAt != null || (rule == null && (ruleId ?: 0L) > 0L)) {
                            Badge(
                                title = stringResource(id = R.string.rule_since_deleted),
                                color = LocalCustomColorsPalette.current.warningColor
                            )
                        }
                    }
                }
            }

            InfoView(
                title = stringResource(id = if (log.log.isDirectory) R.string.folder_access else R.string.file_access),
                value = log.getPath().asDisplayPath()
            )

            InfoView(
                title = stringResource(id = R.string.access_at),
                value = timeFormatter.format(log.log.createdAt)
            )

            rule?.let {
                InfoView(
                    title = stringResource(id = R.string.access_resolution),
                    value = it.accessResolution.title
                )

                InfoView(
                    title = stringResource(id = R.string.access_duration),
                    value = rule.accessDuration.title
                )
            }
            }

            rule?.let {
                if (ruleId != null && it.deletedAt == null) {
                    Button(
                        onClick = { onDelete(ruleId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(2.dp, customColorsPalette.deleteColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_associated_rule),
                            color = customColorsPalette.deleteColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
