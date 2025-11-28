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
    // Constants
    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHECK_INTERVAL: Long = 1000
        private const val BLOCKING_CHECK_INTERVAL: Long = 2000
        private const val RE_BLOCK_DELAY_MS: Long = 500
        
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_UPDATE_MONITORED_APPS = "UPDATE_MONITORED_APPS"
        const val EXTRA_APPS = "apps"
        
        // Behavior types
        private const val BEHAVIOR_ASK = "ask"
        private const val BEHAVIOR_STOP = "stop"
        
        // React Native event names
        private const val EVENT_SESSION_STARTED = "sessionStarted"
        private const val EVENT_SESSION_EXTENDED = "sessionExtended"
        private const val EVENT_SET_COOLING_PERIOD = "setCoolingPeriod"
        private const val EVENT_COOLING_PERIOD_STARTED = "coolingPeriodStarted"
        
        // Default cooling period minutes
        private const val DEFAULT_COOLING_MINUTES = 30
        
        private var instance: AppMonitorService? = null
        fun getInstance(): AppMonitorService? = instance
    }

    // State
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

    // Lifecycle
    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeRepositories()
        initializeServices()
        setupNotifications()
        startForegroundService()
        startBlockingCheck()
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

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopMonitoring()
        Log.d(TAG, "AppMonitorService destroyed")
    }

    // Public API
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

    fun getCoolingPeriod(packageName: String): Long? {
        return coolingPeriodRepository.getCoolingPeriodEnd(packageName)
    }

    // Initialization
    private fun initializeRepositories() {
        sessionRepository = AppSessionRepository()
        configRepository = AppConfigRepository()
        coolingPeriodRepository = CoolingPeriodRepository()
        monitoredAppsRepository = MonitoredAppsRepository()
    }

    private fun initializeServices() {
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        blockingHandler = Handler(Looper.getMainLooper())
    }

    private fun setupNotifications() {
        NotificationHelper.createNotificationChannel(this)
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

    // Monitoring
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

    // Foreground App Detection
    private fun checkForegroundApp() {
        if (usageStatsManager == null) return

        val foregroundApp = detectForegroundApp()
        Log.d(TAG, "Launched app: $foregroundApp")

        checkAllActiveSessions(foregroundApp)
        
        if (foregroundApp != null) {
            handleForegroundApp(foregroundApp)
        }
    }

    private fun detectForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager?.queryEvents(time - CHECK_INTERVAL * 2, time)
        
        if (events == null) return null
        
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                return event.packageName
            }
        }
        return null
    }

    private fun handleForegroundApp(packageName: String) {
        if (handleBlockedApp(packageName)) return
        checkExpiredSessionInForeground(packageName)
        handleAppChange(packageName)
    }

    private fun handleBlockedApp(packageName: String): Boolean {
        if (!monitoredAppsRepository.isBlocked(packageName)) return false
        
        if (coolingPeriodRepository.isInCoolingPeriod(packageName)) {
            val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(packageName) ?: return true
            Log.d(TAG, "App $packageName is in cooling period until $coolingEnd")
            showCoolingPeriodDialog(packageName, coolingEnd)
        }
        AppBlocker.blockApp(this, packageName, "App is blocked")
        return true
    }

    private fun checkExpiredSessionInForeground(packageName: String) {
        if (!monitoredAppsRepository.isMonitored(packageName)) return
        
        val activeSession = sessionRepository.getSession(packageName) ?: return
        
        val elapsedMs = activeSession.getElapsedMs()
        val requestedMs = activeSession.getRequestedMs()
        
        Log.d(TAG, "Checking session for $packageName: elapsed=${elapsedMs}ms, requested=${requestedMs}ms")
        
        if (elapsedMs >= requestedMs) {
            if (!pendingTimeUpChecks.contains(packageName)) {
                Log.d(TAG, "Time expired for $packageName, handling time up (app is in foreground)")
                pendingTimeUpChecks.add(packageName)
                handleTimeUp(packageName, activeSession, isInForeground = true)
            } else {
                Log.d(TAG, "Time expired for $packageName but already pending check")
            }
        } else {
            pendingTimeUpChecks.remove(packageName)
        }
    }

    private fun handleAppChange(packageName: String) {
        if (packageName == lastForegroundApp) return
        
        lastForegroundApp = packageName
        pendingTimeUpChecks.remove(packageName)
        
        if (monitoredAppsRepository.isMonitored(packageName)) {
            handleMonitoredAppLaunch(packageName)
        }
    }

    // App Launch Handling
    private fun handleMonitoredAppLaunch(packageName: String) {
        Log.d(TAG, "Monitored app launched: $packageName")
        logAppState(packageName)
        
        if (handleBlockedAppOnLaunch(packageName)) return
        if (handleCoolingPeriodOnLaunch(packageName)) return
        handleSessionOnLaunch(packageName)
    }

    private fun logAppState(packageName: String) {
        Log.d(TAG, "Is blocked: ${monitoredAppsRepository.isBlocked(packageName)}")
        Log.d(TAG, "Is in cooling period: ${coolingPeriodRepository.isInCoolingPeriod(packageName)}")
    }

    private fun handleBlockedAppOnLaunch(packageName: String): Boolean {
        if (!monitoredAppsRepository.isBlocked(packageName)) return false
        Log.d(TAG, "App $packageName is blocked, blocking it")
        AppBlocker.blockApp(this, packageName, "App is blocked")
        return true
    }

    private fun handleCoolingPeriodOnLaunch(packageName: String): Boolean {
        if (!coolingPeriodRepository.isInCoolingPeriod(packageName)) return false
        
        val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(packageName) ?: return false
        Log.d(TAG, "App $packageName is in cooling period until $coolingEnd")
        monitoredAppsRepository.blockApp(packageName)
        showCoolingPeriodDialog(packageName, coolingEnd)
        AppBlocker.blockApp(this, packageName, "App is in cooling period")
        return true
    }

    private fun handleSessionOnLaunch(packageName: String) {
        val activeSession = sessionRepository.getSession(packageName)
        
        if (activeSession != null) {
            if (activeSession.isExpired()) {
                Log.d(TAG, "Active session found for $packageName: ${activeSession.requestedMinutes} minutes, expired: true")
                handleTimeUp(packageName, activeSession, isInForeground = true)
            } else {
                Log.d(TAG, "Session active for $packageName, ${activeSession.getRemainingMinutes()} minutes remaining")
            }
        } else {
            Log.d(TAG, "No active session for $packageName, showing time selection dialog")
            showTimeSelectionDialog(packageName)
        }
    }

    // Dialog Management
    private fun showTimeSelectionDialog(packageName: String) {
        Log.d(TAG, "showTimeSelectionDialog called for $packageName")
        handler?.post {
            try {
                val appName = AppNameResolver.getAppName(this@AppMonitorService, packageName)
                Log.d(TAG, "App name resolved: $appName, showing dialog on main thread")
                
                TimeSelectionDialog.show(this@AppMonitorService, packageName, appName) { selectedMinutes ->
                    handleTimeSelection(packageName, selectedMinutes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing time selection dialog for $packageName", e)
            }
        }
    }

    private fun handleTimeSelection(packageName: String, selectedMinutes: Int) {
        Log.d(TAG, "Time selected: $selectedMinutes minutes for $packageName")
        
        if (selectedMinutes > 0) {
            startNewSession(packageName, selectedMinutes)
        } else {
            Log.d(TAG, "User cancelled time selection, blocking app")
            AppBlocker.blockApp(this@AppMonitorService, packageName, "No time selected")
        }
    }

    private fun startNewSession(packageName: String, selectedMinutes: Int) {
        val config = configRepository.getConfig(packageName)
        val behavior = config["behavior"] as? String ?: BEHAVIOR_ASK
        
        val session = AppSession(
            packageName = packageName,
            startTime = System.currentTimeMillis(),
            requestedMinutes = selectedMinutes,
            behavior = behavior
        )
        sessionRepository.addSession(session)
        
        sendEventToReactNative(EVENT_SESSION_STARTED, createSessionMap(session))
        Log.d(TAG, "Started session for $packageName: $selectedMinutes minutes")
    }

    private fun showTimeExtensionDialog(packageName: String, session: AppSession) {
        Log.d(TAG, "Showing time extension dialog for $packageName (app is in foreground, behavior=ask)")
        handler?.post {
            try {
                val appName = AppNameResolver.getAppName(this@AppMonitorService, packageName)
                TimeSelectionDialog.show(this@AppMonitorService, packageName, appName, isExtension = true) { selectedMinutes ->
                    handleTimeExtension(packageName, session, selectedMinutes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing time extension dialog for $packageName", e)
            }
        }
    }

    private fun handleTimeExtension(packageName: String, session: AppSession, selectedMinutes: Int) {
        pendingTimeUpChecks.remove(packageName)
        
        if (selectedMinutes > 0) {
            extendSession(packageName, session, selectedMinutes)
        } else {
            handleUserDeclinedExtension(packageName, session)
        }
    }

    private fun extendSession(packageName: String, session: AppSession, selectedMinutes: Int) {
        val updatedSession = session.copy(
            startTime = System.currentTimeMillis(),
            requestedMinutes = selectedMinutes
        )
        sessionRepository.updateSession(packageName, updatedSession)
        sendEventToReactNative(EVENT_SESSION_EXTENDED, createSessionMap(updatedSession))
        Log.d(TAG, "Session extended for $packageName: $selectedMinutes minutes")
    }

    private fun handleUserDeclinedExtension(packageName: String, session: AppSession) {
        monitoredAppsRepository.blockApp(packageName)
        AppBlocker.blockApp(this@AppMonitorService, packageName, "User declined more time")
        blockAppAndStartCooling(packageName, session)
    }

    // Time Up Handling
    private fun handleTimeUp(packageName: String, session: AppSession, isInForeground: Boolean = false) {
        Log.d(TAG, "Time up for $packageName, behavior: ${session.behavior}, inForeground: $isInForeground")
        
        when (session.behavior) {
            BEHAVIOR_ASK -> handleAskBehaviorTimeUp(packageName, session, isInForeground)
            BEHAVIOR_STOP -> handleStopBehaviorTimeUp(packageName, session)
        }
    }

    private fun handleAskBehaviorTimeUp(packageName: String, session: AppSession, isInForeground: Boolean) {
        if (isInForeground && !monitoredAppsRepository.isBlocked(packageName)) {
            showTimeExtensionDialog(packageName, session)
        } else {
            handleAskBehaviorTimeUpInBackground(packageName, session)
        }
    }

    private fun handleAskBehaviorTimeUpInBackground(packageName: String, session: AppSession) {
        Log.d(TAG, "App $packageName not in foreground or already blocked, blocking immediately")
        pendingTimeUpChecks.remove(packageName)
        blockAppAndStartCooling(packageName, session)
    }

    private fun handleStopBehaviorTimeUp(packageName: String, session: AppSession) {
        pendingTimeUpChecks.remove(packageName)
        sessionRepository.removeSession(packageName)
        blockAppImmediately(packageName, "Time limit reached")
        scheduleReBlockingIfNeeded(packageName)
        blockAppAndStartCooling(packageName, session)
    }

    private fun blockAppImmediately(packageName: String, reason: String) {
        monitoredAppsRepository.blockApp(packageName)
        AppBlocker.blockApp(this, packageName, reason)
    }

    private fun scheduleReBlockingIfNeeded(packageName: String) {
        blockingHandler?.postDelayed({
            val currentApp = ForegroundAppDetector.getCurrentForegroundApp(usageStatsManager)
            if (currentApp == packageName) {
                Log.d(TAG, "App $packageName still in foreground after blocking, re-blocking")
                AppBlocker.blockApp(this, packageName, "Time limit reached - re-blocking")
            }
        }, RE_BLOCK_DELAY_MS)
    }

    // Cooling Period Management
    private fun blockAppAndStartCooling(packageName: String, session: AppSession) {
        val coolingMinutes = getCoolingPeriodMinutes(packageName)
        val coolingEnd = System.currentTimeMillis() + (coolingMinutes * 60 * 1000)
        
        coolingPeriodRepository.setCoolingPeriodEnd(packageName, coolingEnd)
        sessionRepository.removeSession(packageName)
        monitoredAppsRepository.blockApp(packageName)
        
        showCoolingPeriodDialog(packageName, coolingEnd)
        notifyCoolingPeriodStarted(packageName, coolingEnd, coolingMinutes)
        
        Log.d(TAG, "Started cooling period for $packageName: $coolingMinutes minutes")
    }

    private fun getCoolingPeriodMinutes(packageName: String): Int {
        val config = configRepository.getConfig(packageName)
        return (config["coolingPeriodMinutes"] as? Number)?.toInt() ?: DEFAULT_COOLING_MINUTES
    }

    private fun notifyCoolingPeriodStarted(packageName: String, coolingEnd: Long, coolingMinutes: Int) {
        sendEventToReactNative(EVENT_SET_COOLING_PERIOD, Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("endTime", coolingEnd.toDouble())
        })
        
        sendEventToReactNative(EVENT_COOLING_PERIOD_STARTED, Arguments.createMap().apply {
            putString("packageName", packageName)
            putDouble("coolingEndTime", coolingEnd.toDouble())
            putInt("coolingMinutes", coolingMinutes)
        })
    }

    private fun showCoolingPeriodDialog(packageName: String, coolingEndTime: Long) {
        val appName = AppNameResolver.getAppName(this, packageName)
        CoolingPeriodDialog.show(this, packageName, appName, coolingEndTime) {
            AppBlocker.blockApp(this, packageName, "User dismissed cooling period dialog")
        }
        
        scheduleCoolingPeriodEnd(packageName, coolingEndTime)
    }

    private fun scheduleCoolingPeriodEnd(packageName: String, coolingEndTime: Long) {
        val remainingTime = coolingEndTime - System.currentTimeMillis()
        if (remainingTime > 0) {
            blockingHandler?.postDelayed({
                endCoolingPeriod(packageName)
            }, remainingTime)
        }
    }

    private fun endCoolingPeriod(packageName: String) {
        CoolingPeriodDialog.dismiss()
        monitoredAppsRepository.unblockApp(packageName)
        coolingPeriodRepository.clearCoolingPeriod(packageName)
        Log.d(TAG, "Cooling period ended for $packageName")
    }

    // Session Expiration Checking
    private fun checkAllActiveSessions(currentForegroundApp: String? = null) {
        val expiredSessions = findExpiredSessions()
        
        expiredSessions.forEach { (packageName, session) ->
            if (currentForegroundApp == packageName) {
                handleExpiredSessionInForeground(packageName, session)
            } else {
                handleExpiredSessionInBackground(packageName, session)
            }
        }
    }

    private fun findExpiredSessions(): List<Pair<String, AppSession>> {
        val expiredSessions = mutableListOf<Pair<String, AppSession>>()
        
        sessionRepository.getAllSessions().forEach { (packageName, session) ->
            if (session.isExpired()) {
                expiredSessions.add(Pair(packageName, session))
                Log.d(TAG, "Session expired for $packageName: ${session.getElapsedMinutes()}m elapsed (limit: ${session.requestedMinutes}m)")
            }
        }
        
        return expiredSessions
    }

    private fun handleExpiredSessionInForeground(packageName: String, session: AppSession) {
        if (pendingTimeUpChecks.contains(packageName)) return
        
        Log.d(TAG, "Handling expired session for foreground app $packageName")
        pendingTimeUpChecks.add(packageName)
        handleTimeUp(packageName, session, isInForeground = true)
    }

    private fun handleExpiredSessionInBackground(packageName: String, session: AppSession) {
        if (pendingTimeUpChecks.contains(packageName)) return
        
        Log.d(TAG, "Session expired for closed app $packageName, blocking without dialog")
        pendingTimeUpChecks.add(packageName)
        
        blockAppAndStartCooling(packageName, session)
    }

    // Blocking Check Loop
    private fun startBlockingCheck() {
        blockingHandler?.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val currentForeground = ForegroundAppDetector.getCurrentForegroundApp(usageStatsManager)
                
                checkExpiredSessionsInBlockingLoop(currentForeground)
                enforceBlockedApps(currentForeground, now)
                unblockExpiredCoolingPeriods(now)
                
                blockingHandler?.postDelayed(this, BLOCKING_CHECK_INTERVAL)
            }
        })
    }

    private fun checkExpiredSessionsInBlockingLoop(currentForeground: String?) {
        sessionRepository.getAllSessions().forEach { (packageName, session) ->
            if (isSessionExpired(session) && !monitoredAppsRepository.isBlocked(packageName)) {
                val isInForeground = currentForeground == packageName
                Log.d(TAG, "Blocking check: Time expired for $packageName, in foreground: $isInForeground")
                
                if (!pendingTimeUpChecks.contains(packageName)) {
                    pendingTimeUpChecks.add(packageName)
                    handleTimeUp(packageName, session, isInForeground = isInForeground)
                }
            }
        }
    }

    private fun isSessionExpired(session: AppSession): Boolean {
        return session.getElapsedMs() >= session.getRequestedMs()
    }

    private fun enforceBlockedApps(currentForeground: String?, now: Long) {
        if (currentForeground == null) return
        if (!monitoredAppsRepository.isBlocked(currentForeground)) return
        
        val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(currentForeground)
        if (coolingEnd != null && coolingEnd > now) {
            showCoolingPeriodDialog(currentForeground, coolingEnd)
        }
        AppBlocker.blockApp(this@AppMonitorService, currentForeground, "App is blocked - continuous enforcement")
    }

    private fun unblockExpiredCoolingPeriods(now: Long) {
        val appsToUnblock = findAppsToUnblock(now)
        appsToUnblock.forEach { packageName ->
            monitoredAppsRepository.unblockApp(packageName)
            Log.d(TAG, "Unblocked app $packageName")
        }
    }

    private fun findAppsToUnblock(now: Long): List<String> {
        val appsToUnblock = mutableListOf<String>()
        
        monitoredAppsRepository.getBlockedApps().forEach { packageName ->
            val coolingEnd = coolingPeriodRepository.getCoolingPeriodEnd(packageName)
            if (coolingEnd == null || coolingEnd <= now) {
                if (shouldUnblockApp(packageName)) {
                    appsToUnblock.add(packageName)
                }
            }
        }
        
        return appsToUnblock
    }

    private fun shouldUnblockApp(packageName: String): Boolean {
        val session = sessionRepository.getSession(packageName)
        return session == null || session.getElapsedMinutes() < session.requestedMinutes
    }

    // React Native Communication
    private fun createSessionMap(session: AppSession): WritableMap {
        return Arguments.createMap().apply {
            putString("packageName", session.packageName)
            putDouble("startTime", session.startTime.toDouble())
            putInt("requestedMinutes", session.requestedMinutes)
            putString("behavior", session.behavior)
        }
    }

    private fun sendEventToReactNative(eventName: String, params: WritableMap) {
        try {
            reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit(eventName, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event to React Native: $eventName", e)
        }
    }
}
