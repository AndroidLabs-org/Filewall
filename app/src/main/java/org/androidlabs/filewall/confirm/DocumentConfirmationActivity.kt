package org.androidlabs.filewall.confirm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import org.androidlabs.filewall.ui.asDisplayPath
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.SharedViewModel
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.provider.AccessResolution
import org.androidlabs.filewall.provider.AccessType
import org.androidlabs.filewall.provider.FilewallProvider
import org.androidlabs.filewall.ui.DeleteRequestAlert
import org.androidlabs.filewall.ui.FolderAccessOptions
import org.androidlabs.filewall.ui.PermissionsRow
import org.androidlabs.filewall.ui.theme.LocalCustomColorsPalette
import org.androidlabs.filewall.ui.theme.FilewallTheme
import org.androidlabs.filewall.provider.AccessDuration
import org.androidlabs.filewall.ui.SpinnerComponent
import kotlin.getValue
import org.androidlabs.filewall.ui.secureWindow

class DocumentConfirmationActivity : ComponentActivity() {
    private val TAG = "DocumentConfirmationActivity"
    private lateinit var viewModel: DocumentConfirmationViewModel
    private val sharedViewModel: SharedViewModel by viewModels()
    private var isFinishedByUser: Boolean = false

    /** Set when the card that opened this dialog stood for a batch of requests. */
    private var groupIds: LongArray? = null

    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
    }

    /**
     * The activity is singleTask, so an instance left behind with Home is brought forward
     * for whichever card is opened next - still showing the request it was created for,
     * and the Decline confirmation if that was open. Start afresh for a different request.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = requestFrom(intent)
        val sameCard = ::viewModel.isInitialized &&
            incoming?.id == viewModel.request.id &&
            intent.getLongArrayExtra("group_ids").contentEquals(groupIds)
        if (sameCard) {
            return
        }
        // Like the X button: the request on screen stays pending, nothing is answered.
        isFinishedByUser = true
        finish()
        startActivity(intent)
    }

    private fun requestFrom(intent: Intent): RequestData? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("request", RequestData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("request")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, disabling the back button
            }
        }
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(19988)
        }
        catch (ex:Exception) {
            Log.e(TAG,"notification cancel error:"+ex.message)
        }
        onBackPressedDispatcher.addCallback(this, callback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
        }

        val request = requestFrom(intent)
        groupIds = intent.getLongArrayExtra("group_ids")
        val groupHasFolders = intent.getBooleanExtra("group_has_folders", false)
        val isEdit = intent.extras?.getBoolean("isedit") == true
        Log.d(TAG, "Open ${request.toString()}")
        Log.d(TAG, "Open for edit:"+isEdit.toString())

        request?.let {
            // Built through a factory so the selections survive a configuration change;
            // constructing it here handed every rotation a fresh, empty dialog.
            viewModel = ViewModelProvider(
                this,
                viewModelFactory {
                    initializer { DocumentConfirmationViewModel(it, applicationContext, groupHasFolders) }
                }
            )[DocumentConfirmationViewModel::class.java]
            setContent {
                FilewallTheme {
                    Alert(
                        viewModel,
                        sharedViewModel,
                        //onBlock = ::block,
                        onConfirm = ::handleConfirmation,
                        onDismiss = ::dismiss,
                        onClose = ::close,
                        requestCount = groupIds?.size ?: 1
                    )
                }
            }
        } ?: run {
            dismiss()
        }
    }

    private fun close() {
        isFinishedByUser = true
        finish()
    }

    private fun dismiss() {
        isFinishedByUser = true
        clearDecidedRequests()
        finish()
    }

    /** One decision answers the whole card, whether it stood for one request or ten thousand. */
    private fun clearDecidedRequests() {
        groupIds?.let {
            sharedViewModel.deleteRequestsByIds(it.toList())
            return
        }
        sharedViewModel.deleteRequestById(viewModel.request.id)
    }

    private fun handleConfirmation() {
        Log.d(TAG, "Confirm $viewModel")
        lifecycleScope.launch {
            sharedViewModel.saveRuleConfirmed(true)
            isFinishedByUser = true
            val data = ConfirmationData(
                viewModel.requestToConfirm(),
                viewModel.accessType.value!!,
                viewModel.accessDuration.value!!,
                viewModel.accessResolution.value!!,
                viewModel.isListOfFilesAllowed.value!!,
            )
            ConfirmationNotifier.notifyConfirmation(data)
            clearDecidedRequests()
            finish()
        }
    }

    private fun sendCancellation() {
        Log.d(TAG, "Cancel $viewModel")
        val data = ConfirmationData(
            viewModel.request,
            AccessType.DENY_ACCESS,
            AccessDuration.FOREVER,
            if (viewModel.request.isDirectory) AccessResolution.ENTIRE_FOLDER else AccessResolution.THIS_FILE,
            false
        )
        ConfirmationNotifier.notifyConfirmation(data)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFinishedByUser && !isChangingConfigurations) {
            Log.d(TAG, "onDestroy w/o user")
            sendCancellation()
        }
    }
}

