package org.androidlabs.filewall.rules.edit

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SharedViewModel
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.FilewallProvider
import org.androidlabs.filewall.rules.ListRuleItem
import org.androidlabs.filewall.ui.FolderAccessOptions
import org.androidlabs.filewall.ui.PermissionsRow
import org.androidlabs.filewall.ui.SpinnerComponent
import org.androidlabs.filewall.ui.theme.LocalCustomColorsPalette
import org.androidlabs.filewall.ui.theme.FilewallTheme
import java.lang.System

@Composable
fun RuleEditDialog(
    rule: ListRuleItem,
    sharedViewModel: SharedViewModel,
    onSave: (ListRuleItem) -> Unit,
    onDismiss: () -> Unit
) {
    var accessDuration by remember { mutableStateOf<AccessDuration>(rule.rule.accessDuration) }
    var isReadAllowed by remember { mutableStateOf(rule.rule.readAccess) }
    var isWriteAllowed by remember { mutableStateOf(rule.rule.writeAccess) }
    var isListOfFilesAllowed by remember { mutableStateOf(rule.rule.isListOfFilesAllowed) }
    var accessResolution by remember { mutableStateOf(rule.rule.accessResolution) }

    var isBlocked by remember {
        mutableStateOf(
            !rule.rule.readAccess &&
                    !rule.rule.writeAccess &&
                    !rule.rule.isListOfFilesAllowed
        )
    }

    val isSaveable by remember(isBlocked, isReadAllowed, isWriteAllowed, isListOfFilesAllowed) {
        mutableStateOf(isBlocked || isReadAllowed || isWriteAllowed || isListOfFilesAllowed)
    }

    Dialog(
        onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FilewallTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),

                        ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(id = R.string.edit_rule),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = rememberDrawablePainter(rule.appIcon),
                                contentDescription = rule.appName,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text(
                                    text = rule.appName,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = rule.rule.pkg,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.access_duration),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 0.dp),
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SpinnerComponent(
                            entries = AccessDuration.entries.map { it.title },
                            value = accessDuration.title,
                            onChange = {
                                accessDuration = AccessDuration.fromTitle(it)!!
                                sharedViewModel.setDefaultDuration(accessDuration.ordinal)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.padding(horizontal = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.permissions),
                                modifier = Modifier.padding(horizontal = 0.dp),
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                PermissionsRow(
                                    isBlocked = isBlocked,
                                    isReadAllowed = isReadAllowed,
                                    isWriteAllowed = isWriteAllowed,
                                    isListOfFilesAllowed = isListOfFilesAllowed,
                                    onBlockChanged = { isBlocked = it
                                        if (isBlocked) {
                                            isReadAllowed = false
                                            isWriteAllowed = false
                                            isListOfFilesAllowed = false
                                            //accessResolution = AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
                                        }
                                    },
                                    onReadChanged = { isReadAllowed = it },
                                    onWriteChanged = { isWriteAllowed = it },
                                    onListOfFilesChanged = { isListOfFilesAllowed = it }
                                )
                            }
                        }

                        if (accessResolution == AccessResolution.ENTIRE_FOLDER || accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS) {
                            //if (!isBlocked)
                                FolderAccessOptions(
                                    isBlocked = isBlocked,
                                    accessResolution = accessResolution,
                                    onAccessResolutionChanged = { accessResolution = it },
                                    isCentered = true
                                )
                        }

                        if (accessResolution == AccessResolution.ENTIRE_FOLDER) {
                            Text(stringResource(id = R.string.entire_folder_description))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(id = R.string.cancel))
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalCustomColorsPalette.current.greenDark,
                                    contentColor = Color.White
                                ),
                                enabled = isSaveable,
                                onClick = {
                                    val newAccessType =
                                        AccessType.fromPermissions(isReadAllowed, isWriteAllowed)
                                    val newRule = rule.rule.copy(
                                        accessDuration = accessDuration,
                                        isListOfFilesAllowed = isListOfFilesAllowed,
                                        accessType = newAccessType,
                                        createdAt = System.currentTimeMillis(),
                                        accessResolution = accessResolution,
                                        //isDirectory = applyToParentFolder
                                    )
                                    val newRuleItem = rule.copy(
                                        rule = newRule
                                    )
                                    onSave(newRuleItem)
                                }) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }
        }
    }
}