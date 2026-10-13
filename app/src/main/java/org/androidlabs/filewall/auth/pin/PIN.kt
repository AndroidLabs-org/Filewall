package org.androidlabs.filewall.auth.pin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PIN {
    companion object {
        private const val TAG = "PIN"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val ENCRYPTION_IV_LENGTH = 12
        private const val ENCRYPTION_TAG_LENGTH = 128

        /** Consecutive wrong entries allowed before the first lockout. */
        private const val FREE_ATTEMPTS = 4

        /** Lockout applied on the 5th, 6th, 7th … consecutive failure. */
        private val LOCKOUT_LADDER_MS = longArrayOf(
            30_000L,      // 30 seconds
            60_000L,      // 1 minute
            300_000L,     // 5 minutes
            900_000L,     // 15 minutes
            1_800_000L    // 30 minutes, repeated from here on
        )

        /** Milliseconds until PIN entry is accepted again; 0 when not locked out. */
        fun lockoutRemainingMillis(context: Context): Long {
            val until = runBlocking {
                context.dataStore.data
                    .map { preferences -> preferences[SettingsKeys.PIN_LOCKOUT_UNTIL] ?: 0L }
                    .first()
            }
            return (until - System.currentTimeMillis()).coerceAtLeast(0L)
        }

        /** Records a wrong entry and returns the lockout it triggered, 0 if none yet. */
        fun registerFailedAttempt(context: Context): Long {
            var lockout = 0L
            try {
                runBlocking(Dispatchers.IO) {
                    context.dataStore.edit { preferences ->
                        val attempts = (preferences[SettingsKeys.PIN_FAILED_ATTEMPTS] ?: 0) + 1
                        preferences[SettingsKeys.PIN_FAILED_ATTEMPTS] = attempts
                        if (attempts > FREE_ATTEMPTS) {
                            val step = (attempts - FREE_ATTEMPTS - 1)
                                .coerceAtMost(LOCKOUT_LADDER_MS.lastIndex)
                            lockout = LOCKOUT_LADDER_MS[step]
                            preferences[SettingsKeys.PIN_LOCKOUT_UNTIL] =
                                System.currentTimeMillis() + lockout
                        }
                    }
                }
                Log.d(TAG, "Failed PIN attempt recorded, lockout ${lockout}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record attempt: ${e.message}")
            }
            return lockout
        }

        /** Clears the failure counter and any lockout. Call after a correct entry. */
        fun clearFailedAttempts(context: Context) {
            try {
                runBlocking(Dispatchers.IO) {
                    context.dataStore.edit { preferences ->
                        preferences.remove(SettingsKeys.PIN_FAILED_ATTEMPTS)
                        preferences.remove(SettingsKeys.PIN_LOCKOUT_UNTIL)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear attempts: ${e.message}")
            }
        }

        fun isEnabled(context: Context): Boolean {
            val isEnabled = runBlocking {
                context.dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.IS_PIN_ENABLED] == true
                    }
                    .first()
            }
            return isEnabled
        }

        fun isPinSet(context: Context): Boolean {
            val encryptedPin = runBlocking {
                context.dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.PIN]
                    }
                    .first()
            }
            return encryptedPin != null
        }

        fun savePin(context: Context, pin: String) {
            val encryptedPin = encrypt(pin, getSecretKey())

            try {
                // Block the main thread, but execute the edit operation on the IO dispatcher
                runBlocking(Dispatchers.IO) {
                    context.dataStore.edit { preferences ->
                        preferences[SettingsKeys.PIN] = encryptedPin
                    }
                }
                Log.d(TAG, "Pin saved successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save pin: ${e.message}")
            }
        }

        fun checkPin(context: Context, pin: String): Boolean {
            if (lockoutRemainingMillis(context) > 0L) {
                Log.d(TAG, "PIN check refused: locked out")
                return false
            }
            val encryptedPin = runBlocking {
                context.dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.PIN]
                    }
                    .first()
            }
            if (encryptedPin == null) {
                return false
            }
            val decryptedPin = decrypt(encryptedPin, getSecretKey())
            return decryptedPin == pin
        }

        fun removePin(context: Context, pin: String, dontcheckPIN:Boolean): Boolean {
            if (!dontcheckPIN)
                if (!checkPin(context, pin)) {
                    return false
                }
            try {
                // Block the main thread, but execute the edit operation on the IO dispatcher
                runBlocking(Dispatchers.IO) {
                    context.dataStore.edit { preferences ->
                        preferences.remove(SettingsKeys.PIN)
                    }
                }
                Log.d(TAG, "Pin removed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove pin: ${e.message}")
            }

            return true
        }

        private fun getSecretKey(): SecretKey {
            val alias = "pin"

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            var key = keyStore.getKey(alias, null) as? SecretKey

            if (key == null) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )

                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)

                key = keyGenerator.generateKey()
            }
            return key!!
        }

        private fun encrypt(data: String, secretKey: SecretKey): String {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val ivAndEncryptedData = iv + encryptedBytes

            return Base64.encodeToString(ivAndEncryptedData, Base64.DEFAULT)
        }

        private fun decrypt(encryptedData: String, secretKey: SecretKey): String {
            val ivAndEncryptedData = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = ivAndEncryptedData.copyOfRange(0, ENCRYPTION_IV_LENGTH)
            val encryptedBytes =
                ivAndEncryptedData.copyOfRange(ENCRYPTION_IV_LENGTH, ivAndEncryptedData.size)

            val cipher = Cipher.getInstance(AES_MODE)
            val gcmParameterSpec = GCMParameterSpec(ENCRYPTION_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        }
    }
}