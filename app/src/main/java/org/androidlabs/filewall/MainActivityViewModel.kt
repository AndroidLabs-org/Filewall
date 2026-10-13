package org.androidlabs.filewall

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.auth.biometric.Biometric
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val dataStore = application.dataStore
    private val dbHelper: Database = Database(application.applicationContext)

    private val _isBiometricChanged: MutableLiveData<Boolean?> = MutableLiveData(null)
    val isBiometricChanged: LiveData<Boolean?>
        get() = _isBiometricChanged

    private val _isAuthEnabled: MutableLiveData<Boolean?> = MutableLiveData(null)
    val isAuthEnabled: LiveData<Boolean?>
        get() = _isAuthEnabled


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
                    Biometric.createBiometricKey()
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

    fun isPinEnabled(context: Context): Boolean {
        val isEnabled = runBlocking {
            context.dataStore.data
                .map { preferences ->
                    preferences[SettingsKeys.IS_PIN_ENABLED] == true
                }
                .first()
        }
        return isEnabled
    }

    fun checkBiometricChanged() {
        val isBiometricEnabled =
            Biometric.isEnabled(getApplication<Application>().applicationContext)
        if (!isBiometricEnabled) {
            _isBiometricChanged.postValue(false)
            if (!isPinEnabled(getApplication<Application>().applicationContext))
                _isAuthEnabled.postValue(false)
        } else
            runBlocking(Dispatchers.IO) {
                Biometric.createBiometricKey()
                if (!Biometric.isBiometricKeyValid()) {
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
                    _isAuthEnabled.postValue(true)
                }
            }
    }

    /** True from the moment biometric is switched off by an enrolment change until acknowledged. */
    val biometricDisabledByChange: Flow<Boolean> = dataStore.data
        .map { it[SettingsKeys.BIOMETRIC_DISABLED_BY_CHANGE] == true }

    fun acknowledgeBiometricDisabled() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[SettingsKeys.BIOMETRIC_DISABLED_BY_CHANGE] = false }
        }
    }

    fun resetBiometricChanged() {
        _isAuthEnabled.value = null
        _isBiometricChanged.value = null
    }

}