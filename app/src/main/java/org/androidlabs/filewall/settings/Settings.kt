package org.androidlabs.filewall.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")

    /**
     * Set when biometric access is switched off because the device's enrolled biometrics
     * changed. It has to outlive the check that sets it: the check runs on every resume,
     * and the first resume after the change happens behind the PIN gate, where the user
     * cannot see anything. By the next resume biometric is already off, so the in-memory
     * flag reports "nothing changed" and the explanation is lost. Cleared when the user
     * acknowledges it.
     */
    val BIOMETRIC_DISABLED_BY_CHANGE = booleanPreferencesKey("biometric_disabled_by_change")

    val IS_PIN_ENABLED = booleanPreferencesKey("is_pin_enabled")
    val PIN = stringPreferencesKey("pin")

    /** Consecutive failed PIN entries; cleared on success. */
    val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")

    /** Epoch millis until which PIN entry is refused. */
    val PIN_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until")

    val IS_DEBUG_MODE_ENABLED = booleanPreferencesKey("is_debug_mode_enabled")

    val IS_ACCESS_LOG_ENABLED = booleanPreferencesKey("is_access_log_enabled")

    val IS_SILENCE_MODE_ENABLED = booleanPreferencesKey("is_silence_mode_enabled")

    val DEFAULT_DURATION = intPreferencesKey("default_duration")

    val IS_PROTECTION_DISABLED = booleanPreferencesKey("is_protection_disabled")

    val PROTECTION_END_TIMESTAMP = longPreferencesKey("protection_end_timestamp")
}