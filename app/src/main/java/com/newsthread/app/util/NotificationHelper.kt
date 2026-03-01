package com.newsthread.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.newsthread.app.R
import com.newsthread.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing system notifications.
 * Phase 10: Notifications & Updates
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "story_updates"
        const val CHANNEL_NAME = "Story Updates"
        const val CHANNEL_DESCRIPTION = "Notifications for updates to tracked stories"
        const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showNotification(title: String, body: String, storyId: String) {
        if (!hasPermission()) return

        // Create an Intent for the activity
        // Use simpler intent construction to ensure deep link data is preserved
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("newsthread://story/$storyId")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setPackage(context.packageName) // Ensure it stays within app
        }

        val resultPendingIntent = PendingIntent.getActivity(
            context,
            storyId.hashCode(), // Unique request code per story
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this resource exists, or use minimal icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(resultPendingIntent)
            .setAutoCancel(true)

        // Check if app is in foreground
        val isForeground = try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.STARTED
            )
        } catch (e: Exception) {
            false
        }

        if (isForeground) {
            // App is open, show non-intrusive Toast
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "$title: $body", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            // App is background, show system notification
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    with(NotificationManagerCompat.from(context)) {
                        notify(storyId.hashCode(), builder.build())
                    }
                }
            } else {
                with(NotificationManagerCompat.from(context)) {
                    notify(storyId.hashCode(), builder.build())
                }
            }
        }
    }
}
