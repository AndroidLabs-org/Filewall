package org.androidlabs.filewall.accesslogs

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.ConfirmDeleteDialog
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.ui.BackNavigable
import org.androidlabs.filewall.ui.DeleteRuleAlert
import org.androidlabs.filewall.ui.SecurityFragmentActivity
import java.text.SimpleDateFormat
import java.util.Locale

class AccessLogsActivity : SecurityFragmentActivity() {
    private val viewModel: AccessLogsViewModel by viewModels()

    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // The clear action lives next to Share so the log can be emptied from the
            // screen that shows it; without it "No access logs to display" was
            // reachable only on a fresh install.
            var showClearDialog by remember { mutableStateOf(false) }

            BackNavigable(
                titleResId = R.string.settings_access_logs,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding, isScreenHidden ->
                    RulesScreen(
                        viewModel = viewModel,
                        modifier = Modifier
                            .padding(innerPadding),
                        onDeleteRuleById = ::onDeleteRuleById
                    )

                    if (showClearDialog) {
                        ConfirmDeleteDialog(
                            title = stringResource(R.string.confirm_delete),
                            message = stringResource(R.string.confirm_delete_access_logs),
                            onConfirm = {
                                viewModel.clearLogs()
                                showClearDialog = false
                            },
                            onDismiss = { showClearDialog = false }
                        )
                    }
                },
                rightView = {
                    val logs by viewModel.logs.observeAsState(initial = listOf())

                    Row {
                        TextButton(onClick = ::shareLogs) {
                            Text(text = stringResource(id = R.string.share_logs))
                        }
                        if (logs.isNotEmpty()) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.clear_logs),
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    private fun onDeleteRuleById(id: Long) {
        viewModel.deleteRuleById(id)
    }

    private fun shareLogs() {
        val logs = viewModel.logs.value
        if (logs == null || logs.isEmpty()) {
            return
        }
        viewModel.shareLogs { file->
            file?.let {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.file-provider",
                    it
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    // a .csv shared as text/plain gets saved as access_logs.csv.txt and
                    // does not open in a spreadsheet
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(
                    Intent.createChooser(shareIntent, getString(R.string.share_logs))
                )
            } ?: run {
                Toast.makeText(this, "Failed to generate file", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun RulesScreen(
    viewModel: AccessLogsViewModel,
    modifier: Modifier,
    onDeleteRuleById: (id: Long) -> Unit
) {
    val isSharing by viewModel.isSharing.collectAsState()
    var deleteIdDialog by remember { mutableLongStateOf(-1) }
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
    val list = viewModel.logs.observeAsState(initial = listOf())
    var query by rememberSaveable { mutableStateOf("") }

    // A log of any size is a wall of near-identical rows; this is the only way to find
    // the app or the path you came here about.
    val visible = remember(list.value, query) {
        if (query.isBlank()) {
            list.value
        } else {
            list.value.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.log.pkg.contains(query, ignoreCase = true) ||
                    it.getPath().contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.refreshLogs()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (list.value.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.no_access_logs),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                label = { Text(stringResource(id = R.string.filter_logs)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.logs_shown_of_total,
                            visible.size,
                            list.value.size
                        )
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_filter)
                            )
                        }
                    }
                }
            )

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.no_logs_match))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = visible.size,
                        key = { index -> visible[index].log.id ?: index.toLong() }
                    ) { index ->
                        val log = visible[index]

                        AccessLogRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            log = log,
                            timeFormatter = formatter,
                            isSelected = deleteIdDialog == log.log.ruleId,
                            onDelete = {
                                deleteIdDialog = it
                            }
                        )
                    }
                }
            }
        }
    }

    if (deleteIdDialog > 0) {
        DeleteRuleAlert(
            onCancel = { deleteIdDialog = -1 },
            onDelete = {
                onDeleteRuleById(deleteIdDialog)
                deleteIdDialog = -1
            }
        )
    }

    if (isSharing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}