package com.techmania.phonedetox

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

class AppMonitorService : Service() {
    private var usageStatsManager: UsageStatsManager? = null
    private var handler: Handler? = null
    private var isMonitoring = false
    private var lastForegroundApp: String? = null
    private var checkInterval: Long = 1000 // Check every second
    private var monitoredApps: Set<String> = emptySet()
    private var activeSessions: MutableMap<String, AppSession> = mutableMapOf()
    private var reactContext: ReactApplicationContext? = null
    private var appConfigs: MutableMap<String, Map<String, Any>> = mutableMapOf()
    private var coolingPeriods: MutableMap<String, Long> = mutableMapOf()
    private var pendingTimeUpChecks: MutableSet<String> = mutableSetOf() // Track apps that are waiting for time up dialog
    private var blockedApps: MutableSet<String> = mutableSetOf() // Track apps that should be blocked
    private var blockingHandler: Handler? = null

    companion object {
        private const val TAG = "AppMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "app_monitor_channel"
        private var instance: AppMonitorService? = null

        fun getInstance(): AppMonitorService? = instance
    }

    data class AppSession(
        val packageName: String,
        val startTime: Long,
        val requestedMinutes: Int,
        val behavior: String // "ask" or "stop"
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        blockingHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        
        // Start foreground service - the type is declared in AndroidManifest.xml
        // For Android 14+ (API 34+), we need to specify the service type
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, createNotification(), serviceType)
            } catch (e: Exception) {
                // Fallback for older API or if method signature differs
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Start continuous blocking check
        startBlockingCheck()
        
        Log.d(TAG, "AppMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITORING" -> {
                startMonitoring()
            }
            "STOP_MONITORING" -> {
                stopMonitoring()
            }
            "UPDATE_MONITORED_APPS" -> {
                val apps = intent.getStringArrayListExtra("apps") ?: emptyList()
                updateMonitoredApps(apps.toSet())
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun setReactContext(context: ReactApplicationContext) {
        this.reactContext = context
    }

    fun updateMonitoredApps(apps: Set<String>) {
        monitoredApps = apps
        Log.d(TAG, "Updated monitored apps: ${apps.size}")
    }

    fun updateAppConfig(packageName: String, config: Map<String, Any>) {
        appConfigs[packageName] = config
        Log.d(TAG, "Updated config for $packageName: $config")
    }

    fun setCoolingPeriodEnd(packageName: String, endTime: Long) {
        coolingPeriods[packageName] = endTime
        Log.d(TAG, "Set cooling period for $packageName until $endTime")
    }

    fun getActiveSession(packageName: String): AppSession? {
        return activeSessions[packageName]
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Starting app monitoring")
        
        handler?.post(object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    checkForegroundApp()
                    handler?.postDelayed(this, checkInterval)
                }
            }
        })
    }

    private fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "Stopping app monitoring")
    }

    private fun checkForegroundApp() {
        if (usageStatsManager == null) return

        val time = System.currentTimeMillis()
        val events = usageStatsManager?.queryEvents(time - checkInterval * 2, time)
        
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

        // Always check all active sessions for expiration (even if app is closed/minimized)
        // This will handle expired sessions for foreground apps immediately
        checkAllActiveSessions(foregroundApp)
        
        if (foregroundApp != null) {
            // Check if app is blocked (time expired or in cooling period)
            if (blockedApps.contains(foregroundApp)) {
                // App should be blocked, force it closed
                blockApp(foregroundApp, "App is blocked")
                return
            }
            
            // Always check if current foreground app has expired time
            // Remove the pendingTimeUpChecks check to allow continuous monitoring
            if (monitoredApps.contains(foregroundApp)) {
                val activeSession = activeSessions[foregroundApp]
                if (activeSession != null) {
                    val elapsedMs = System.currentTimeMillis() - activeSession.startTime
                    val elapsedMinutes = elapsedMs / 60000
                    val requestedMs = activeSession.requestedMinutes * 60000L
                    
                    Log.d(TAG, "Checking session for $foregroundApp: elapsed=${elapsedMs}ms (${elapsedMinutes}m), requested=${requestedMs}ms (${activeSession.requestedMinutes}m)")
                    
                    if (elapsedMs >= requestedMs) {
                        // Time is up, enforce behavior (only if not already pending)
                        if (!pendingTimeUpChecks.contains(foregroundApp)) {
                            Log.d(TAG, "Time expired for $foregroundApp, handling time up (app is in foreground)")
                            pendingTimeUpChecks.add(foregroundApp)
                            handleTimeUp(foregroundApp, activeSession, isInForeground = true)
                        } else {
                            Log.d(TAG, "Time expired for $foregroundApp but already pending check")
                        }
                    } else {
                        // Time not expired yet, remove from pending if it was there
                        pendingTimeUpChecks.remove(foregroundApp)
                    }
                }
            }
            
            // Check if app changed
            if (foregroundApp != lastForegroundApp) {
                lastForegroundApp = foregroundApp
                pendingTimeUpChecks.remove(foregroundApp) // Reset check flag for new app
                
                // Check if this is a monitored app
                if (monitoredApps.contains(foregroundApp)) {
                    handleMonitoredAppLaunch(foregroundApp)
                }
            }
        }
    }

    private fun handleMonitoredAppLaunch(packageName: String) {
        Log.d(TAG, "Monitored app launched: $packageName")
        
        // Check if app is blocked
        if (blockedApps.contains(packageName)) {
            blockApp(packageName, "App is blocked")
            return
        }
        
        // Check if app is in cooling period
        val coolingEnd = getCoolingPeriodEnd(packageName)
        if (coolingEnd != null && coolingEnd > System.currentTimeMillis()) {
            // App is in cooling period, show cooling period dialog and block app
            blockedApps.add(packageName)
            showCoolingPeriodDialog(packageName, coolingEnd)
            blockApp(packageName, "App is in cooling period")
            return
        }

        // Check if there's an active session
        val activeSession = activeSessions[packageName]
        if (activeSession != null) {
            val elapsedMinutes = (System.currentTimeMillis() - activeSession.startTime) / 60000
            val remainingMinutes = activeSession.requestedMinutes - elapsedMinutes
            
            Log.d(TAG, "Active session found for $packageName: $elapsedMinutes minutes elapsed, $remainingMinutes minutes remaining")
            
            if (elapsedMinutes >= activeSession.requestedMinutes) {
                // Time is up - enforce behavior immediately
                Log.d(TAG, "Time limit reached for $packageName, enforcing behavior: ${activeSession.behavior}")
                handleTimeUp(packageName, activeSession)
            } else {
                // Session is still active, timer continues running
                // User can use the app, timer keeps counting in background
                Log.d(TAG, "Session active for $packageName, $remainingMinutes minutes remaining")
                return
            }
        } else {
            // New launch, show time selection dialog
            showTimeSelectionDialog(packageName)
        }
    }

    private fun showTimeSelectionDialog(packageName: String) {
        val appName = getAppName(packageName)
        TimeSelectionDialog.show(this, packageName, appName) { selectedMinutes ->
            if (selectedMinutes > 0) {
                // User selected time, start session
                val appConfig = getAppConfig(packageName)
                val behavior = appConfig?.get("behavior") as? String ?: "ask"
                
                val session = AppSession(
                    packageName = packageName,
                    startTime = System.currentTimeMillis(),
                    requestedMinutes = selectedMinutes,
                    behavior = behavior
                )
                activeSessions[packageName] = session
                
                // Send event to React Native
                sendEventToReactNative("sessionStarted", createSessionMap(session))
                
                Log.d(TAG, "Started session for $packageName: $selectedMinutes minutes")
            } else {
                // User cancelled, block the app
                blockApp(packageName, "No time selected")
            }
        }
    }

    private fun handleTimeUp(packageName: String, session: AppSession, isInForeground: Boolean = false) {
        Log.d(TAG, "Time up for $packageName, behavior: ${session.behavior}, elapsed: ${(System.currentTimeMillis() - session.startTime) / 60000} minutes, inForeground: $isInForeground")
        
        when (session.behavior) {
            "ask" -> {
                if (isInForeground && !blockedApps.contains(packageName)) {
                    // App is in foreground and not already blocked - show dialog to ask for more time
                    Log.d(TAG, "Showing time extension dialog for $packageName (app is in foreground, behavior=ask)")
                    val appName = getAppName(packageName)
                    TimeSelectionDialog.show(this, packageName, appName, isExtension = true) { selectedMinutes ->
                        pendingTimeUpChecks.remove(packageName)
                        if (selectedMinutes > 0) {
                            // User granted more time - extend session (app is not blocked)
                            activeSessions[packageName] = session.copy(
                                startTime = System.currentTimeMillis(),
                                requestedMinutes = selectedMinutes
                            )
                            sendEventToReactNative("sessionExtended", createSessionMap(activeSessions[packageName]!!))
                            Log.d(TAG, "Session extended for $packageName: $selectedMinutes minutes")
                        } else {
                            // User declined or cancelled - block app and start cooling period
                            blockedApps.add(packageName)
                            blockApp(packageName, "User declined more time")
                            blockAppAndStartCooling(packageName, session)
                        }
                    }
                } else {
                    // App is not in foreground or already blocked - block immediately and start cooling period
                    if (blockedApps.contains(packageName)) {
                        Log.d(TAG, "App $packageName already blocked, skipping dialog")
                    } else {
                        Log.d(TAG, "App $packageName not in foreground, blocking immediately without asking")
                    }
                    pendingTimeUpChecks.remove(packageName)
                    blockedApps.add(packageName)
                    blockApp(packageName, "Time limit reached - app not in foreground or already blocked")
                    blockAppAndStartCooling(packageName, session)
                }
            }
            "stop" -> {
                // Stop immediately and start cooling period
                pendingTimeUpChecks.remove(packageName)
                // Remove active session immediately
                activeSessions.remove(packageName)
                // Mark as blocked first
                blockedApps.add(packageName)
                // Block the app aggressively and immediately
                blockApp(packageName, "Time limit reached")
                // Also block again after a short delay to ensure it stays closed
                blockingHandler?.postDelayed({
                    val currentApp = getCurrentForegroundApp()
                    if (currentApp == packageName) {
                        Log.d(TAG, "App $packageName still in foreground after blocking, re-blocking")
                        blockApp(packageName, "Time limit reached - re-blocking")
                    }
                }, 500)
                // Start cooling period (this will also show the cooling dialog)
                blockAppAndStartCooling(packageName, session)
            }
        }
    }

    private fun blockAppAndStartCooling(packageName: String, session: AppSession) {
        val appConfig = getAppConfig(packageName)
        val coolingMinutes = (appConfig?.get("coolingPeriodMinutes") as? Number)?.toInt() ?: 30
        
        val coolingEnd = System.currentTimeMillis() + (coolingMinutes * 60 * 1000)
        setCoolingPeriodEnd(packageName, coolingEnd)
        
        activeSessions.remove(packageName)
        // App is already blocked, keep it blocked during cooling period
        blockedApps.add(packageName)
        
        // Show cooling period dialog
        showCoolingPeriodDialog(packageName, coolingEnd)
        
        // Notify React Native to save cooling period
        sendEventToReactNative("setCoolingPeriod", Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("endTime", coolingEnd.toDouble())
        })
        
        sendEventToReactNative("coolingPeriodStarted", Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("coolingEndTime", coolingEnd.toDouble())
            putInt("coolingMinutes", coolingMinutes)
        })
        
        Log.d(TAG, "Started cooling period for $packageName: $coolingMinutes minutes")
    }

    private fun blockApp(packageName: String, reason: String) {
        try {
            Log.d(TAG, "Blocking app $packageName: $reason")
            
            // Method 1: Move app to background by launching home screen (most reliable)
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            startActivity(homeIntent)
            
            // Method 2: Try to kill the app process immediately
            try {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                
                // Get running app processes
                val runningApps = activityManager.runningAppProcesses
                runningApps?.forEach { processInfo ->
                    // Check if this process belongs to the package
                    processInfo.pkgList?.forEach { pkg ->
                        if (pkg == packageName) {
                            // Kill the process
                            try {
                                android.os.Process.killProcess(processInfo.pid)
                                Log.d(TAG, "Killed process for $packageName (PID: ${processInfo.pid})")
                            } catch (e: Exception) {
                                Log.d(TAG, "Could not kill process ${processInfo.pid}: ${e.message}")
                            }
                        }
                    }
                }
                
                // Additional blocking: Send app to background again after a short delay
                // This helps if the app tries to reopen immediately
                blockingHandler?.postDelayed({
                    val currentApp = getCurrentForegroundApp()
                    if (currentApp == packageName) {
                        // App is still in foreground, block again
                        val homeIntent = Intent(Intent.ACTION_MAIN)
                        homeIntent.addCategory(Intent.CATEGORY_HOME)
                        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(homeIntent)
                        Log.d(TAG, "Re-blocked app $packageName (still in foreground)")
                    }
                }, 200) // Check after 200ms
            } catch (e: Exception) {
                Log.d(TAG, "Could not kill app process (may require special permissions): ${e.message}")
            }
            
            // Method 3: Try to force stop using ActivityManager (requires system permission)
            try {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val method = activityManager.javaClass.getDeclaredMethod("forceStopPackage", String::class.java)
                method.isAccessible = true
                method.invoke(activityManager, packageName)
                Log.d(TAG, "Force stopped app $packageName")
            } catch (e: Exception) {
                Log.d(TAG, "Could not force stop app (expected without system permissions): ${e.message}")
            }
            
            Log.d(TAG, "Successfully blocked app $packageName: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking app $packageName: ${e.message}", e)
        }
    }
    
    private fun startBlockingCheck() {
        // Continuously check and block apps that should be blocked
        blockingHandler?.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val appsToUnblock = mutableListOf<String>()
                
                // Get current foreground app first
                val currentForeground = getCurrentForegroundApp()
                
                // First, check for expired sessions that need to be blocked
                activeSessions.forEach { (packageName, session) ->
                    val elapsedMs = now - session.startTime
                    val elapsedMinutes = elapsedMs / 60000
                    val requestedMs = session.requestedMinutes * 60000L
                    
                    if (elapsedMs >= requestedMs && !blockedApps.contains(packageName)) {
                        // Time expired but app not blocked yet
                        // Only show dialog if app is in foreground, otherwise block immediately
                        val isInForeground = currentForeground == packageName
                        Log.d(TAG, "Blocking check: Time expired for $packageName (${elapsedMs}ms >= ${requestedMs}ms), in foreground: $isInForeground")
                        
                        if (!pendingTimeUpChecks.contains(packageName)) {
                            pendingTimeUpChecks.add(packageName)
                            // Pass the foreground status to handleTimeUp
                            handleTimeUp(packageName, session, isInForeground = isInForeground)
                        } else {
                            Log.d(TAG, "Blocking check: $packageName already pending time up check")
                        }
                    }
                }
                
                // Check if any blocked app is currently in foreground and block it
                if (currentForeground != null && blockedApps.contains(currentForeground)) {
                    // App is blocked and in foreground - check if it's in cooling period
                    val coolingEnd = getCoolingPeriodEnd(currentForeground)
                    if (coolingEnd != null && coolingEnd > now) {
                        // App is in cooling period - show dialog
                        showCoolingPeriodDialog(currentForeground, coolingEnd)
                    }
                    // Force close it immediately and aggressively
                    blockApp(currentForeground, "App is blocked - continuous enforcement")
                }
                
                // Check cooling periods - unblock if cooling period ended
                blockedApps.forEach { packageName ->
                    val coolingEnd = getCoolingPeriodEnd(packageName)
                    if (coolingEnd == null || coolingEnd <= now) {
                        // Cooling period ended or no cooling period
                        // Check if there's an active session
                        val session = activeSessions[packageName]
                        if (session == null) {
                            // No active session, can unblock
                            appsToUnblock.add(packageName)
                        } else {
                            // Check if session time is still valid
                            val elapsedMinutes = (now - session.startTime) / 60000
                            if (elapsedMinutes < session.requestedMinutes) {
                                // Session still valid, unblock
                                appsToUnblock.add(packageName)
                            }
                        }
                    }
                }
                
                appsToUnblock.forEach { packageName ->
                    blockedApps.remove(packageName)
                    Log.d(TAG, "Unblocked app $packageName")
                }
                
                // Schedule next check
                blockingHandler?.postDelayed(this, 2000) // Check every 2 seconds
            }
        })
    }
    
    private fun getCurrentForegroundApp(): String? {
        if (usageStatsManager == null) return null
        
        try {
            val time = System.currentTimeMillis()
            val events = usageStatsManager?.queryEvents(time - 2000, time)
            
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
            
            return foregroundApp
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app: ${e.message}", e)
            return null
        }
    }

    private fun checkAllActiveSessions(currentForegroundApp: String? = null) {
        // Check all active sessions to see if time has expired
        // This ensures timer continues even when app is closed/minimized
        val now = System.currentTimeMillis()
        val expiredSessions = mutableListOf<Pair<String, AppSession>>()
        
        activeSessions.forEach { (packageName, session) ->
            val elapsedMs = now - session.startTime
            val elapsedMinutes = elapsedMs / 60000
            val requestedMs = session.requestedMinutes * 60000L
            
            if (elapsedMs >= requestedMs) {
                // Time has expired - mark for handling
                expiredSessions.add(Pair(packageName, session))
                Log.d(TAG, "Session expired for $packageName: ${elapsedMs}ms (${elapsedMinutes}m) elapsed, limit: ${requestedMs}ms (${session.requestedMinutes}m)")
            }
        }
        
        // Handle expired sessions
        expiredSessions.forEach { (packageName, session) ->
            if (currentForegroundApp == packageName) {
                // App is currently in foreground and time expired - handle immediately
                if (!pendingTimeUpChecks.contains(packageName)) {
                    Log.d(TAG, "Handling expired session for foreground app $packageName")
                    pendingTimeUpChecks.add(packageName)
                    handleTimeUp(packageName, session, isInForeground = true)
                } else {
                    Log.d(TAG, "Expired session for foreground app $packageName already pending")
                }
            } else {
                // App is not in foreground, but time expired - block immediately without showing dialog
                // The session will be enforced when the app is launched again
                Log.d(TAG, "Session expired for closed app $packageName, blocking without dialog")
                if (!pendingTimeUpChecks.contains(packageName)) {
                    pendingTimeUpChecks.add(packageName)
                    // For apps not in foreground, block immediately without asking
                    handleTimeUp(packageName, session, isInForeground = false)
                }
            }
        }
    }
    
    private fun checkActiveSessions(currentPackage: String) {
        // Legacy method - kept for compatibility but not actively used
        checkAllActiveSessions()
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    private fun showCoolingPeriodDialog(packageName: String, coolingEndTime: Long) {
        val appName = getAppName(packageName)
        CoolingPeriodDialog.show(this, packageName, appName, coolingEndTime) {
            // On OK clicked: close the app if it's open
            blockApp(packageName, "User dismissed cooling period dialog")
        }
        
        // The dialog will auto-dismiss when cooling period ends (handled by the dialog's timer)
        // Also schedule cleanup when cooling period ends
        val remainingTime = coolingEndTime - System.currentTimeMillis()
        if (remainingTime > 0) {
            blockingHandler?.postDelayed({
                CoolingPeriodDialog.dismiss()
                blockedApps.remove(packageName)
                coolingPeriods.remove(packageName)
                Log.d(TAG, "Cooling period ended for $packageName")
            }, remainingTime)
        }
    }

    private fun getAppConfig(packageName: String): Map<String, Any>? {
        return appConfigs[packageName] ?: mapOf(
            "behavior" to "ask",
            "coolingPeriodMinutes" to 30
        )
    }

    private fun getCoolingPeriodEnd(packageName: String): Long? {
        return coolingPeriods[packageName]
    }

    private fun createSessionMap(session: AppSession): WritableMap {
        return Arguments.createMap().apply {
            putString("packageName", session.packageName)
            putDouble("startTime", session.startTime.toDouble())
            putInt("requestedMinutes", session.requestedMinutes)
            putString("behavior", session.behavior)
        }
    }

    private fun sendEventToReactNative(eventName: String, params: WritableMap) {
        reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage and shows time selection dialogs"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Detox")
            .setContentText("Monitoring app usage")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopMonitoring()
        Log.d(TAG, "AppMonitorService destroyed")
    }
}

