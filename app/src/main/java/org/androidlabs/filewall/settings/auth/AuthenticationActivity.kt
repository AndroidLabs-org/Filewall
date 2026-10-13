package org.androidlabs.filewall.settings.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.auth.biometric.Biometric
import org.androidlabs.filewall.auth.pin.PIN
import org.androidlabs.filewall.auth.pin.PinActivity
import org.androidlabs.filewall.auth.pin.PinListener
import org.androidlabs.filewall.auth.pin.PinNotifier
import org.androidlabs.filewall.database.AuthItem
import org.androidlabs.filewall.database.AuthType
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.settings.SettingsRow
import org.androidlabs.filewall.ui.BackNavigable
import org.androidlabs.filewall.ui.Section
import org.androidlabs.filewall.ui.secureWindow

class AuthenticationActivity : FragmentActivity(), PinListener {
    private val viewModel: AuthenticationViewModel by viewModels()
    private lateinit var dbHelper: Database

    private val sfApplication: SFApplication
        get() = application as SFApplication

    enum class DialogType() {
        PIN,
        PIN_DISABLE,
        PIN_DISABLE_WITH_BIO,
        BIOMETRIC,
        BIOMETRIC_DISABLE,
    }

    override fun onResume() {
        super.onResume()
        sfApplication.checkAndRunAuth()
        viewModel.checkBiometricChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow()

        dbHelper = Database(this)

        PinNotifier.registerListener(this)

        setContent {
            BackNavigable(
                titleResId = R.string.authentication,
                onBackPressedDispatcher = onBackPressedDispatcher,
                content = { innerPadding, isScreenHidden ->
                    AuthenticationContent(
                        modifier = Modifier
                            .padding(innerPadding),
                        viewModel = viewModel,
                        onToggleBiometric = ::onToggleBiometric,
                        onTogglePin = ::onTogglePin,
                        ::onCheckAuth,
                    )
                }
            )
        }
    }

    private fun onCheckAuth() {
        sfApplication.checkAndRunAuth()
    }

    override fun onDestroy() {
        super.onDestroy()
        PinNotifier.unregisterListener(this)
    }

    private fun onToggleBiometric(value: Boolean) {
        if (!value) {
            /*viewModel.toggleBiometric(value)
            dbHelper.insertAuth(
                AuthItem(
                    null,
                    packageName,
                    AuthType.BIOMETRIC,
                    System.currentTimeMillis()
                )
            )
            sfApplication.refreshAccessStatus()*/
            val intent = Intent(this, PinActivity::class.java).apply {
                putExtra("settings", true)
                putExtra("disablebio", true)
                putExtra("purpose", getString(R.string.auth_for_change_settings))
            }
            startActivity(intent)
        } else
            Biometric.auth(
                this,
                getString(R.string.auth_for_change_settings),
                false
            ) { isAllowed, errCode, errString ->
                if (isAllowed) {
                    viewModel.toggleBiometric(value)
                    dbHelper.insertAuth(
                        AuthItem(
                            null,
                            packageName,
                            AuthType.BIOMETRIC,
                            System.currentTimeMillis()
                        )
                    )
                    sfApplication.refreshAccessStatus()
                }

                errCode?.let { code ->
                    if (code != ERROR_NEGATIVE_BUTTON && code != ERROR_USER_CANCELED) {
                        errString?.let { msg ->
                            viewModel.setAlertError("$msg ($code)")
                        }
                    }
                }
            }
    }

    private fun onTogglePin(value: Boolean) {
        if (!value) {
            val granted = sfApplication.isAccessGranted.value
            if (granted != null)
                if (granted) {
                    val isRemoved = PIN.removePin(this, "", true)
                    PinNotifier.notifyResolution(isRemoved, true, false)
                    return
                }
        }
        val intent = Intent(this, PinActivity::class.java).apply {
            putExtra("settings", true)
            putExtra("purpose", getString(R.string.auth_for_change_settings))
        }
        startActivity(intent)

    }

    override fun onPinResolution(isAllowed: Boolean, fromSettings: Boolean, disableBio: Boolean) {
        if (fromSettings && isAllowed) {
            if (disableBio) {
                viewModel.toggleBiometric(false)
                dbHelper.insertAuth(
                    AuthItem(
                        null,
                        packageName,
                        AuthType.BIOMETRIC,
                        System.currentTimeMillis()
                    )
                )
                sfApplication.refreshAccessStatus()
            } else {
                viewModel.togglePin(!PIN.isEnabled(this))
                dbHelper.insertAuth(
                    AuthItem(
                        null,
                        packageName,
                        AuthType.PIN,
                        System.currentTimeMillis()
                    )
                )
                sfApplication.refreshAccessStatus()
            }
        }
    }
}

