package com.techmania.phonedetox.model

/**
 * Represents an active app monitoring session
 */
data class AppSession(
    val packageName: String,
    val startTime: Long,
    val requestedMinutes: Int,
    val behavior: String // "ask" or "stop"
) {
    fun getElapsedMinutes(): Int {
        return ((System.currentTimeMillis() - startTime) / 60000).toInt()
    }
    
    fun getRemainingMinutes(): Int {
        return maxOf(0, requestedMinutes - getElapsedMinutes())
    }
    
    fun isExpired(): Boolean {
        return getElapsedMinutes() >= requestedMinutes
    }
    
    fun getElapsedMs(): Long {
        return System.currentTimeMillis() - startTime
    }
    
    fun getRequestedMs(): Long {
        return requestedMinutes * 60 * 1000L
    }
}

