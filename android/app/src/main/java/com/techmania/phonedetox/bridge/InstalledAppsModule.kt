package com.techmania.phonedetox.bridge

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.facebook.react.bridge.*

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
                if (isUserApp(appInfo)) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    
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
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        
        val coreSystemPackages = listOf(
            "android",
            "com.android"
        )
        
        val isCoreSystem = coreSystemPackages.any { appInfo.packageName.startsWith(it) }
        
        if (isCoreSystem && isSystemApp && !isUpdatedSystemApp) {
            return false
        }
        
        return true
    }
}