@Composable
private fun AuthenticationContent(
    modifier: Modifier,
    viewModel: AuthenticationViewModel,
    onToggleBiometric: (Boolean) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onCheckAuth: () -> Unit,
) {
    val isBiometricAvailable by viewModel.isBiometricAvailable.observeAsState(initial = false)
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState(initial = false)
    val isPinEnabled by viewModel.isPinEnabled.collectAsState(initial = false)
    val alertError by viewModel.alertError.observeAsState(initial = null)
    val isBiometricChanged by viewModel.isBiometricChanged.observeAsState(initial = false)
    var showBioChangedDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<AuthenticationActivity.DialogType?>(null) }

    val scrollState = rememberScrollState()

    LaunchedEffect(key1 = true) {
        viewModel.refreshBiometricAvailable()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBiometricAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    isBiometricChanged?.let {
        if (it) {
            showBioChangedDialog = true
        } else
            onCheckAuth()
    }

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
                        showBioChangedDialog = false
                        viewModel.resetBiometricChanged()
                        onCheckAuth()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Column(modifier = modifier.verticalScroll(scrollState)) {
        Section(
            header = stringResource(id = R.string.authentication_header),
            footer = stringResource(id = R.string.authentication_footer)
        ) {
            SettingsRow(
                title = stringResource(id = R.string.settings_pin),
                subtitle = stringResource(id = R.string.settings_pin_description),
                iconView = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "PIN Icon",
                    )
                },
                rightView = {
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { checked ->
                            if (!checked && isBiometricEnabled) {
                                pendingType = AuthenticationActivity.DialogType.PIN_DISABLE_WITH_BIO
                                showDialog = true
                            } else
                                if (checked) {
                                    pendingType = AuthenticationActivity.DialogType.PIN
                                    showDialog = true
                                } else {
                                    pendingType = AuthenticationActivity.DialogType.PIN_DISABLE
                                    showDialog = true
                                }
                        }
                    )
                }
            )

            SettingsRow(
                title = stringResource(id = R.string.settings_biometric),
                subtitle = stringResource(id = R.string.settings_biometric_description),
                iconView = {
                    val tintColor = if (!isPinEnabled) {
                        Color.Gray
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Image(
                        painter = painterResource(id = R.drawable.ic_fingerprint_24),
                        contentDescription = "Fingerprint Icon",
                        colorFilter = ColorFilter.tint(tintColor)
                    )
                },
                rightView = {
                    Switch(
                        checked = isBiometricEnabled,
                        enabled = isBiometricAvailable && isPinEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                pendingType = AuthenticationActivity.DialogType.BIOMETRIC
                                showDialog = true
                            } else {
                                pendingType = AuthenticationActivity.DialogType.BIOMETRIC_DISABLE
                                showDialog = true
                                //onToggleBiometric(false)
                            }
                        }
                    )
                },
                isDisabledText = !isPinEnabled
            )
        }

        alertError?.let {
            AlertDialog(
                onDismissRequest = { viewModel.setAlertError(null) },
                title = {
                    Text(text = "Error")
                },
                text = {
                    Text(it)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.setAlertError(null) }
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        if (showDialog && pendingType != null) {
            var titleRes: Int = R.string.important_notice
            var messageRes: Int = R.string.enable_pin_message
            var confirmRes: Int = R.string.understand

            when (pendingType) {
                AuthenticationActivity.DialogType.PIN -> {
                    titleRes = R.string.important_notice
                    messageRes = R.string.enable_pin_message
                    confirmRes = R.string.understand
                }

                AuthenticationActivity.DialogType.PIN_DISABLE -> {
                    titleRes = R.string.warning
                    messageRes = R.string.are_you_sure
                    confirmRes = R.string.yes
                }

                AuthenticationActivity.DialogType.PIN_DISABLE_WITH_BIO -> {
                    titleRes = R.string.disable_auth_title
                    messageRes = R.string.bio_will_be_off
                    confirmRes = R.string.dis_auth
                }

                AuthenticationActivity.DialogType.BIOMETRIC -> {
                    confirmRes = R.string.understand
                    titleRes = R.string.bio_notice
                    messageRes = R.string.enable_biometric_message
                }

                AuthenticationActivity.DialogType.BIOMETRIC_DISABLE -> {
                    confirmRes = R.string.ok
                    titleRes = R.string.bio_notice
                    messageRes = R.string.disable_biometric_message
                }

                else -> {}
            }

            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(titleRes)) },
                text = { Text(stringResource(messageRes)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (pendingType) {
                                AuthenticationActivity.DialogType.PIN -> onTogglePin(true)
                                AuthenticationActivity.DialogType.PIN_DISABLE -> onTogglePin(false)
                                AuthenticationActivity.DialogType.PIN_DISABLE_WITH_BIO -> onTogglePin(false)
                                AuthenticationActivity.DialogType.BIOMETRIC -> onToggleBiometric(
                                    true
                                )

                                AuthenticationActivity.DialogType.BIOMETRIC_DISABLE -> onToggleBiometric(
                                    false
                                )

                                null -> {}
                            }
                            showDialog = false
                        }
                    ) {
                        Text(stringResource(confirmRes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}