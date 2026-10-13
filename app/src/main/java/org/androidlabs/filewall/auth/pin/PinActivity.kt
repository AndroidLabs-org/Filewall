package org.androidlabs.filewall.auth.pin

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.logger.Log
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import org.androidlabs.filewall.ui.secureWindow

class PinActivity : ComponentActivity() {
    private val TAG = "PinActivity"
    private var isFinishedByUser: Boolean = false
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow(always = true)

        Log.d(TAG, "onCreate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG)
        }

        val fromSettings = intent.getBooleanExtra("settings", false)
        // The extra is advisory display text. Asserting it non-null crashed the gate — and
        // because the gate is what the app opens into, it crashed again on every relaunch.
        val purpose = intent.getStringExtra("purpose")
            ?: getString(R.string.auth_for_app_access)
        val disableBio = intent.getBooleanExtra("disablebio",false)

        setContent {
            PinScreenManager(
                purpose = purpose,
                isPinSet = PIN.isPinSet(this),
                onPinSetSuccess = { pin ->
                    Log.d(TAG, "pin is set")
                    PIN.savePin(this, pin)
                    PinNotifier.notifyResolution(true, fromSettings,false)
                    finishByUser()
                },
                onPinCheckSuccess = { pin ->
                    Log.d(TAG, "pin check is successful")
                    if (fromSettings) {
                        if (disableBio) {
                            PinNotifier.notifyResolution(true, true, true)
                        } else {
                            val isRemoved = PIN.removePin(this, pin, false)
                            PinNotifier.notifyResolution(isRemoved, true, false)
                        }
                    } else {
                        PinNotifier.notifyResolution(isAllowed = true, fromSettings = false, false)
                    }
                    sfApplication.refreshAccessStatus()
                    finishByUser()
                },
                lockoutRemainingMillis = { PIN.lockoutRemainingMillis(this) },
                onPinValidate = { pin ->
                    val accepted = PIN.checkPin(this, pin)
                    if (accepted) {
                        PIN.clearFailedAttempts(this)
                    } else {
                        PIN.registerFailedAttempt(this)
                    }
                    accepted
                },
                onCancel = {
                    sendCancellation()
                    finishByUser()
                }
            )
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, disabling the back button
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun sendCancellation() {
        Log.d(TAG, "pin is cancelled")
        val fromSettings = intent.getBooleanExtra("settings", false)
        val disableBio = intent.getBooleanExtra("disablebio", false)
        PinNotifier.notifyResolution(false, fromSettings, disableBio)
    }

    private fun finishByUser() {
        isFinishedByUser = true
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged $hasFocus")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy $isFinishedByUser")
        if (!isFinishedByUser) {
            sendCancellation()
        }
    }
}


@Composable
fun PinScreenManager(
    purpose: String,
    isPinSet: Boolean,
    onPinSetSuccess: (String) -> Unit,
    onPinCheckSuccess: (String) -> Unit,
    onPinValidate: (String) -> Boolean,
    lockoutRemainingMillis: () -> Long,
    onCancel: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(if (isPinSet) Screen.EnterPinForCheck else Screen.SetPin) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp)) // Apply clip first to create rounded corners
            .background(Color.White)          // Apply background after clipping
            .padding(16.dp)                   // Apply padding
    ) {
        when (currentScreen) {
            Screen.SetPin -> SetPinScreen(
                onNextClick = {
                    currentScreen = Screen.ConfirmPin
                },
                onCancelClick = onCancel,
                onPinChange = { pin = it },
                pin = pin
            )

            Screen.ConfirmPin -> ConfirmPinScreen(
                pin = pin,
                errorMessage = errorMessage,
                onPinConfirm = { enteredPin ->
                    if (enteredPin == pin) {
                        onPinSetSuccess(pin)
                    } else {
                        confirmPin = ""
                        errorMessage = "PINs do not match. Please try again."
                    }
                },
                onCancelClick = onCancel,
                onPinChange = { confirmPin = it },
                confirmPin = confirmPin
            )

            Screen.EnterPinForCheck -> EnterPinForCheckScreen(
                purpose = purpose,
                onPinValidate = onPinValidate,
                lockoutRemainingMillis = lockoutRemainingMillis,
                errorMessage = errorMessage,
                onSuccess = { pin ->
                    onPinCheckSuccess(pin)
                },
                onFailure = {
                    errorMessage = "Incorrect PIN. Please try again."
                },
                onCancelClick = onCancel
            )
        }
    }
}

