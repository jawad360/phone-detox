package com.techmania.phonedetox.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Utility class for blocking apps
 */
object AppBlocker {
    private const val TAG = "AppBlocker"
    
    fun blockApp(context: Context, packageName: String, reason: String) {
        try {
            Log.d(TAG, "Blocking app $packageName: $reason")
            
            // Method 1: Move app to background by launching home screen
            launchHomeScreen(context)
            
            // Method 2: Try to kill the app process
            killAppProcess(context, packageName)
            
            // Method 3: Try to force stop (requires system permission)
            forceStopApp(context, packageName)
            
            Log.d(TAG, "Successfully blocked app $packageName: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking app $packageName: ${e.message}", e)
        }
    }
    
    private fun launchHomeScreen(context: Context) {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.d(TAG, "Could not launch home screen: ${e.message}")
        }
    }
    
    private fun killAppProcess(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses
            
            runningApps?.forEach { processInfo ->
                processInfo.pkgList?.forEach { pkg ->
                    if (pkg == packageName) {
                        try {
                            android.os.Process.killProcess(processInfo.pid)
                            Log.d(TAG, "Killed process for $packageName (PID: ${processInfo.pid})")
                        } catch (e: Exception) {
                            Log.d(TAG, "Could not kill process ${processInfo.pid}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not kill app process: ${e.message}")
        }
    }
    
    private fun forceStopApp(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = activityManager.javaClass.getDeclaredMethod("forceStopPackage", String::class.java)
            method.isAccessible = true
            method.invoke(activityManager, packageName)
            Log.d(TAG, "Force stopped app $packageName")
        } catch (e: Exception) {
            Log.d(TAG, "Could not force stop app (expected without system permissions): ${e.message}")
        }
    }
}

