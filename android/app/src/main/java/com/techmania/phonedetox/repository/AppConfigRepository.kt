package com.techmania.phonedetox.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing app-specific configurations
 */
class AppConfigRepository {
    private val configs: MutableMap<String, Map<String, Any>> = ConcurrentHashMap()
    
    fun getConfig(packageName: String): Map<String, Any> {
        return configs[packageName] ?: getDefaultConfig()
    }
    
    fun updateConfig(packageName: String, config: Map<String, Any>) {
        configs[packageName] = config
    }
    
    fun setConfig(packageName: String, behavior: String? = null, coolingPeriodMinutes: Int? = null) {
        val current = getConfig(packageName)
        configs[packageName] = current.toMutableMap().apply {
            behavior?.let { put("behavior", it) }
            coolingPeriodMinutes?.let { put("coolingPeriodMinutes", it) }
        }
    }
    
    private fun getDefaultConfig(): Map<String, Any> {
        return mapOf(
            "behavior" to "ask",
            "coolingPeriodMinutes" to 30
        )
    }
    
    fun clear() {
        configs.clear()
    }
}

