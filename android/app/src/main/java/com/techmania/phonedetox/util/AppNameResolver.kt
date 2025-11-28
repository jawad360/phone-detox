package com.techmania.phonedetox.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility class for resolving app names from package names
 */
object AppNameResolver {
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

