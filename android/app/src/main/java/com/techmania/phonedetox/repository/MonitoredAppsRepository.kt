package com.techmania.phonedetox.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing monitored and blocked apps
 */
class MonitoredAppsRepository {
    private val monitoredApps: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val blockedApps: MutableSet<String> = ConcurrentHashMap.newKeySet()
    
    fun getMonitoredApps(): Set<String> {
        return monitoredApps.toSet()
    }
    
    fun setMonitoredApps(packageNames: Set<String>) {
        monitoredApps.clear()
        monitoredApps.addAll(packageNames)
    }
    
    fun isMonitored(packageName: String): Boolean {
        return monitoredApps.contains(packageName)
    }
    
    fun blockApp(packageName: String) {
        blockedApps.add(packageName)
    }
    
    fun unblockApp(packageName: String) {
        blockedApps.remove(packageName)
    }
    
    fun isBlocked(packageName: String): Boolean {
        return blockedApps.contains(packageName)
    }
    
    fun getBlockedApps(): Set<String> {
        return blockedApps.toSet()
    }
    
    fun clear() {
        monitoredApps.clear()
        blockedApps.clear()
    }
}

