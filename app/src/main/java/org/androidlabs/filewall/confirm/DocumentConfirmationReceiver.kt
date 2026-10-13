package org.androidlabs.filewall.confirm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.androidlabs.filewall.MainActivity
import org.androidlabs.filewall.R
import org.androidlabs.filewall.database.Database
import org.androidlabs.filewall.logger.Log
import org.androidlabs.filewall.settings.SettingsKeys
import org.androidlabs.filewall.settings.dataStore

class DocumentConfirmationReceiver : BroadcastReceiver() {
    private val TAG = "DocumentConfirmationReceiver"
    companion object {
        private var lastReceiveTime: Long = 0L
        private const val DUPLICATE_INTERVAL_MS = 1000L
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("request", RequestData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("request")
        }

        Log.d(TAG, "onReceive: ${request.toString()}, $context")

        if (context != null && request != null) {
            val dbHelper = Database(context)
            val id = dbHelper.insertRequest(request)
            RequestsSignal.notifyChanged()
            //if (id==-1L) // already exists
                //return
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReceiveTime < DUPLICATE_INTERVAL_MS) {
                Log.d(TAG, "Duplicate broadcast ignored (within 1 second)")
                return
            }
            lastReceiveTime = currentTime
            //check silence mode
            val isSilenceEnabled = runBlocking {
                context.dataStore.data
                    .map { preferences ->
                        preferences[SettingsKeys.IS_SILENCE_MODE_ENABLED] == true
                    }
                    .first()
            }
            if (isSilenceEnabled) {
                Log.d(TAG, "silence mode enabled - no notification")
                return
            }
            Log.d(TAG, "SHOW TOAST:"+id.toString())

            val confirmationIntent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra("request", request)
                    putExtra("open_requests", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            val pintent = PendingIntent.getActivity(
                context,
                0,
                confirmationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = createNotification(context, pintent)
            val notificationManager = NotificationManagerCompat.from(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notificationManager.notify(19988, notification)

            Handler(Looper.getMainLooper()).postDelayed({
                Toast.makeText(
                    context,
                    "Please add rule in app! Click app notification",
                    Toast.LENGTH_LONG
                ).show()
            }, 500)

        }
    }

    private fun createNotification(context: Context, intent: PendingIntent): Notification {
        val notificationChannelId = "DOCUMENT_CONFIRMATION_CHANNEL"
        val channel = NotificationChannel(
            notificationChannelId,
            "Document Confirmation",
            NotificationManager.IMPORTANCE_DEFAULT //IMPORTANCE_HIGH
        ).apply {
            lightColor = Color.TRANSPARENT
            setShowBadge(false)
        }
        val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(context, notificationChannelId)
            .setContentIntent(intent)
            .setContentTitle("Access Request")
            .setContentText("Please confirm")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) //PRIORITY_HIGH)
            .setAutoCancel(false)
            .setColor(ContextCompat.getColor(context, android.R.color.transparent))
            .setColorized(false)
            .build()
    }
}