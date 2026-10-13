package org.androidlabs.filewall.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.accesslogs.AccessLogsActivity
import org.androidlabs.filewall.accesslogs.AccessLogsViewModel
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.docs.DocsViewerActivity
import org.androidlabs.filewall.logger.FileLogger
import org.androidlabs.filewall.logger.LogsActivity
import org.androidlabs.filewall.settings.auth.AuthenticationActivity
import org.androidlabs.filewall.settings.legal.LegalActivity
import org.androidlabs.filewall.ui.DurationDialog

class SettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var dbHelper: Database
    private var version: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()

        dbHelper = Database(context)

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        version = "${packageInfo.versionName} (${packageInfo.longVersionCode})"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsScreen(
                    viewModel = viewModel,
                    version = version,
                    onAuthentication = ::onAuthentication,
                    onToggleDebugMode = ::onToggleDebugMode,
                    onLogs = ::onLogs,
                    onToggleAccessLog = ::onToggleAccessLog,
                    onAccessLogs = ::onAccessLogs,
                    onLegal = ::onLegal,
                    onFAQ = ::onFAQ,
                    onToggleSilenceMode = :: onToggleSilenceMode,
                    onToggleDisableProtection = :: onToggleDisableProtection
                )
            }
        }
    }

    private fun onFAQ() {
        val intent = Intent(requireContext(), DocsViewerActivity::class.java).apply {
            putExtra("name", getString(R.string.faq))
            putExtra("filename", "FAQ")
        }
        startActivity(intent)
    }

    private fun onAuthentication() {
        val intent = Intent(requireContext(), AuthenticationActivity::class.java)
        startActivity(intent)
    }

    private fun onToggleDisableProtection(value: Boolean, duration: Int) {
        viewModel.onToggleDisableProtection(value,duration)
    }

    private fun onToggleDebugMode(value: Boolean) {
        viewModel.onToggleDebugMode(value)
        if (!value) {
            FileLogger(requireContext()).deleteLogs()
        }
    }

    private fun onToggleSilenceMode(value: Boolean) {
        viewModel.onToggleSilenceMode(value)
    }

    private fun onLogs() {
        val intent = Intent(requireContext(), LogsActivity::class.java)
        startActivity(intent)
    }

    private fun onToggleAccessLog(value: Boolean) {
        viewModel.onToggleAccessLog(value)
        if (!value) {
            dbHelper.clearAccessLogs()
            AccessLogsViewModel.deleteExportedLogs(requireContext())
        }
    }


    private fun onAccessLogs() {
        val intent = Intent(requireContext(), AccessLogsActivity::class.java)
        startActivity(intent)
    }

    private fun onLegal() {
        val intent = Intent(requireContext(), LegalActivity::class.java)
        startActivity(intent)
    }

}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    version: String,
    onAuthentication: () -> Unit,
    onToggleDebugMode: (Boolean) -> Unit,
    onLogs: () -> Unit,
    onToggleAccessLog: (Boolean) -> Unit,
    onAccessLogs: () -> Unit,
    onLegal: () -> Unit,
    onFAQ: () -> Unit,
    onToggleSilenceMode: (Boolean) -> Unit,
    onToggleDisableProtection: (Boolean, Int) -> Unit,
) {
    var showDisableDebugModeDialog by remember { mutableStateOf(false) }
    var showDisableAccessLogDialog by remember { mutableStateOf(false) }
    var showDisableProtectionDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var selectedDurationIndex by remember { mutableStateOf(2) }

    val context = LocalContext.current
    val app = context.applicationContext as SFApplication
    val isProtectionDisabled by app.isProtectionDisabled.collectAsState()
    val remainingTime by app.remainingTime.collectAsState()

    val remainingText = remember(remainingTime) {
        if (remainingTime > 0) {
            val totalSeconds = remainingTime / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        } else null
    }

    val isPinEnabled by viewModel.isPinEnabled.collectAsState(initial = false)
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState(initial = false)
    val isDebugModeEnabled by viewModel.isDebugModeEnabled.collectAsState(initial = false)
    val isAccessLogEnabled by viewModel.isAccessLogEnabled.collectAsState(initial = false)
    val isSilenceModeEnabled by viewModel.isSilenceModeEnabled.collectAsState(initial = false)
    //val isProtectionDisabled by viewModel.isProtectionDisabled.collectAsState(initial = false)
    val scrollState = rememberScrollState()

    val isAuthEnabled = isPinEnabled || isBiometricEnabled

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        SettingsRow(
            title = stringResource(id = R.string.authentication),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_fingerprint_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = if (isAuthEnabled) R.string.enabled else R.string.disabled),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            },
            onClick = onAuthentication
        )

        SettingsRow(
            title = stringResource(id = R.string.settings_silence_mode),
            subtitle = null,
            iconView = {
                Image(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Switch(
                    checked = isSilenceModeEnabled,
                    onCheckedChange = {
                        onToggleSilenceMode(it)
                    }
                )
            }
        )

        SettingsRow(
            title = stringResource(id = R.string.settings_disable_protection),
            subtitle = when {
                isProtectionDisabled && remainingText != null ->
                    stringResource(R.string.time_until_shutdown)+" "+remainingText
                else -> null
            },
            iconView = {
                Image(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Switch(
                    checked = isProtectionDisabled,
                    onCheckedChange = {
                        if (it) {
                            showDisableProtectionDialog = true
                        } else {
                            onToggleDisableProtection(false,0)
                        }
                    }
                )
            }
        )

        SettingsRow(
            title = stringResource(id = R.string.settings_access_logs),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_history_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Switch(
                    checked = isAccessLogEnabled,
                    onCheckedChange = {
                        if (it) {
                            onToggleAccessLog(true)
                        } else {
                            showDisableAccessLogDialog = true
                        }
                    }
                )
            }
        )

        if (isAccessLogEnabled) {
            SettingsRow(
                title = stringResource(id = R.string.settings_view_access_logs),
                subtitle = stringResource(id = R.string.settings_view_access_logs_description),
                iconView = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_feed_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                },
                rightView = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                onClick = onAccessLogs
            )
        }

        SettingsRow(
            title = stringResource(id = R.string.settings_debug_mode),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_bug_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Switch(
                    checked = isDebugModeEnabled,
                    onCheckedChange = {
                        if (it) {
                            onToggleDebugMode(true)
                        } else {
                            showDisableDebugModeDialog = true
                        }
                    }
                )
            }
        )

        if (isDebugModeEnabled) {
            SettingsRow(
                title = stringResource(id = R.string.settings_view_logs),
                subtitle = stringResource(id = R.string.settings_view_logs_description),
                iconView = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_feed_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                },
                rightView = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                onClick = onLogs
            )
        }

        SettingsRow(
            title = stringResource(id = R.string.legal),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onLegal
        )

        SettingsRow(
            title = stringResource(id = R.string.faq),
            subtitle = null,
            iconView = {
                Image(
                    painter = painterResource(id = R.drawable.ic_article_24),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            onClick = onFAQ
        )

        SettingsRow(
            title = stringResource(id = R.string.version),
            subtitle = null,
            iconView = {
                Image(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            },
            rightView = { Text(version) }
        )
    }

    if (showDisableDebugModeDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDebugModeDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.are_you_sure),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(text = stringResource(R.string.disable_debug_mode_dialog))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onToggleDebugMode(false)
                        showDisableDebugModeDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDebugModeDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDisableAccessLogDialog) {
        AlertDialog(
            onDismissRequest = { showDisableAccessLogDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.are_you_sure),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(text = stringResource(R.string.disable_access_log_dialog))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onToggleAccessLog(false)
                        showDisableAccessLogDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableAccessLogDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDisableProtectionDialog) {
        AlertDialog(
            onDismissRequest = { showDisableProtectionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.warning),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(text = stringResource(R.string.disable_protection))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        //onToggleDisableProtection(true)
                        showDisableProtectionDialog = false
                        showDurationDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableProtectionDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    DurationDialog(
        showDialog = showDurationDialog,
        onDismiss = { showDurationDialog = false },
        title = stringResource(R.string.select_duration),
        entries = listOf("1 minute", "5 minutes", "10 minutes", "30 minutes", "60 minutes"),
        currentIndex = selectedDurationIndex,
        onConfirm = { value ->
            selectedDurationIndex = value
            onToggleDisableProtection(true,selectedDurationIndex)
        }
    )
}
