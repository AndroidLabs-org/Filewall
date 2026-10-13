package org.androidlabs.filewall.rules.add

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.ui.asDisplayPath
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.SharedViewModel
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.database.RuleItem
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.ui.BackNavigable
import org.androidlabs.filewall.ui.FolderAccessOptions
import org.androidlabs.filewall.ui.PermissionsRow
import org.androidlabs.filewall.ui.SpinnerComponent
import kotlin.getValue
import org.androidlabs.filewall.ui.secureWindow

class AddRuleActivity : FragmentActivity() {
    private lateinit var dbHelper: Database

    val sharedViewModel: SharedViewModel by viewModels()
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow()

        dbHelper = Database(this)
        setContent {
            BackNavigable(
                titleResId = R.string.add_rule,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding, isScreenHidden ->
                    AddRuleContent(
                        modifier = Modifier
                            .padding(innerPadding),
                        sharedViewModel = sharedViewModel,
                        onCreateRule = ::onCreateRule
                    )
                }
            )
        }
    }

    private fun onCreateRule(rule: RuleItem): Boolean {
        sharedViewModel.saveRuleConfirmed(true)
        if (dbHelper.isRuleExists(rule)) {
            return false
        }
        // Same app, same path, same resolution: update that rule rather than adding a
        // second one that overlaps it.
        dbHelper.upsertRule(rule)
        finish()
        return true
    }
}

@Composable
private fun AddRuleContent(
    modifier: Modifier,
    sharedViewModel: SharedViewModel,
    onCreateRule: (rule: RuleItem) -> Boolean
) {
    val scrollState = rememberScrollState()

    var showAppSelector by remember { mutableStateOf(false) }
    var showPathSelector by remember { mutableStateOf(false) }

    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var fileItem by remember { mutableStateOf<FileItem?>(null) }

    var isReadAllowed by remember { mutableStateOf(false) }
    var isWriteAllowed by remember { mutableStateOf(false) }
    var isListOfFilesAllowed by remember { mutableStateOf(false) }
    var accessResolution by remember { mutableStateOf<AccessResolution>(AccessResolution.THIS_FILE) }

    val defaultDurationIndex by sharedViewModel.getDefaultDuration.collectAsState(initial = 2)
    var accessDuration by remember(defaultDurationIndex) {
        mutableStateOf(AccessDuration.entries[defaultDurationIndex])
    }
    //var applyToParentFolder by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }

    var showDuplicationError by remember { mutableStateOf(false) }

    val isCreatable by remember(selectedApp, fileItem, isBlocked, isReadAllowed, isWriteAllowed, isListOfFilesAllowed) {
        mutableStateOf(selectedApp != null && fileItem != null && (isBlocked || isReadAllowed || isWriteAllowed || isListOfFilesAllowed)
        )
    }

    fun onCreate() {
        fileItem?.let { file ->
            selectedApp?.let { app ->
                val date = System.currentTimeMillis()
                val newRule = RuleItem(
                    null,
                    file.rulePath,
                    file.isDirectory,
                    app.packageName,
                    AccessType.fromPermissions(isReadAllowed, isWriteAllowed),
                    accessDuration,
                    accessResolution,
                    isListOfFilesAllowed,
                    date,
                    0L
                )
                val isCreated = onCreateRule(newRule)
                if (!isCreated) {
                    showDuplicationError = true
                }
            }
        }
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAppSelector = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(64.dp)
            ) {
                selectedApp?.let {
                    it.icon?.let { icon ->
                        Image(
                            painter = rememberDrawablePainter(icon),
                            contentDescription = it.name,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Column {
                        Text(
                            text = it.name,
                            fontWeight = FontWeight.Medium
                        )
                        Text(text = it.packageName)
                    }
                } ?: run {
                    Image(
                        painter = painterResource(id = R.drawable.ic_apps_24),
                        contentDescription = stringResource(R.string.application),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = stringResource(R.string.select_application),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isBlocked = false
                    showPathSelector = true
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(64.dp)
            ) {
                val isFile = fileItem != null && !fileItem!!.isDirectory
                Image(
                    painter = painterResource(id = if (isFile) R.drawable.ic_file_24 else R.drawable.ic_folder_24),
                    contentDescription = stringResource(if (isFile) R.string.file else R.string.folder),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )

                Spacer(modifier = Modifier.width(16.dp))

                fileItem?.let {
                    Column {
                        Text(
                            text = it.path.asDisplayPath(),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } ?: run {
                    Text(
                        text = stringResource(R.string.select_path),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }


        Text(
            text = stringResource(R.string.access_duration),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        SpinnerComponent(
            entries = AccessDuration.entries.map { it.title },
            value = accessDuration.title,
            onChange = {
                accessDuration = AccessDuration.fromTitle(it)!!
                val index = AccessDuration.entries.indexOf(accessDuration)
                sharedViewModel.setDefaultDuration(index)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                //.height(64.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

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
                                       //if (fileItem?.isDirectory == true)
                                       //    accessResolution = AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
                                   }
                                 },
                onReadChanged = { isReadAllowed = it },
                onWriteChanged = { isWriteAllowed = it },
                onListOfFilesChanged = { isListOfFilesAllowed = it }
            )

            fileItem?.let { file ->

                if (accessResolution == AccessResolution.ENTIRE_FOLDER || accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS) {
                    //if (!isBlocked)
                        FolderAccessOptions(
                            isBlocked = isBlocked,
                            accessResolution = accessResolution,
                            onAccessResolutionChanged = { accessResolution = it },
                        )
                }

                if (accessResolution == AccessResolution.ENTIRE_FOLDER) {
                    Text(
                        text = stringResource(id = R.string.entire_folder_description),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Button(
                onClick = ::onCreate,
                enabled = isCreatable,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_rule))
            }
        }
    }

    if (showAppSelector) {
        AppSelectorDialog(
            onDismiss = { showAppSelector = false },
            onAppSelected = {
                selectedApp = it
                showAppSelector = false
            }
        )
    }

    if (showPathSelector) {
        PathSelectorDialog(
            fileItem = fileItem ?: FileItem("/", "/", true),
            onDismiss = { showPathSelector = false },
            onItemSelected = {
                fileItem = it
                accessResolution =
                    if (it.isDirectory) AccessResolution.ENTIRE_FOLDER else AccessResolution.THIS_FILE
                showPathSelector = false
            }
        )
    }

    if (showDuplicationError) {
        AlertDialog(
            onDismissRequest = { showDuplicationError = false },
            title = { Text(stringResource(R.string.duplicated_rule)) },
            text = { Text(stringResource(R.string.duplicated_rule_text)) },
            confirmButton = {
                TextButton(onClick = { showDuplicationError = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
