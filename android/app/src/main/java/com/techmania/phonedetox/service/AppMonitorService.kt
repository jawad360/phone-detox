package com.techmania.phonedetox.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.techmania.phonedetox.model.AppSession
import com.techmania.phonedetox.repository.*
import com.techmania.phonedetox.ui.dialog.CoolingPeriodDialog
import com.techmania.phonedetox.ui.dialog.TimeSelectionDialog
import com.techmania.phonedetox.util.*

class AppMonitorService : Service() {
    private var usageStatsManager: UsageStatsManager? = null
    private var handler: Handler? = null
    private var blockingHandler: Handler? = null
    private var isMonitoring = false
    private var lastForegroundApp: String? = null
    private var reactContext: ReactApplicationContext? = null
    private var pendingTimeUpChecks: MutableSet<String> = mutableSetOf()
    
    // Repositories
    private lateinit var sessionRepository: AppSessionRepository
    private lateinit var configRepository: AppConfigRepository
    private lateinit var coolingPeriodRepository: CoolingPeriodRepository
    private lateinit var monitoredAppsRepository: MonitoredAppsRepository

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHECK_INTERVAL: Long = 1000
        private const val BLOCKING_CHECK_INTERVAL: Long = 2000
        
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_UPDATE_MONITORED_APPS = "UPDATE_MONITORED_APPS"
        const val EXTRA_APPS = "apps"
        
        private var instance: AppMonitorService? = null
        fun getInstance(): AppMonitorService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize repositories
        sessionRepository = AppSessionRepository()
        configRepository = AppConfigRepository()
        coolingPeriodRepository = CoolingPeriodRepository()
        monitoredAppsRepository = MonitoredAppsRepository()
        
        // Initialize services
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        blockingHandler = Handler(Looper.getMainLooper())
        
        // Setup notifications
        NotificationHelper.createNotificationChannel(this)
        
        // Start foreground service
        startForegroundService()
        
        // Start blocking check loop
        startBlockingCheck()
        
        // Auto-start monitoring when service is created
        startMonitoring()
        
        Log.d(TAG, "AppMonitorService created and monitoring started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_MONITORED_APPS -> {
                val apps = intent.getStringArrayListExtra(EXTRA_APPS) ?: emptyList()
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
        monitoredAppsRepository.setMonitoredApps(apps)
        Log.d(TAG, "Updated monitored apps: ${apps.size}")
    }

    fun updateAppConfig(packageName: String, config: Map<String, Any>) {
        val behavior = config["behavior"] as? String
        val coolingPeriodMinutes = (config["coolingPeriodMinutes"] as? Number)?.toInt()
        configRepository.setConfig(packageName, behavior, coolingPeriodMinutes)
        Log.d(TAG, "Updated config for $packageName: behavior=$behavior, coolingPeriodMinutes=$coolingPeriodMinutes")
    }

    fun setCoolingPeriodEnd(packageName: String, endTime: Long) {
        coolingPeriodRepository.setCoolingPeriodEnd(packageName, endTime)
        Log.d(TAG, "Set cooling period for $packageName until $endTime")
    }

