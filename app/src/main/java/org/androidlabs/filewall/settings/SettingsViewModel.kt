package org.androidlabs.filewall.settings

import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.logger.Log

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SettingsViewModel"

    private val dataStore = application.dataStore

    val isBiometricEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_BIOMETRIC_ENABLED] == true
        }

    val isPinEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_PIN_ENABLED] == true
        }

    val isDebugModeEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_DEBUG_MODE_ENABLED] == true
        }

    val isAccessLogEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_ACCESS_LOG_ENABLED] == true
        }

    val isSilenceModeEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_SILENCE_MODE_ENABLED] == true
        }


    val isProtectionDisabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_PROTECTION_DISABLED] == true
        }


    fun onToggleSilenceMode(enabled: Boolean) {
        Log.d(TAG, "onToggleSilenceMode $enabled")
        try {
            runBlocking(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_SILENCE_MODE_ENABLED] = enabled
                }
            }
            Log.d(TAG, "Silence mode state changed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change silence mode: ${e.message}")
        }
    }


    fun onToggleDebugMode(enabled: Boolean) {
        Log.d(TAG, "onToggleDebugMode $enabled")
        try {
            // Block the main thread, but execute the edit operation on the IO dispatcher
            runBlocking(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_DEBUG_MODE_ENABLED] = enabled
                }
            }
            Log.d(TAG, "Debug mode state changed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change debug mode: ${e.message}")
        }
    }

    fun onToggleAccessLog(enabled: Boolean) {
        Log.d(TAG, "onToggleAccessLog $enabled")
        try {
            // Block the main thread, but execute the edit operation on the IO dispatcher
            runBlocking(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_ACCESS_LOG_ENABLED] = enabled
                }
            }
            Log.d(TAG, "Access log state changed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change access log: ${e.message}")
        }
    }

    fun onToggleDisableProtection(enabled: Boolean, durationIndex: Int) {
        Log.d(TAG, "onToggleDisableProtection $enabled")
        try {
            viewModelScope.launch(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_PROTECTION_DISABLED] = enabled
                }
            }
            val durationMinutes = when (durationIndex) {
                0 -> 1
                1 -> 5
                2 -> 10
                3 -> 30
                4 -> 60
                else -> 1
            }
            val app = getApplication<SFApplication>()
           if (enabled)
                app.enableProtectionFor(durationMinutes)
            else
                app.disableProtection()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change disable protection: ${e.message}")
        }
    }
}