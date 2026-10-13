package org.androidlabs.filewall

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.androidlabs.filewall.rules.RulesFragment
import org.androidlabs.filewall.rules.add.AddRuleActivity
import org.androidlabs.filewall.settings.SettingsFragment
import org.androidlabs.filewall.ui.SecurityFragmentActivity
import org.androidlabs.filewall.ui.theme.FilewallTheme
import org.androidlabs.filewall.ui.useVisibilityState
import android.Manifest
import android.app.NotificationManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.androidlabs.filewall.accessrequests.AccessRequestFragment
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.dataStore
import org.androidlabs.filewall.utils.htmlToAnnotatedString
import kotlin.getValue


class MainActivity : SecurityFragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by viewModels()
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onResume() {
        super.onResume()
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(19988)
        } catch (ex: Exception) {
            Log.e("MA", "notification cancel error:" + ex.message)
        }
        viewModel.checkBiometricChanged()
    }

    private fun handleIntent(intent: Intent) {
        val openRequests = intent.getBooleanExtra("open_requests",false)
        if (openRequests) {
            sharedViewModel.openScreen(Screen.Requests.route)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val appName = resources.getString(R.string.app_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        handleIntent(intent)

        setContent {
            FilewallTheme {
                MainScreen(
                    appName,
                    viewModel,
                    sharedViewModel,
                    onAddRule = ::onAddRule,
                    onCheckAuth = ::onCheckAuth,
                )
            }
        }
    }

    private fun onAddRule() {
        val intent = Intent(this, AddRuleActivity::class.java)
        startActivity(intent)
    }

    private fun onCheckAuth() {
        sfApplication.checkAndRunAuth()
    }
}

sealed class Screen(val route: String, val icon: ImageVector, val title: String) {
    data object Requests : Screen("requests", Icons.Default.Lock, "Requests")
    data object Rules : Screen("rules", Icons.AutoMirrored.Filled.List, "Rules")
    data object Settings : Screen("settings", Icons.Default.Settings, "Settings")
}

@Composable
fun BottomNavigationBar(navController: NavController, requestsCount: Long) {
    val items = listOf(
        Screen.Requests,
        Screen.Rules,
        Screen.Settings
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val selectedLabel = stringResource(R.string.tab_selected)
        val notSelectedLabel = stringResource(R.string.tab_not_selected)
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                // Nothing in this bar reached TalkBack carrying the selected state, so
                // every tab read the same and there was no telling which one you were on.
                modifier = Modifier
                    .navigationBarsPadding()
                    .semantics(mergeDescendants = true) {
                        this.role = Role.Tab
                        this.selected = isSelected
                        this.stateDescription =
                            if (isSelected) selectedLabel else notSelectedLabel
                    },
                icon = {
                    // The label names the tab; describing the icon too reads it twice.
                    if (screen is Screen.Requests && requestsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(requestsCount.toString())
                                }
                            }
                        ) {
                            Icon(screen.icon, contentDescription = null)
                        }
                    } else {
                        Icon(screen.icon, contentDescription = null)
                    }
                },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        // Prevent multiple copies of the same destination
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid creating multiple instances of the same destination
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    title: String,
    viewModel: MainActivityViewModel,
    sharedViewModel: SharedViewModel,
    onAddRule: () -> Unit,
    onCheckAuth: () -> Unit,
) {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val scope = rememberCoroutineScope()
    var showDialog1 by remember { mutableStateOf(false) }
    var dialog1Finished by remember { mutableStateOf(false) }


    var showDeleteRequestsDialog by remember { mutableStateOf(false) }
    var showDeleteRulesDialog by remember { mutableStateOf(false) }
    var bioChangeAcknowledged by remember { mutableStateOf(false) }

    val currentApp = LocalContext.current.applicationContext as SFApplication
    val isBiometricChanged by viewModel.isBiometricChanged.observeAsState(initial = false)
    val isAuthEnabled by viewModel.isAuthEnabled.observeAsState(initial = true)
    val requestsCount by sharedViewModel.requestsCount.observeAsState(initial = 0)
    val rulesCount by sharedViewModel.rulesCount.observeAsState(initial = 0L)
    val ruleConfirmed by sharedViewModel.ruleConfirmedFlow.collectAsStateWithLifecycle(initialValue = false)
    val navController = rememberNavController()
    val targetScreen by sharedViewModel.navigateToScreen.collectAsState()

    LaunchedEffect(ruleConfirmed) {
        if (ruleConfirmed) {
            sharedViewModel.saveRuleConfirmed(false)
            val prefs = dataStore.data.first()
            val hide1 = prefs[booleanPreferencesKey("dont_show_dialog1")] ?: false
            if (!hide1) {
                showDialog1 = true
                dialog1Finished = false
            } else {
                dialog1Finished = true
            }
        } else {
            //dialog1Finished = true
        }
    }

    LaunchedEffect(navController, targetScreen) {
        targetScreen?.let { route ->
            try {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(Screen.Requests.route) {
                        saveState = true
                    }
                }
                sharedViewModel.resetNavigation()
            } catch (ex: Exception) {
                Log.e("MA", "Navigation error: ${ex.message}")
            }
        }
    }

    val isScreenHidden = useVisibilityState(
        currentApp.isAccessGranted,
        onVisibilityChanged = {
        })

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharedViewModel.loadRequestsCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isRules = currentRoute == Screen.Rules.route
    val isRequests = currentRoute == Screen.Requests.route
    val titleForScreen = when (currentRoute) {
        Screen.Requests.route -> "Active Access Requests"
        Screen.Rules.route -> "Active Rules"
        Screen.Settings.route -> "Settings"
        else -> "Unknown Screen"
    }

    // A change that has just switched biometric off leaves the gate locked too: hand over
    // to the PIN right away rather than showing the lock text with nothing to act on.
    isBiometricChanged?.let {
        onCheckAuth()
    }

    // Driven off the stored flag rather than the in-memory one: the check that detects
    // the change runs behind the PIN gate, where nobody can see a dialog, and by the time
    // the user is looking at this screen the in-memory answer is already "nothing
    // changed". The flag survives until it has been acknowledged.
    val biometricDisabledByChange by viewModel.biometricDisabledByChange
        .collectAsStateWithLifecycle(initialValue = false)
    // OK clears the stored flag on another thread, so for a moment it still reads true.
    // Re-arming the dialog from it brought the dialog straight back and it took a second
    // tap to get rid of. Remember the acknowledgement here until the flag has caught up.
    LaunchedEffect(biometricDisabledByChange) {
        if (!biometricDisabledByChange) {
            bioChangeAcknowledged = false
        }
    }
    val showBioChangedDialog =
        biometricDisabledByChange && !isScreenHidden && !bioChangeAcknowledged

    if (showDialog1) {
        var isChecked by remember { mutableStateOf(true) }
        val htmlText = stringResource(R.string.dialog1_text)
        val annotated = htmlToAnnotatedString(htmlText)
        AlertDialog(
            onDismissRequest = { showDialog1 = false },
            title = {
                Text(text = LocalContext.current.getString(R.string.dialog1_title))
            },
            text = {
                Column {
                    Text(text = annotated)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.toggleable(
                            value = isChecked,
                            role = Role.Checkbox,
                            onValueChange = { isChecked = it }
                        )
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                        Text(
                            text = LocalContext.current.getString(R.string.dont_show_again)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog1 = false
                        dialog1Finished = true
                        if (isChecked) {
                            scope.launch {
                                dataStore.edit { it[booleanPreferencesKey("dont_show_dialog1")] = true }
                            }
                        }
                    }
                ) {
                    Text(LocalContext.current.getString(R.string.ok))
                }
            }
        )
    }
    if (dialog1Finished || !ruleConfirmed) { //rulesCount==0L) {
    if (showBioChangedDialog) {
        AlertDialog(
            onDismissRequest = {  },
            title = {
                Text(text = LocalContext.current.getString(R.string.bio_dis_title))
            },
            text = {
                Text(LocalContext.current.getString(R.string.biometric_changed))
            },
            confirmButton = {
                Button(
                    onClick = {
                        bioChangeAcknowledged = true
                        viewModel.acknowledgeBiometricDisabled()
                        viewModel.resetBiometricChanged()
                        onCheckAuth()
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
    isAuthEnabled?.let {
        if (!it && !SFApplication.wasDialogShown) {

            AlertDialog(
                onDismissRequest = {
                    SFApplication.wasDialogShown = true
                    viewModel.resetBiometricChanged()
                },
                title = {
                    Text(text = stringResource(R.string.auth_not_enabled))
                },
                text = {
                    Text(LocalContext.current.getString(R.string.auth_disabled))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            SFApplication.wasDialogShown = true
                            viewModel.resetBiometricChanged()
                            sharedViewModel.openScreen(Screen.Settings.route)
                        }
                    ) {
                        Text(stringResource(R.string.go_settings))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            SFApplication.wasDialogShown = true
                            viewModel.resetBiometricChanged()
                        }
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            )
        }
    }
    }

    // alpha(0f) was not a lock: the whole NavHost still composed, the fragments were built
    // and loaded their data, and the toolbar and bottom navigation were not covered at all,
    // so the app looked unlocked. Nothing protected is composed until access is granted.
    if (isScreenHidden) {
        LockedScreen()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = rememberDrawablePainter(
                                drawable = LocalContext.current.getDrawable(
                                    R.mipmap.ic_launcher
                                )
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                title,
                                fontSize = 15.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(titleForScreen, fontSize = 13.sp, lineHeight = 13.sp)
                        }
                    }
                },
                actions = {
                    when {
                        isRequests -> {
                            if (requestsCount > 0)
                                IconButton(onClick = {
                                    showDeleteRequestsDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription =
                                            stringResource(R.string.delete_all_requests),
                                    )
                                }
                        }

                        isRules -> {
                            Row {
                                // ruleConfirmed is a one-shot trigger for the first-rule
                                // dialog - it is reset to false as soon as it is read - so
                                // gating on it meant this action never appeared. It belongs
                                // on "there is something to delete", like the Requests one.
                                if (rulesCount > 0)
                                    IconButton(onClick = {
                                        showDeleteRulesDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription =
                                                stringResource(R.string.delete_all_rules),
                                        )
                                    }
                                IconButton(onClick = onAddRule) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_add_24),
                                        contentDescription = stringResource(R.string.add_rule),
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController, requestsCount)
        }
    ) { innerPadding ->
        NavHost(
            navController, startDestination = Screen.Requests.route, modifier = Modifier
                .padding(innerPadding)
        ) {
            composable(Screen.Requests.route) {
                FragmentScreen("requests") { AccessRequestFragment() }
            }
            composable(Screen.Rules.route) {
                FragmentScreen("rules") { RulesFragment() }
            }
            composable(Screen.Settings.route) {
                FragmentScreen("settings") { SettingsFragment() }
            }
        }

        if (showDeleteRequestsDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete),
                message = stringResource(R.string.confirm_delete_requests),
                onConfirm = {
                    sharedViewModel.deleteAllRequests()
                    showDeleteRequestsDialog = false
                },
                onDismiss = { showDeleteRequestsDialog = false }
            )
        }

        if (showDeleteRulesDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete),
                message = stringResource(R.string.confirm_delete_rules),
                onConfirm = {
                    sharedViewModel.deleteAllRules()
                    showDeleteRulesDialog = false
                },
                onDismiss = { showDeleteRulesDialog = false }
            )
        }
    }
}

