package com.techmania.phonedetox

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class InstalledAppsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "InstalledApps"
    }

    @ReactMethod
    fun getApps(promise: Promise) {
        try {
            val packageManager = reactApplicationContext.packageManager
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val appList = WritableNativeArray()
            
            for (appInfo in apps) {
                // Filter out system apps and our own app
                if (isUserApp(appInfo)) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    
                    // Skip our own app
                    if (packageName == reactApplicationContext.packageName) {
                        continue
                    }
                    
                    val appMap = WritableNativeMap().apply {
                        putString("packageName", packageName)
                        putString("appName", appName)
                    }
                    appList.pushMap(appMap)
                }
            }
            
            promise.resolve(appList)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to get installed apps: ${e.message}", e)
        }
    }

    private fun isUserApp(appInfo: ApplicationInfo): Boolean {
        // Check if it's a system app that cannot be uninstalled
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        
        // Exclude only core system apps that cannot be disabled/uninstalled
        // Include:
        // - User-installed apps (!isSystemApp)
        // - Updated system apps (can be uninstalled)
        // - System apps that can be disabled (most Google apps fall here)
        
        // Core system apps to exclude (these cannot be monitored anyway)
        val coreSystemPackages = listOf(
            "android",
            "com.android.systemui",
            "com.android.internal",
            "com.android.settings",
            "com.android.phone",
            "com.android.providers",
            "com.android.server",
            "com.android.keychain"
        )
        
        // Check if it's a core system package
        val isCoreSystem = coreSystemPackages.any { appInfo.packageName.startsWith(it) }
        
        // Exclude core system apps that cannot be disabled
        if (isCoreSystem && isSystemApp && !isUpdatedSystemApp) {
            return false
        }
        
        // Include everything else (user apps, updated system apps, and most pre-installed apps)
        return true
    }
}

