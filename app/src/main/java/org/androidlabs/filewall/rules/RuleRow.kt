package org.androidlabs.filewall.rules

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.ui.asDisplayPath
import org.androidlabs.filewall.R
import org.androidlabs.filewall.ui.AccessBadges
import org.androidlabs.filewall.ui.InfoView
import java.text.SimpleDateFormat

@Composable
fun RuleRow(
    rule: ListRuleItem,
    timeFormatter: SimpleDateFormat,
    isSelected: Boolean,
    onSelect: (ListRuleItem) -> Unit,
    onClick: (ListRuleItem) -> Unit,
    @SuppressLint("ModifierParameter") reorderModifier: Modifier
) {
    val haptic = LocalHapticFeedback.current
    val expiration = rule.rule.expirationDate()
    val created = rule.rule.createdAt

    SelectableItem(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        isSelected = isSelected,
        content = {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        //.padding(vertical = 8.dp, horizontal = 16.dp),
                        .padding(start = 16.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = rememberDrawablePainter(rule.appIcon),
                            contentDescription = rule.appName,
                            modifier = Modifier.size(48.dp)
                        )

                        Column {
                            Text(
                                text = rule.appName,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = rule.rule.pkg,
                                fontWeight = FontWeight.Normal
                            )

                            AccessBadges(rule.rule)
                        }
                    }

                    InfoView(title = "Type", value = rule.rule.accessResolution.title)

                    InfoView(title = "Path", value = rule.getPath().asDisplayPath())

                    InfoView(title = "Created", value = timeFormatter.format(created))

                    if (expiration != null) {
                        InfoView(title = "Expiration", value = timeFormatter.format(expiration))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { onClick(rule) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_rule),
                        )
                    }

                    IconButton(modifier = reorderModifier/*.fillMaxHeight()*/, onClick = {}) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drag_handle_24),
                            contentDescription = stringResource(R.string.reorder),
                        )
                    }

                    IconButton(onClick = { onSelect(rule) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_rule),
                        )
                    }
                }
            }
        },
        onClick = { onClick(rule) },
        onLongPress = {
            onSelect(rule)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )
}