/** What the main screen shows while the authentication gate has not been passed. */
@Composable
fun LockedScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.auth_required_without_app_name),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Hosts a fragment inside the Compose tree.
 *
 * The fragment is identified by [tag] and built by [newFragment] only when the fragment
 * manager does not already hold it. This used to take a Fragment instance as a parameter,
 * so a new one was constructed on every recomposition, and it committed that one into a
 * freshly generated container id - which meant replace() had nothing to replace and simply
 * added another fragment and another view tree beside the last. Nothing removed them when
 * the screen left composition either, so ordinary tab switching grew the retained view
 * count without bound.
 */
@SuppressLint("ContextCastToActivity")
@Composable
fun FragmentScreen(tag: String, newFragment: () -> Fragment) {
    val activity = LocalContext.current as FragmentActivity
    val containerId = rememberSaveable { View.generateViewId() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply { id = containerId }
        },
        update = { container ->
            val manager = activity.supportFragmentManager
            val current = manager.findFragmentByTag(tag)
            // After a configuration change the manager restores the fragment before this
            // container exists, so the fragment is held but has no view in the screen: the
            // tab was drawn empty. Anything not showing in this container is replaced.
            if (!manager.isStateSaved && current?.view?.parent !== container) {
                manager.commit { replace(containerId, newFragment(), tag) }
            }
        },
        onRelease = {
            val manager = activity.supportFragmentManager
            val current = manager.findFragmentByTag(tag)
            if (current != null && !manager.isStateSaved) {
                manager.beginTransaction().remove(current).commitAllowingStateLoss()
            }
        }
    )
}