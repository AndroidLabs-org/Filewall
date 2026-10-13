package org.androidlabs.filewall.auth

import android.content.Context
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.auth.biometric.Biometric
import org.androidlabs.filewall.auth.biometric.BiometricListener
import org.androidlabs.filewall.auth.biometric.BiometricNotifier
import org.androidlabs.filewall.auth.biometric.BiometricPlaceholderActivity
import org.androidlabs.filewall.auth.biometric.BiometricReceiver
import org.androidlabs.filewall.auth.pin.PIN
import org.androidlabs.filewall.auth.pin.PinActivity
import org.androidlabs.filewall.auth.pin.PinListener
import org.androidlabs.filewall.auth.pin.PinNotifier
import org.androidlabs.filewall.auth.pin.PinReceiver
import org.androidlabs.filewall.database.AuthItem
import org.androidlabs.filewall.database.AuthType
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.utils.Utils

class AuthManager private constructor() : BiometricListener, PinListener {
    companion object {
        val instance: AuthManager by lazy { AuthManager() }
    }

    private val TAG = "AuthManager"
    private var dbHelper: Database? = null
    private var isAuthRetryRequested: Boolean = false
    private var lastCancelRetry: Long? = null
    private var timeoutToNextRequest: Long = 1000
    private var lastCallingPackage: String? = null

    public var isBioWas: Boolean = false

    init {
        BiometricNotifier.registerListener(this)
        PinNotifier.registerListener(this)
    }

    fun isEnabled(context: Context): Boolean {
        return PIN.isEnabled(context) || Biometric.isEnabled(context)
    }

    fun validateSecurity(
        context: Context,
        dbHelper: Database,
        documentId: String,
        purpose: String,
        callingPackage: String,
        withoutRetry: Boolean = false
    ) {
        Log.d(TAG, "validateSecurity: $callingPackage | $purpose")

        lastCallingPackage = callingPackage

        this.dbHelper = dbHelper

        val app = context.applicationContext as? SFApplication
        val isBiometricEnabled = app?.isBiometricEnabled ?: Biometric.isEnabled(context)
        val isPinEnabled = app?.isPinEnabled ?: PIN.isEnabled(context)

        if (!isBiometricEnabled && !isPinEnabled) {
            Log.d(TAG, "validateSecurity passed because of disabled auth")
            return
        }


        if (isBiometricEnabled && isBioWas == false) {
            Log.d(TAG, "validateSecurity - biometric validation")
            val biometricValidated = validateMethod(
                context = context,
                purpose = purpose,
                isEnabled = isBiometricEnabled,
                documentId = documentId,
                callingPackage = callingPackage,
                type = AuthType.BIOMETRIC,
                dbHelper = dbHelper,
                activityClass = BiometricPlaceholderActivity::class.java,
                receiverClass = BiometricReceiver::class.java,
                withoutRetry = isPinEnabled || withoutRetry,
            )
            return
        }

        if (isPinEnabled) {
            Log.d(TAG, "validateSecurity - pin validation")
            isBioWas = false
            val pinValidated = validateMethod(
                context = context,
                purpose = purpose,
                isEnabled = true,
                documentId = documentId,
                callingPackage = callingPackage,
                type = AuthType.PIN,
                dbHelper = dbHelper,
                activityClass = PinActivity::class.java,
                receiverClass = PinReceiver::class.java,
                withoutRetry = withoutRetry
            )

            if (!pinValidated) {
                Log.e(TAG, "pin validation failed")
                //lastCallingPackage = null
                throw SecurityException()
            }
        } else {
            Log.e(TAG, "biometric validation failed")
            throw SecurityException()
        }

        Log.d(TAG, "validateSecurity passed")
    }

    private fun validateMethod(
        context: Context,
        purpose: String,
        isEnabled: Boolean,
        documentId: String,
        callingPackage: String,
        type: AuthType,
        dbHelper: Database,
        activityClass: Class<out Any>,
        receiverClass: Class<out Any>,
        withoutRetry: Boolean = false
    ): Boolean {
        if (!isEnabled) {
            return true
        }

        try {
            Log.d(TAG, "validate method ${type.name}")

            fun validateAuth(): Boolean {
                return dbHelper.validateAuth(documentId, callingPackage)
            }

            if (validateAuth()) {
                Log.d(TAG, "validate method - passed")
                return true
            } else {
                Log.d(TAG, "validate method - start")
            }

            lastCancelRetry?.let {
                val elapsedFromLastRetry = System.currentTimeMillis() - it
                if (elapsedFromLastRetry < timeoutToNextRequest) {
                    Log.d(TAG, "elapsedFromLastRetry $elapsedFromLastRetry")
                    return false
                }
            }


            if (callingPackage == context.packageName) // only requests from authactivity
                try {
                    Utils.startActivity(
                        context = context,
                        activityClass = activityClass,
                    ) {
                        putExtra("purpose", purpose)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.message)
                    return false
                }

            val newResolution = validateAuth()

            Log.d(TAG, "resolution new - $newResolution")

            if (!newResolution && !withoutRetry) {
                Log.d(TAG, "present access denied")

                if (isAuthRetryRequested) {
                    isAuthRetryRequested = false
                    return validateMethod(
                        context,
                        purpose,
                        true,
                        documentId,
                        callingPackage,
                        type,
                        dbHelper,
                        activityClass,
                        receiverClass,
                        withoutRetry
                    )
                } else {
                    return false
                }
            } else {
                return newResolution
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateMethod failed by - ${e.message}, ${e.localizedMessage}")
        }

        return true
    }

    override fun onBiometricResolution(isAllowed: Boolean) {
        Log.d(TAG, "onBiometricResolution $isAllowed, $lastCallingPackage")
        isBioWas = true
        if (isAllowed && lastCallingPackage != null) {
            dbHelper!!.insertAuth(
                AuthItem(
                    null,
                    lastCallingPackage!!,
                    AuthType.BIOMETRIC,
                    System.currentTimeMillis()
                )
            )
        }
    }

    override fun onPinResolution(isAllowed: Boolean, fromSettings: Boolean, disableBio: Boolean) {
        if (fromSettings) {
            return
        }

        Log.d(TAG, "onPinResolution $isAllowed, $lastCallingPackage")

        if (isAllowed && lastCallingPackage != null) {
            dbHelper!!.insertAuth(
                AuthItem(
                    null,
                    lastCallingPackage!!,
                    AuthType.PIN,
                    System.currentTimeMillis()
                )
            )
        }
    }

}