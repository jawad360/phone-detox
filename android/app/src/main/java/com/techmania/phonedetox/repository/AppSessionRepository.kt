package com.techmania.phonedetox.repository

import com.techmania.phonedetox.model.AppSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing app monitoring sessions
 */
class AppSessionRepository {
    private val sessions: MutableMap<String, AppSession> = ConcurrentHashMap()
    
    fun getSession(packageName: String): AppSession? {
        return sessions[packageName]
    }
    
    fun getAllSessions(): Map<String, AppSession> {
        return sessions.toMap()
    }
    
    fun addSession(session: AppSession) {
        sessions[session.packageName] = session
    }
    
    fun removeSession(packageName: String) {
        sessions.remove(packageName)
    }
    
    fun updateSession(packageName: String, session: AppSession) {
        sessions[packageName] = session
    }
    
    fun getExpiredSessions(): List<AppSession> {
        return sessions.values.filter { it.isExpired() }
    }
    
    fun clear() {
        sessions.clear()
    }
}

