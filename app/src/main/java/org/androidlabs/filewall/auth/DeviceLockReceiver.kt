package org.androidlabs.filewall.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.androidlabs.filewall.SFApplication
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log

class DeviceLockReceiver : BroadcastReceiver() {
    private val TAG = "DeviceLockReceiver"

    private companion object {
        /** Some devices send these in place of BOOT_COMPLETED. */
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val HTC_QUICKBOOT_POWERON = "com.htc.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive $intent")
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Device locked or screen turned off")
                val dbHelper = Database(context)
                dbHelper.clearAllAuth(context)
                clear(context)
            }

            Intent.ACTION_BOOT_COMPLETED,
            QUICKBOOT_POWERON,
            HTC_QUICKBOOT_POWERON -> {
                Log.d(TAG, "Device rebooted")
                clear(context)
            }
        }
    }

    private fun clear(context: Context) {
        val dbHelper = Database(context)
        dbHelper.clearAllAuth(context)
        (context.applicationContext as SFApplication).removeGrantedAccessIfNeeded()
    }
}