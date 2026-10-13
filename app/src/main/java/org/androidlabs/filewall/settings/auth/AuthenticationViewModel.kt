package org.androidlabs.filewall.settings.auth

import android.app.Application
import org.androidlabs.filewall.R
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.auth.biometric.Biometric
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore

class AuthenticationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SettingsViewModel"

    private val _isBiometricAvailable = MutableLiveData(Biometric.isAvailable(application))

    private val dbHelper: Database = Database(application.applicationContext)

    private val _alertError: MutableLiveData<String?> = MutableLiveData(null)

    private val dataStore = application.dataStore

    private val _isBiometricChanged: MutableLiveData<Boolean?> = MutableLiveData(null)
    val isBiometricChanged: LiveData<Boolean?>
        get() = _isBiometricChanged
    val alertError: LiveData<String?>
        get() = _alertError

    val isBiometricAvailable: LiveData<Boolean>
        get() = _isBiometricAvailable

    val isBiometricEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_BIOMETRIC_ENABLED] == true
        }

    val isPinEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SettingsKeys.IS_PIN_ENABLED] == true
        }

    fun toggleBiometric(enabled: Boolean) {
        Log.d(TAG, "toggleBiometric $enabled")
        try {
            // Block the main thread, but execute the edit operation on the IO dispatcher
            runBlocking(Dispatchers.IO) {
                if (enabled) {
                    // The invalidation key has to exist before the next enrolment, not be
                    // made lazily by the next check: a key created after a new fingerprint
                    // is valid, so the enrolment went undetected. A key left from an earlier
                    // period of being on predates enrolments made since, so start afresh.
                    Biometric.deleteBiometricKey()
                    if (!Biometric.createBiometricKey()) {
                        // Switching it on anyway ended at the next launch: the missing key
                        // read as "biometrics changed", biometric was switched off again
                        // and the user was told of an enrolment that never happened.
                        _alertError.postValue(
                            getApplication<Application>().getString(R.string.biometric_key_unavailable)
                        )
                        return@runBlocking
                    }
                }
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_BIOMETRIC_ENABLED] = enabled
                }
            }
            Log.d(TAG, "Biometric state changed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change biometric state: ${e.message}")
        }
    }

    fun togglePin(enabled: Boolean) {
        Log.d(TAG, "togglePin $enabled")
        try {
            var isBiometricEnabled = false
            // Block the main thread, but execute the edit operation on the IO dispatcher
            runBlocking(Dispatchers.IO) {
                dataStore.edit { settings ->
                    settings[SettingsKeys.IS_PIN_ENABLED] = enabled
                }

                isBiometricEnabled = dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.IS_BIOMETRIC_ENABLED] == true
                    }
                    .first()
            }

            if (isBiometricEnabled) {
                toggleBiometric(false)
            }
            Log.d(TAG, "Pin state changed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change pin state: ${e.message}")
        }
    }

    fun refreshBiometricAvailable() {
        _isBiometricAvailable.value = Biometric.isAvailable(getApplication())
    }

    fun setAlertError(error: String?) {
        _alertError.postValue(error)
    }

    fun checkBiometricChanged() {
        val isBiometricEnabled =
            Biometric.isEnabled(getApplication<Application>().applicationContext)
        if (!isBiometricEnabled) {
            _isBiometricChanged.postValue(false)
        } else
            runBlocking(Dispatchers.IO) {
                Biometric.createBiometricKey()
                if (Biometric.isBiometricKeyValid() == false) {
                    Biometric.deleteBiometricKey()
                    toggleBiometric(false)
                    dbHelper.clearAllAuth(getApplication<Application>().applicationContext)
                    val sfApplication = getApplication<Application>() as SFApplication
                    //sfApplication.refreshAccessStatus()
                    sfApplication.removeGrantedAccessIfNeeded()
                    dataStore.edit { it[SettingsKeys.BIOMETRIC_DISABLED_BY_CHANGE] = true }
                    _isBiometricChanged.postValue(true)
                } else {
                    _isBiometricChanged.postValue(false)
                }
            }
    }

    fun resetBiometricChanged() {
        _isBiometricChanged.value = null
    }
}