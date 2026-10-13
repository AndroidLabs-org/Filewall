package org.androidlabs.filewall.accessrequests

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.ui.asDisplayPath
import org.androidlabs.filewall.R

@Composable
fun AccessRequestRow(
    request: ListAccessRequestItem,
    isSelected: Boolean,
    onSelect: (ListAccessRequestItem) -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current


    SelectableItem(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        isSelected = isSelected,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = rememberDrawablePainter(request.appIcon),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )

                        Column {
                            Text(
                                text = request.appName, //+ " ID:"+request.request.id.toString(),
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = request.request.callingPackage,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            Text(
                                text = request.request.action,
                            )

                            Text(
                                text = if (request.isBulk)
                                    pluralStringResource(
                                        R.plurals.request_items_count,
                                        request.count,
                                        request.count
                                    )
                                else
                                    request.request.targetName(LocalContext.current)
                                        .asDisplayPath(),
                                textAlign = TextAlign.Start,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            request.request.parentFolder()?.let { parent ->
                                Text(
                                    text = stringResource(id = R.string.in_folder),
                                )

                                Text(
                                    text = parent.asDisplayPath(),
                                    textAlign = TextAlign.Start,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        onClick = { onClick() },
        onLongPress = {
            onSelect(request)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )
}