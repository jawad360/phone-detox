package com.techmania.phonedetox.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Helper class for creating and managing notifications
 */
object NotificationHelper {
    private const val CHANNEL_ID = "app_monitor_channel"
    private const val CHANNEL_NAME = "App Monitor Service"
    private const val NOTIFICATION_ID = 1001
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage and shows time selection dialogs"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun createNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Phone Detox")
            .setContentText("Monitoring app usage")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
    
    fun getNotificationId(): Int = NOTIFICATION_ID
}

