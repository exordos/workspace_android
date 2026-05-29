package ru.genesiscorporation.workspace.beta

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionCookieStore {
    private val lock = Mutex()
    private var _sessionId: String? = null
    private var _fullSessionCookie: String? = null
    suspend fun setSessionId(value: String) = lock.withLock {
        _sessionId = value
    }
    suspend fun getSessionId(): String? = lock.withLock { _sessionId }


    suspend fun setFullSessionCookie(value: String) = lock.withLock {
        _fullSessionCookie = value
    }
    suspend fun getFullSessionCookie(): String? = lock.withLock { _fullSessionCookie }
}