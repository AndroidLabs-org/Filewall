package org.androidlabs.filewall.auth.biometric

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import org.androidlabs.filewall.R
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.ui.secureWindow

class BiometricPlaceholderActivity : FragmentActivity() {
    private val TAG = "BiometricPlaceholderActivity"
    private var isFinishedByUser: Boolean = false
    private val sfApplication: SFApplication
        get() = application as SFApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow(always = true)

        Log.d(TAG, "onCreate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, disabling the back button
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
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

        Handler(Looper.getMainLooper()).postDelayed({
            auth()
        }, 500)
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
            BiometricNotifier.notifyResolution(false)
        }
        sfApplication.refreshAccessStatus()
    }

    private fun finishByUser() {
        isFinishedByUser = true
        finish()
    }

    private fun auth() {
        val purpose = intent.getStringExtra("purpose")
            ?: getString(R.string.auth_for_app_access)

        Biometric.auth(this, purpose, true) { isAllowed, _, _ ->
            BiometricNotifier.notifyResolution(isAllowed)
            finishByUser()
        }
    }
}