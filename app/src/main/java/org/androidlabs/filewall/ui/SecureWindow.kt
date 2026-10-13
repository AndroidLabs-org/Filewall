package org.androidlabs.filewall.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore

/**
 * Keeps a window out of screenshots, screen recordings, MediaProjection consumers and the
 * recents thumbnail by holding [WindowManager.LayoutParams.FLAG_SECURE].
 *
 * Call from `onCreate`, after `super.onCreate`.
 *
 * @param always when true the flag is set unconditionally — for screens that show the
 * credential itself or exist only while the app is protected. Otherwise the flag follows the
 * authentication settings: there is nothing to protect while the app is unprotected, and the
 * user is told as much on first launch.
 */
fun ComponentActivity.secureWindow(always: Boolean = false) {
    if (always) {
        setSecure(true)
        return
    }

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            val pin = dataStore.data.map { it[SettingsKeys.IS_PIN_ENABLED] == true }
            val biometric = dataStore.data.map { it[SettingsKeys.IS_BIOMETRIC_ENABLED] == true }

            combine(pin, biometric) { pinEnabled, biometricEnabled -> pinEnabled || biometricEnabled }
                .distinctUntilChanged()
                .collect(::setSecure)
        }
    }
}

private fun ComponentActivity.setSecure(secure: Boolean) {
    if (secure) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
