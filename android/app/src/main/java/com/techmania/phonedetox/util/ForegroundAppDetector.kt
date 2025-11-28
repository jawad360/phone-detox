package com.techmania.phonedetox.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * Utility class for detecting the current foreground app
 */
object ForegroundAppDetector {
    fun getCurrentForegroundApp(usageStatsManager: UsageStatsManager?): String? {
        if (usageStatsManager == null) return null
        
        return try {
            val time = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(time - 2000, time)
            
            var foregroundApp: String? = null
            
            if (events != null) {
                val event = UsageEvents.Event()
                while (events.getNextEvent(event)) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        foregroundApp = event.packageName
                    }
                }
            }
            
            foregroundApp
        } catch (e: Exception) {
            null
        }
    }
}

