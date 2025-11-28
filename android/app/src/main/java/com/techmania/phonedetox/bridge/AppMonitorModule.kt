package com.techmania.phonedetox.bridge

import android.content.Intent
import com.facebook.react.bridge.*
import com.techmania.phonedetox.service.AppMonitorService

class AppMonitorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    init {
        val service = AppMonitorService.getInstance()
        service?.setReactContext(reactContext)
    }

    override fun getName(): String {
        return "AppMonitor"
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    fun startMonitoring() {
        val intent = Intent(reactApplicationContext, AppMonitorService::class.java).apply {
            action = AppMonitorService.ACTION_START_MONITORING
        }
        reactApplicationContext.startForegroundService(intent)
    }

    @ReactMethod
    fun stopMonitoring() {
        val intent = Intent(reactApplicationContext, AppMonitorService::class.java).apply {
            action = AppMonitorService.ACTION_STOP_MONITORING
        }
        reactApplicationContext.startService(intent)
    }

    @ReactMethod
    fun updateMonitoredApps(apps: ReadableArray) {
        val packageNames = mutableListOf<String>()
        for (i in 0 until apps.size()) {
            val app = apps.getMap(i)
            val packageName = app?.getString("packageName")
            if (packageName != null) {
                packageNames.add(packageName)
            }
        }
        
        val intent = Intent(reactApplicationContext, AppMonitorService::class.java).apply {
            action = AppMonitorService.ACTION_UPDATE_MONITORED_APPS
            putStringArrayListExtra(AppMonitorService.EXTRA_APPS, ArrayList(packageNames))
        }
        reactApplicationContext.startService(intent)
    }

    @ReactMethod
    fun updateAppConfig(packageName: String, config: ReadableMap) {
        val service = AppMonitorService.getInstance()
        val configMap = mutableMapOf<String, Any>()
        
        if (config.hasKey("behavior")) {
            configMap["behavior"] = config.getString("behavior") ?: "ask"
        }
        if (config.hasKey("coolingPeriodMinutes")) {
            configMap["coolingPeriodMinutes"] = config.getInt("coolingPeriodMinutes")
        }
        
        service?.updateAppConfig(packageName, configMap)
    }

    @ReactMethod
    fun setCoolingPeriod(packageName: String, endTime: Double) {
        val service = AppMonitorService.getInstance()
        service?.setCoolingPeriodEnd(packageName, endTime.toLong())
    }

    @ReactMethod
    fun getActiveSession(packageName: String, promise: Promise) {
        try {
            val service = AppMonitorService.getInstance()
            val session = service?.getActiveSession(packageName)
            
            if (session != null) {
                val sessionMap = Arguments.createMap().apply {
                    putString("packageName", session.packageName)
                    putDouble("startTime", session.startTime.toDouble())
                    putInt("requestedMinutes", session.requestedMinutes)
                    putString("behavior", session.behavior)
                }
                promise.resolve(sessionMap)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to get active session: ${e.message}", e)
        }
    }
}

