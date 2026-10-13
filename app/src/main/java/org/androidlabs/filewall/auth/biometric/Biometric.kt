package org.androidlabs.filewall.auth.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.R
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

typealias AuthCallback = (isAllowed: Boolean, errorCode: Int?, errString: CharSequence?) -> Unit

class Biometric {
    companion object {
        private const val TAG = "Biometric"
        private const val KEY_NAME = "biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private var activePrompt: BiometricPrompt? = null

        fun isAvailable(context: Context): Boolean {
            val biometricManager = BiometricManager.from(context)
            return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        }

        fun isEnabled(context: Context): Boolean {
            val isEnabled = runBlocking {
                context.dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.IS_BIOMETRIC_ENABLED] == true
                    }
                    .first()
            }
            return isEnabled
        }

        fun auth(
            fragment: FragmentActivity,
            purpose: String,
            cancel: Boolean,
            callback: AuthCallback
        ) {
            val executor = ContextCompat.getMainExecutor(fragment)
            val biometricPrompt = BiometricPrompt(
                fragment,
                executor,
                getCallback(callback)
            )
            activePrompt = if (cancel)
                biometricPrompt
            else
                null
            presentPrompt(biometricPrompt, purpose, fragment, cancel)
        }

        fun auth(
            fragment: Fragment,
            purpose: String,
            cancel: Boolean,
            callback: AuthCallback
        ) {
            val context = fragment.requireContext()
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                fragment,
                executor,
                getCallback(callback)
            )
            activePrompt = if (cancel)
                biometricPrompt
            else
                null
            presentPrompt(biometricPrompt, purpose, context, cancel)
        }

        private fun presentPrompt(
            biometricPrompt: BiometricPrompt,
            purpose: String,
            context: Context,
            cancel: Boolean
        ) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.app_name))
                .setSubtitle(purpose)
                .setNegativeButtonText("Cancel")
                .build()
            activePrompt = if (cancel)
                biometricPrompt
            else
                null
            biometricPrompt.authenticate(promptInfo)

            Log.d(TAG, "present biometric prompt")
        }

        private fun getCallback(callback: AuthCallback): BiometricPrompt.AuthenticationCallback {
            return object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "onAuthenticationSucceeded $result")
                    callback(true, null, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "onAuthenticationError $errorCode $errString")
                    activePrompt?.cancelAuthentication()
                    callback(false, errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "onAuthenticationFailed")
                    activePrompt?.cancelAuthentication()
                    callback(false, null, null)
                }
            }
        }

        /**
         * Makes the key that a new enrolment invalidates, unless it is already there.
         *
         * @return false when the keystore will not make one. Some devices refuse - "At least
         * one biometric must be enrolled to create keys requiring user authentication" with
         * a fingerprint enrolled, seen on an Android 10 image - and without the key a new
         * enrolment cannot be told from a missing key.
         */
        fun createBiometricKey(): Boolean {
            return try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (keyStore.containsAlias(KEY_NAME))
                    return true // Key already exists

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

                val spec = KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).apply {
                    setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    setUserAuthenticationRequired(true)
                    setInvalidatedByBiometricEnrollment(true)
                }.build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun deleteBiometricKey() {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_NAME)) {
                keyStore.deleteEntry(KEY_NAME)
            }
        }

        fun isBiometricKeyValid(): Boolean {
            return try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val secretKey = keyStore.getKey(KEY_NAME, null) as SecretKey
                val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                true // Key is valid
            } catch (e: Exception) {
                false // Key is invalid, likely due to biometric change
            }
        }
    }
}