@Composable
fun Alert(
    viewModel: DocumentConfirmationViewModel,
    sharedViewModel: SharedViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    requestCount: Int = 1
) {
    val isSaveable by viewModel.isSaveable.observeAsState(initial = false)
    val isBlocked by viewModel.isBlocked.observeAsState(initial = false)
    // Declining deletes the request for good, so it asks first, as the long-press delete
    // on the Requests tab does. Kept across a rotation so the question is not lost.
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AlertDialog(
            onDismissRequest = {},//onBlock,
            title = {
                Box(
                    Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.filewall_add_rule),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
            },
            text = {
                Column {
                    // In landscape the dialog used to clip below the permissions and there
                    // was no way to reach Allow or Dismiss at all. The body scrolls; the
                    // buttons stay outside it so they are always on screen.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DocumentConfirmationContent(
                            viewModel,
                            sharedViewModel = sharedViewModel
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalCustomColorsPalette.current.renameColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(id = R.string.decline))
                        }

                        Button(
                            onClick = onConfirm,
                            enabled = isSaveable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalCustomColorsPalette.current.greenDark,
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = if (isBlocked) stringResource(R.string.block_short) else stringResource(id = R.string.allow))

                        }
                    }
                }
            },
            dismissButton =  {},
            confirmButton =  {},

            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            modifier = Modifier
                .wrapContentHeight()
                .background(Color.Transparent)
        )
    }

    if (showDeleteConfirm) {
        DeleteRequestAlert(
            onDelete = {
                showDeleteConfirm = false
                onDismiss()
            },
            onCancel = { showDeleteConfirm = false },
            requestCount = requestCount
        )
    }
}

@Composable
fun DocumentConfirmationContent(viewModel: DocumentConfirmationViewModel, sharedViewModel: SharedViewModel) {
    val context = LocalContext.current

    val defaultDurationIndex by sharedViewModel.getDefaultDuration.collectAsState(initial = -1)
    //val accessDuration = AccessDuration.entries[defaultDurationIndex]
    val accessDuration by viewModel.accessDuration.observeAsState()
    val accessResolution by viewModel.accessResolution.observeAsState()
    val isReadAllowed by viewModel.isReadAllowed.observeAsState(initial = false)
    val isWriteAllowed by viewModel.isWriteAllowed.observeAsState(initial = false)
    val isListOfFilesAllowed by viewModel.isListOfFilesAllowed.observeAsState(initial = false)
    val isBlocked by viewModel.isBlocked.observeAsState(initial = false)
    val appName = viewModel.request.appName(context = context)
    LaunchedEffect(viewModel.request.mode, defaultDurationIndex) {
        if (!viewModel.isInitialized &&
            viewModel.request.mode.isNotEmpty() &&
            defaultDurationIndex >= 0
        ) {
            viewModel.applyRequestDefaults(AccessDuration.entries[defaultDurationIndex])
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            viewModel.request.appIcon(context)?.let {
                Image(
                    painter = rememberDrawablePainter(it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = appName,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = viewModel.request.action,
        )

        Text(
            text = viewModel.request.targetName(context).asDisplayPath(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        // The root has no parent folder to name.
        viewModel.request.parentFolder()?.let { parent ->
            Text(
                text = stringResource(id = R.string.in_folder),
            )

            Text(
                text = parent.asDisplayPath(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (/*!viewModel.request.isCreation || !viewModel.request.isDirectory*/true) {
            PermissionsRow(
                isBlocked = isBlocked,
                isReadAllowed = isReadAllowed,
                isWriteAllowed = isWriteAllowed,
                isListOfFilesAllowed = isListOfFilesAllowed,
                onBlockChanged = { viewModel.setBlocked(it) },
                onReadChanged = { viewModel.setReadAccess(it) },
                onWriteChanged = { viewModel.setWriteAccess(it) },
                onListOfFilesChanged = { viewModel.setListOfFilesAllowed(it) }
            )

            if (!viewModel.request.isDirectory) {
                val resolutionOptions = AccessResolution.getValues(viewModel.request.isDirectory)

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    resolutionOptions.map { it.title }.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = resolutionOptions.size
                            ),
                            onClick = {
                                viewModel.setAccessResolution(
                                    AccessResolution.fromTitle(
                                        label
                                    )!!
                                )
                            },
                            selected = label == accessResolution!!.title || (label == AccessResolution.ENTIRE_FOLDER.title && accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
            SpinnerComponent(
                entries = AccessDuration.entries.map { it.title },
                value = accessDuration!!.title,
                onChange = { title ->
                    val newDuration = AccessDuration.fromTitle(title)!!
                    val newIndex = newDuration.ordinal
                    viewModel.setAccessDuration(newDuration)
                    sharedViewModel.setDefaultDuration(newIndex)
                }
            )
            if (isBlocked) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(id = R.string.block_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(id = R.string.block_text),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(id = R.string.allow_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(id = R.string.allow_text),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )

                    // Write on a folder is also permission to erase it. deleteDocument
                    // calls deleteRecursively(), so everything underneath goes with it,
                    // including items that have no rule of their own.
                    if (isWriteAllowed && viewModel.request.isDirectory) {
                        Text(
                            stringResource(
                                if (accessResolution ==
                                    AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS
                                ) {
                                    R.string.write_deletes_tree_subfolders
                                } else {
                                    R.string.write_deletes_tree
                                }
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalCustomColorsPalette.current.warningColor
                        )
                    }
                }
            }
            if (accessResolution == AccessResolution.ENTIRE_FOLDER || accessResolution == AccessResolution.ENTIRE_FOLDER_INCLUDE_SUBFOLDERS) {
                //if (!isBlocked)
                    FolderAccessOptions(
                        isBlocked = isBlocked,
                        accessResolution = accessResolution,
                        onAccessResolutionChanged = { viewModel.setAccessResolution(it) },
                        isCentered = true
                    )
            }

            if (accessResolution == AccessResolution.ENTIRE_FOLDER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(id = R.string.extend_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(stringResource(id = R.string.entire_folder_description), fontSize = 11.sp,)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

