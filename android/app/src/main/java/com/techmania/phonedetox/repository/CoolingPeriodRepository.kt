package com.techmania.phonedetox.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing cooling periods
 */
class CoolingPeriodRepository {
    private val coolingPeriods: MutableMap<String, Long> = ConcurrentHashMap()
    
    fun getCoolingPeriodEnd(packageName: String): Long? {
        return coolingPeriods[packageName]
    }
    
    fun setCoolingPeriodEnd(packageName: String, endTime: Long) {
        coolingPeriods[packageName] = endTime
    }
    
    fun clearCoolingPeriod(packageName: String) {
        coolingPeriods.remove(packageName)
    }
    
    fun isInCoolingPeriod(packageName: String): Boolean {
        val endTime = coolingPeriods[packageName] ?: return false
        return endTime > System.currentTimeMillis()
    }
    
    fun getExpiredCoolingPeriods(): List<String> {
        val now = System.currentTimeMillis()
        return coolingPeriods.filter { it.value <= now }.keys.toList()
    }
    
    fun clear() {
        coolingPeriods.clear()
    }
}

