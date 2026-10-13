package org.androidlabs.filewall.auth.pin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.androidlabs.filewall.logger.Log

class PinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("PinReceiver", "onReceive: $context")

        val confirmationIntent =
            Intent(context, PinActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Only forward the extra when there is one: passing null through makes the
                // activity fall back to its default rather than receive an empty string.
                intent?.getStringExtra("purpose")?.let { putExtra("purpose", it) }
            }
        context!!.startActivity(confirmationIntent)
    }
}