@Composable
fun SetPinScreen(
    onNextClick: () -> Unit,
    onCancelClick: () -> Unit,
    onPinChange: (String) -> Unit,
    pin: String
) {
    val minLength = 4
    val maxLength = 8

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Header()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set a PIN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            NoCopyPasteOutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= maxLength) onPinChange(it)
                },
                placeholder = "Enter your PIN",
                onDone = { onNextClick() }
            )

            Text(
                text = "PIN must be at least $minLength digits, but a $maxLength-digit PIN is recommended for added security",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClick) {
                Text("CANCEL")
            }

            Button(
                onClick = onNextClick,
                enabled = pin.length >= minLength,
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Text("NEXT")
            }
        }
    }
}

@Composable
fun ConfirmPinScreen(
    pin: String,
    errorMessage: String,
    onPinConfirm: (String) -> Unit,
    onCancelClick: () -> Unit,
    onPinChange: (String) -> Unit,
    confirmPin: String
) {
    fun onConfirm() {
        onPinConfirm(confirmPin)
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Header()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Re-enter your PIN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            NoCopyPasteOutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= pin.length) onPinChange(it)
                },
                placeholder = "Confirm your PIN",
                onDone = { onConfirm() }
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClick) {
                Text("CANCEL")
            }

            Button(
                onClick = ::onConfirm,
                enabled = confirmPin.length == pin.length,
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Text("CONFIRM")
            }
        }
    }
}

@Composable
fun EnterPinForCheckScreen(
    purpose: String,
    onPinValidate: (String) -> Boolean,
    lockoutRemainingMillis: () -> Long,
    errorMessage: String,
    onSuccess: (pin: String) -> Unit,
    onFailure: () -> Unit,
    onCancelClick: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var lockedForMillis by remember { mutableLongStateOf(lockoutRemainingMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            lockedForMillis = lockoutRemainingMillis()
            delay(500)
        }
    }

    val isLockedOut = lockedForMillis > 0L

    fun validate() {
        if (isLockedOut) return
        if (onPinValidate(enteredPin)) {
            onSuccess(enteredPin)
        } else {
            enteredPin = ""
            // reflect a lockout the moment it is triggered, not on the next tick
            lockedForMillis = lockoutRemainingMillis()
            onFailure()
        }
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Header()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your PIN to continue",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = purpose)

            NoCopyPasteOutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    if (it.length <= 8) enteredPin = it
                },
                placeholder = "Enter your device PIN to continue",
                onDone = { validate() }
            )

            val message = if (isLockedOut) {
                stringResource(R.string.pin_locked_out, formatLockout(lockedForMillis))
            } else {
                errorMessage
            }

            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClick) {
                Text("CANCEL")
            }

            Button(
                onClick = ::validate,
                enabled = enteredPin.isNotEmpty() && !isLockedOut,
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Text("SUBMIT")
            }
        }
    }
}

/** "45s" below a minute, otherwise "3 min". */
private fun formatLockout(millis: Long): String {
    val seconds = ((millis + 999) / 1000).toInt()
    return if (seconds < 60) "${seconds}s" else "${(seconds + 59) / 60} min"
}

enum class Screen {
    SetPin, ConfirmPin, EnterPinForCheck
}

@Composable
fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Icon",
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(stringResource(id = R.string.app_name))
    }

}

@Composable
fun NoCopyPasteOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: KeyboardActionScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    CompositionLocalProvider(
        LocalTextToolbar provides EmptyTextToolbar
    ) {

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword).copy(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = onDone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .focusable()
                .focusRequester(focusRequester)
        )
    }
}

object EmptyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() {}

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
    }
}