    fun getActiveSession(packageName: String): AppSession? {
        return sessionRepository.getSession(packageName)
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NotificationHelper.getNotificationId(), NotificationHelper.createNotification(this), serviceType)
            } catch (e: Exception) {
                startForeground(NotificationHelper.getNotificationId(), NotificationHelper.createNotification(this))
            }
        } else {
            startForeground(NotificationHelper.getNotificationId(), NotificationHelper.createNotification(this))
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Starting app monitoring")
        
        handler?.post(object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    checkForegroundApp()
                    handler?.postDelayed(this, CHECK_INTERVAL)
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
        val events = usageStatsManager?.queryEvents(time - CHECK_INTERVAL * 2, time)
        
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

        // Check expired sessions
        checkAllActiveSessions(foregroundApp)
        
        if (foregroundApp != null) {
            // Check if app is blocked
            if (monitoredAppsRepository.isBlocked(foregroundApp)) {
                AppBlocker.blockApp(this, foregroundApp, "App is blocked")
                return
            }
            
            // Check if current foreground app has expired time
            if (monitoredAppsRepository.isMonitored(foregroundApp)) {
                val activeSession = sessionRepository.getSession(foregroundApp)
                if (activeSession != null) {
                    val elapsedMs = activeSession.getElapsedMs()
                    val requestedMs = activeSession.getRequestedMs()
                    
                    Log.d(TAG, "Checking session for $foregroundApp: elapsed=${elapsedMs}ms, requested=${requestedMs}ms")
                    
                    if (elapsedMs >= requestedMs) {
                        if (!pendingTimeUpChecks.contains(foregroundApp)) {
                            Log.d(TAG, "Time expired for $foregroundApp, handling time up (app is in foreground)")
                            pendingTimeUpChecks.add(foregroundApp)
                            handleTimeUp(foregroundApp, activeSession, isInForeground = true)
                        } else {
                            Log.d(TAG, "Time expired for $foregroundApp but already pending check")
                        }
                    } else {
                        pendingTimeUpChecks.remove(foregroundApp)
                    }
                }
            }
            
            // Check if app changed
            if (foregroundApp != lastForegroundApp) {
                lastForegroundApp = foregroundApp
                pendingTimeUpChecks.remove(foregroundApp)
                
                if (monitoredAppsRepository.isMonitored(foregroundApp)) {
                    handleMonitoredAppLaunch(foregroundApp)
                }
            }
        }
    }

    private fun handleMonitoredAppLaunch(packageName: String) {
        Log.d(TAG, "Monitored app launched: $packageName")
        Log.d(TAG, "Is blocked: ${monitoredAppsRepository.isBlocked(packageName)}")
        Log.d(TAG, "Is in cooling period: ${coolingPeriodRepository.isInCoolingPeriod(packageName)}")
        
        if (monitoredAppsRepository.isBlocked(packageName)) {
            Log.d(TAG, "App $packageName is blocked, blocking it")
            AppBlocker.blockApp(this, packageName, "App is blocked")
            return
        }
        
        if (coolingPeriodRepository.isInCoolingPeriod(packageName)) {
            val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(packageName) ?: return
            Log.d(TAG, "App $packageName is in cooling period until $coolingEnd")
            monitoredAppsRepository.blockApp(packageName)
            showCoolingPeriodDialog(packageName, coolingEnd)
            AppBlocker.blockApp(this, packageName, "App is in cooling period")
            return
        }

        val activeSession = sessionRepository.getSession(packageName)
        if (activeSession != null) {
            Log.d(TAG, "Active session found for $packageName: ${activeSession.requestedMinutes} minutes, expired: ${activeSession.isExpired()}")
            if (activeSession.isExpired()) {
                handleTimeUp(packageName, activeSession, isInForeground = true)
            } else {
                Log.d(TAG, "Session active for $packageName, ${activeSession.getRemainingMinutes()} minutes remaining")
                return
            }
        } else {
            Log.d(TAG, "No active session for $packageName, showing time selection dialog")
            showTimeSelectionDialog(packageName)
        }
    }

    private fun showTimeSelectionDialog(packageName: String) {
        Log.d(TAG, "showTimeSelectionDialog called for $packageName")
        // Ensure we're on the main thread for UI operations
        handler?.post {
            try {
                val appName = AppNameResolver.getAppName(this@AppMonitorService, packageName)
                Log.d(TAG, "App name resolved: $appName, showing dialog on main thread")
                TimeSelectionDialog.show(this@AppMonitorService, packageName, appName) { selectedMinutes ->
                    Log.d(TAG, "Time selected: $selectedMinutes minutes for $packageName")
                    if (selectedMinutes > 0) {
                        val config = configRepository.getConfig(packageName)
                        val behavior = config["behavior"] as? String ?: "ask"
                        
                        val session = AppSession(
                            packageName = packageName,
                            startTime = System.currentTimeMillis(),
                            requestedMinutes = selectedMinutes,
                            behavior = behavior
                        )
                        sessionRepository.addSession(session)
                        
                        sendEventToReactNative("sessionStarted", createSessionMap(session))
                        Log.d(TAG, "Started session for $packageName: $selectedMinutes minutes")
                    } else {
                        Log.d(TAG, "User cancelled time selection, blocking app")
                        AppBlocker.blockApp(this@AppMonitorService, packageName, "No time selected")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing time selection dialog for $packageName", e)
            }
        }
    }

    private fun handleTimeUp(packageName: String, session: AppSession, isInForeground: Boolean = false) {
        Log.d(TAG, "Time up for $packageName, behavior: ${session.behavior}, inForeground: $isInForeground")
        
        when (session.behavior) {
            "ask" -> {
                if (isInForeground && !monitoredAppsRepository.isBlocked(packageName)) {
                    Log.d(TAG, "Showing time extension dialog for $packageName (app is in foreground, behavior=ask)")
                    // Ensure we're on the main thread for UI operations
                    handler?.post {
                        try {
                            val appName = AppNameResolver.getAppName(this@AppMonitorService, packageName)
                            TimeSelectionDialog.show(this@AppMonitorService, packageName, appName, isExtension = true) { selectedMinutes ->
                                pendingTimeUpChecks.remove(packageName)
                                if (selectedMinutes > 0) {
                                    val updatedSession = session.copy(
                                        startTime = System.currentTimeMillis(),
                                        requestedMinutes = selectedMinutes
                                    )
                                    sessionRepository.updateSession(packageName, updatedSession)
                                    sendEventToReactNative("sessionExtended", createSessionMap(updatedSession))
                                    Log.d(TAG, "Session extended for $packageName: $selectedMinutes minutes")
                                } else {
                                    monitoredAppsRepository.blockApp(packageName)
                                    AppBlocker.blockApp(this@AppMonitorService, packageName, "User declined more time")
                                    blockAppAndStartCooling(packageName, session)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error showing time extension dialog for $packageName", e)
                        }
                    }
                } else {
                    Log.d(TAG, "App $packageName not in foreground or already blocked, blocking immediately")
                    pendingTimeUpChecks.remove(packageName)
                    monitoredAppsRepository.blockApp(packageName)
                    AppBlocker.blockApp(this, packageName, "Time limit reached - app not in foreground")
                    blockAppAndStartCooling(packageName, session)
                }
            }
            "stop" -> {
                pendingTimeUpChecks.remove(packageName)
                sessionRepository.removeSession(packageName)
                monitoredAppsRepository.blockApp(packageName)
                AppBlocker.blockApp(this, packageName, "Time limit reached")
                
                blockingHandler?.postDelayed({
                    val currentApp = ForegroundAppDetector.getCurrentForegroundApp(usageStatsManager)
                    if (currentApp == packageName) {
                        Log.d(TAG, "App $packageName still in foreground after blocking, re-blocking")
                        AppBlocker.blockApp(this, packageName, "Time limit reached - re-blocking")
                    }
                }, 500)
                
                blockAppAndStartCooling(packageName, session)
            }
        }
    }

    private fun blockAppAndStartCooling(packageName: String, session: AppSession) {
        val config = configRepository.getConfig(packageName)
        val coolingMinutes = (config["coolingPeriodMinutes"] as? Number)?.toInt() ?: 30
        
        val coolingEnd = System.currentTimeMillis() + (coolingMinutes * 60 * 1000)
        coolingPeriodRepository.setCoolingPeriodEnd(packageName, coolingEnd)
        sessionRepository.removeSession(packageName)
        monitoredAppsRepository.blockApp(packageName)
        
        showCoolingPeriodDialog(packageName, coolingEnd)
        
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

    private fun startBlockingCheck() {
        blockingHandler?.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val currentForeground = ForegroundAppDetector.getCurrentForegroundApp(usageStatsManager)
                
                // Check for expired sessions
                sessionRepository.getAllSessions().forEach { (packageName, session) ->
                    val elapsedMs = session.getElapsedMs()
                    val requestedMs = session.getRequestedMs()
                    
                    if (elapsedMs >= requestedMs && !monitoredAppsRepository.isBlocked(packageName)) {
                        val isInForeground = currentForeground == packageName
                        Log.d(TAG, "Blocking check: Time expired for $packageName, in foreground: $isInForeground")
                        
                        if (!pendingTimeUpChecks.contains(packageName)) {
                            pendingTimeUpChecks.add(packageName)
                            handleTimeUp(packageName, session, isInForeground = isInForeground)
                        }
                    }
                }
                
                // Check if any blocked app is currently in foreground
                if (currentForeground != null && monitoredAppsRepository.isBlocked(currentForeground)) {
                    val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(currentForeground)
                    if (coolingEnd != null && coolingEnd > now) {
                        showCoolingPeriodDialog(currentForeground, coolingEnd)
                    }
                    AppBlocker.blockApp(this@AppMonitorService, currentForeground, "App is blocked - continuous enforcement")
                }
                
                // Check cooling periods - unblock if cooling period ended
                val appsToUnblock = mutableListOf<String>()
                monitoredAppsRepository.getBlockedApps().forEach { packageName ->
                    val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(packageName)
                    if (coolingEnd == null || coolingEnd <= now) {
                        val session = sessionRepository.getSession(packageName)
                        if (session == null) {
                            appsToUnblock.add(packageName)
                        } else {
                            val elapsedMinutes = session.getElapsedMinutes()
                            if (elapsedMinutes < session.requestedMinutes) {
                                appsToUnblock.add(packageName)
                            }
                        }
                    }
                }
                
                appsToUnblock.forEach { packageName ->
                    monitoredAppsRepository.unblockApp(packageName)
                    Log.d(TAG, "Unblocked app $packageName")
                }
                
                blockingHandler?.postDelayed(this, BLOCKING_CHECK_INTERVAL)
            }
        })
    }

    private fun checkAllActiveSessions(currentForegroundApp: String? = null) {
        val now = System.currentTimeMillis()
        val expiredSessions = mutableListOf<Pair<String, AppSession>>()
        
        sessionRepository.getAllSessions().forEach { (packageName, session) ->
            if (session.isExpired()) {
                expiredSessions.add(Pair(packageName, session))
                Log.d(TAG, "Session expired for $packageName: ${session.getElapsedMinutes()}m elapsed (limit: ${session.requestedMinutes}m)")
            }
        }
        
        expiredSessions.forEach { (packageName, session) ->
            if (currentForegroundApp == packageName) {
                if (!pendingTimeUpChecks.contains(packageName)) {
                    Log.d(TAG, "Handling expired session for foreground app $packageName")
                    pendingTimeUpChecks.add(packageName)
                    handleTimeUp(packageName, session, isInForeground = true)
                }
            } else {
                Log.d(TAG, "Session expired for closed app $packageName, blocking without dialog")
                if (!pendingTimeUpChecks.contains(packageName)) {
                    pendingTimeUpChecks.add(packageName)
                    if (session.behavior == "ask") {
                        monitoredAppsRepository.blockApp(packageName)
                        blockAppAndStartCooling(packageName, session)
                    } else {
                        sessionRepository.removeSession(packageName)
                        monitoredAppsRepository.blockApp(packageName)
                        blockAppAndStartCooling(packageName, session)
                    }
                }
            }
        }
    }

    private fun showCoolingPeriodDialog(packageName: String, coolingEndTime: Long) {
        val appName = AppNameResolver.getAppName(this, packageName)
        CoolingPeriodDialog.show(this, packageName, appName, coolingEndTime) {
            AppBlocker.blockApp(this, packageName, "User dismissed cooling period dialog")
        }
        
        val remainingTime = coolingEndTime - System.currentTimeMillis()
        if (remainingTime > 0) {
            blockingHandler?.postDelayed({
                CoolingPeriodDialog.dismiss()
                monitoredAppsRepository.unblockApp(packageName)
                coolingPeriodRepository.clearCoolingPeriod(packageName)
                Log.d(TAG, "Cooling period ended for $packageName")
            }, remainingTime)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopMonitoring()
        Log.d(TAG, "AppMonitorService destroyed")
    }
}

